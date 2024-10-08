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

package com.hedera.node.app.records.schemas;

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.RUNNING_HASHES_STATE_KEY;
import static com.hedera.node.config.types.StreamMode.RECORDS;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.config.data.BlockStreamConfig;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.Schema;
import edu.umd.cs.findbugs.annotations.NonNull;

public class V0560BlockRecordSchema extends Schema {
    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(56).patch(0).build();

    private static final String SHARED_BLOCK_RECORD_INFO = "SHARED_BLOCK_RECORD_INFO";
    private static final String SHARED_RUNNING_HASHES = "SHARED_RUNNING_HASHES";

    public V0560BlockRecordSchema() {
        super(VERSION);
    }

    @Override
    public void restart(@NonNull final MigrationContext ctx) {
        if (ctx.previousVersion() != null) {
            final var streamMode =
                    ctx.configuration().getConfigData(BlockStreamConfig.class).streamMode();
            if (streamMode != RECORDS) {
                // We need to give the upcoming BlockStreamService schemas migration info needed
                final var blocksState = ctx.newStates().getSingleton(BLOCK_INFO_STATE_KEY);
                final var runningHashesState = ctx.newStates().getSingleton(RUNNING_HASHES_STATE_KEY);
                ctx.sharedValues().put(SHARED_BLOCK_RECORD_INFO, blocksState.get());
                ctx.sharedValues().put(SHARED_RUNNING_HASHES, runningHashesState.get());
            }
        }
    }
}
