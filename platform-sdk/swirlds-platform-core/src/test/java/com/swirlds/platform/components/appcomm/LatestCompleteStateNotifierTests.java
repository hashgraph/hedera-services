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

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.state.notifications.NewSignedStateListener;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Basic sanity check tests for the {@link DefaultLatestCompleteStateNotifier} class
 */
public class LatestCompleteStateNotifierTests {

    @Test
    @DisplayName("NewLatestCompleteStateEventNotification")
    void testNewLatestCompleteStateEventNotification() throws InterruptedException {
        final NotificationEngine notificationEngine = NotificationEngine.buildEngine(getStaticThreadManager());
        final SignedState signedState = new RandomSignedStateGenerator().build();

        final CountDownLatch senderLatch = new CountDownLatch(1);
        final CountDownLatch listenerLatch = new CountDownLatch(1);
        final AtomicInteger numInvocations = new AtomicInteger();

        notificationEngine.register(NewSignedStateListener.class, n -> {
            numInvocations.incrementAndGet();
            try {
                assertThat(senderLatch.await(1, TimeUnit.SECONDS)).isTrue();
                assertFalse(
                        n.getSwirldState().isDestroyed(),
                        "SwirldState should not be destroyed until the callback has completed");
                assertEquals(signedState.getSwirldState(), n.getSwirldState(), "Unexpected SwirldState");
                listenerLatch.countDown();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        final LatestCompleteStateNotifier component = new DefaultLatestCompleteStateNotifier();
        final CompleteStateNotificationWithCleanup notificationWithCleanup = component.latestCompleteStateHandler(
                signedState.reserve("testNewLatestCompleteStateEventNotification"));

        assertNotEquals(null, notificationWithCleanup, "component output should not be null");

        notificationEngine.dispatch(
                NewSignedStateListener.class,
                notificationWithCleanup.notification(),
                notificationWithCleanup.cleanup());

        // Allow the notification callback to execute
        senderLatch.countDown();

        // Wait for the notification callback to complete
        assertThat(listenerLatch.await(1, TimeUnit.SECONDS)).isTrue();

        // The notification listener has completed, but the post-listener callback may not have yet
        assertEventuallyEquals(
                -1, signedState::getReservationCount, Duration.ofSeconds(1), "Signed state should be fully released");
        assertEquals(1, numInvocations.get(), "Unexpected number of notification callbacks");
    }
}
