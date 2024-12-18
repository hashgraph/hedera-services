// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.wiring.components;

import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Contains a reserved signed state, and the consensus round which caused the state to be created
 *
 * @param reservedSignedState the state
 * @param round               the round that caused the state to be created
 */
public record StateAndRound(@NonNull ReservedSignedState reservedSignedState, @NonNull ConsensusRound round) {
    /**
     * Make an additional reservation on the reserved signed state
     *
     * @param reservationReason the reason for the reservation
     * @return a copy of this object, which has its own new reservation on the state
     */
    @NonNull
    public StateAndRound makeAdditionalReservation(@NonNull final String reservationReason) {
        return new StateAndRound(reservedSignedState.getAndReserve(reservationReason), round);
    }
}
