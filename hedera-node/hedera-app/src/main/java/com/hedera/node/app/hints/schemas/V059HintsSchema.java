/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hints.schemas;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.hints.CRSState;
import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.HintsKeySet;
import com.hedera.hapi.node.state.hints.HintsPartyId;
import com.hedera.hapi.node.state.hints.PreprocessingVote;
import com.hedera.hapi.node.state.hints.PreprocessingVoteId;
import com.hedera.node.app.hints.HintsService;
import com.hedera.node.app.hints.impl.HintsContext;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Registers the states needed for the {@link HintsService}; these are,
 * <ul>
 *     <li>A singleton with the active hinTS construction; until this
 *     construction is complete, the hinTS service will not be able to
 *     aggregate partial signatures.</li>
 *     <li>A singleton with the next hinTS construction; this may or may
 *     not be ongoing, as there may not be a candidate roster.</li>
 *     <li>A map from pair of {@code (party id, number of parties)} to
 *     a set of hinTS keys this party is using; and the time at which
 *     the active key was adopted.</li>
 *     <li>A map from pair of {@code (node id, construction id}} to the
 *     node's vote for the output of preprocessing for the identified
 *     construction.</li>
 * </ul>
 */
public class V059HintsSchema extends Schema {
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().minor(59).build();

    private static final long MAX_HINTS = 1L << 31;
    private static final long MAX_PREPROCESSING_VOTES = 1L << 31;

    public static final String HINTS_KEY_SETS_KEY = "HINTS_KEY_SETS";
    public static final String ACTIVE_HINT_CONSTRUCTION_KEY = "ACTIVE_HINT_CONSTRUCTION";
    public static final String NEXT_HINT_CONSTRUCTION_KEY = "NEXT_HINT_CONSTRUCTION";
    public static final String PREPROCESSING_VOTES_KEY = "PREPROCESSING_VOTES";
    public static final String CRS_STATE_KEY = "CRS_STATE";

    private final HintsContext signingContext;

    public V059HintsSchema(@NonNull final HintsContext signingContext) {
        super(VERSION);
        this.signingContext = requireNonNull(signingContext);
    }

    @Override
    public @NonNull Set<StateDefinition> statesToCreate() {
        return Set.of(
                StateDefinition.singleton(ACTIVE_HINT_CONSTRUCTION_KEY, HintsConstruction.PROTOBUF),
                StateDefinition.singleton(NEXT_HINT_CONSTRUCTION_KEY, HintsConstruction.PROTOBUF),
                StateDefinition.onDisk(HINTS_KEY_SETS_KEY, HintsPartyId.PROTOBUF, HintsKeySet.PROTOBUF, MAX_HINTS),
                StateDefinition.onDisk(
                        PREPROCESSING_VOTES_KEY,
                        PreprocessingVoteId.PROTOBUF,
                        PreprocessingVote.PROTOBUF,
                        MAX_PREPROCESSING_VOTES),
                StateDefinition.singleton(CRS_STATE_KEY, CRSState.PROTOBUF));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        final var states = ctx.newStates();
        states.<HintsConstruction>getSingleton(ACTIVE_HINT_CONSTRUCTION_KEY).put(HintsConstruction.DEFAULT);
        states.<HintsConstruction>getSingleton(NEXT_HINT_CONSTRUCTION_KEY).put(HintsConstruction.DEFAULT);
    }

    @Override
    public void restart(@NonNull final MigrationContext ctx) {
        final var states = ctx.newStates();
        final var activeConstruction =
                requireNonNull(states.<HintsConstruction>getSingleton(ACTIVE_HINT_CONSTRUCTION_KEY)
                        .get());
        if (activeConstruction.hasHintsScheme()) {
            signingContext.setConstruction(activeConstruction);
        }
    }
}
