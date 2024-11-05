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
import com.hedera.node.app.service.networkadmin.FreezeService;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Initial mod-service schema for the {@link FreezeService}.
 */
public class V0490FreezeSchema extends Schema {
    private static final Logger log = LogManager.getLogger(V0490FreezeSchema.class);

    public static final String UPGRADE_FILE_HASH_KEY = "UPGRADE_FILE_HASH";
    public static final String FREEZE_TIME_KEY = "FREEZE_TIME";

    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(49).patch(0).build();

    /**
     * Constructs a new {@link V0490FreezeSchema}.
     */
    public V0490FreezeSchema() {
        super(VERSION);
    }

    @NonNull
    @Override
    @SuppressWarnings("rawtypes")
    public Set<StateDefinition> statesToCreate() {
        return Set.of(
                StateDefinition.singleton(UPGRADE_FILE_HASH_KEY, ProtoBytes.PROTOBUF),
                StateDefinition.singleton(FREEZE_TIME_KEY, Timestamp.PROTOBUF));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        final var isGenesis = ctx.previousVersion() == null;
        if (isGenesis) {
            final var upgradeFileHashKeyState = ctx.newStates().<ProtoBytes>getSingleton(UPGRADE_FILE_HASH_KEY);
            upgradeFileHashKeyState.put(ProtoBytes.DEFAULT);

            final var freezeTimeKeyState = ctx.newStates().<Timestamp>getSingleton(FREEZE_TIME_KEY);
            freezeTimeKeyState.put(Timestamp.DEFAULT);
        }
    }
}
