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

package com.swirlds.platform.state.service.schemas;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterState;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Roster Schema
 */
public class V0540RosterBaseSchema extends Schema {
    public static final String ROSTER_KEY = "ROSTERS";
    public static final String ROSTER_STATES_KEY = "ROSTER_STATE";

    private static final Logger log = LogManager.getLogger(V0540RosterBaseSchema.class);
    /**
     * this can't be increased later so we pick some number large enough, 2^16.
     */
    private static final long MAX_ROSTERS = 65_536L;

    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(54).patch(0).build();

    /**
     * Create a new instance
     */
    public V0540RosterBaseSchema() {
        super(VERSION);
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(
                StateDefinition.singleton(ROSTER_STATES_KEY, RosterState.PROTOBUF),
                StateDefinition.onDisk(ROSTER_KEY, ProtoBytes.PROTOBUF, Roster.PROTOBUF, MAX_ROSTERS));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        final var rosterState = ctx.newStates().getSingleton(ROSTER_STATES_KEY);
        // On genesis, create a default roster state from the genesis network info
        if (rosterState.get() == null) {
            log.info("Creating default roster state");
            rosterState.put(RosterState.DEFAULT);
        }
    }
}