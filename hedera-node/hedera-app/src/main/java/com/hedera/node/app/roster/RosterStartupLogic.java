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

package com.hedera.node.app.roster;

import static com.hedera.node.app.ServicesMain.loadAddressBook;
import static com.swirlds.platform.builder.PlatformBuildConstants.GENESIS_CONFIG_FILE_NAME;
import static com.swirlds.platform.system.address.AddressBookUtils.createRoster;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.swirlds.platform.roster.RosterHistory;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;

public class RosterStartupLogic {
    private final WritableRosterStore rosterStore;
    private final AddressBook diskAddressBook;
    private final ReadableNodeStore addressBookStore;

    public RosterStartupLogic(
            @NonNull final WritableRosterStore rosterStore,
            @NonNull final ReadableNodeStore addressBookStore,
            @NonNull final AddressBook diskAddressBook) {
        requireNonNull(rosterStore);
        requireNonNull(addressBookStore);
        requireNonNull(diskAddressBook);

        this.rosterStore = rosterStore;
        this.addressBookStore = addressBookStore;
        this.diskAddressBook = diskAddressBook;
    }

    /**
     * Determining the Roster History.
     * There are three non-genesis modes that a node can start in:
     * <ul>
     *   <li> Genesis Network - The node is started with a genesis roster and no pre-existing state on disk. </li>
     *   <li> Network Transplant - The node is started with a state on disk and an overriding roster for a different network. </li>
     *   <li> Software Upgrade - The node is restarted with the same state on disk and a software upgrade is happening. </li>
     *   <li> Normal Restart - The node is restarted with the same state on disk and no software upgrade is happening. </li>
     * </ul>
     *
     * @param isGenesis            whether running in genesis mode
     * @param softwareUpgrade      whether a software upgrade is happening
     * @return the roster history if able to determine it, otherwise IllegalStateException is thrown
     */
    public RosterHistory determineRosterHistory(final boolean isGenesis, final boolean softwareUpgrade) {
        if (isGenesis) {
            return initializeGenesisRoster();
        }

        final boolean overrideConfigExists = false; // An override-config.txt file is present on disk
        if (overrideConfigExists) {
            // FUTURE: Network Transplant
        }

        // Normal Restart, no software upgrade is happening
        if (!softwareUpgrade) {
            return handleRestart();
        }

        // Migration Software Upgrade
        // The roster state is empty (no candidate roster and no active rosters)
        final var activeRoster = rosterStore.getActiveRoster();
        final var candidateRoster = rosterStore.getCandidateRoster();

        if (activeRoster == null && candidateRoster == null) {
            return handleMigrationUpgrade();
        }

        // Subsequent Software Upgrades
        // There is a candidate roster in the roster state
        // No override-config.txt file is present on disk
        if (candidateRoster != null && !overrideConfigExists) {
            return handleRegularUpgrade(candidateRoster);
        }

        // If none of the cases above were met, this is a fatal error.
        throw new IllegalStateException("Invalid state for determining roster history");
    }

    @NonNull
    private RosterHistory initializeGenesisRoster() {
        final var genesisAddressBook = loadAddressBook(GENESIS_CONFIG_FILE_NAME);
        final var genesisRoster = createRoster(genesisAddressBook);
        // Set (genesisRoster, 0) ase the new active roster in the roster state.
        rosterStore.putActiveRoster(genesisRoster, 0);

        // rosterHistory := [(genesisRoster, 0)]
        return new RosterHistory(genesisRoster, 0, genesisRoster, 0);
    }

    @NonNull
    private RosterHistory handleRestart() {
        final var roundRosterPairs = rosterStore.getRosterHistory();
        // If there exists active rosters in the roster state.
        if (roundRosterPairs != null) {
            // Read the active rosters and construct the existing rosterHistory from roster state
            final var current = roundRosterPairs.get(0);
            final var previous = roundRosterPairs.get(1);
            return new RosterHistory(
                    rosterStore.get(current.activeRosterHash()),
                    current.roundNumber(),
                    rosterStore.get(previous.activeRosterHash()),
                    previous.roundNumber());
        } else {
            // If there is no roster state content, this is a fatal error: The migration did not happen on software
            // upgrade.
            throw new IllegalStateException("No active rosters found in the roster state");
        }
    }

    @NonNull
    private RosterHistory handleMigrationUpgrade() {
        // Read the current AddressBooks from the platform state.
        // previousRoster := translateToRoster(currentAddressBook)
        final var prevousRoster = addressBookStore.snapshotOfFutureRoster();

        // configAddressBook := Read the address book in config.txt
        // currentRoster := translateToRoster(configAddressBook)
        final var currentRoster = createRoster(diskAddressBook);

        // currentRound := state round +1
        final long currentRound = rosterStore.getRosterHistory().getFirst().roundNumber() + 1;
        // set (previousRoster, 0) as the active roster in the roster state.
        // set (currentRoster, currentRound) as the active roster in the roster state.
        rosterStore.putActiveRoster(prevousRoster, 0);
        rosterStore.putActiveRoster(currentRoster, currentRound);

        // rosterHistory := [(currentRoster, currentRound), (previousRoster, 0)]
        return new RosterHistory(currentRoster, currentRound, prevousRoster, 0);
    }

    @NonNull
    private RosterHistory handleRegularUpgrade(@NonNull final Roster candidateRoster) {
        final var previousRoundPair = rosterStore.getRosterHistory().getFirst();

        // currentRound := state round +1
        final long currentRound = previousRoundPair.roundNumber() + 1;

        // (previousRoster, previousRound) := read the latest (current) active roster and round from the roster
        // state.
        final var previousRoster = rosterStore.getActiveRoster();
        final var previousRound = previousRoundPair.roundNumber();

        // clear the candidate roster from the roster state.
        // set (candidateRoster, currentRound) as the new active roster in the roster state.
        rosterStore.putActiveRoster(candidateRoster, currentRound);

        // new rosterHistory := [(candidateRoster, currentRound), (previousRoster, previousRound)]
        return new RosterHistory(candidateRoster, currentRound, previousRoster, previousRound);
    }
}
