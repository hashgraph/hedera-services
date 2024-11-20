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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.Network;
import com.hedera.hapi.node.state.NodeMetadata;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.node.app.tss.stores.WritableTssStore;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.swirlds.platform.config.AddressBookConfig;
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
 * A restart-only schema that ensures state has a current roster if {@link AddressBookConfig#useRosterLifecycle()} is
 * set; the logic is currently simplified to use the genesis roster from the genesis {@link Network}s returned by
 * {@link MigrationContext#startupNetworks()}, and to promote any candidate roster in state to the current roster on
 * an upgrade.
 * <p>
 * The full logic will be done in <a href="https://github.com/hashgraph/hedera-services/issues/16552">this</a> issue.
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

    private final Function<WritableStates, WritableTssStore> tssStoreFactory;

    public V057RosterSchema(
            @NonNull final Predicate<Roster> canAdopt,
            @NonNull final Function<WritableStates, WritableRosterStore> rosterStoreFactory,
            @NonNull final Function<WritableStates, WritableTssStore> tssStoreFactory) {
        super(VERSION);
        this.canAdopt = requireNonNull(canAdopt);
        this.rosterStoreFactory = requireNonNull(rosterStoreFactory);
        this.tssStoreFactory = requireNonNull(tssStoreFactory);
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
                log.info("Found override network for round {}", roundNumber);
                setActiveRoster(roundNumber + 1, rosterStore, overrideNetwork.get());
                cleanUpTssEncryptionKeysFromState(states, rostersEntriesNodeIds);
                startupNetworks.setOverrideRound(roundNumber);
            } else if (isUpgrade(ctx)) {
                if (rosterStore.getActiveRoster() == null) {
                    log.info("Migrating active roster at round {}", roundNumber);
                    // If there is no active roster at a migration boundary, we
                    // must have a migration network in the startup assets
                    final var network = startupNetworks.migrationNetworkOrThrow();
                    rosterStore.putActiveRoster(rosterFrom(network), roundNumber + 1);
                    cleanUpTssEncryptionKeysFromState(states, rostersEntriesNodeIds);
                } else {
                    final var candidateRoster = rosterStore.getCandidateRoster();
                    if (canAdopt.test(candidateRoster)) {
                        log.info("Adopting candidate roster at round {}", roundNumber);
                        rosterStore.adoptCandidateRoster(roundNumber);
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

        var activeRosterEntries =
                rosterStore.getActiveRoster().rosterEntries().stream().map(RosterEntry::nodeId);
        var candidateRosterEntries = requireNonNull(rosterStore.getCandidateRoster()).rosterEntries().stream()
                .map(RosterEntry::nodeId);
        return Stream.concat(activeRosterEntries, candidateRosterEntries)
                .distinct()
                .map(EntityNumber::new)
                .toList();
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
