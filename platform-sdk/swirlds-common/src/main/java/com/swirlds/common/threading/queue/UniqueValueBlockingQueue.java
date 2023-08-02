/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.queue;

import com.swirlds.common.threading.framework.internal.AbstractBlockingQueue;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * A concurrent data structure that makes sure that only unique values are getting into the underlying {@link ArrayBlockingQueue}.
 * To check the uniqueness it relies on `equals` and `hashCode`, so it's absolutely necessary to have meaningful implementations of these methods
 * for the objects that are going to be added to the queue. Otherwise, the data structure is not going to work as intended.
 * This data structure is thread-safe. In case there are multiple producers and consumers it makes sure that
 * there is only one task from a certain producer at a time, assuming that the producer creates tasks that are equal to each other.
 *
 * @param <V> the type of the values
 */
public class UniqueValueBlockingQueue<V> extends AbstractBlockingQueue<V> {

    /** Concurrent map to quickly verify if a value is already present in the queue. */
    private final ConcurrentHashMap<V, Boolean> map;

    /**
     * Constructs a UniqueValueBlockingQueue wrapping the specified queue.
     *
     * @param capacity the capacity of the queue
     */
    public UniqueValueBlockingQueue(int capacity) {
        super(new ArrayBlockingQueue<>(capacity));
        this.map = new ConcurrentHashMap<>();
    }

    // Add operations

    /**
     * Inserts the specified element if it is not already present.
     *
     * @param v the element to add
     * @return true if the element was added, false otherwise
     */
    @Override
    public boolean add(V v) {
        Boolean prevValue = map.putIfAbsent(v, Boolean.TRUE);
        if (prevValue == null) {
            return super.add(v);
        }
        return false;
    }

    /**
     * Inserts the specified element if it is not already present, waiting for space to become available if necessary.
     *
     * @param v the element to add
     * @throws InterruptedException if interrupted while waiting
     */
    @Override
    public void put(V v) throws InterruptedException {
        Boolean prevValue = map.putIfAbsent(v, Boolean.TRUE);
        if (prevValue == null) {
            super.put(v);
        }
    }

    /**
     * Inserts the specified element if it is not already present.
     *
     * @param v the element to add
     * @return true if the element was added, false otherwise
     */
    @Override
    public boolean offer(V v) {
        Boolean prevValue = map.putIfAbsent(v, Boolean.TRUE);
        if (prevValue == null) {
            return super.offer(v);
        }
        return false;
    }

    /**
     * Inserts the specified element if it is not already present, waiting up to the specified wait time.
     *
     * @param v the element to add
     * @param timeout the time to wait for space to become available
     * @param unit the time unit of the timeout
     * @return true if the element was added, false otherwise
     * @throws InterruptedException if interrupted while waiting
     */
    @Override
    public boolean offer(V v, long timeout, TimeUnit unit) throws InterruptedException {
        Boolean prevValue = map.putIfAbsent(v, Boolean.TRUE);
        if (prevValue == null) {
            return super.offer(v, timeout, unit);
        }
        return false;
    }

    // Remove operations

    /**
     * Removes and returns the head of this queue.
     *
     * @return the head of the queue
     */
    @Override
    public V remove() {
        V value = super.remove();
        map.remove(value);
        return value;
    }

    /**
     * Removes and returns the head of this queue, or returns null if this queue is empty.
     *
     * @return the head of the queue, or null if empty
     */
    @Override
    public V poll() {
        V value = super.poll();
        if (value != null) {
            map.remove(value);
        }
        return value;
    }

    /**
     * Removes the specified element from this queue, if it is present.
     *
     * @param o the element to be removed
     * @return true if the element was removed, false otherwise
     */
    @Override
    public boolean remove(Object o) {
        boolean remove = super.remove(o);
        if (remove) {
            map.remove(o);
        }
        return remove;
    }

    // Other operations

    /**
     * Removes all the elements from this queue.
     */
    @Override
    public void clear() {
        map.clear();
        super.clear();
    }

    /**
     * Returns true if this queue contains the specified element.
     *
     * @param o the element whose presence is to be tested
     * @return true if this queue contains the specified element
     */
    @Override
    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    // Unsupported mass operations

    /**
     * This operation is not supported.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public boolean addAll(Collection<? extends V> c) {
        throw new UnsupportedOperationException("addAll is not supported");
    }

    /**
     * This operation is not supported.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("retainAll is not supported");
    }

    /**
     * This operation is not supported.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("removeAll is not supported");
    }

    /**
     * This operation is not supported.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public int drainTo(Collection<? super V> c) {
        throw new UnsupportedOperationException("drainTo is not supported");
    }

    /**
     * This operation is not supported.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public int drainTo(Collection<? super V> c, int maxElements) {
        throw new UnsupportedOperationException("drainTo is not supported");
    }
}
