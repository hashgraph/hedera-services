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

import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.components.appcomm.CompleteStateNotificationWithCleanup;
import com.swirlds.platform.listeners.PlatformStatusChangeNotification;
import com.swirlds.platform.listeners.ReconnectCompleteNotification;
import com.swirlds.platform.listeners.StateLoadedFromDiskNotification;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteNotification;
import com.swirlds.platform.system.state.notifications.IssNotification;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A component that wraps around a notification engine, for sending notifications to the app.
 */
public interface AppNotifier {
    /**
     * Send a notification to the app that a state has been written to disk.
     *
     * @param notification the notification
     */
    @InputWireLabel("StateWriteToDiskCompleteNotification")
    void sendStateWrittenToDiskNotification(@NonNull final StateWriteToDiskCompleteNotification notification);

    /**
     * Send a notification to the app that the state has been loaded from disk.
     *
     * @param notification the notification
     */
    @InputWireLabel("StateLoadedFromDiskNotification")
    void sendStateLoadedFromDiskNotification(@NonNull final StateLoadedFromDiskNotification notification);

    /**
     * Send a notification to the app that a reconnect has completed.
     *
     * @param notification the notification
     */
    @InputWireLabel("ReconnectCompleteNotification")
    void sendReconnectCompleteNotification(@NonNull final ReconnectCompleteNotification notification);

    /**
     * Send a notification to the app that the platform status has changed.
     *
     * @param notification the notification
     */
    @InputWireLabel("PlatformStatusChangeNotification")
    void sendPlatformStatusChangeNotification(@NonNull final PlatformStatusChangeNotification notification);

    /**
     * Send a notification to the app with the latest complete state.
     *
     * @param notificationWithCleanup the notification, with required cleanup
     */
    @InputWireLabel("CompleteStateNotificationWithCleanup")
    void sendLatestCompleteStateNotification(
            @NonNull final CompleteStateNotificationWithCleanup notificationWithCleanup);

    /**
     * Notify the app of an ISS
     *
     * @param notification the notification
     */
    @InputWireLabel("IssNotification")
    void sendIssNotification(@NonNull final IssNotification notification);
}
