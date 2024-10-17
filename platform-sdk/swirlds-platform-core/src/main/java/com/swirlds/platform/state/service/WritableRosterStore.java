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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.node.state.roster.RosterState.Builder;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.RosterStateId;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.roster.RosterValidator;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Read-write implementation for accessing rosters states.
 */
public class WritableRosterStore {

    private final ReadableRosterStore rosterStateAccessor;

    /**
     * The roster state singleton. This is the state that holds the candidate roster hash and the list of pairs of
     * active roster hashes and the round number in which those rosters became active.
     *
     * @implNote the use of {@link ReadablePlatformStateStore} and {@link WritablePlatformStateStore} to provide access
     * to the roster states (beyond just the {@link PlatformState}) is deliberate, for convenience.
     */
    private final WritableSingletonState<RosterState> rosterState;

    private final WritableKVState<ProtoBytes, Roster> rosterMap;

    /**
     * The maximum number of active rosters to keep in the roster state.
     */
    static final int MAXIMUM_ROSTER_HISTORY_SIZE = 2;

    /**
     * Constructs a new {@link WritableRosterStore} instance.
     *
     * @param writableStates the readable states
     */
    public WritableRosterStore(@NonNull final WritableStates writableStates) {
        Objects.requireNonNull(writableStates);
        this.rosterStateAccessor = new ReadableRosterStore(writableStates);
        this.rosterState = writableStates.getSingleton(RosterStateId.ROSTER_STATES_KEY);
        this.rosterMap = writableStates.get(RosterStateId.ROSTER_KEY);
    }

    /**
     * Sets the candidate roster. This will be called to inform the platform of a new candidate roster.
     * Setting the candidate roster indicates that this roster should be adopted as the active roster when required.
     *
     * @param candidateRoster a candidate roster to set. It must be a valid roster.
     */
    public void setCandidateRoster(@NonNull final Roster candidateRoster) {
        Objects.requireNonNull(candidateRoster);
        RosterValidator.validate(candidateRoster);

        final Bytes incomingCandidateRosterHash =
                RosterUtils.hash(candidateRoster).getBytes();

        // update the roster state/map
        final RosterState previousRosterState = rosterStateOrThrow();
        final Bytes previousCandidateRosterHash = previousRosterState.candidateRosterHash();
        final Builder newRosterState =
                previousRosterState.copyBuilder().candidateRosterHash(incomingCandidateRosterHash);
        removeRoster(previousCandidateRosterHash);
        storeRoster(candidateRoster, incomingCandidateRosterHash, newRosterState);
    }

    /**
     * Gets the candidate roster.
     *
     * @return the candidate roster
     */
    @Nullable
    public Roster getCandidateRoster() {
        return rosterStateAccessor.getCandidateRoster();
    }

    /**
     * Gets the active roster present in the state.
     *
     * @return the active roster if present. Null otherwise.
     */
    @Nullable
    public Roster getActiveRoster() {
        return rosterStateAccessor.getActiveRoster();
    }

    /**
     * Sets the Active roster.
     * This will be called to store a new Active Roster in the state.
     * The roster must be valid according to rules codified in {@link com.swirlds.platform.roster.RosterValidator}.
     *
     * @param roster an active roster to set
     * @param round the round number in which the roster became active.
     *              It must be a positive number greater than the round number of the current active roster.
     */
    public void setActiveRoster(@NonNull final Roster roster, final long round) {
        Objects.requireNonNull(roster);
        RosterValidator.validate(roster);

        // update the roster state
        final RosterState previousRosterState = rosterStateOrThrow();
        final List<RoundRosterPair> roundRosterPairs = new LinkedList<>(previousRosterState.roundRosterPairs());
        final Bytes activeRosterHash = RosterUtils.hash(roster).getBytes();
        if (!roundRosterPairs.isEmpty()) {
            final RoundRosterPair activeRosterPair = roundRosterPairs.getFirst();
            if (round < 0 || round <= activeRosterPair.roundNumber()) {
                throw new IllegalArgumentException(
                        "incoming round number must be greater than the round number of the current active roster.");
            }
        }
        roundRosterPairs.addFirst(new RoundRosterPair(round, activeRosterHash));

        if (roundRosterPairs.size() > MAXIMUM_ROSTER_HISTORY_SIZE) {
            final RoundRosterPair lastRemovedRoster = roundRosterPairs.removeLast();
            removeRoster(lastRemovedRoster.activeRosterHash());

            // At this phase of the implementation, the roster state has a fixed size limit for active rosters.
            // Future implementations (e.g. DAB) can modify this.
            if (roundRosterPairs.size() > MAXIMUM_ROSTER_HISTORY_SIZE) {
                // additional safety check to ensure that the roster state does not contain more than set limit.
                throw new IllegalStateException(
                        "Active rosters in the Roster state cannot be more than  " + MAXIMUM_ROSTER_HISTORY_SIZE);
            }
        }

        final Builder newRosterState = previousRosterState
                .copyBuilder()
                .candidateRosterHash(Bytes.EMPTY)
                .roundRosterPairs(roundRosterPairs);
        // since a new active roster is being set, the existing candidate roster is no longer valid
        // so we remove it if it meets removal criteria.
        removeRoster(previousRosterState.candidateRosterHash());
        storeRoster(roster, activeRosterHash, newRosterState);
    }

    /**
     * Returns the roster state or throws an exception if the state is null.
     * @return the roster state
     * @throws NullPointerException if the roster state is null
     */
    @NonNull
    private RosterState rosterStateOrThrow() {
        return requireNonNull(rosterState.get());
    }

    /**
     * Stores the roster in the roster map and updates the roster store with the provided builder.
     *
     * @param rosterToStore              the roster to store
     * @param rosterHash                   the hash of the roster
     * @param rosterStateBuilder       the roster state builder
     */
    private void storeRoster(
            @NonNull final Roster rosterToStore,
            @NonNull final Bytes rosterHash,
            @NonNull final Builder rosterStateBuilder) {

        this.rosterState.put(rosterStateBuilder.build());
        this.rosterMap.put(ProtoBytes.newBuilder().value(rosterHash).build(), rosterToStore);
    }

    /**
     * Removes a roster from the roster map, but only if it doesn't match any of the active roster hashes in
     * the roster state. The check ensures we don't inadvertently remove a roster still in use.
     *
     * @param rosterHash the hash of the roster
     */
    private void removeRoster(@NonNull final Bytes rosterHash) {
        final List<RoundRosterPair> activeRosterHistory = rosterStateOrThrow().roundRosterPairs();
        if (activeRosterHistory.stream()
                .noneMatch(rosterPair -> rosterPair.activeRosterHash().equals(rosterHash))) {
            this.rosterMap.remove(ProtoBytes.newBuilder().value(rosterHash).build());
        }
    }
}
