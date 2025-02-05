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

import static com.hedera.node.app.hints.schemas.V059HintsSchema.ACTIVE_HINT_CONSTRUCTION_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.hints.CRSStage;
import com.hedera.hapi.node.state.hints.CRSState;
import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.node.app.hints.impl.HintsContext;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

public class V060HintsSchema extends Schema {
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().minor(60).build();
    public static final String CRS_STATE_KEY = "CRS_STATE";

    private final HintsContext signingContext;

    public V060HintsSchema(@NonNull final HintsContext signingContext) {
        super(VERSION);
        this.signingContext = requireNonNull(signingContext);
    }

    @Override
    public @NonNull Set<StateDefinition> statesToCreate() {
        return Set.of(StateDefinition.singleton(CRS_STATE_KEY, CRSState.PROTOBUF));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        final var states = ctx.newStates();
        states.<CRSState>getSingleton(CRS_STATE_KEY)
                .put(CRSState.newBuilder()
                        .stage(CRSStage.WAITING_FOR_INITIAL_CRS)
                        .build());
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
