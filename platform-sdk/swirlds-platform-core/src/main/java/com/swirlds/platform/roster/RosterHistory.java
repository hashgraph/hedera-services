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

package com.swirlds.platform.roster;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;

/**
 * A Roster History object that encapsulates information about the current active roster
 * and the previous active roster, and their corresponding starting round numbers.
 */
public class RosterHistory {
    private final List<RoundRosterPair> history;
    private final Map<Bytes, Roster> rosters;

    /**
     * Construct a RosterHistory out of the RosterState and RosterMap inputs.
     * @param history a non-empty list of round number/roster hash pairs
     * @param rosters a map from roster hash to roster objects which must contain all the roster hashes found in the history.
     */
    public RosterHistory(@NonNull final List<RoundRosterPair> history, @NonNull final Map<Bytes, Roster> rosters) {
        this.history = history;
        this.rosters = rosters;

        if (history.isEmpty()) {
            throw new IllegalArgumentException("Roster history is empty");
        }

        if (history.stream().map(RoundRosterPair::activeRosterHash).anyMatch(hash -> !rosters.containsKey(hash))) {
            throw new IllegalArgumentException(
                    "Roster history refers to roster hashes not found in the roster map: history: " + history
                            + ", rosters: " + rosters);
        }
    }

    /**
     * Constructs a RosterHistory out of a current and previous Roster objects and their corresponding starting round numbers.
     * This is a simplified version of the constructor to be used with the legacy, AddressBook/config.txt-driven startup lifecycle
     * until the new, Roster-only-based lifecycle is fully implemented.
     * @param currentRoster currentRoster
     * @param currentRound {@code currentRound >= previousRound}
     * @param previousRoster previousRoster
     * @param previousRound {@code previousRound <= currentRound}
     */
    public RosterHistory(
            @NonNull final Roster currentRoster,
            final long currentRound,
            @NonNull final Roster previousRoster,
            final long previousRound) {
        if (currentRound < previousRound) {
            throw new IllegalArgumentException(
                    "Current round must be greater than or equal to the previous round. currentRound: " + currentRound
                            + ", previousRound: " + previousRound);
        }

        final Bytes currentHash = RosterUtils.hash(currentRoster).getBytes();
        final Bytes previousHash = RosterUtils.hash(previousRoster).getBytes();

        this.rosters = Map.of(
                currentHash, currentRoster,
                previousHash, previousRoster);

        this.history = List.of(
                RoundRosterPair.newBuilder()
                        .activeRosterHash(currentHash)
                        .roundNumber(currentRound)
                        .build(),
                RoundRosterPair.newBuilder()
                        .activeRosterHash(previousHash)
                        .roundNumber(previousRound)
                        .build());
    }

    /**
     * Returns the current active roster, which is the very first (index == 0) entry in the history list.
     * @return the current active roster
     */
    @NonNull
    public Roster getCurrentRoster() {
        return rosters.get(history.get(0).activeRosterHash());
    }

    /**
     * Returns the previous roster, which is the second (index == 1) entry in the history list,
     * or the very first entry equal to the current active roster if the history has a single entry only.
     * @return the previous roster
     */
    @NonNull
    public Roster getPreviousRoster() {
        return rosters.get(history.get(history.size() > 1 ? 1 : 0).activeRosterHash());
    }

    /**
     * Returns a roster active in a given round, or null if the given round predates the available roster history.
     * @param roundNumber a round number
     * @return an active roster for that round
     */
    @Nullable
    public Roster getRosterForRound(final long roundNumber) {
        for (final RoundRosterPair roundRosterPair : history) {
            if (roundRosterPair.roundNumber() <= roundNumber) {
                return rosters.get(roundRosterPair.activeRosterHash());
            }
        }
        return null;
    }
}
