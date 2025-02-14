/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.NotificationResult;
import com.swirlds.common.threading.futures.StandardFuture.CompletionCallback;
import com.swirlds.platform.components.appcomm.CompleteStateNotificationWithCleanup;
import com.swirlds.platform.listeners.PlatformStatusChangeListener;
import com.swirlds.platform.listeners.PlatformStatusChangeNotification;
import com.swirlds.platform.listeners.ReconnectCompleteListener;
import com.swirlds.platform.listeners.ReconnectCompleteNotification;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteNotification;
import com.swirlds.platform.state.MerkeNodeState;
import com.swirlds.platform.system.state.notifications.AsyncFatalIssListener;
import com.swirlds.platform.system.state.notifications.IssListener;
import com.swirlds.platform.system.state.notifications.IssNotification;
import com.swirlds.platform.system.state.notifications.IssNotification.IssType;
import com.swirlds.platform.system.state.notifications.NewSignedStateListener;
import com.swirlds.platform.system.state.notifications.NewSignedStateNotification;
import com.swirlds.platform.system.state.notifications.StateHashedListener;
import com.swirlds.platform.system.state.notifications.StateHashedNotification;
import com.swirlds.platform.system.status.PlatformStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

public class DefaultAppNotifierTest {

    NotificationEngine notificationEngine;
    AppNotifier notifier;

    @BeforeEach
    void beforeEach() {
        notificationEngine = mock(NotificationEngine.class);
        notifier = new DefaultAppNotifier(notificationEngine);
    }

    @Test
    void testStateWrittenToDiskNotificationSent() {
        final StateWriteToDiskCompleteNotification notification =
                new StateWriteToDiskCompleteNotification(100, Instant.now(), false);

        assertDoesNotThrow(() -> notifier.sendStateWrittenToDiskNotification(notification));
        verify(notificationEngine, times(1)).dispatch(StateWriteToDiskCompleteListener.class, notification);
        verifyNoMoreInteractions(notificationEngine);
    }

    @Test
    void testStateHashNotificationSent() {
        final StateHashedNotification notification = new StateHashedNotification(100L, new Hash(DigestType.SHA_384));

        assertDoesNotThrow(() -> notifier.sendStateHashedNotification(notification));
        verify(notificationEngine, times(1)).dispatch(StateHashedListener.class, notification);
        verifyNoMoreInteractions(notificationEngine);
    }

    @Test
    void testReconnectCompleteNotificationSent() {
        final MerkeNodeState state = mock(MerkeNodeState.class);
        final ReconnectCompleteNotification notification =
                new ReconnectCompleteNotification(100L, Instant.now(), state);

        assertDoesNotThrow(() -> notifier.sendReconnectCompleteNotification(notification));
        verify(notificationEngine, times(1)).dispatch(ReconnectCompleteListener.class, notification);
        verifyNoMoreInteractions(notificationEngine);
    }

    @Test
    void testPlatformStatusChangeNotificationSent() {
        final PlatformStatus status = PlatformStatus.ACTIVE;
        final ArgumentCaptor<PlatformStatusChangeNotification> captor =
                ArgumentCaptor.forClass(PlatformStatusChangeNotification.class);

        assertDoesNotThrow(() -> notifier.sendPlatformStatusChangeNotification(status));
        verify(notificationEngine, times(1)).dispatch(eq(PlatformStatusChangeListener.class), captor.capture());
        verifyNoMoreInteractions(notificationEngine);

        final PlatformStatusChangeNotification notification = captor.getValue();
        assertNotNull(notification);
        assertEquals(status, notification.getNewStatus());
    }

    @Test
    void testLatestCompleteStateNotificationSent() {
        final MerkeNodeState state = mock(MerkeNodeState.class);
        final CompletionCallback<NotificationResult<NewSignedStateNotification>> cleanup =
                mock(CompletionCallback.class);
        final NewSignedStateNotification signedStateNotification =
                new NewSignedStateNotification(state, 100L, Instant.now());
        final CompleteStateNotificationWithCleanup notificationWithCleanup =
                new CompleteStateNotificationWithCleanup(signedStateNotification, cleanup);

        assertDoesNotThrow(() -> notifier.sendLatestCompleteStateNotification(notificationWithCleanup));
        verify(notificationEngine, times(1)).dispatch(NewSignedStateListener.class, signedStateNotification, cleanup);
        verifyNoMoreInteractions(notificationEngine);
    }

    public static List<Arguments> issTypes() {
        return List.of(
                Arguments.of(IssType.CATASTROPHIC_ISS, true),
                Arguments.of(IssType.SELF_ISS, true),
                Arguments.of(IssType.OTHER_ISS, false));
    }

    @ParameterizedTest
    @MethodSource("issTypes")
    void testIssNotificationSent(final IssType type, final boolean isFatal) {
        final IssNotification notification = new IssNotification(100L, type);

        assertDoesNotThrow(() -> notifier.sendIssNotification(notification));

        // verify the ISS notification is always sent to the IssListener
        verify(notificationEngine, times(1)).dispatch(IssListener.class, notification);

        if (isFatal) {
            // if the ISS event is considered fatal to the local node, verify the event is also sent to the
            // FatalIssListener
            verify(notificationEngine, times(1)).dispatch(AsyncFatalIssListener.class, notification);
        }

        verifyNoMoreInteractions(notificationEngine);
    }
}
