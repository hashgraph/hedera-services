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

import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableSingletonState;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * todo
 */
public class WritableRosterStoreImpl extends ReadableRosterStoreImpl {

    /**
     * Create a new {@link ReadableRosterStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public WritableRosterStoreImpl(@NonNull final ReadableStates states) {
        super(states);
    }

    /**
     * todo
     *
     */
    public void putRoster(@NonNull Roster roster, final boolean designateCandidate) {
        final var hashedRosterBytes = sha384Of(roster);
        rosters().put(ProtoBytes.newBuilder().value(Bytes.wrap(hashedRosterBytes)).build(), roster);

        if (designateCandidate) {
            final var newRosterState = currentRostersState().get().copyBuilder().candidateRosterHash(Bytes.wrap(sha384Of(roster))).build();
            currentRostersState().put(newRosterState);
        }
    }

    @Override
    protected WritableKVState<ProtoBytes, Roster> rosters() {
        return super.rosters();
    }

    @Override
    protected WritableSingletonState<RosterState> currentRostersState() {
        return super.currentRostersState();
    }

    private static byte[] sha384Of(@NonNull final Roster roster) {
        try {
            return MessageDigest.getInstance("SHA-384").digest(Roster.PROTOBUF.toBytes(roster).toByteArray());
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-384 not available", e);
        }
    }
}
