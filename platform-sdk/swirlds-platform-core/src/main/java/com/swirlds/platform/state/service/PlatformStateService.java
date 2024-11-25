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

package com.swirlds.platform.state.service;

import static com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema.PLATFORM_STATE_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.platform.state.ConsensusSnapshot;
import com.hedera.hapi.platform.state.PlatformState;
import com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema;
import com.swirlds.platform.state.service.schemas.V057PlatformStateSchema;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.lifecycle.Service;
import com.swirlds.state.merkle.MerkleStateRoot;
import com.swirlds.state.merkle.singleton.SingletonNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A service that provides the schema for the platform state, used by {@link MerkleStateRoot}
 * to implement accessors to the platform state.
 */
public enum PlatformStateService implements Service {
    PLATFORM_STATE_SERVICE;

    private static final AtomicReference<Supplier<Roster>> ACTIVE_ROSTER = new AtomicReference<>();
    private static final AtomicReference<Supplier<SoftwareVersion>> APP_VERSION = new AtomicReference<>();
    private static final Collection<Schema> SCHEMAS = List.of(
            new V0540PlatformStateSchema(),
            new V057PlatformStateSchema(
                    () -> requireNonNull(ACTIVE_ROSTER.get()).get(),
                    () -> requireNonNull(APP_VERSION.get()).get(),
                    WritablePlatformStateStore::new));

    public static final String NAME = "PlatformStateService";

    @NonNull
    @Override
    public String getServiceName() {
        return NAME;
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        requireNonNull(registry);
        SCHEMAS.forEach(registry::register);
    }

    /**
     * Sets the active roster to the given roster.
     * @param roster the roster to set as active
     */
    public void setActiveRosterFn(@NonNull final Supplier<Roster> roster) {
        ACTIVE_ROSTER.set(requireNonNull(roster));
    }

    /**
     * Clears the active roster.
     */
    public void clearActiveRosterFn() {
        ACTIVE_ROSTER.set(null);
    }

    /**
     * Sets the application version to the given version.
     * @param appVersionFn the version to set as the application version
     */
    public void setAppVersionFn(@NonNull final Supplier<SoftwareVersion> appVersionFn) {
        APP_VERSION.set(requireNonNull(appVersionFn));
    }

    /**
     * Given a {@link MerkleStateRoot}, returns the creation version of the platform state if it exists.
     * @param root the root to extract the creation version from
     * @return the creation version of the platform state, or null if the state is a genesis state
     */
    public SemanticVersion creationVersionOf(@NonNull final MerkleStateRoot root) {
        requireNonNull(root);
        final var state = platformStateOf(root);
        return state == null ? null : state.creationSoftwareVersionOrThrow();
    }

    /**
     * Given a {@link MerkleStateRoot}, returns the round number of the platform state if it exists.
     * @param root the root to extract the round number from
     * @return the round number of the platform state, or zero if the state is a genesis state
     */
    public long roundOf(@NonNull final MerkleStateRoot root) {
        requireNonNull(root);
        final var platformState = platformStateOf(root);
        return platformState == null
                ? 0L
                : platformState
                        .consensusSnapshotOrElse(ConsensusSnapshot.DEFAULT)
                        .round();
    }

    @SuppressWarnings("unchecked")
    public @Nullable PlatformState platformStateOf(@NonNull final MerkleStateRoot root) {
        final var index = root.findNodeIndex(NAME, PLATFORM_STATE_KEY);
        if (index == -1) {
            return null;
        }
        return ((SingletonNode<PlatformState>) root.getChild(index)).getValue();
    }
}
