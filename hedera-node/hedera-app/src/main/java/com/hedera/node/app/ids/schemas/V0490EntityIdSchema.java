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

package com.hedera.node.app.ids.schemas;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class V0490EntityIdSchema extends Schema {
    private static final Logger log = LogManager.getLogger(V0490EntityIdSchema.class);

    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(49).patch(0).build();

    public static final String ENTITY_ID_STATE_KEY = "ENTITY_ID";

    public V0490EntityIdSchema() {
        super(VERSION);
    }

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
        if (entityIdState.get() == null) {
            final var config = ctx.configuration().getConfigData(HederaConfig.class);
            final var entityNum = config.firstUserEntity() - 1;
            log.info("Setting initial entity id to {}", entityNum);
            entityIdState.put(new EntityNumber(entityNum));
        }
    }
}
