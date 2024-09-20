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

import static com.swirlds.platform.util.BootstrapUtils.detectSoftwareUpgrade;
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
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.SoftwareVersion;
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

        // update the roster state
        final RosterState currentRosterState = rosterStateOrThrow();
        final Bytes candidateRosterHash = RosterUtils.hashOf(candidateRoster).getBytes();
        final Builder rosterStateBuilder = RosterState.newBuilder()
                .candidateRosterHash(candidateRosterHash)
                .roundRosterPairs(currentRosterState.roundRosterPairs());
        this.rosterState.put(rosterStateBuilder.build());

        // update the roster map and commit the changes
        this.rosterMap.put(ProtoBytes.newBuilder().value(candidateRosterHash).build(), candidateRoster);
        commit();
    }

    /**
     * Determines the initial active roster based on the given software version and initial state.
     * The active roster is obtained by adopting the candidate roster if a software upgrade is detected.
     * Otherwise, the active roster is retrieved from the state or an exception is thrown if not found.
     *
     * @param version the software version of the current node
     * @param initialState the initial state of the platform
     * @return the active roster which will be used by the platform
     */
    @NonNull
    @Override
    public Roster determineActiveRoster(@NonNull final SoftwareVersion version,
            @NonNull final ReservedSignedState initialState) {
        final boolean softwareUpgrade = detectSoftwareUpgrade(version, initialState.get());

        if (!softwareUpgrade) {
            final Roster lastUsedActiveRoster = rosterStateAccessor.getActiveRoster();
            // not in software upgrade mode (i.e., normal restart), return the
            // active roster present in the state or throw if not found.
            return Objects.requireNonNull(
                    lastUsedActiveRoster, "Active Roster must be present in the state during normal network restart.");
        }

        // software upgrade is detected, we adopt the candidate roster
        final Roster candidateRoster = Objects.requireNonNull(rosterStateAccessor.getCandidateRoster(),
                "Candidate Roster must be present in the state during software upgrade.");
        adoptCandidateRoster(initialState.get().getRound() + 1);
        commit();
        return candidateRoster;
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
    @NonNull
    @Override
    public Roster getActiveRoster() {
        return rosterStateAccessor.getActiveRoster();
    }

    /**
     * Adopts the candidate roster present in the state as the active roster.
     * Until the Dynamic Address Book is implemented, this method will be called unconditionally on network upgrade.
     *
     * @param roundNumber the round number in which the candidate roster became active
     */
    private void adoptCandidateRoster(final long roundNumber) {
        final RosterState previousRosterState = rosterStateOrThrow();
        final Roster candidateRoster = rosterMap.get(ProtoBytes.newBuilder()
                .value(previousRosterState.candidateRosterHash())
                .build());
        if (candidateRoster == null) {
            throw new IllegalStateException("Candidate roster not found in the state.");
        }

        storeAsActive(candidateRoster, roundNumber);
        removeCandidateRoster();
    }

    /**
     * Stores this roster as the active roster.
     *
     * @param roster        a roster to set as active
     * @param round        the round in which this roster became active
     */
    private void storeAsActive(@NonNull final Roster roster, final long round) {
        Objects.requireNonNull(roster);
        RosterValidator.validate(roster);

        final RosterState previousRosterState = rosterStateOrThrow();

        // update the roster state
        final List<RoundRosterPair> roundRosterPairs = new LinkedList<>(previousRosterState.roundRosterPairs());
        final Bytes activeRosterHash = RosterUtils.hashOf(roster).getBytes();
        roundRosterPairs.addFirst(new RoundRosterPair(round, activeRosterHash));

        // remove the formerly previous active roster, i.e., the roster that was active before the last two adopted
        // rosters, if any.
        if (roundRosterPairs.size() > 2) {
            roundRosterPairs.removeLast();
        }

        final Builder rosterStateBuilder = RosterState.newBuilder()
                .candidateRosterHash(previousRosterState.candidateRosterHash())
                .roundRosterPairs(roundRosterPairs);
        this.rosterState.put(rosterStateBuilder.build());

        // update the roster map
        this.rosterMap.put(ProtoBytes.newBuilder().value(activeRosterHash).build(), roster);
    }

    /**
     * Removes the candidate roster from the roster state.
     * This method is called after the candidate roster is adopted as the active roster.
     */
    private void removeCandidateRoster() {
        final RosterState previousRosterState = rosterStateOrThrow();

        final Builder rosterStateBuilder = RosterState.newBuilder()
                .candidateRosterHash(Bytes.EMPTY)
                .roundRosterPairs(previousRosterState.roundRosterPairs());
        this.rosterState.put(rosterStateBuilder.build());
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
     * Commits the changes to the state.
     */
    private void commit() {
        if (writableStates instanceof final CommittableWritableStates committableWritableStates) {
            committableWritableStates.commit();
        }
    }
}
