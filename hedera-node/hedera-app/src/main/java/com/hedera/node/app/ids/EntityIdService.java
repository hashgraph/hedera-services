/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Service for providing incrementing entity id numbers. It stores the most recent entity id in state.
 */
@SuppressWarnings("rawtypes")
public class EntityIdService implements Service {
    private static final Logger log = LogManager.getLogger(EntityIdService.class);
    public static final String NAME = "EntityIdService";
    public static final String ENTITY_ID_STATE_KEY = "ENTITY_ID";

    private long fs = -1;

    /** {@inheritDoc} */
    @NonNull
    @Override
    public String getServiceName() {
        return NAME;
    }

    public void setFs(long fs) {
        this.fs = fs;
    }

    /** {@inheritDoc} */
    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry, @NonNull final SemanticVersion version) {
        registry.register(new Schema(version) {
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

                final var isGenesis = ctx.previousVersion() == null;
                if (isGenesis) {
                    // Set the initial entity id to the first user entity minus one
                    final var entityNum = config.firstUserEntity() - 1;
                    log.info("Setting initial entity id to " + entityNum);
                    entityIdState.put(new EntityNumber(entityNum));
                }

                if (fs > -1) {
                    log.info("BBM: Setting initial entity id to " + fs);
                    entityIdState.put(new EntityNumber(fs - 1));
                } else {
                    log.warn("BBM: no entity ID 'from' state found");
                }

                // Usually we un-assign the 'from' state here, but in this case there's no need because the only field
                // is
                // a copied long
            }
        });
    }
}
