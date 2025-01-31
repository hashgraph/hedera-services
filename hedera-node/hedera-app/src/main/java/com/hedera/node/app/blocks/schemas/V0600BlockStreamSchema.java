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

package com.hedera.node.app.blocks.schemas;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A migration schema which adds a genesisWorkDone flag to the BlockStreamInfo state.
 */
public class V0600BlockStreamSchema extends Schema {
    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(60).patch(0).build();

    public static final String BLOCK_STREAM_INFO_KEY = "BLOCK_STREAM_INFO";

    /**
     * Constructs a new schema instance.
     */
    public V0600BlockStreamSchema() {
        super(VERSION);
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        if (!ctx.isGenesis()) {
            final var blockStreamInfoState = ctx.newStates().<BlockStreamInfo>getSingleton(BLOCK_STREAM_INFO_KEY);
            final BlockStreamInfo blockStreamInfo = blockStreamInfoState.get();
            requireNonNull(blockStreamInfo);
            blockStreamInfoState.put(
                    blockStreamInfo.copyBuilder().genesisWorkDone(true).build());
        }
    }
}
