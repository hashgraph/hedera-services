/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.networkadmin.impl;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.node.app.service.networkadmin.FreezeService;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/** Standard implementation of the {@link FreezeService} {@link com.hedera.node.app.spi.Service}. */
public final class FreezeServiceImpl implements FreezeService {
    public static final String UPGRADE_FILE_HASH_KEY = "UPGRADE_FILE_HASH";
    public static final String FREEZE_TIME_KEY = "FREEZE_TIME";
    public static final String LAST_FROZEN_TIME_KEY = "LAST_FROZEN_TIME";

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry, final SemanticVersion version) {
        registry.register(networkAdminSchema(version));
    }

    private Schema networkAdminSchema(final SemanticVersion version) {
        return new Schema(version) {
            @NonNull
            @Override
            @SuppressWarnings("rawtypes")
            public Set<StateDefinition> statesToCreate() {
                return Set.of(
                        StateDefinition.singleton(UPGRADE_FILE_HASH_KEY, ProtoBytes.PROTOBUF),
                        StateDefinition.singleton(FREEZE_TIME_KEY, Timestamp.PROTOBUF),
                        StateDefinition.singleton(LAST_FROZEN_TIME_KEY, Timestamp.PROTOBUF));
            }

            @Override
            public void migrate(@NonNull final MigrationContext ctx) {
                // Reset the upgrade file hash to empty
                // It should always be empty at genesis or after an upgrade, to indicate that no upgrade is in progress
                // Nothing in state can ever be null, so use Type.DEFAULT to indicate an empty hash
                final var isGenesis = ctx.previousStates().isEmpty();

                final var upgradeFileHashKeyState = ctx.newStates().<ProtoBytes>getSingleton(UPGRADE_FILE_HASH_KEY);
                final var freezeTimeKeyState = ctx.newStates().<Timestamp>getSingleton(FREEZE_TIME_KEY);
                final var lastFrozenTimeKeyState = ctx.newStates().<Timestamp>getSingleton(LAST_FROZEN_TIME_KEY);

                if (isGenesis) {
                    upgradeFileHashKeyState.put(ProtoBytes.DEFAULT);
                    freezeTimeKeyState.put(Timestamp.DEFAULT);
                    lastFrozenTimeKeyState.put(Timestamp.DEFAULT);
                }
            }
        };
    }
}
