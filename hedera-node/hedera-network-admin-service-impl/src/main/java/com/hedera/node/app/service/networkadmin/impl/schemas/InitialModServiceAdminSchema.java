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

package com.hedera.node.app.service.networkadmin.impl.schemas;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * General schema for the admin service
 * (FUTURE) When mod-service release is finalized, rename this class to e.g.
 * {@code Release47AdminSchema} as it will no longer be appropriate to assume
 * this schema is always correct for the current version of the software.
 */
public class InitialModServiceAdminSchema extends Schema {
    private static final Logger log = LogManager.getLogger(InitialModServiceAdminSchema.class);
    private static MerkleNetworkContext mnc;

    public InitialModServiceAdminSchema(@NonNull final SemanticVersion version) {
        super(version);
    }

    @NonNull
    @Override
    @SuppressWarnings("rawtypes")
    public Set<StateDefinition> statesToCreate() {
        return Set.of(
                StateDefinition.singleton(FreezeServiceImpl.UPGRADE_FILE_HASH_KEY, ProtoBytes.PROTOBUF),
                StateDefinition.singleton(FreezeServiceImpl.FREEZE_TIME_KEY, Timestamp.PROTOBUF));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        log.info("BBM: migrating Admin service");
        // Reset the upgrade file hash to empty
        // It should always be empty at genesis or after an upgrade, to indicate that no upgrade is in progress
        // Nothing in state can ever be null, so use Type.DEFAULT to indicate an empty hash
        final var isGenesis = ctx.previousVersion() == null;

        final var upgradeFileHashKeyState =
                ctx.newStates().<ProtoBytes>getSingleton(FreezeServiceImpl.UPGRADE_FILE_HASH_KEY);
        final var freezeTimeKeyState = ctx.newStates().<Timestamp>getSingleton(FreezeServiceImpl.FREEZE_TIME_KEY);

        if (isGenesis || mnc != null) {
            upgradeFileHashKeyState.put(ProtoBytes.DEFAULT);
            freezeTimeKeyState.put(Timestamp.DEFAULT);
        }
        mnc = null;
        log.info("BBM: finished migrating Admin service");
    }

    public static void setFs(@Nullable final MerkleNetworkContext mn) {
        mnc = mn;
    }
}
