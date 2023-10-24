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

package com.hedera.node.app.workflows.handle.record;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.spi.throttle.HandleThrottleParser;
import com.hedera.node.app.spi.workflows.record.GenesisRecordsBuilder;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An implementation of {@link MigrationContext}.
 *
 * @param previousStates The previous states.
 * @param newStates The new states, preloaded with any new state definitions.
 * @param configuration The configuration to use
 * @param genesisRecordsBuilder The instance responsible for genesis records
 * @param handleThrottling The instance responsible for handle throttling
 */
public record MigrationContextImpl(
        @NonNull ReadableStates previousStates,
        @NonNull WritableStates newStates,
        @NonNull Configuration configuration,
        @NonNull NetworkInfo networkInfo,
        @NonNull GenesisRecordsBuilder genesisRecordsBuilder,
        @NonNull HandleThrottleParser handleThrottling)
        implements MigrationContext {

    public MigrationContextImpl {
        requireNonNull(previousStates);
        requireNonNull(newStates);
        requireNonNull(configuration);
        requireNonNull(networkInfo);
        requireNonNull(genesisRecordsBuilder);
        requireNonNull(handleThrottling);
    }
}
