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

package com.swirlds.fchashmap.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Configuration for {@link com.swirlds.fchashmap.FCHashMap}.
 *
 * @param rebuildSplitFactor
 *      split the binary tree at this depth relative to the root of the binary tree. The tree will be split into 2^split-factor subtrees, and each subtree will be eligible to be handled on a separate thread.
 * @param rebuildThreadCount
 *      rebuilding the FCHashMap in a MerkleMap, use this many threads to rebuild the tree.
 *
 */
@ConfigData("fcHashMap")
public record FCHashMapConfig(
        @ConfigProperty(defaultValue = "7") int rebuildSplitFactor,
        @ConfigProperty(defaultValue = "24") int rebuildThreadCount) {}
