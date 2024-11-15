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

package com.hedera.node.app.services;

import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * Defines a type able to perform some related set of migrations on a {@link State} instance
 * given a current and previous version of the state; a configuration; and network information, and
 * the metrics that services will use.
 */
public interface ServiceMigrator {
    /**
     * Perform the migrations on the given state.
     *
     * @param state            The state to migrate
     * @param servicesRegistry The services registry to use for the migrations
     * @param previousVersion The previous version of the state
     * @param currentVersion The current version of the state
     * @param nodeConfiguration The configuration to use for the migrations
     * @param platformConfiguration The platform configuration to use for subsequent object initializations
     * @param genesisNetworkInfo The network information to use for the migrations
     * @param metrics The metrics to use for the migrations
     * @return The list of builders for state changes that occurred during the migrations
     */
    List<StateChanges.Builder> doMigrations(
            @NonNull State state,
            @NonNull ServicesRegistry servicesRegistry,
            @Nullable SoftwareVersion previousVersion,
            @NonNull SoftwareVersion currentVersion,
            @NonNull Configuration nodeConfiguration,
            @NonNull Configuration platformConfiguration,
            @Nullable NetworkInfo genesisNetworkInfo,
            @NonNull Metrics metrics);

    /**
     * Given a {@link State}, returns the creation version of the state if it was deserialized, or null otherwise.
     * @param state the state
     * @return the version of the state if it was deserialized, otherwise null
     */
    @Nullable
    SemanticVersion creationVersionOf(@NonNull State state);
}
