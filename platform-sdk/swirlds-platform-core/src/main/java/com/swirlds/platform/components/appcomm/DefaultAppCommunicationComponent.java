/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteNotification;
import com.swirlds.common.system.state.notifications.IssListener;
import com.swirlds.common.system.state.notifications.IssNotification;
import com.swirlds.common.system.state.notifications.NewSignedStateListener;
import com.swirlds.common.system.state.notifications.NewSignedStateNotification;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import java.nio.file.Path;

/**
 * Default implementation of the {@link AppCommunicationComponent}
 */
public class DefaultAppCommunicationComponent implements AppCommunicationComponent {

    private final NotificationEngine notificationEngine;

    public DefaultAppCommunicationComponent(final NotificationEngine notificationEngine) {
        this.notificationEngine = notificationEngine;
    }

    @Override
    public void stateToDiskAttempt(
            final ReservedSignedState signedStateWrapper, final Path directory, final boolean success) {
        if (success) {
            final SignedState state = signedStateWrapper.get();
            // Synchronous notification, no need to take an extra reservation
            notificationEngine.dispatch(
                    StateWriteToDiskCompleteListener.class,
                    new StateWriteToDiskCompleteNotification(
                            state.getRound(),
                            state.getConsensusTimestamp(),
                            state.getSwirldState(),
                            directory,
                            state.isFreezeState()));
        }
    }

    @Override
    public void newLatestCompleteStateEvent(final ReservedSignedState signedStateWrapper) {
        final SignedState signedState = signedStateWrapper.get();
        final NewSignedStateNotification notification = new NewSignedStateNotification(
                signedState.getSwirldState(),
                signedState.getState().getSwirldDualState(),
                signedState.getRound(),
                signedState.getConsensusTimestamp());
        notificationEngine.dispatch(NewSignedStateListener.class, notification, r -> signedStateWrapper.close());
    }

    @Override
    public void iss(final long round, final IssNotification.IssType issType, final Long otherNodeId) {
        final IssNotification notification = new IssNotification(round, issType, otherNodeId);
        notificationEngine.dispatch(IssListener.class, notification);
    }
}
