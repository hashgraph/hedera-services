/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.virtualmap.internal.cache;

import com.swirlds.common.threading.futures.StandardFuture;
import java.util.Arrays;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * An array-backed concurrent data structure optimized for use by the {@link VirtualNodeCache}.
 * <p>
 * This class has been carefully designed to enable a high rate of concurrency (mostly lock-free)
 * with a minimum of array copying overhead. We were not able to avoid all array copies (see the
 * implementation of {@link #sortedStream(Comparator)}. If we could have found a better way to do
 * concurrent sorting without needing to rewrite a pile of highly optimized sort code from the JDK
 * or an array copy, we would have done it). This class has been very careful to be defensive and
 * assert which operations are safe to execute concurrently on mutable instances and which require
 * immutable instances. Please see the documentation for each field and method to get a deeper
 * understanding of those conditions.
 * <p>
 * A {@link ConcurrentArray} is created with a {@code capacity}. The capacity is used to size the
 * underlying arrays and to indicate the growth rate. When the underlying storage must be expanded,
 * this class does not double the size of the storage, it simply increments by {@code capacity}.
 * <p>
 * Typically, an instance is created and then {@link #add(Object)} is called repeatedly. When the
 * {@link VirtualNodeCache} wishes to merge two arrays together, it will create a new {@link ConcurrentArray}
 * by supplying the two previous arrays to it via {@link #ConcurrentArray(ConcurrentArray, ConcurrentArray)}.
 * The source arrays for this operation <strong>must</strong> be immutable. An array is made immutable
 * by calling {@link #seal()}.
 * <p>
 * After all elements have been added, the {@link #sortedStream(Comparator)} method can be called to get
 * a sorted stream of all array elements. Since this operation is incompatible with concurrent calls to
 * {@link #add(Object)}, the instance must first be sealed {@link #seal()} before calling {@code sortedStream}.
 * This is a safety precaution to guard against accidental sorting of a mutable {@link ConcurrentArray}.
 *
 * @param <T>
 * 		the element type
 */
final class ConcurrentArray<T> {
    /**
     * The default number of elements to store in each array
     */
    private static final int DEFAULT_ELEMENT_ARRAY_LENGTH = 1024;

    /**
     * A concurrent linked deque of arrays. Initially, the {@link ConcurrentArray} has a single
     * sub-array sized to either the capacity provided in the constructor or {@link #DEFAULT_ELEMENT_ARRAY_LENGTH}.
     * As elements are added, they eventually fill up the sub-array, and it becomes necessary to create
     * a new sub-array and add it to the list.
     * <p>
     * A naive implementation of this class may have attempted to use a single array and, using synchronization,
     * resize the array. Indeed, this would be relatively easy to code. However, we found a few issues with this.
     * First, it required a rather coarse-grained lock which was bad for performance. Second, it required large
     * array copies, which hurt performance.
     * <p>
     * Instead, we have a concurrent linked dequeue of smaller "sub" arrays. Whenever we need to grow capacity,
     * rather than creating a new larger array and copying all the elements out of the old array into the new one,
     * we simply create a new array. Whenever we need to "add all" from another {@link ConcurrentArray} into this
     * one, we just add a few pointers to the concurrent linked dequeue and avoid all array copies.
     * <p>
     * To make this safe, it is imperative that <strong>only</strong> an immutable {@link ConcurrentArray} is the
     * source of a copy, so that the destination array can safely refer to the sub-arrays of the source.
     */
    private final ConcurrentLinkedDeque<SubArray<T>> arrays = new ConcurrentLinkedDeque<>();

    /**
     * A thread-safe field holding the number of elements (that is, the number of actual elements in all sub-arrays).
     * Under thread contention, the count will be eventually consistent. That is, we add elements to the sub-arrays
     * first, and then update the elementCount. You are guaranteed that your element will be included in the count
     * by the end of the {@link #add(Object)} method.
     */
    private final AtomicInteger elementCount = new AtomicInteger(0);

    /**
     * True when this class has finished being written to and is immutable. Once the class has been "sealed" it is
     * no longer mutable. Only immutable {@link ConcurrentArray}s can be merged into other arrays or sorted.
     */
    private final AtomicBoolean immutable = new AtomicBoolean(false);

    /**
     * Defines the size of each sub-array created by this {@link ConcurrentArray}. This is a fixed-cost overhead
     * for a sub-array. If the value is too small and there are many elements, then you will have many sub-arrays.
     * If the value is too large, and you have few elements, you will waste RAM. It is wise to set this number to
     * be large enough to handle the maximum expected load based on the TPS throttle in the system. This value
     * must <strong>always</strong> be greater than zero.
     */
    private final int subarrayCapacity;

    /**
     * Create a new {@link ConcurrentArray} with the capacity set to {@link #DEFAULT_ELEMENT_ARRAY_LENGTH}.
     */
    ConcurrentArray() {
        this(DEFAULT_ELEMENT_ARRAY_LENGTH);
    }

    /**
     * Create a new {@link ConcurrentArray} with the given size for the capacity.
     *
     * @param capacity
     * 		The initial amount of memory to allocate to the array, and the size by which the array is
     * 		increment each time it must be expanded. The larger the value, the less thread contention
     * 		while populating the array, but the more wasted memory if the number of elements is small.
     * @throws IllegalArgumentException
     * 		If the capacity is specified to be less than or equal to zero.
     */
    ConcurrentArray(int capacity) {
        // Negative and zero length capacities are bad. Negative will clearly raise an exception later,
        // and zero is useless because it would mean I could never add any elements. Maybe that is OK?
        // But it seems more likely that a 0 capacity would be a bug. Better to throw.
        if (capacity <= 0) {
            throw new IllegalArgumentException("The capacity must be > 0");
        }

        // Add the first array, so we are primed and ready to go. This also saves on a check later for an empty
        // concurrent linked dequeue (we always know there is at least one sub-array).
        this.subarrayCapacity = capacity;
        arrays.add(new SubArray<>(capacity));
    }

    /**
     * Create a new {@link ConcurrentArray} with the contents of two other {@link ConcurrentArray}s.
     * <strong>IMPORTANT!</strong> this does <strong>NOT</strong> copy the array data out of the source
     * {@link ConcurrentArray}s. It directly reuses the data. Both of the source arrays must be immutable.
     * This array will be <strong>sealed</strong>. It cannot be mutated after merging.
     *
     * @param arrayOne
     * 		The first array to take data from. Cannot be null. Must be immutable.
     * @param arrayTwo
     * 		The second array to take data from. Cannot be null. Must be immutable.
     * @throws NullPointerException
     * 		if either array is null
     * @throws IllegalArgumentException
     * 		if either array is mutable, or if they are the same instance.
     */
    ConcurrentArray(ConcurrentArray<T> arrayOne, ConcurrentArray<T> arrayTwo) {
        Objects.requireNonNull(arrayOne);
        Objects.requireNonNull(arrayTwo);

        // We don't allow either to be mutable
        if (!arrayOne.isImmutable() || !arrayTwo.isImmutable()) {
            throw new IllegalArgumentException("The source arrays *must* be immutable");
        }

        // OK, this is just being really picky. But don't let them be the same either. That's just weird.
        // If both are the same, it is probably a bug. If we needed to support this, we could relax
        // the restriction and just accept one of the two.
        if (arrayOne == arrayTwo) {
            throw new IllegalArgumentException("Both arguments should be distinct instances");
        }

        // Copy the pointers to the arrays of arrayOne and arrayTwo into the dequeue. We know
        // that both sources are immutable, so we can do this safely.
        this.subarrayCapacity = 1;
        arrays.addAll(arrayOne.arrays);
        arrays.addAll(arrayTwo.arrays);
        elementCount.addAndGet(arrayOne.size() + arrayTwo.size());
        immutable.set(true);
    }

    /**
     * Make this class immutable.
     *
     * @return A self-reference. Useful for chaining.
     */
    ConcurrentArray<T> seal() {
        immutable.set(true);
        return this;
    }

    /**
     * Get whether this instance is immutable.
     *
     * @return true if this instance is immutable
     */
    boolean isImmutable() {
        return immutable.get();
    }

    /**
     * Gets the number of elements in this data structure. This size is accurate at the time of
     * the call, but may not reflect the size at a subsequent time due to the multi-threaded
     * nature of this API. The size can only grow larger, <strong>never</strong> become smaller.
     *
     * @return A non-negative number of elements.
     */
    int size() {
        return elementCount.get();
    }

    /**
     * Get the value at the given index. Used for debugging.
     *
     * @param index
     * 		The index from which to get data.
     * @return
     * 		The data.
     */
    T get(final int index) {
        final int size = size();
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index " + index + " is out of bounds (0, " + size + "]");
        }

        int count = 0;
        for (final SubArray<T> arr : arrays) {
            final int arrSize = arr.size.get();
            if (index >= count + arrSize) {
                count += arrSize;
            } else {
                return arr.get(index - count);
            }
        }

        throw new NoSuchElementException("Not able to find an element by that index");
    }

    /**
     * Adds the given element to the end of the array. Sets the sorted state to false if it had been true.
     *
     * @param element
     * 		The element to add. Cannot be null.
     * @throws IllegalStateException
     * 		If this instance is immutable
     */
    void add(T element) {
        Objects.requireNonNull(element); // Or use assert here? It really should be validated by VirtualNodeCache...
        if (immutable.get()) {
            throw new IllegalStateException("You can not call add on a immutable ConcurrentArray");
        }

        // The sub-arrays in the dequeue all considered full except for the very last one. If the very last one is
        // also full, then add a new sub-array. We want to avoid locking for normal operations, and only
        // do so if the sub-array is full. So we will get the last sub-array, try to add to it, and if we fail
        // to do so, then we will acquire the lock to create a new sub-array.
        SubArray<T> lastArray = arrays.getLast();
        boolean success = lastArray.add(element);
        if (!success) {
            synchronized (this) {
                // Within this lock we need to create a new sub-array. Unless in a race another thread has already
                // entered this critical section and created the sub array. So we will once again get the last
                // sub-array from the dequeue, try to add to it, and determine if we need to add a new sub-array.
                lastArray = arrays.getLast();
                success = lastArray.add(element);
                if (!success) {
                    // Create the sub-array
                    final SubArray<T> newArray = new SubArray<>(subarrayCapacity);
                    // Add the element
                    success = newArray.add(element);
                    // This must always be true! Capacity is always strictly greater than zero, and
                    // we hold the lock, so the above add operation should always succeed.
                    assert success;
                    // Now, and only now, can we add the array to the dequeue. As soon as we add it,
                    // other threads can come along and add items to the array. If we were to put this
                    // into the dequeue too soon, then the array could be filled up before our thread
                    // has a chance, causing the above assertion to fail.
                    arrays.add(newArray);
                }
            }
        }

        // we succeeded in adding so increment count
        elementCount.incrementAndGet();
    }

    /**
     * Get a sorted stream of all elements. The comparator will be used to sort the elements.
     * If the comparator is null, then the natural ordering of the elements will be used.
     * This method can only be called on immutable instances. It may be called concurrently.
     *
     * @param comparator
     * 		The comparator to use to sort the elements. May be null, in which
     * 		case natural ordering is used.
     * @return A non-null Stream over all elements.
     * @throws IllegalStateException
     * 		If this instance is not immutable.
     */
    Stream<T> sortedStream(Comparator<T> comparator) {
        if (!immutable.get()) {
            throw new IllegalStateException("You can not call sortedStream on a mutable ConcurrentArray");
        }

        // A quick exit: if there are no elements, then return an empty stream.
        final int numberOfElements = this.elementCount.get();
        if (numberOfElements == 0) {
            return Stream.empty();
        }

        // Convert the sub-arrays into a single array and sort it. We considered numerous alternatives to try to
        // avoid the array copies, but we could not find a more efficient solution. Lock so that we can safely combine
        // the arrays and sort the result.
        final SubArray<T> newArray = new SubArray<>(numberOfElements);
        synchronized (this) {
            // Copy all the arrays to one new array
            int nextIndex = 0;
            for (final SubArray<T> array : arrays) {
                final int arraySize = array.size.get();
                System.arraycopy(array.array, 0, newArray.array, nextIndex, arraySize);
                nextIndex += arraySize;
            }
            newArray.size.set(numberOfElements);
            arrays.clear();
            arrays.add(newArray);

            // Now sort
            Arrays.parallelSort(newArray.array, 0, numberOfElements, comparator);
        }
        // Create and return the stream
        return Arrays.stream(newArray.array);
    }

    public StandardFuture<Void> parallelTraverse(Executor executor, Consumer<T> action) {
        if (!isImmutable()) {
            throw new IllegalArgumentException("You can not call parallelTraverse on a mutable ConcurrentArray");
        }

        final StandardFuture<Void> result = new StandardFuture<>();
        final AtomicInteger count = new AtomicInteger(1);
        for (SubArray<T> subArray : arrays) {
            count.incrementAndGet();
            executor.execute(() -> {
                T[] array = subArray.array;
                int size = subArray.size.get();
                for (int i = 0; i < size; ++i) {
                    action.accept(array[i]);
                }
                if (count.decrementAndGet() == 0) {
                    result.complete(null);
                }
            });
        }
        if (count.decrementAndGet() == 0) {
            result.complete(null);
        }
        return result;
    }

    /**
     * Simple struct for an array of elements and a size for the number of elements stored in that array.
     */
    private static class SubArray<T> {
        private final T[] array;
        private final AtomicInteger size = new AtomicInteger(0);

        @SuppressWarnings("unchecked")
        public SubArray(int capacity) {
            this.array = (T[]) new Object[capacity];
        }

        /**
         * Get the value at a specific index. Used for debugging.
         *
         * @param index
         * 		The index to get data from.
         * @return
         * 		The data.
         */
        T get(final int index) {
            return array[index];
        }

        /**
         * Adds the given element to this sub-array if there is room. If not,
         * return false. This method may be called by multiple threads concurrently.
         *
         * @param element
         * 		The element to add.
         * @return true if the element was added, otherwise false.
         */
        boolean add(T element) {
            // Atomically get the new insertion index. This index may exceed the capacity of the
            // sub-array, in which case we will return false from this method. If that happens,
            // we will need to decrement the size to keep it correct.
            final int index = size.getAndIncrement();
            if (index >= array.length) {
                size.decrementAndGet(); // keep size correct
                return false;
            }

            // Note that this assignment does not need to be volatile because, while it is true
            // that multiple threads are making assignments to the array, we only access the array
            // elements within a synchronized block, which forms a memory barrier. So we are assured
            // of having all the array elements visible to our thread at the time we go to read the
            // values.
            array[index] = element;
            return true;
        }
    }
}
