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

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.platform.components.PlatformComponent;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteNotification;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.StateSavingResult;
import com.swirlds.platform.system.state.notifications.NewSignedStateListener;
import com.swirlds.platform.system.state.notifications.NewSignedStateNotification;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This component responsible for notifying the application of various platform events
 */
public class AppCommunicationComponent implements PlatformComponent {
    private static final Logger logger = LogManager.getLogger(AppCommunicationComponent.class);

    private final NotificationEngine notificationEngine;
    private final Predicate<ReservedSignedState> offerPredicate;

    /** The round of the latest state provided to the application */
    private long latestStateProvidedRound = ConsensusConstants.ROUND_UNDEFINED;

    /**
     * Create a new instance
     *
     * @param notificationEngine    the notification engine
     * @param offerPredicate        the offer to app communicator predicate
     */
    public AppCommunicationComponent(
            @NonNull final NotificationEngine notificationEngine,
            @NonNull final Predicate<ReservedSignedState> offerPredicate) {
        this.notificationEngine = notificationEngine;
        this.offerPredicate = offerPredicate;
    }

    /**
     * Notify the application that a state has been saved to disk successfully
     *
     * @param stateSavingResult the result of the state saving operation
     */
    public void stateSavedToDisk(@NonNull final StateSavingResult stateSavingResult) {
        notificationEngine.dispatch(
                StateWriteToDiskCompleteListener.class,
                new StateWriteToDiskCompleteNotification(
                        stateSavingResult.round(),
                        stateSavingResult.consensusTimestamp(),
                        stateSavingResult.freezeState()));
    }

    public void newLatestCompleteStateEvent(@NonNull final ReservedSignedState reservedSignedState) {
        // the state is reserved by the caller
        // it will be released by the notification engine after the app communicator consumes it
        // if the state does not make into the task scheduler, it will be released below
        final boolean reservedSignedStateOfferAccepted = offerPredicate.test(reservedSignedState);
        if (!reservedSignedStateOfferAccepted) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Unable to add new latest complete state task (state round = {}) to {}",
                    reservedSignedState.get().getRound(),
                    "AppCommunicationComponentTaskScheduler");
            reservedSignedState.close();
        }
    }

    /**
     * Handler for application communication task
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
