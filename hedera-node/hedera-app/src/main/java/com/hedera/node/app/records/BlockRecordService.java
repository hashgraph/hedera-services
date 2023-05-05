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

package com.hedera.node.app.records;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.*;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

@SuppressWarnings("rawtypes")
public class BlockRecordService implements Service {
    public static final String NAME = "BlockRecordService";
    private static final SemanticVersion GENESIS_VERSION = SemanticVersion.DEFAULT;
    static final String RUNNING_HASHES_STATE_KEY = "RUNNING_HASHES";
    static final String BLOCK_INFO_STATE_KEY = "BLOCKS";
    private static final Bytes GENESIS_HASH = Bytes.wrap(new byte[48]);

    @NonNull
    @Override
    public String getServiceName() {
        return NAME;
    }

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry) {
        registry.register(new Schema(GENESIS_VERSION) {
            /**
             * Gets a {@link Set} of state definitions for states to create in this schema. For example,
             * perhaps in this version of the schema, you need to create a new state FOO. The set will have
             * a {@link StateDefinition} specifying the metadata for that state.
             *
             * @return A map of all states to be created. Possibly empty.
             */
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(
                        StateDefinition.singleton(RUNNING_HASHES_STATE_KEY, RunningHashes.PROTOBUF),
                        StateDefinition.singleton(BLOCK_INFO_STATE_KEY, BlockInfo.PROTOBUF));
            }

            /**
             * Called after all new states have been created (as per {@link #statesToCreate()}), this method
             * is used to perform all <b>synchronous</b> migrations of state. This method will always be
             * called with the {@link ReadableStates} of the previous version of the {@link Schema}. If
             * there was no previous version, then {@code previousStates} will be empty, but not null.
             *
             * @param ctx {@link MigrationContext} for this schema migration
             */
            @Override
            public void migrate(@NonNull MigrationContext ctx) {
                final var runningHashState = ctx.newStates().getSingleton(RUNNING_HASHES_STATE_KEY);
                final var runningHashes = new RunningHashes(GENESIS_HASH, null, null, null);
                runningHashState.put(runningHashes);

                final var blocksState = ctx.newStates().getSingleton(BLOCK_INFO_STATE_KEY);
                // Last block is set to -1 because the first valid block is 0
                final var blocks = new BlockInfo(-1, null, Bytes.EMPTY);
                blocksState.put(blocks);
            }
        });
    }
}
