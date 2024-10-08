/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.node.app.roster.schemas.V0540RosterSchema;
import com.hedera.node.app.service.token.ReadableRosterStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;

import java.util.Objects;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Default implementation of {@link ReadableRosterStore}.
 */
public class ReadableRosterStoreImpl implements ReadableRosterStore {
    /** The underlying data storage class that holds the account data. */
   private final ReadableKVState<ProtoBytes, Roster> rosters;
   /** todo*/
   private final ReadableSingletonState<RosterState> currentRosters;

    /**
     * Create a new {@link ReadableRosterStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableRosterStoreImpl(@NonNull final ReadableStates states) {
        this.rosters = states.get(V0540RosterSchema.ROSTER_KEY);
        this.currentRosters = states.getSingleton(V0540RosterSchema.ROSTER_STATES_KEY);
    }

    @Override
    public Roster getRoster(@NonNull final byte[] rosterHash) {
        requireNonNull(rosterHash);
        return rosters().get(ProtoBytes.newBuilder().value(Bytes.wrap(rosterHash)).build());

    }

    @NonNull
    @Override
    public Roster getCandidateRoster() {
        final var candidateRosterHash = currentRostersState().get().candidateRosterHash();
        final var candidateRoster = rosters().get(ProtoBytes.newBuilder().value(candidateRosterHash).build());
        if (candidateRoster == null) {
            throw new IllegalStateException("No candidate roster found");
        }

        return candidateRoster;
    }

    @NonNull
    @Override
    public Roster getActiveRoster(final long roundNum) {
        return currentRostersState().get().roundRosterPairs().stream()
                .filter(pair -> pair.roundNumber() == roundNum)
                .map(pair -> rosters().get(ProtoBytes.newBuilder().value(pair.activeRosterHash()).build()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active roster found for round " + roundNum));
    }

    /** Get the current roster state. Convenience method for auto-casting to the right kind of state (readable vs. writable) */
    @NonNull
    protected <T extends ReadableSingletonState<RosterState>> T currentRostersState() {
        return (T) currentRosters;
    }

    /** Get all rosters in state. Convenience method for auto-casting to the right kind of state (readable vs. writable) */
    protected <T extends ReadableKVState<ProtoBytes, Roster>> T rosters() {
        return (T) rosters;
    }
}
