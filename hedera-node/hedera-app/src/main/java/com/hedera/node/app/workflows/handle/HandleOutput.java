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

package com.hedera.node.app.workflows.handle;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.node.app.spi.records.RecordSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A temporary wrapper class as we transition from the V6 record stream to the block stream;
 * includes at least one of,
 * <ol>
 *     <li>The V6 record stream items,</li>
 *     <li>The block stream output items</li>
 * </ol>
 * @param blockItems maybe the block stream output items
 * @param recordStreamSource maybe record source derived from the V6 record stream items
 */
public record HandleOutput(@Nullable List<BlockItem> blockItems, @Nullable RecordSource recordStreamSource) {
    public HandleOutput {
        if (blockItems == null) {
            requireNonNull(recordStreamSource);
        }
    }

    public @NonNull RecordSource recordSourceOrThrow() {
        return requireNonNull(recordStreamSource);
    }

    public @NonNull List<BlockItem> blocksItemsOrThrow() {
        return requireNonNull(blockItems);
    }
}
