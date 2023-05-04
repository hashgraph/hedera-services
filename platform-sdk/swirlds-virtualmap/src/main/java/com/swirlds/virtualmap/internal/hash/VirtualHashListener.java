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
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;

/**
 * Listens to various events that occur during the hashing process.
 */
public interface VirtualHashListener<K extends VirtualKey, V extends VirtualValue> {
    /**
     * Called when starting a new fresh hash operation.
     */
    default void onHashingStarted() {}

    /**
     * Called when a batch (that is, a sub-tree) is about to be hashed. Batches are
     * hashed sequentially, one at a time. You ar guaranteed that {@link #onHashingStarted()}
     * will be called before this method, and that {@link #onBatchCompleted()} will be called
     * before another invocation of this method.
     */
    default void onBatchStarted() {}

    /**
     * Called when hashing a rank within the batch.
     * It may be that multiple batches work on the same rank, just a different subset of it.
     * This will be called once for each combination of batch and rank. The ranks are called
     * from bottom-to-top (that is, from the deepest rank to the lowest rank). This is
     * called between {@link #onBatchStarted()} and {@link #onBatchCompleted()} methods.
     */
    default void onRankStarted() {}

    /**
     * Called after each internal node on a rank is hashed. This is called between
     * {@link #onRankStarted()} and {@link #onRankCompleted()}. Each call within the rank
     * will send internal nodes in <strong>ascending path order</strong>.  There is no guarantee
     * 	 * on the relative order between batches.
     *
     * @param internal
     * 		A non-null internal record representing the hashed internal node.
     */
    default void onInternalHashed(VirtualInternalRecord internal) {}

    /**
     * Called after each leaf node on a rank is hashed. This is called between
     * {@link #onRankStarted()} and {@link #onRankCompleted()}. Each call within the rank
     * will send leaf nodes in <strong>ascending path order</strong>. There is no guarantee
     * on the relative order between batches.
     *
     * @param leaf
     * 		A non-null leaf record representing the hashed leaf.
     */
    default void onLeafHashed(VirtualLeafRecord<K, V> leaf) {}

    /**
     * Called when processing a rank within a batch has completed.
     */
    default void onRankCompleted() {}

    /**
     * Called when processing a batch has completed.
     */
    default void onBatchCompleted() {}

    /**
     * Called when all hashing has completed.
     */
    default void onHashingCompleted() {}
}
