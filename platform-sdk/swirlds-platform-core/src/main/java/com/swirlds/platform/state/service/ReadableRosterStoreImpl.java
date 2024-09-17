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
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.state.MutabilityException;
import com.swirlds.common.RosterStateId;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * ReadableRosterStore implementation.
 */
public class ReadableRosterStoreImpl implements ReadableRosterStore {

    /**
     * The roster state singleton. This is the state that holds the candidate roster hash and the list of pairs of round
     * and active roster hashes.
     */
    private final ReadableSingletonState<RosterState> rosterState;

    /**
     * The key-value map of roster hashes and rosters.
     */
    private final ReadableKVState<ProtoBytes, Roster> rosterMap;

    public static final String READ_ONLY = "Roster state is read-only";
    /**
     * Create a new {@link ReadableRosterStoreImpl} instance.
     *
     * @param readableStates The state to use.
     */
    public ReadableRosterStoreImpl(@NonNull final ReadableStates readableStates) {
        Objects.requireNonNull(readableStates);
        this.rosterState = requireNonNull(readableStates).getSingleton(RosterStateId.ROSTER_STATES_KEY);
        this.rosterMap = requireNonNull(readableStates).get(RosterStateId.ROSTER_KEY);
    }

    @Override
    public void setCandidateRoster(@NonNull final Roster candidateRoster) {
        throw new MutabilityException(READ_ONLY);
    }

    @Nullable
    @Override
    public Roster getCandidateRoster() {
        final RosterState rosterStateSingleton = rosterState.get();
        if (rosterStateSingleton == null) {
            return null;
        }
        final Bytes candidateRosterHash = rosterStateSingleton.candidateRosterHash();
        return rosterMap.get(ProtoBytes.newBuilder().value(candidateRosterHash).build());
    }

    @Override
    public void setActiveRoster(@NonNull final Roster activeRoster, final long round) {
        throw new MutabilityException(READ_ONLY);
    }

    /**
     * Gets the active roster.
     * Returns the active roster iff:
     *      the roster state singleton is not null
     *      the list of round roster pairs is not empty
     *      the first round roster pair is not null
     *      the active roster hash is present in the roster map
     * otherwise returns null.
     * @return the active roster
     */
    @Nullable
    @Override
    public Roster getActiveRoster() {
        final RosterState rosterStateSingleton = rosterState.get();
        if (rosterStateSingleton == null) {
            return null;
        }
        final List<RoundRosterPair> rostersAndRounds = rosterStateSingleton.roundRosterPairs();
        if (rostersAndRounds.isEmpty()) {
            return null;
        }
        // by design, the first round roster pair is the active roster
        // this may need to be revisited when we reach DAB
        final RoundRosterPair latestRoundRosterPair = rostersAndRounds.getFirst();
        final Bytes activeRosterHash = latestRoundRosterPair.activeRosterHash();
        return rosterMap.get(ProtoBytes.newBuilder().value(activeRosterHash).build());
    }

    @Override
    public void adoptCandidateRoster(final long roundNumber) {
        throw new MutabilityException(READ_ONLY);
    }

    @Override
    public long getActiveRosterRoundNumber() {
        return requireNonNull(rosterState.get()).roundRosterPairs().getFirst().roundNumber();
    }
}
