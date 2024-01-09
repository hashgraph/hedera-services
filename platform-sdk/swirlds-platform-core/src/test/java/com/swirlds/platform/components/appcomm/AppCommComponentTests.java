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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.ResettableRandom;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.StateSavingResult;
import com.swirlds.platform.system.state.notifications.IssListener;
import com.swirlds.platform.system.state.notifications.IssNotification;
import com.swirlds.platform.system.state.notifications.NewSignedStateListener;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Basic sanity check tests for the {@link AppCommunicationComponent} class
 */
public class AppCommComponentTests {
    private final PlatformContext context;

    public AppCommComponentTests() {
        context = TestPlatformContextBuilder.create().build();
    }

    @Test
    @DisplayName("StateWriteToDiskCompleteNotification")
    void testStateWriteToDiskCompleteNotification() {
        final NotificationEngine notificationEngine = NotificationEngine.buildEngine(getStaticThreadManager());
        final ResettableRandom random = RandomUtils.getRandomPrintSeed();
        final StateSavingResult result = new StateSavingResult(
                random.nextLong(1, Long.MAX_VALUE),
                random.nextBoolean(),
                RandomUtils.randomInstant(random),
                random.nextLong(1, Long.MAX_VALUE));

        final AtomicInteger numInvocations = new AtomicInteger();
        notificationEngine.register(StateWriteToDiskCompleteListener.class, n -> {
            numInvocations.getAndIncrement();
            assertEquals(result.consensusTimestamp(), n.getConsensusTimestamp(), "Unexpected consensus timestamp");
            assertEquals(result.round(), n.getRoundNumber(), "Unexpected notification round number");
            assertEquals(result.freezeState(), n.isFreezeState(), "Unexpected notification freeze state");
            assertNull(n.getState(), "Deprecated field should be null");
            assertNull(n.getFolder(), "Deprecated field should be null");
        });

        final AppCommunicationComponent component = new AppCommunicationComponent(notificationEngine, context);
        component.stateSavedToDisk(result);

        assertEquals(1, numInvocations.get(), "Unexpected number of notifications");
    }

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
                senderLatch.await();
                assertFalse(
                        n.getSwirldState().isDestroyed(),
                        "SwirldState should not be destroyed until the callback has completed");
                assertEquals(signedState.getSwirldState(), n.getSwirldState(), "Unexpected SwirldState");
                listenerLatch.countDown();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        final AppCommunicationComponent component = new AppCommunicationComponent(notificationEngine, context);
        component.start();
        component.newLatestCompleteStateEvent(signedState);

        // Allow the notification callback to execute
        senderLatch.countDown();

        // Wait for the notification callback to complete
        listenerLatch.await();

        // The notification listener has completed, but the post-listener callback may not have yet
        assertEventuallyEquals(
                -1, signedState::getReservationCount, Duration.ofSeconds(1), "Signed state should be fully released");
        assertEquals(1, numInvocations.get(), "Unexpected number of notification callbacks");
    }

    @RepeatedTest(100)
    @DisplayName("IssNotification")
    void testIssNotification() {
        final Random random = RandomUtils.getRandomPrintSeed();
        final long round = random.nextLong();
        final int numTypes = IssNotification.IssType.values().length;
        final IssNotification.IssType issType = IssNotification.IssType.values()[random.nextInt(numTypes)];
        final NodeId otherNodeId = random.nextDouble() > 0.8 ? null : new NodeId(Math.abs(random.nextLong()));

        final AtomicInteger numInvocations = new AtomicInteger();
        final NotificationEngine notificationEngine = NotificationEngine.buildEngine(getStaticThreadManager());
        notificationEngine.register(IssListener.class, n -> {
            numInvocations.getAndIncrement();
            assertEquals(round, n.getRound(), "Unexpected ISS round");
            assertEquals(issType, n.getIssType(), "Unexpected ISS Type");
            assertEquals(otherNodeId, n.getOtherNodeId(), "Unexpected other node id");
        });

        final AppCommunicationComponent component = new AppCommunicationComponent(notificationEngine, context);
        component.iss(round, issType, otherNodeId);

        assertEquals(1, numInvocations.get(), "Unexpected number of notification callbacks");
    }
}
