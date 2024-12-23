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

package com.hedera.node.app.hints;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.hints.schemas.V058HintsSchema;
import com.hedera.node.app.spi.AppContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

public class HintsServiceImpl implements HintsService {
    private final AppContext appContext;

    public HintsServiceImpl(@NonNull final AppContext appContext) {
        this.appContext = requireNonNull(appContext);
    }

    @Override
    public void start() {
        // No-op
    }

    @Override
    public void orchestrateConstruction(
            @NonNull final Instant now,
            @NonNull final WritableStates writableStates,
            @Nullable final Bytes priorRosterHash,
            @NonNull final Bytes currentRosterHash,
            @NonNull final ReadableRosterStore rosterStore) {
        requireNonNull(now);
        requireNonNull(writableStates);
        requireNonNull(currentRosterHash);
        requireNonNull(rosterStore);
    }

    @Override
    public void stop() {
        // No-op
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        requireNonNull(registry);
        registry.register(new V058HintsSchema());
    }
}
