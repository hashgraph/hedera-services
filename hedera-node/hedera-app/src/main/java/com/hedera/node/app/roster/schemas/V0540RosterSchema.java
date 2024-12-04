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
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.state.service.ReadablePlatformStateStore;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Initial {@link com.hedera.node.app.roster.RosterService} schema that registers two states,
 * <ol>
 *     <li>A mapping from roster hashes to rosters (which may be either candidate or active).</li>
 *     <li>A singleton that contains the history of active rosters along with the round numbers where
 *     they were adopted; along with the hash of a candidate roster if there is one.</li>
 * </ol>
 */
public class V0540RosterSchema extends Schema implements RosterTransplantSchema {
    private static final Logger log = LogManager.getLogger(V0540RosterSchema.class);

    public static final String ROSTER_KEY = "ROSTERS";
    public static final String ROSTER_STATES_KEY = "ROSTER_STATE";

    private static final long MAX_ROSTERS = 65_536L;

    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(54).patch(0).build();

    /**
     * The test to use to determine if a candidate roster may be adopted at an upgrade boundary.
     */
    private final Predicate<Roster> canAdopt;
    /**
     * The factory to use to create the writable roster store.
     */
    private final Function<WritableStates, WritableRosterStore> rosterStoreFactory;
    /**
     * Required until the upgrade that adopts the roster lifecycle; at that upgrade boundary,
     * we must initialize the active roster from the platform state's legacy address books.
     */
    @Deprecated
    private final Supplier<ReadablePlatformStateStore> platformStateStoreFactory;

    public V0540RosterSchema(
            @NonNull final Predicate<Roster> canAdopt,
            @NonNull final Function<WritableStates, WritableRosterStore> rosterStoreFactory,
            @NonNull final Supplier<ReadablePlatformStateStore> platformStateStoreFactory) {
        super(VERSION);
        this.canAdopt = requireNonNull(canAdopt);
        this.rosterStoreFactory = requireNonNull(rosterStoreFactory);
        this.platformStateStoreFactory = requireNonNull(platformStateStoreFactory);
    }

    @Override
    public @NonNull Set<StateDefinition> statesToCreate() {
        return Set.of(
                StateDefinition.singleton(ROSTER_STATES_KEY, RosterState.PROTOBUF),
                StateDefinition.onDisk(ROSTER_KEY, ProtoBytes.PROTOBUF, Roster.PROTOBUF, MAX_ROSTERS));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        final var rosterState = ctx.newStates().getSingleton(ROSTER_STATES_KEY);
        if (!ctx.appConfig().getConfigData(AddressBookConfig.class).useRosterLifecycle()) {
            log.info("Setting default roster state for non-lifecycle mode");
            rosterState.put(RosterState.DEFAULT);
        } else {
            final var startupNetworks = ctx.startupNetworks();
            final var rosterStore = rosterStoreFactory.apply(ctx.newStates());
            final var activeRoundNumber = ctx.roundNumber() + 1;
            if (ctx.isGenesis()) {
                rosterStore.putActiveRoster(rosterFrom(startupNetworks.genesisNetworkOrThrow()), 0L);
            } else if (rosterStore.getActiveRoster() == null) {
                // (FUTURE) Once the roster lifecycle is active by default, remove this code building an initial
                // roster history  from the last address book and the first roster at the upgrade boundary
                final var addressBook = platformStateStoreFactory.get().getAddressBook();
                final var previousRoster = buildRoster(requireNonNull(addressBook));
                rosterStore.putActiveRoster(previousRoster, 0);
                final var currentRoster = rosterFrom(startupNetworks.migrationNetworkOrThrow());
                rosterStore.putActiveRoster(currentRoster, activeRoundNumber);
            } else if (ctx.isUpgrade(ServicesSoftwareVersion::from, ServicesSoftwareVersion::new)) {
                final var candidateRoster = rosterStore.getCandidateRoster();
                if (candidateRoster == null) {
                    log.info("No candidate roster to adopt in round {}", activeRoundNumber);
                } else if (canAdopt.test(candidateRoster)) {
                    log.info("Adopting candidate roster in round {}", activeRoundNumber);
                    rosterStore.adoptCandidateRoster(activeRoundNumber);
                } else {
                    log.info("Rejecting candidate roster in round {}", activeRoundNumber);
                }
            }
        }
    }

    @Override
    public void restart(@NonNull MigrationContext ctx) {
        RosterTransplantSchema.super.restart(ctx, rosterStoreFactory);
    }
}
