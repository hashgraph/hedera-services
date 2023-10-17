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

package com.hedera.node.app.spi.state;

import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.throttle.HandleThrottleParser;
import com.hedera.node.app.spi.workflows.record.GenesisRecordsBuilder;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Provides the context for a migration of state from one {@link Schema} version to another.
 */
public interface MigrationContext {
    /**
     * Provides a reference to the previous {@link ReadableStates}. For example, if the previous state was version
     * 1.2.3, then this method will return a {@link ReadableStates} that can be used to read the state of version 1.2.3.
     * This state is strictly read-only. This is useful as it allows the migration code to refer to the previous state.
     *
     * @return A non-null reference to the previous states. For Genesis, this will be an empty {@link ReadableStates}.
     */
    @NonNull
    ReadableStates previousStates();

    /**
     * Provides a references to the current working state. Initially, this state will be identical to that returned by
     * {@link #previousStates()}, but as the migration progresses, this state will be updated to reflect the new values
     * of the state. All new {@link Schema#statesToCreate()} will exist in this state.
     *
     * @return A non-null reference to the working state.
     */
    @NonNull
    WritableStates newStates();

    /**
     * The {@link Configuration} for this migration. Any portion of this configuration which was based on state (such
     * as, in our case, file 121) will be current as of the previous state. This configuration is read-only. Having this
     * configuration is useful for migrations that should behavior differently based on configuration.
     *
     * @return The configuration to use.
     */
    @NonNull
    Configuration configuration();

    /**
     * Information about the network itself. Generally, this is not useful information for migrations, but is used at
     * genesis for the file service. In the future, this may no longer be required.
     *
     * @return The {@link NetworkInfo} of the network at the time of migration.
     */
    NetworkInfo networkInfo();

    /**
     * Provides a class to store any entities created during genesis. The data saved in this class
     * during genesis will then enable record generation from said data once a consensus timestamp is
     * available.
     * <p>
     * It's possible that this method could be expanded to cover records for any migration (genesis
     * or otherwise) in the future.
     */
    @NonNull
    GenesisRecordsBuilder genesisRecordsBuilder();

    @NonNull
    HandleThrottleParser handleThrottling();
}
