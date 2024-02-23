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

package com.swirlds.virtualmap.internal.hash;

import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * A {@link HashingQueue} implementation based on an array. This implementation is the main data structure
 * used for holding {@link HashJob}s in the {@link VirtualHasher} implementation. An important performance
 * tradeoff that was explicitly made in this class is to favor low-garbage generate over memory usage.
 *
 * @param <K>
 *     The key.
 * @param <V>
 *     The value.
 */
final class ArrayHashingQueue<K extends VirtualKey, V extends VirtualValue> implements HashingQueue<K, V> {
    private final AtomicInteger size = new AtomicInteger();

    @SuppressWarnings("unchecked")
    private HashJob<K, V>[] queue = new HashJob[0];

    private int capacity = 0;

    // Loses all data but sizes correctly. Could use multiple queue's like Jasper does elsewhere so these
    // queues are more adaptive to usage.
    void ensureCapacity(int capacity) {
        if (queue.length < capacity) {
            //noinspection unchecked
            queue = new HashJob[capacity];
        }
        this.capacity = capacity;
        reset();
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public HashingQueue<K, V> reset() {
        size.set(0);
        return this;
    }

    @Override
    public HashJob<K, V> appendHashJob() {
        return getForModify(size.getAndIncrement());
    }

    @Override
    public HashJob<K, V> addHashJob(int index) {
        size.updateAndGet(s -> s <= index ? (index + 1) : s);
        return getForModify(index);
    }

    @Override
    public HashJob<K, V> get(int index) {
        assert index < size.get() : "Unexpected index out of bounds " + index;
        return queue[index];
    }

    // Resets the darn thing. Lazily creates it.
    HashJob<K, V> getForModify(int index) {
        assert index < size.get() : "Unexpected index out of bounds " + index;
        assert index < capacity : "Unexpected index out of bounds of capacity " + index;
        HashJob<K, V> job = queue[index];
        if (job == null) {
            job = new HashJob<>();
            queue[index] = job;
        } else {
            job.reset();
        }
        return job;
    }

    @Override
    public Stream<HashJob<K, V>> stream() {
        return Arrays.stream(queue, 0, size());
    }
}
