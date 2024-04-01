/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.blocknode.util;

import com.hedera.services.stream.v7.proto.Block;
import com.hedera.services.stream.v7.proto.BlockItem;

import static java.util.Objects.requireNonNull;

public class BlockNodeUtil {

    private static final String FILE_EXTENSION = ".blk.gz";

    public static String blockNumberToKey(long blockNumber) {
        return String.format("%036d", blockNumber) + FILE_EXTENSION;
    }

    public static long extractBlockNumber(Block block) {
        Long blockNumber = null;
        for (BlockItem item : block.getItemsList()) {
            if (item.hasHeader()) {
                blockNumber = item.getHeader().getNumber();
                break;
            }
        }

        requireNonNull(blockNumber, "Block number can not be extracted.");

        return blockNumber;
    }

    public static String extractBlockFileNameFromBlock(Block block) {
        Long blockNumber = extractBlockNumber(block);
        return blockNumberToKey(blockNumber);
    }

}
