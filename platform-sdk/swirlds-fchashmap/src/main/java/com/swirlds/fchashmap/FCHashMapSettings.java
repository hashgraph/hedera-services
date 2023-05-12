/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.fchashmap;

import java.time.Duration;

/**
 * Settings for {@link com.swirlds.fchashmap.FCHashMap}.
 *
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future. If you need
 * 		to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public interface FCHashMapSettings {

    /**
     * Get the maximum expected size of the FCHashMapGarbageCollector's queue.
     */
    int getMaximumGCQueueSize();

    /**
     * Get the amount of time that must pass between error logs about the FCHashMapGarbageCollector's queue size.
     */
    Duration getGCQueueThresholdPeriod();

    /**
     * Is the archival of FCHashMap enabled?
     */
    boolean isArchiveEnabled();

    /**
     * When rebuilding the FCHashMap in a MerkleMap, split the binary tree at this depth relative to the root
     * of the binary tree. The tree will be split into 2^split-factor subtrees, and each subtree will be eligible
     * to be handled on a separate thread.
     */
    int getRebuildSplitFactor();

    /**
     * When rebuilding the FCHashMap in a MerkleMap, use this many threads to rebuild the tree.
     */
    int getRebuildThreadCount();
}
