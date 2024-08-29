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
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * This is a schema for the platform state that is represented by a singleton. The schema is responsible for
 * registering {@link #GENESIS_PLATFORM_STATE} state if an instance of the state does not exist.
 */
public class V0540PlatformStateSchema extends Schema {
    public static final PlatformState GENESIS_PLATFORM_STATE =
            new PlatformState(SemanticVersion.DEFAULT, 0, null, null, null, Bytes.EMPTY, 0L, 0L, null, null, null);
    public static final String PLATFORM_STATE_KEY = "PLATFORM_STATE";

    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(54).patch(0).build();

    public V0540PlatformStateSchema() {
        super(VERSION);
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(StateDefinition.singleton(PLATFORM_STATE_KEY, PlatformState.PROTOBUF));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        final var platformState = ctx.newStates().getSingleton(PLATFORM_STATE_KEY);
        if (platformState.get() == null) {
            platformState.put(GENESIS_PLATFORM_STATE);
        }
    }
}
