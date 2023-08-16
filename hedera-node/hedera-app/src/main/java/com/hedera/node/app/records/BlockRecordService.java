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
import com.hedera.node.app.records.impl.BlockRecordManagerImpl;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.*;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import javax.inject.Singleton;

/**
 * A {@link Service} for managing the state of the running hashes and block information. Used by the
 * {@link BlockRecordManagerImpl}. This service is not exposed outside `hedera-app`.
 */
@SuppressWarnings("rawtypes")
@Singleton
public final class BlockRecordService implements Service {
    /** The name of this service */
    public static final String NAME = "BlockRecordService";
    /** The key for the {@link RunningHashes} object in state */
    public static final String RUNNING_HASHES_STATE_KEY = "RUNNING_HASHES";
    /** The key for the {@link BlockInfo} object in state */
    public static final String BLOCK_INFO_STATE_KEY = "BLOCKS";

    /** The original version of the state */
    private static final SemanticVersion GENESIS_VERSION = SemanticVersion.DEFAULT;
    /** The original hash, only used at genesis */
    private static final Bytes GENESIS_HASH = Bytes.wrap(new byte[48]);

    @NonNull
    @Override
    public String getServiceName() {
        return NAME;
    }

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry) {
        registry.register(new Schema(GENESIS_VERSION) {
            /** {@inheritDoc} */
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(
                        StateDefinition.singleton(RUNNING_HASHES_STATE_KEY, RunningHashes.PROTOBUF),
                        StateDefinition.singleton(BLOCK_INFO_STATE_KEY, BlockInfo.PROTOBUF));
            }

            /** {@inheritDoc} */
            @Override
            public void migrate(@NonNull final MigrationContext ctx) {
                final var runningHashState = ctx.newStates().getSingleton(RUNNING_HASHES_STATE_KEY);
                final var runningHashes =
                        RunningHashes.newBuilder().runningHash(GENESIS_HASH).build();
                runningHashState.put(runningHashes);

                final var blocksState = ctx.newStates().getSingleton(BLOCK_INFO_STATE_KEY);
                // Last block is set to 0 because the first valid block is 1
                final var blocks = new BlockInfo(0, null, Bytes.EMPTY);
                blocksState.put(blocks);
            }
        });
    }
}
