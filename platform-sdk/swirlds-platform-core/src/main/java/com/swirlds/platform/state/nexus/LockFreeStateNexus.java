/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.nexus;

import com.swirlds.common.consensus.ConsensusConstants;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A lock-free implementation of {@link SignedStateNexus}. This implementation is the most efficient, but it makes it
 * more complicated to add additional functionality to the nexus.
 */
public class LockFreeStateNexus implements SignedStateNexus {
    private final AtomicReference<ReservedSignedState> currentState = new AtomicReference<>();
    private final AtomicLong currentStateRound = new AtomicLong(ConsensusConstants.ROUND_UNDEFINED);

    @Override
    public @Nullable ReservedSignedState getState(@NonNull final String reason) {
        ReservedSignedState state;
        do {
            state = currentState.get();
            if (state == null) {
                return null;
            }

            // between the get method on the atomic reference and the tryGetAndReserve method on the state, the state
            // could have been closed. If this happens, tryGetAndReserve will return null
            final ReservedSignedState newReservation = state.tryGetAndReserve(reason);
            if (newReservation != null) {
                return newReservation;
            }
            // if tryGetAndReserve returned null, then we should check if set() was called in the meantime
            // if yes, then we should try again and reserve the new state
        } while (state != currentState.get());
        // this means we cannot reserve the state we are holding, this is probably an error, since we should hold a
        // reservation on it
        return null;
    }

    @Override
    public void setState(@Nullable final ReservedSignedState reservedSignedState) {
        final ReservedSignedState oldState = currentState.getAndSet(reservedSignedState);
        currentStateRound.set(
                reservedSignedState == null
                        ? ConsensusConstants.ROUND_UNDEFINED
                        : reservedSignedState.get().getRound());
        if (oldState != null) {
            oldState.close();
        }
    }

    @Override
    public long getRound() {
        return currentStateRound.get();
    }
}
