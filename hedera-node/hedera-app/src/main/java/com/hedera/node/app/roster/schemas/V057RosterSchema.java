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

package com.hedera.node.app.roster.schemas;

import static com.swirlds.platform.roster.RosterRetriever.buildRoster;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.node.app.tss.stores.WritableTssStore;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.node.internal.network.Network;
import com.hedera.node.internal.network.NodeMetadata;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.state.service.ReadablePlatformStateStore;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A restart-only schema that ensures state has a current roster if {@link AddressBookConfig#useRosterLifecycle()} is set.
 */
public class V057RosterSchema extends Schema {
    private static final Logger log = LogManager.getLogger(V057RosterSchema.class);

    private static final long GENESIS_ROUND_NO = 0L;
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(57).build();

    /**
     * The test to use to determine if a candidate roster may be
     * adopted at an upgrade boundary.
     */
    private final Predicate<Roster> canAdopt;
    /**
     * The factory to use to create the writable roster store.
     */
    private final Function<WritableStates, WritableRosterStore> rosterStoreFactory;
    /**
     * The factory to use to create the readable platform store.
     */
    private final Function<WritableStates, ReadablePlatformStateStore> platformStateStoreFactory;
    /**
     * The factory to use to create the writable tss store.
     */
    private final Function<WritableStates, WritableTssStore> tssStoreFactory;

    public V057RosterSchema(
            @NonNull final Predicate<Roster> canAdopt,
            @NonNull final Function<WritableStates, WritableRosterStore> rosterStoreFactory,
            @NonNull final Function<WritableStates, ReadablePlatformStateStore> platformStateStoreFactory,
            @NonNull final Function<WritableStates, WritableTssStore> tssStoreFactory) {
        super(VERSION);
        this.canAdopt = requireNonNull(canAdopt);
        this.rosterStoreFactory = requireNonNull(rosterStoreFactory);
        this.tssStoreFactory = requireNonNull(tssStoreFactory);
        this.platformStateStoreFactory = requireNonNull(platformStateStoreFactory);
    }

    @Override
    public void restart(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        if (!ctx.configuration().getConfigData(AddressBookConfig.class).useRosterLifecycle()) {
            return;
        }
        final var states = ctx.newStates();
        final var rosterStore = rosterStoreFactory.apply(states);
        final var rostersEntriesNodeIds = combinedEntriesNodeIds(rosterStore);
        final var startupNetworks = ctx.startupNetworks();
        if (ctx.isGenesis()) {
            setActiveRoster(GENESIS_ROUND_NO, rosterStore, startupNetworks.genesisNetworkOrThrow());
            cleanUpTssEncryptionKeysFromState(states, rostersEntriesNodeIds);
        } else {
            final long roundNumber = ctx.roundNumber();
            final var overrideNetwork = startupNetworks.overrideNetworkFor(roundNumber);
            if (overrideNetwork.isPresent()) {
                // currentRound := state round +1
                final long currentRound = roundNumber + 1;
                log.info("Found override network for round {}", currentRound);

                // If there is no active roster in the roster state.
                if (rosterStore.getActiveRoster() == null) {
                    //   Read the current AddressBooks from the platform state.
                    //   previousRoster := translateToRoster(currentAddressBook)
                    final var platformState = platformStateStoreFactory.apply(ctx.newStates());
                    final var previousRoster = buildRoster(platformState.getAddressBook());
                    //   (previousRoster, previousRound) := (previousRoster, 0)
                    //   set (previousRoster, 0) as the active roster in the roster state.
                    rosterStore.putActiveRoster(previousRoster, 0L);
                    cleanUpTssEncryptionKeysFromState(states, rostersEntriesNodeIds);
                }

                // set (overrideRoster, currentRound) as the active roster in the roster state.
                setActiveRoster(currentRound, rosterStore, overrideNetwork.get());
                cleanUpTssEncryptionKeysFromState(states, rostersEntriesNodeIds);
                startupNetworks.setOverrideRound(roundNumber);
            } else if (isUpgrade(ctx)) {
                if (rosterStore.getActiveRoster() == null) {
                    // currentRound := state round +1
                    final long currentRound = roundNumber + 1;
                    log.info("Migrating active roster at round {}", currentRound);

                    // Read the current AddressBooks from the platform state.
                    // previousRoster := translateToRoster(currentAddressBook)
                    // set (previousRoster, 0) as the active roster in the roster state.
                    final var platformState = platformStateStoreFactory.apply(ctx.newStates());
                    final var previousRoster = buildRoster(platformState.getAddressBook());
                    rosterStore.putActiveRoster(previousRoster, 0L);
                    cleanUpTssEncryptionKeysFromState(states, rostersEntriesNodeIds);

                    // If there is no active roster at a migration boundary, we
                    // must have a migration network in the startup assets
                    // configAddressBook := Read the address book in config.txt
                    final var network = startupNetworks.migrationNetworkOrThrow();

                    // currentRoster := translateToRoster(configAddressBook)
                    // set (currentRoster, currentRound) as the active roster in the roster state.
                    rosterStore.putActiveRoster(rosterFrom(network), currentRound);
                    cleanUpTssEncryptionKeysFromState(states, rostersEntriesNodeIds);
                } else {
                    // candidateRoster := read the candidate roster from the roster state.
                    final var candidateRoster = rosterStore.getCandidateRoster();
                    if (canAdopt.test(candidateRoster)) {
                        // currentRound := state round +1
                        final long currentRound = roundNumber + 1;
                        log.info("Adopting candidate roster at round {}", currentRound);

                        // set (candidateRoster, currentRound) as the new active roster in the roster state.
                        rosterStore.adoptCandidateRoster(currentRound);
                        cleanUpTssEncryptionKeysFromState(states, rostersEntriesNodeIds);
                    }
                }
            }
        }
    }

    private void setActiveRoster(
            final long roundNumber, @NonNull final WritableRosterStore rosterStore, @NonNull final Network network) {
        rosterStore.putActiveRoster(rosterFrom(network), roundNumber);
    }

    private List<EntityNumber> combinedEntriesNodeIds(@NonNull final WritableRosterStore rosterStore) {
        requireNonNull(rosterStore);
        final var activeRoster = rosterStore.getActiveRoster();
        final var candidateRoster = rosterStore.getCandidateRoster();

        if (activeRoster != null && candidateRoster != null) {
            Stream<Long> activeRosterEntries =
                    activeRoster.rosterEntries().stream().map(RosterEntry::nodeId);
            Stream<Long> candidateRosterEntries =
                    candidateRoster.rosterEntries().stream().map(RosterEntry::nodeId);
            return Stream.concat(activeRosterEntries, candidateRosterEntries)
                    .distinct()
                    .map(EntityNumber::new)
                    .toList();
        }
        // If we are here, at least one of the rosters is null
        // if activeRoster is not null, then candidateRoster is null
        // so we return only the entries from activeRoster.
        if (activeRoster != null) {
            return activeRoster.rosterEntries().stream()
                    .map(RosterEntry::nodeId)
                    .map(EntityNumber::new)
                    .toList();
        }
        // If we are here, then activeRoster is null
        // so we check whether to return the candidateRoster's entries.
        if (candidateRoster != null) {
            return candidateRoster.rosterEntries().stream()
                    .map(RosterEntry::nodeId)
                    .map(EntityNumber::new)
                    .toList();
        }
        // Empty list if both rosters are null.
        return List.of();
    }

    private void cleanUpTssEncryptionKeysFromState(
            @NonNull final WritableStates states, @NonNull final List<EntityNumber> rostersEntriesNodeIds) {
        requireNonNull(states);
        requireNonNull(rostersEntriesNodeIds);
        tssStoreFactory.apply(states).removeIfNotPresent(rostersEntriesNodeIds);
    }

    private Roster rosterFrom(@NonNull final Network network) {
        return new Roster(network.nodeMetadata().stream()
                .map(NodeMetadata::rosterEntryOrThrow)
                .toList());
    }

    private boolean isUpgrade(@NonNull final MigrationContext ctx) {
        return ServicesSoftwareVersion.from(ctx.configuration())
                        .compareTo(new ServicesSoftwareVersion(requireNonNull(ctx.previousVersion())))
                > 0;
    }
}
