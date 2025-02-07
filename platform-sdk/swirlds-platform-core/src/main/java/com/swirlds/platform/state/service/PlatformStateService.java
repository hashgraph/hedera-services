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

package com.swirlds.platform.state.service;

import static com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema.PLATFORM_STATE_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.state.ConsensusSnapshot;
import com.hedera.hapi.platform.state.PlatformState;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema;
import com.swirlds.platform.state.service.schemas.V058RosterLifecycleTransitionSchema;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.lifecycle.Service;
import com.swirlds.state.merkle.MerkleStateRoot;
import com.swirlds.state.merkle.singleton.SingletonNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A service that provides the schema for the platform state, used by {@link MerkleStateRoot}
 * to implement accessors to the platform state.
 */
public enum PlatformStateService implements Service {
    PLATFORM_STATE_SERVICE;

    /**
     * Temporary access to a function that computes an application version from config.
     */
    private static final AtomicReference<Function<Configuration, SoftwareVersion>> APP_VERSION_FN =
            new AtomicReference<>();
    /**
     * Temporary access to the disk address book used in upgrade or network transplant
     * scenarios before the roster lifecycle is enabled.
     */
    @Deprecated
    private static final AtomicReference<AddressBook> DISK_ADDRESS_BOOK = new AtomicReference<>();

    /**
     * Temporary access to the re-clamped stake weights used in 0.58 upgrade specifically.
     */
    private static final AtomicReference<Map<Long, Long>> RECLAMPED_STAKE_WEIGHTS = new AtomicReference<>();

    /**
     * The schemas to register with the {@link SchemaRegistry}.
     */
    private static final Collection<Schema> SCHEMAS = List.of(
            new V0540PlatformStateSchema(DISK_ADDRESS_BOOK::get, config -> requireNonNull(APP_VERSION_FN.get())
                    .apply(config)),
            new V058RosterLifecycleTransitionSchema(
                    DISK_ADDRESS_BOOK::get,
                    RECLAMPED_STAKE_WEIGHTS::get,
                    config -> requireNonNull(APP_VERSION_FN.get()).apply(config),
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
     * Sets the application version to the given version.
     * @param appVersionFn the version to set as the application version
     */
    public void setAppVersionFn(@NonNull final Function<Configuration, SoftwareVersion> appVersionFn) {
        APP_VERSION_FN.set(requireNonNull(appVersionFn));
    }

    /**
     * Sets the disk address book to the given address book.
     */
    public void setDiskAddressBook(@NonNull final AddressBook addressBook) {
        DISK_ADDRESS_BOOK.set(requireNonNull(addressBook));
    }

    /**
     * Clears the disk address book.
     */
    public void clearDiskAddressBook() {
        DISK_ADDRESS_BOOK.set(null);
    }

    /**
     * Sets the re-clamped stake weights to the given map.
     */
    public void setReclampedStakeWeights(@NonNull final Map<Long, Long> reclampedStakeWeights) {
        RECLAMPED_STAKE_WEIGHTS.set(requireNonNull(reclampedStakeWeights));
    }

    /**
     * Clears the re-clamped stake weights.
     */
    public void clearReclampedStakeWeights() {
        RECLAMPED_STAKE_WEIGHTS.set(null);
    }

    /**
     * Given a {@link MerkleStateRoot}, returns the creation version of the platform state if it exists.
     * @param root the root to extract the creation version from
     * @return the creation version of the platform state, or null if the state is a genesis state
     */
    public SemanticVersion creationVersionOf(@NonNull final MerkleStateRoot<?> root) {
        requireNonNull(root);
        final var state = platformStateOf(root);
        return state == null ? null : state.creationSoftwareVersionOrThrow();
    }

    /**
     * Given a {@link MerkleStateRoot}, returns the round number of the platform state if it exists.
     * @param root the root to extract the round number from
     * @return the round number of the platform state, or zero if the state is a genesis state
     */
    public long roundOf(@NonNull final MerkleStateRoot<?> root) {
        requireNonNull(root);
        final var platformState = platformStateOf(root);
        return platformState == null
                ? 0L
                : platformState
                        .consensusSnapshotOrElse(ConsensusSnapshot.DEFAULT)
                        .round();
    }

    @SuppressWarnings("unchecked")
    public @Nullable PlatformState platformStateOf(@NonNull final MerkleStateRoot<?> root) {
        final var index = root.findNodeIndex(NAME, PLATFORM_STATE_KEY);
        if (index == -1) {
            return null;
        }
        return ((SingletonNode<PlatformState>) root.getChild(index)).getValue();
    }
}
