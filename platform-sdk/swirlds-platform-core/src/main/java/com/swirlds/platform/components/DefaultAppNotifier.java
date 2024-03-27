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

package com.swirlds.platform.components;

import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.platform.components.appcomm.CompleteStateNotificationWithCleanup;
import com.swirlds.platform.listeners.PlatformStatusChangeListener;
import com.swirlds.platform.listeners.PlatformStatusChangeNotification;
import com.swirlds.platform.listeners.ReconnectCompleteListener;
import com.swirlds.platform.listeners.ReconnectCompleteNotification;
import com.swirlds.platform.listeners.StateLoadedFromDiskCompleteListener;
import com.swirlds.platform.listeners.StateLoadedFromDiskNotification;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteNotification;
import com.swirlds.platform.system.state.notifications.IssListener;
import com.swirlds.platform.system.state.notifications.IssNotification;
import com.swirlds.platform.system.state.notifications.NewSignedStateListener;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The default implementation of the AppNotifier interface.
 */
public record DefaultAppNotifier(@NonNull NotificationEngine notificationEngine) implements AppNotifier {
    /**
     * {@inheritDoc}
     */
    @Override
    public void sendStateWrittenToDiskNotification(@NonNull final StateWriteToDiskCompleteNotification notification) {
        notificationEngine.dispatch(StateWriteToDiskCompleteListener.class, notification);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendStateLoadedFromDiskNotification(@NonNull final StateLoadedFromDiskNotification notification) {
        notificationEngine.dispatch(StateLoadedFromDiskCompleteListener.class, notification);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendReconnectCompleteNotification(@NonNull final ReconnectCompleteNotification notification) {
        notificationEngine.dispatch(ReconnectCompleteListener.class, notification);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendPlatformStatusChangeNotification(@NonNull final PlatformStatusChangeNotification notification) {
        notificationEngine.dispatch(PlatformStatusChangeListener.class, notification);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendLatestCompleteStateNotification(
            @NonNull final CompleteStateNotificationWithCleanup notificationWithCleanup) {
        notificationEngine.dispatch(
                NewSignedStateListener.class,
                notificationWithCleanup.notification(),
                notificationWithCleanup.cleanup());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendIssNotification(@NonNull final IssNotification notification) {
        notificationEngine.dispatch(IssListener.class, notification);
    }
}
