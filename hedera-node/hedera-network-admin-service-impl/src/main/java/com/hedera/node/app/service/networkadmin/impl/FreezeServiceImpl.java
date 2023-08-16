/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
    private static final SemanticVersion GENESIS_VERSION = SemanticVersion.DEFAULT;

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry) {
        registry.register(networkAdminSchema());
    }

    private Schema networkAdminSchema() {
        return new Schema(GENESIS_VERSION) {
            @NonNull
            @Override
            @SuppressWarnings("rawtypes")
            public Set<StateDefinition> statesToCreate() {
                return Set.of(StateDefinition.singleton(UPGRADE_FILE_HASH_KEY, ProtoBytes.PROTOBUF));
            }

            @Override
            public void migrate(@NonNull final MigrationContext ctx) {
                // Reset the upgrade file hash to empty
                // It should always be empty at genesis or after an upgrade, to indicate that no upgrade is in progress
                // Nothing in state can ever be null, so use Bytes.EMPTY to indicate an empty hash
                final var upgradeFileHashKeyState = ctx.newStates().<ProtoBytes>getSingleton(UPGRADE_FILE_HASH_KEY);
                upgradeFileHashKeyState.put(ProtoBytes.DEFAULT);
            }
        };
    }
}
