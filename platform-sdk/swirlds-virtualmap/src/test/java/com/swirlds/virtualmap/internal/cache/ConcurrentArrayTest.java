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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.test.framework.TestQualifierTags;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

/**
 *
 */
class ConcurrentArrayTest {

    private final ArrayList<Runnable> cleanupTasks = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Runnable task : cleanupTasks) {
            task.run();
        }
        cleanupTasks.clear();
    }

    /**
     * According to the specification, the capacity cannot be zero. A zero capacity is useless because
     * it means that I can never add any items to the array!
     */
    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("The capacity cannot be negative")
    void negativeCapacityThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ConcurrentArray<String>(-1), "Expected IAE");
    }

    /**
     * A capacity can clearly not be negative either.
     */
    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("The capacity cannot be zero")
    void zeroCapacityThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ConcurrentArray<String>(0), "Expected IAE");
    }

    /**
     * Just make sure it is possible to create a default array. The only way this goes wrong is if the
     * DEFAULT_ELEMENT_ARRAY_LENGTH were to ever be set to 0 or -1.
     */
    @Test(/* no exception expected */ )
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("Verify that we can use the default constructor")
    void defaultConstructor() {
        final ConcurrentArray<String> array = new ConcurrentArray<>();
        assertEquals(0, array.size(), "Checking array could be created");
    }

    /**
     * I should not be able to pass null as an argument to merge.
     */
    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("Null is not a valid input for merge")
    void mergeConstructorCannotTakeNull() {
        final ConcurrentArray<String> source = new ConcurrentArray<>(10);
        source.seal();
        assertThrows(NullPointerException.class, () -> source.merge(null), "Expected NPE");
    }

    /**
     * Neither array can be mutable when merged.
     */
    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("ConcurrentArrays must be immutable when merged")
    void mergeConstructorCannotTakeMutableSources() {
        final ConcurrentArray<String> mutableSource1 = new ConcurrentArray<>(10);
        final ConcurrentArray<String> immutableSource = new ConcurrentArray<>(10);
        immutableSource.seal();
        final ConcurrentArray<String> mutableSource2 = new ConcurrentArray<>(10);

        assertThrows(
                IllegalArgumentException.class,
                () -> immutableSource.merge(mutableSource1),
                "Expected IAE when merging with a mutable source");
        assertThrows(
                IllegalArgumentException.class,
                () -> mutableSource2.merge(immutableSource),
                "Expected IAE when merging a mutable array");
    }

    /**
     * Don't feed the same instance as both sources to the merge constructor. That's just weird.
     */
    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("Merged ConcurrentArrays must be unique")
    void mergeConstructorArgsMustBeUniqueInstances() {
        final ConcurrentArray<String> immutableSource = new ConcurrentArray<>(10);
        immutableSource.seal();
        assertThrows(
                IllegalArgumentException.class,
                () -> immutableSource.merge(immutableSource),
                "Expected IAE when passing same source twice");
    }

    /**
     * A merge constructor taking two sources to merge must end up with exactly the correct number
     * of entries, and when receiving a sorted stream, must exhibit exactly the right elements in the right order.
     * I'm going to test the case where the two source arrays have DIFFERENT capacities, and their elements are
     * all mixed up and need to be sorted properly.
     */
    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("Merged ConcurrentArrays must have the right elements")
    void mergedArrayHasTheRightElements() {
        final ConcurrentArray<String> a = new ConcurrentArray<>(5);
        a.add("Element 3");
        a.add("Element D");
        a.add("Element 4");
        a.add("Element 6");
        // Let 5 be left off
        a.seal();

        final ConcurrentArray<String> b = new ConcurrentArray<>(10);
        b.add("Element F");
        b.add("Element 2");
        b.add("Element 7");
        b.add("Element B");
        b.add("Element 8");
        b.add("Element 1");
        b.add("Element C");
        b.add("Element A");
        b.add("Element E");
        b.add("Element 9");
        b.seal();

        // Merge. I should have 9 elements, and 5 should not be one of them.
        b.merge(a);
        assertEquals(14, b.size(), "Wrong value");

        final List<String> elements = b.sortedStream(null).collect(Collectors.toList());

        assertEquals("Element 1", elements.get(0), "Wrong value");
        assertEquals("Element 2", elements.get(1), "Wrong value");
        assertEquals("Element 3", elements.get(2), "Wrong value");
        assertEquals("Element 4", elements.get(3), "Wrong value");
        assertEquals("Element 6", elements.get(4), "Wrong value");
        assertEquals("Element 7", elements.get(5), "Wrong value");
        assertEquals("Element 8", elements.get(6), "Wrong value");
        assertEquals("Element 9", elements.get(7), "Wrong value");
        assertEquals("Element A", elements.get(8), "Wrong value");
        assertEquals("Element B", elements.get(9), "Wrong value");
        assertEquals("Element C", elements.get(10), "Wrong value");
        assertEquals("Element D", elements.get(11), "Wrong value");
        assertEquals("Element E", elements.get(12), "Wrong value");
        assertEquals("Element F", elements.get(13), "Wrong value");
    }

    /**
     * Merged concurrent arrqys are immutable
     */
    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("Merged concurrent arrays are immutable")
    void mergedArraysAreImmutable() {
        final ConcurrentArray<String> a = new ConcurrentArray<>(5);
        a.add("Element 1");
        a.add("Element 2");
        a.seal();

        // Make sure there is an empty space in this array for the test to be valid
        final ConcurrentArray<String> b = new ConcurrentArray<>(5);
        b.add("Element 5");
        b.add("Element 6");
        b.seal();

        b.merge(a);
        assertTrue(b.isImmutable(), "Expected array to be immutable");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("Multiple merge")
    void reusingTheSameSourceArrayIsOK() {
        final ConcurrentArray<String> a = new ConcurrentArray<>(5);
        a.add("Element 1");
        a.add("Element 2");
        a.add("Element 3");
        a.add("Element 4");
        a.seal();

        final ConcurrentArray<String> b = new ConcurrentArray<>(5);
        b.add("Element 5");
        b.add("Element 6");
        b.add("Element 7");
        b.add("Element 8");
        b.seal();

        final ConcurrentArray<String> c = new ConcurrentArray<>(5);
        c.add("Element A");
        c.add("Element B");
        c.add("Element C");
        c.add("Element D");
        c.seal();

        // Merge a and b together and validate the result
        b.merge(a);
        assertEquals(8, b.size(), "Wrong value");
        List<String> elements = b.sortedStream(null).collect(Collectors.toList());

        assertEquals(8, elements.size(), "Wrong value");
        assertEquals("Element 1", elements.get(0), "Wrong value");
        assertEquals("Element 2", elements.get(1), "Wrong value");
        assertEquals("Element 3", elements.get(2), "Wrong value");
        assertEquals("Element 4", elements.get(3), "Wrong value");
        assertEquals("Element 5", elements.get(4), "Wrong value");
        assertEquals("Element 6", elements.get(5), "Wrong value");
        assertEquals("Element 7", elements.get(6), "Wrong value");
        assertEquals("Element 8", elements.get(7), "Wrong value");

        // Merge a and c and validate the result.
        c.merge(b);
        assertEquals(12, c.size(), "Wrong value");
        elements = c.sortedStream(null).collect(Collectors.toList());

        assertEquals(12, elements.size(), "Wrong value");
        assertEquals("Element 1", elements.get(0), "Wrong value");
        assertEquals("Element 2", elements.get(1), "Wrong value");
        assertEquals("Element 3", elements.get(2), "Wrong value");
        assertEquals("Element 4", elements.get(3), "Wrong value");
        assertEquals("Element 5", elements.get(4), "Wrong value");
        assertEquals("Element 6", elements.get(5), "Wrong value");
        assertEquals("Element 7", elements.get(6), "Wrong value");
        assertEquals("Element 8", elements.get(7), "Wrong value");
        assertEquals("Element A", elements.get(8), "Wrong value");
        assertEquals("Element B", elements.get(9), "Wrong value");
        assertEquals("Element C", elements.get(10), "Wrong value");
        assertEquals("Element D", elements.get(11), "Wrong value");
    }

    /**
     * Other tests indirectly validate this claim, but I thought it worth testing this explicitly.
     */
    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("Invoking seal makes an array immutable")
    void sealMakesImmutable() {
        final ConcurrentArray<String> arr = new ConcurrentArray<>(10);
        arr.seal();
        assertTrue(arr.isImmutable(), "Sealed array should be immutable");
    }

    /**
     * An empty (newly created) array should always return 0 as the size, regardless of the capacity.
     */
    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("Empty arrays have a size of 0")
    void sizeOfEmptyArray() {
        for (int i = 1; i <= 10; i++) {
            assertEquals(0, new ConcurrentArray<String>(i).size(), "empty array should have size 0");
        }
    }

    /**
     * Given a small initial capacity, add many items, forcing new sub-arrays to be allocated.
     * Verify that the size always matches what we expect. Initially, size is less than
     * the capacity, but at some point, it will start to exceed the capacity, and that is OK.
     */
    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("The size matches the actual number of elements in the array")
    void sizeOfPopulatedArray() {
        // This array is sized such that it will need to grow
        final ConcurrentArray<Integer> arr = new ConcurrentArray<>(10);
        for (int i = 0; i < 100; i++) {
            arr.add(i);
            assertEquals(i + 1, arr.size(), "Wrong value");
        }
    }

    /**
     * It should not be possible to add a null element
     */
    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("You cannot add a null element")
    void cannotAddNullElements() {
        final ConcurrentArray<String> arr = new ConcurrentArray<>(10);
        assertThrows(NullPointerException.class, () -> arr.add(null), "Expected NPE on null add");
    }

    /**
     * It should not be possible to add any element to an immutable array.
     */
    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("You cannot add to an immutable array")
    void cannotAddToImmutableArray() {
        final ConcurrentArray<String> arr = new ConcurrentArray<>(10);
        arr.seal();
        assertThrows(IllegalStateException.class, () -> arr.add("Anything"), "expected IAE on add to immutable array");
    }

    /**
     * This test is irritating because it is non-deterministic. I don't know how to make it
     * totally deterministic. So it may pass for months and then fail. However, if it *DOES*
     * fail, it means there is a REAL PROBLEM and must not be swept under the rug.
     */
    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("Test adding items from multiple threads")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void concurrentAdd() throws InterruptedException {
        // This small capacity and large number of concurrent threads was chosen specifically
        // to increase the thread contention so that multiple threads are almost guaranteed
        // to try to expand the array at the same time. *Kronk voice*: Brutal.
        final int capacity = 10;
        final int numThreads = 50;
        final int numElements = numThreads * 100_000;

        // Create the array. Create a countdown latch set to the number of elements.
        // Fire off each thread to add their element. When the element is added, the
        // thread will count down the latch. If all goes well, we will end up with
        // 0 for the latch.
        final ConcurrentArray<Integer> arr = new ConcurrentArray<>(capacity);
        final ExecutorService e = Executors.newFixedThreadPool(numThreads);
        cleanupTasks.add(e::shutdownNow);
        final CountDownLatch latch = new CountDownLatch(numElements);
        for (int i = 0; i < numElements; i++) {
            final int value = i;
            e.submit(() -> {
                arr.add(value);
                latch.countDown();
            });
        }

        // Wait for the latch to reach 0 (indicating all threads have finished). If we wait
        // for more than a minute, the test fails. It should take no more than a few seconds.
        assertTrue(latch.await(1, TimeUnit.MINUTES), "Timed out. Something went wrong.");

        // Check that the size is as we expect, despite so many threads hammering on it concurrently.
        assertEquals(numElements, arr.size(), "Wrong value");

        // Check that every element is there, and in order.
        final AtomicInteger expected = new AtomicInteger(0);
        arr.seal().sortedStream(null).forEach(value -> assertEquals(expected.getAndIncrement(), value, "Wrong value"));
        assertEquals(numElements, expected.get(), "Wrong value");
    }

    /**
     * You cannot call sortedStream on a mutable array (if you could, we'd have to have more sophisticated
     * locking logic to prevent "add" while we do the sorting, which would slow things down).
     */
    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("Verify you cannot call sortedStream on a mutable array")
    void callingSortedStreamOnMutableArrayThrows() {
        final ConcurrentArray<String> arr = new ConcurrentArray<>();
        assertThrows(
                IllegalStateException.class,
                () -> arr.sortedStream(null),
                "Expected IAE when sorting a mutable array with no elements");

        arr.add("Element 5");
        arr.add("Element 2");
        arr.add("Element 8");
        assertThrows(
                IllegalStateException.class,
                () -> arr.sortedStream(null),
                "Expected IAE when sorting a mutable array with elements");
    }

    /**
     * Calling sort on an empty, immutable array is fine, it returns an empty stream.
     */
    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("Empty streams are possible")
    void emptyImmutableArraysYieldEmptyStreams() {
        final ConcurrentArray<String> arr = new ConcurrentArray<>();
        arr.seal();

        final Stream<String> stream = arr.sortedStream(null);
        assertEquals(0, stream.count(), "Stream count should have been 0");
    }

    /**
     * Calling sort concurrently from many threads is fine.
     */
    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("Calling sortedStream from multiple threads concurrently is OK")
    void concurrentSortedStream() {
        // Set up the array
        final int numElements = 1_000;
        final ConcurrentArray<Integer> arr = new ConcurrentArray<>(100);
        for (int i = 0; i < numElements; i++) {
            arr.add(i);
        }
        arr.seal();

        // The large number of threads is intended to create a lot of lock contention.
        final int numThreads = 100;

        // Let each thread call sortedStream independently and verify the results independently.
        // If any thread fails, the associated future will contain the failure.
        final ExecutorService e = Executors.newFixedThreadPool(numThreads);
        cleanupTasks.add(e::shutdownNow);
        final Future<?>[] futures = new Future<?>[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int ii = i + 1;
            futures[i] = e.submit(() -> {
                Comparator<Integer> comparator = Comparator.comparingInt(a -> (a * 997 * ii) % numElements);
                final List<Integer> elements = arr.sortedStream(comparator).toList();
                assertEquals(numElements, elements.size(), "Wrong value");
                for (int j = 0; j < numElements - 1; j++) {
                    assertTrue(comparator.compare(elements.get(j), elements.get(j + 1)) <= 0, "Wrong value");
                }
            });
        }

        // Block waiting for all of them
        for (final Future<?> future : futures) {
            try {
                future.get(1, TimeUnit.MINUTES);
            } catch (ExecutionException ex) {
                final Throwable cause = ex.getCause();
                if (cause instanceof AssertionError) {
                    throw (AssertionError) cause;
                } else {
                    fail("Unexpected exception", cause);
                }
            } catch (InterruptedException | TimeoutException ex) {
                fail("Test did not complete in time", ex);
            }
        }
    }
}
