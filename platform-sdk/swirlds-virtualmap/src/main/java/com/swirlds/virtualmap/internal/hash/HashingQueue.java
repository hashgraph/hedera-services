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
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A specialized type of queue used by the {@link VirtualHasher} engine. This implementation is package
 * private, and is not intended for widespread use. A queue can never hold more than
 * {@link Integer#MAX_VALUE} items.
 * <p>
 * The {@link VirtualHasher} algorithm is such that for large trees (as we commonly encounter in production),
 * a subset of the tree is hashed at a time, where the subset never exceeds {@link Integer#MAX_VALUE} in size.
 * Thus, a single {@link HashingQueue} can be reused for hashing different subsets of the tree. In reality, the
 * size of the hashing queue is often substantially smaller than {@link Integer#MAX_VALUE}.
 *
 * @param <K>
 *     The key
 * @param <V>
 *     The value
 */
interface HashingQueue<K extends VirtualKey<? super K>, V extends VirtualValue> {
    /**
     * Gets the current size of the queue.
     * @return
     * 		The current size of the queue. Will never be negative.
     */
    int size();

    /**
     * Resets the queue to {@link #size()} == 0. A reset operation is an O(1) time operation
     * and is extremely fast. It does not reduce memory utilization.
     *
     * @return
     * 		A reference to this queue, as a convenience. Will not be null.
     */
    HashingQueue<K, V> reset();

    /**
     * Resets this queue and then copies all contents from {@code queue} into this one.
     *
     * @param queue
     * 		The queue to copy. Must not be null.
     */
    default void copyFrom(final HashingQueue<K, V> queue) {
        Objects.requireNonNull(queue);
        reset();
        final int size = queue.size();
        for (int i = 0; i < size; i++) {
            appendHashJob().copy(queue.get(i));
        }
    }

    /**
     * Append a new {@link HashJob} to the end of the queue and return it.
     *
     * @return
     * 		The HashJob. Will not be null. It may be a pooled or reused object. Do not hold an extended reference!
     */
    HashJob<K, V> appendHashJob();

    /**
     * Adds a new {@link HashJob} into the queue at the given {@code index} and return it.
     *
     * @param index
     * 		The index at which to insert. Some queues have a maximum upper limit to their size. If yours does,
     * 		and this index is too large, an exception will be thrown.
     * @return
     * 		The HashJob. Will not be null. It may be a pooled or reused object. Do not hold an extended reference!
     */
    HashJob<K, V> addHashJob(int index);

    /**
     * Gets the {@link HashJob} at the given index.
     * <p>
     * <strong>WARNING:</strong> our implementations are based on a pooled, reused array of objects. You
     * <strong>MUST</strong> only call get on an {@code index} that you have previously placed data into,
     * otherwise you may get perfectly valid but meaningless {@link HashJob}!
     *
     * @param index
     * 		The index from which to get data. Some queues have a maximum upper limit to their size. If yours does,
     * 		and this index is too large, an exception will be thrown.
     * @return
     * 		A HashJob at that index.
     */
    HashJob<K, V> get(int index);

    /**
     * Gets a stream of all elements in this queue.
     * <p>
     * <strong>WARNING:</strong> our implementations are based on a pooled, reused array of objects. You
     * <strong>WILL</strong> get bogus data from the stream if you have not taken care to populate every
     * index from 0 to the {@code size()-1}.
     *
     * @return
     * 		A non-null stream of {@link HashJob}s.
     */
    Stream<HashJob<K, V>> stream();
}
