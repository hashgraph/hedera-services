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

package com.hedera.node.app.ids;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.config.data.HederaConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Service for providing incrementing entity id numbers. It stores the most recent entity id in state.
 */
@SuppressWarnings("rawtypes")
public class EntityIdService implements Service {
    public static final String NAME = "EntityIdService";
    public static final String ENTITY_ID_STATE_KEY = "ENTITY_ID";
    private static final SemanticVersion GENESIS_VERSION = SemanticVersion.DEFAULT;

    /** {@inheritDoc} */
    @NonNull
    @Override
    public String getServiceName() {
        return NAME;
    }

    /** {@inheritDoc} */
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
                return Set.of(StateDefinition.singleton(ENTITY_ID_STATE_KEY, EntityNumber.PROTOBUF));
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
                final var entityIdState = ctx.newStates().getSingleton(ENTITY_ID_STATE_KEY);
                final var config = ctx.configuration().getConfigData(HederaConfig.class);
                // need -1 because it will be incremented on first use before being read
                entityIdState.put(new EntityNumber(config.firstUserEntity() - 1));
            }
        });
    }
}
