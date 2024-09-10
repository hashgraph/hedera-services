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

import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.node.state.roster.RosterState.Builder;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableSingletonState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Provides write methods for interacting with Rosters.
 */
public class WritableRosterStore extends ReadableRosterStoreImpl {

    /**
     * Constructs a new {@link WritableRosterStore} instance.
     *
     * @param states the readable states
     */
    public WritableRosterStore(@NonNull final ReadableStates states) {
        super(states);
    }

    @Override
    protected WritableKVState<ProtoBytes, Roster> rosters() {
        return super.rosters();
    }

    @Override
    protected WritableSingletonState<RosterState> rosterState() {
        return super.rosterState();
    }

    /**
     * Sets the active roster.
     *
     * @param roster the roster to set
     *  @param round the round in which this roster became active
     */
    public void setActiveRoster(@NonNull final Roster roster, final long round) {
        Objects.requireNonNull(roster);
        final RosterState currentRosterState = rosterState().get();
        Objects.requireNonNull(currentRosterState);

        // update the roster state
        final List<RoundRosterPair> roundRosterPairs = new LinkedList<>(currentRosterState.roundRosterPairs());
        final Bytes activeRosterHash = Bytes.wrap(RosterUtils.hashOf(roster));
        roundRosterPairs.addFirst(new RoundRosterPair(round, activeRosterHash));
        final Builder rosterStateBuilder = RosterState.newBuilder()
                .candidateRosterHash(currentRosterState.candidateRosterHash())
                .roundRosterPairs(roundRosterPairs);
        rosterState().put(rosterStateBuilder.build());

        // update the roster map
        rosters().put(ProtoBytes.newBuilder().value(activeRosterHash).build(), roster);
    }

    /**
     * Sets the candidate roster.
     *
     * @param roster the candidate roster
     */
    public void setCandidateRoster(@NonNull final Roster roster) {
        Objects.requireNonNull(roster);

        // update the roster state
        final Bytes candidateRosterHash = Bytes.wrap(RosterUtils.hashOf(roster));
        final Builder rosterStateBuilder = RosterState.newBuilder()
                .candidateRosterHash(candidateRosterHash)
                .roundRosterPairs(rosterState().get().roundRosterPairs());
        rosterState().put(rosterStateBuilder.build());

        // update the roster map
        rosters().put(ProtoBytes.newBuilder().value(candidateRosterHash).build(), roster);
    }
}
