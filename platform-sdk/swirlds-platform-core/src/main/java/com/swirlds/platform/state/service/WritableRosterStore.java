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
import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Provides write methods for interacting with Rosters.
 */
public class WritableRosterStore extends ReadableRosterStoreImpl {

    private final WritableStates writableStates;

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
        super(writableStates);
        this.writableStates = writableStates;
        this.rosterState = writableStates.getSingleton(RosterStateId.ROSTER_STATES_KEY);
        this.rosterMap = writableStates.get(RosterStateId.ROSTER_KEY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setActiveRoster(@NonNull final Roster roster, final long round) {
        Objects.requireNonNull(roster);
        RosterValidator.validate(roster);

        final RosterState previousRosterState = rosterStateOrThrow();
        Objects.requireNonNull(previousRosterState);

        // update the roster state
        final List<RoundRosterPair> roundRosterPairs = new LinkedList<>(previousRosterState.roundRosterPairs());
        final Bytes activeRosterHash = RosterUtils.hashOf(roster).getBytes();
        roundRosterPairs.addFirst(new RoundRosterPair(round, activeRosterHash));
        final Builder rosterStateBuilder = RosterState.newBuilder()
                .candidateRosterHash(previousRosterState.candidateRosterHash())
                .roundRosterPairs(roundRosterPairs);
        update(rosterStateBuilder);

        // update the roster map
        update(ProtoBytes.newBuilder().value(activeRosterHash).build(), roster);
    }

    /**
     * Set the candidate roster.
     *
     * @param candidateRoster a candidate roster to set
     */
    public void setCandidateRoster(@NonNull final Roster candidateRoster) {
        Objects.requireNonNull(candidateRoster);
        RosterValidator.validate(candidateRoster);

        // update the roster state
        final RosterState previousRosterState = rosterStateOrThrow();
        final Bytes candidateRosterHash = RosterUtils.hashOf(candidateRoster).getBytes();
        final Builder rosterStateBuilder = RosterState.newBuilder()
                .candidateRosterHash(candidateRosterHash)
                .roundRosterPairs(previousRosterState.roundRosterPairs());
        update(rosterStateBuilder);

        // update the roster map
        update(ProtoBytes.newBuilder().value(candidateRosterHash).build(), candidateRoster);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void adoptCandidateRoster(final long roundNumber) {
        final RosterState previousRosterState = rosterStateOrThrow();
        final Roster candidateRoster = rosterMap.get(ProtoBytes.newBuilder()
                .value(previousRosterState.candidateRosterHash())
                .build());
        if (candidateRoster == null) {
            throw new IllegalStateException("Candidate roster not found in the state.");
        }
        RosterValidator.validate(candidateRoster);

        setActiveRoster(candidateRoster, roundNumber);
        removeCandidateRoster();
    }

    /**
     * remove the candidate roster from the state.
     */
    private void removeCandidateRoster() {
        final RosterState previousRosterState = rosterStateOrThrow();
        final Bytes candidateRosterHash = previousRosterState.candidateRosterHash();

        final Builder rosterStateBuilder = RosterState.newBuilder()
                .candidateRosterHash(Bytes.EMPTY)
                .roundRosterPairs(previousRosterState.roundRosterPairs());
        update(rosterStateBuilder);

        rosterMap.remove(ProtoBytes.newBuilder().value(candidateRosterHash).build());
    }

    /**
     * returns the roster state or throws an exception if the state is null.
     * @return the roster state
     * @throws NullPointerException if the roster state is null
     * @implNote this method is package-private for testing purposes
     */
    @NonNull
    RosterState rosterStateOrThrow() {
        return requireNonNull(rosterState.get());
    }

    private void update(@NonNull final RosterState.Builder rosterStateBuilder) {
        this.rosterState.put(rosterStateBuilder.build());
        commit();
    }

    private void update(@NonNull final ProtoBytes key, @NonNull final Roster value) {
        this.rosterMap.put(key, value);
        commit();
    }

    private void commit() {
        if (writableStates instanceof final CommittableWritableStates committableWritableStates) {
            committableWritableStates.commit();
        }
    }
}
