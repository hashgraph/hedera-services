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
import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.HintsId;
import com.hedera.hapi.node.state.hints.HintsKey;
import com.hedera.hapi.node.state.hints.PreprocessVoteId;
import com.hedera.hapi.node.state.hints.PreprocessedKeysVote;
import com.hedera.node.app.hints.HintsService;
import com.hedera.node.app.hints.impl.HintsSigningContext;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Registers the states needed for the {@link HintsService}; these are,
 * <ul>
 *     <li>A singleton with the active hinTS construction (must be at least
 *     ongoing once the network is active; and until complete, the hinTS service
 *     will not be able to aggregate partial signatures).</li>
 *     <li>A singleton with the next hinTS construction (may or may not be
 *     ongoing, as there may not be a candidate roster set).</li>
 *     <li>A map from pair of party id and universe size to the party's
 *     timestamped key and hints; and, if applicable, the key and hints
 *     it wants to start using for all constructions of comparable size
 *     that begin after the current one ends.</li>
 *     <li>A map from pair of node id and construction id to the node's
 *     vote for the keys aggregated by preprocessing that construction.</li>
 * </ul>
 */
public class V059HintsSchema extends Schema {
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().minor(59).build();

    private static final long MAX_HINTS = 1L << 31;
    private static final long MAX_PREPROCESSING_VOTES = 1L << 31;

    public static final String HINTS_KEY = "HINTS";
    public static final String ACTIVE_CONSTRUCTION_KEY = "ACTIVE_CONSTRUCTION";
    public static final String NEXT_CONSTRUCTION_KEY = "NEXT_CONSTRUCTION";
    public static final String PREPROCESSING_VOTES_KEY = "PREPROCESSING_VOTES";

    private final HintsSigningContext signingContext;

    public V059HintsSchema(@NonNull final HintsSigningContext signingContext) {
        super(VERSION);
        this.signingContext = requireNonNull(signingContext);
    }

    @Override
    public @NonNull Set<StateDefinition> statesToCreate() {
        return Set.of(
                StateDefinition.singleton(ACTIVE_CONSTRUCTION_KEY, HintsConstruction.PROTOBUF),
                StateDefinition.singleton(NEXT_CONSTRUCTION_KEY, HintsConstruction.PROTOBUF),
                StateDefinition.onDisk(HINTS_KEY, HintsId.PROTOBUF, HintsKey.PROTOBUF, MAX_HINTS),
                StateDefinition.onDisk(
                        PREPROCESSING_VOTES_KEY,
                        PreprocessVoteId.PROTOBUF,
                        PreprocessedKeysVote.PROTOBUF,
                        MAX_PREPROCESSING_VOTES));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        final var states = ctx.newStates();
        states.<HintsConstruction>getSingleton(ACTIVE_CONSTRUCTION_KEY).put(HintsConstruction.DEFAULT);
        states.<HintsConstruction>getSingleton(NEXT_CONSTRUCTION_KEY).put(HintsConstruction.DEFAULT);
    }

    @Override
    public void restart(@NonNull final MigrationContext ctx) {
        final var states = ctx.previousStates();
        final var activeConstruction = requireNonNull(
                states.<HintsConstruction>getSingleton(ACTIVE_CONSTRUCTION_KEY).get());
        if (activeConstruction.hasPreprocessedKeys()) {
            signingContext.setConstruction(activeConstruction);
        }
    }
}
