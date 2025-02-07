/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.blocks.impl.streaming;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Track the current block state
 *
 * @param blockNumber the block number of this block state
 * @param itemBytes the list of item bytes in this block state
 */
public record BlockState(long blockNumber, List<Bytes> itemBytes) {

    /**
     * Create a new block state for a block number
     *
     * @param blockNumber the block number
     * @return the block state for the specified block number
     */
    public static @NonNull BlockState from(long blockNumber) {
        return new BlockState(blockNumber, new ArrayList<>());
    }
}
