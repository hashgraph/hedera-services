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
import com.hedera.node.app.service.mono.state.merkle.MerkleSpecialFiles;
import com.hedera.node.app.service.networkadmin.FreezeService;
import com.hedera.node.app.service.networkadmin.impl.codec.MonoSpecialFilesAdapterCodec;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/** Standard implementation of the {@link FreezeService} {@link com.hedera.node.app.spi.Service}. */
public final class FreezeServiceImpl implements FreezeService {
    // special files will move to FileService
    public static final String UPGRADE_FILES_KEY = "SPECIAL_FILES";

    public static final String DUAL_STATE_KEY = "DUAL_STATE";

    private static final SemanticVersion CURRENT_VERSION =
            SemanticVersion.newBuilder().minor(34).build();

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry) {
        registry.register(networkAdminSchema());
    }

    private Schema networkAdminSchema() {
        return new Schema(CURRENT_VERSION) {
            @NonNull
            @Override
            @SuppressWarnings("rawtypes")
            public Set<StateDefinition> statesToCreate() {
                return Set.of(StateDefinition.singleton(UPGRADE_FILES_KEY, new MonoSpecialFilesAdapterCodec()));
            }

            @Override
            public void migrate(@NonNull MigrationContext ctx) {
                final var upgradeFilesState = ctx.newStates().getSingleton(UPGRADE_FILES_KEY);
                upgradeFilesState.put(new MerkleSpecialFiles());
            }
        };
    }
}
