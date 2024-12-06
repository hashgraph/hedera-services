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

package com.swirlds.platform.system.state.notifications;

import static java.util.Objects.requireNonNull;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.notification.AbstractNotification;
import com.swirlds.common.notification.Notification;
import com.swirlds.platform.wiring.components.StateAndRound;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@link Notification} that a state hash has been computed.
 */
public class StateHashedNotification extends AbstractNotification {
    private final long roundNumber;
    private final Hash hash;

    /**
     * Create a notification for a newly hashed state.
     * @param stateAndRound the state and round that is now hashed
     * @return a new notification
     */
    public static StateHashedNotification from(@NonNull final StateAndRound stateAndRound) {
        try (final var state = stateAndRound.reservedSignedState()) {
            return new StateHashedNotification(
                    stateAndRound.round().getRoundNum(),
                    requireNonNull(state.get().getState().getHash()));
        }
    }

    public StateHashedNotification(final long roundNumber, @NonNull final Hash hash) {
        this.roundNumber = roundNumber;
        this.hash = requireNonNull(hash);
    }

    public long round() {
        return roundNumber;
    }

    public @NonNull Hash hash() {
        return hash;
    }
}
