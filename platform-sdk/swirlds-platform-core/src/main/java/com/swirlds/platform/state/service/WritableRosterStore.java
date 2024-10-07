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
import com.swirlds.platform.state.RosterStateAccessor;
import com.swirlds.platform.state.RosterStateModifier;
import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Read-write interface for accessing rosters states.
 */
public class WritableRosterStore implements RosterStateModifier {

    private final WritableStates writableStates;
    private final RosterStateAccessor rosterStateAccessor;

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
        this.writableStates = writableStates;
        this.rosterStateAccessor = new ReadableRosterStore(writableStates);
        this.rosterState = writableStates.getSingleton(RosterStateId.ROSTER_STATES_KEY);
        this.rosterMap = writableStates.get(RosterStateId.ROSTER_KEY);
    }

    /**
     * Set the candidate roster if valid and doesn't yet exist in the state.
     *
     * @param candidateRoster a candidate roster to set.
     */
    @Override
    public void setCandidateRoster(@NonNull final Roster candidateRoster) {
        Objects.requireNonNull(candidateRoster);
        RosterValidator.validate(candidateRoster);

        final Bytes incomingCandidateRosterHash =
                RosterUtils.hash(candidateRoster).getBytes();

        // update the roster state/map
        final RosterState previousRosterState = rosterStateOrThrow();
        final Builder rosterStateBuilder = RosterState.newBuilder()
                .candidateRosterHash(incomingCandidateRosterHash)
                .roundRosterPairs(previousRosterState.roundRosterPairs());
        final Bytes previousCandidateRosterHash = previousRosterState.candidateRosterHash();
        removeRoster(previousCandidateRosterHash);
        storeRoster(candidateRoster, incomingCandidateRosterHash, rosterStateBuilder);
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Roster getCandidateRoster() {
        return rosterStateAccessor.getCandidateRoster();
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Roster getActiveRoster() {
        return rosterStateAccessor.getActiveRoster();
    }

    /**
     * Stores this roster as the active roster.
     *
     * @param roster     a roster to set as active
     * @param round     the round in which this roster became active
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
            if (round <= activeRosterPair.roundNumber()) {
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

        final Builder rosterStateBuilder = RosterState.newBuilder()
                .candidateRosterHash(previousRosterState.candidateRosterHash())
                .roundRosterPairs(roundRosterPairs);
        storeRoster(roster, activeRosterHash, rosterStateBuilder);
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
     * Stores the roster in the roster state, roster map and commits the changes.
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

        if (writableStates instanceof final CommittableWritableStates committableWritableStates) {
            committableWritableStates.commit();
        }
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
