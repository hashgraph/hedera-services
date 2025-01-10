/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.roster.RosterRetriever;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.platform.state.service.schemas.V0540RosterBaseSchema;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
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
public class V0590RosterSchema extends Schema implements RosterTransplantSchema {
    private static final Logger log = LogManager.getLogger(V0590RosterSchema.class);

    public static final String ROSTER_KEY = "ROSTERS";
    public static final String ROSTER_STATES_KEY = "ROSTER_STATE";

    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(59).patch(0).build();

    /**
     * The delegate schema that defines the base roster schema.
     */
    private final V0540RosterBaseSchema baseSchema = new V0540RosterBaseSchema();
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
    private final Supplier<State> stateSupplier;
    /**
     * Needed to support the legacy, config.txt-driven behavior until Services complete the real Roster lifecycle
     * implementation and eliminate the useRosterLifecycle flag.
     */
    private final Supplier<AddressBook> addressBook;

    public V0590RosterSchema(
            @NonNull final Predicate<Roster> canAdopt,
            @NonNull final Function<WritableStates, WritableRosterStore> rosterStoreFactory,
            @NonNull final Supplier<State> stateSupplier,
            @NonNull final Supplier<AddressBook> addressBook) {
        super(VERSION);
        this.canAdopt = requireNonNull(canAdopt);
        this.rosterStoreFactory = requireNonNull(rosterStoreFactory);
        this.stateSupplier = requireNonNull(stateSupplier);
        this.addressBook = requireNonNull(addressBook);
    }

    @Override
    public @NonNull Set<StateDefinition> statesToCreate() {
        return baseSchema.statesToCreate();
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        if (ctx.isGenesis()) {
            // restart() handles the genesis case.
            // There's no need to migrate anything because at genesis nothing exists yet.
            return;
        }

        final WritableSingletonState<RosterState> rosterState = ctx.newStates().getSingleton(ROSTER_STATES_KEY);
        if (rosterState.get() == null || rosterState.get().roundRosterPairs().isEmpty()) {
            // Migrate data from PlatformState.[previous]AddressBook fields to the RosterService states,
            // unless it's been populated with some data already.
            // The PlatformState fields are going to be nullified in the corresponding PlatformStateSchema.migrate()
            // method
            // which runs **after** this RosterSchema.migrate() method because of the RosterService.migrationOrder().
            final var rosterStore = rosterStoreFactory.apply(ctx.newStates());
            final var previousRoster = RosterRetriever.retrievePreviousRoster(stateSupplier.get());
            if (previousRoster != null) {
                rosterStore.putActiveRoster(previousRoster, 0L);
            }
            final var activeRoster = RosterRetriever.retrieveActiveOrGenesisRoster(stateSupplier.get());
            if (activeRoster != null) {
                rosterStore.putActiveRoster(activeRoster, ctx.roundNumber());
            }
        }
    }

    @Override
    public void restart(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);

        // Fully delegate to the RosterTransplantSchema if we're in the transplant mode
        if (RosterTransplantSchema.super.restart(ctx, rosterStoreFactory)) {
            return;
        }

        final var rosterStore = rosterStoreFactory.apply(ctx.newStates());
        if (!ctx.appConfig().getConfigData(AddressBookConfig.class).useRosterLifecycle()) {
            // FUTURE WORK: this branch will be removed once Services complete the full implementation
            // of the new Roster lifecycle, including the deprecation of the legacy config.txt-based approach.
            // The logic in the `else` branch below will need to be double-checked to ensure it's reasonable
            // and covers all the cases, similar to how this legacy positive branch covers them,
            // but w/o any AddressBooks anymore.

            if (ctx.isGenesis()) {
                rosterStore.putActiveRoster(RosterRetriever.buildRoster(requireNonNull(addressBook.get())), 0L);
            } else {
                // Possible upgrade or an override of the AddressBook
                final var addressBookConfig = ctx.appConfig().getConfigData(AddressBookConfig.class);
                final boolean isOverride = addressBookConfig.forceUseOfConfigAddressBook();
                final boolean addressBookChanged =
                        isOverride || ctx.isUpgrade(ServicesSoftwareVersion::from, ServicesSoftwareVersion::new);
                if (addressBookChanged) {
                    final var diskBook = requireNonNull(addressBook.get()).copy();
                    final var nextBook = isOverride
                            ? withExtantNodeWeights(
                                    diskBook, RosterUtils.buildAddressBook(rosterStore.getActiveRoster()))
                            : diskBook;
                    rosterStore.putActiveRoster(RosterRetriever.buildRoster(nextBook), ctx.roundNumber() + 1L);
                }
            }
        } else {
            final var startupNetworks = ctx.startupNetworks();
            final var activeRoundNumber = ctx.roundNumber() + 1;
            if (ctx.isGenesis()) {
                rosterStore.putActiveRoster(
                        RosterUtils.rosterFrom(startupNetworks.genesisNetworkOrThrow(ctx.platformConfig())), 0L);
            } else if (rosterStore.getActiveRoster() == null) {
                // (FUTURE) Once the roster lifecycle is active by default, remove this code building an initial
                // roster history  from the last address book and the first roster at the upgrade boundary
                final var previousRoster = RosterRetriever.retrieveActiveOrGenesisRoster(stateSupplier.get());
                rosterStore.putActiveRoster(previousRoster, 0);
                final var currentRoster = RosterUtils.rosterFrom(startupNetworks.migrationNetworkOrThrow());
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

    /**
     * If there are weights to copy to the first address book from the (possibly null) second address book,
     * then does so and returns a new address book with the result.
     * @param to the address book to copy weights to
     * @param from the address book to copy weights from
     * @return a address book with any weights copied by matching node id
     */
    private AddressBook withExtantNodeWeights(@NonNull final AddressBook to, @Nullable final AddressBook from) {
        if (from == null) {
            return to;
        }
        final List<Address> addresses = new ArrayList<>();
        for (final var toAddress : to) {
            if (from.contains(toAddress.getNodeId())) {
                final var fromAddress = from.getAddress(toAddress.getNodeId());
                addresses.add(toAddress.copySetWeight(fromAddress.getWeight()));
            } else {
                addresses.add(toAddress);
            }
        }
        return new AddressBook(addresses);
    }
}
