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

package com.swirlds.platform.components.appcomm;

import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.state.notifications.NewSignedStateListener;
import com.swirlds.platform.system.state.notifications.NewSignedStateNotification;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * This component responsible for notifying the application of latest complete state
 */
public class LatestCompleteStateNotifier {
    private final NotificationEngine notificationEngine;

    /** The round of the latest state provided to the application */
    private long latestStateProvidedRound = ConsensusConstants.ROUND_UNDEFINED;

    /**
     * Create a new instance
     *
     * @param notificationEngine the notification engine
     */
    public LatestCompleteStateNotifier(@NonNull final NotificationEngine notificationEngine) {
        this.notificationEngine = Objects.requireNonNull(notificationEngine);
    }

    /**
     * Handler for latest completion notification task
     *
     * @param reservedSignedState the reserved signed state to retrieve hash information from and log.
     */
    public void latestCompleteStateHandler(@NonNull final ReservedSignedState reservedSignedState) {
        if (reservedSignedState.get().getRound() <= latestStateProvidedRound) {
            // this state is older than the latest state provided to the application, no need to notify
            reservedSignedState.close();
            return;
        }
        latestStateProvidedRound = reservedSignedState.get().getRound();
        final NewSignedStateNotification notification = new NewSignedStateNotification(
                reservedSignedState.get().getSwirldState(),
                reservedSignedState.get().getState().getPlatformState(),
                reservedSignedState.get().getRound(),
                reservedSignedState.get().getConsensusTimestamp());

        notificationEngine.dispatch(NewSignedStateListener.class, notification, r -> reservedSignedState.close());
    }
}
