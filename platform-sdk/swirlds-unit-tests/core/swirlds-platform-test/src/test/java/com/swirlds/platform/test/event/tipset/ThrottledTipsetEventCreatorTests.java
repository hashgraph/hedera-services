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

package com.swirlds.platform.test.event.tipset;

import static com.swirlds.common.system.EventCreationRuleResponse.PASS;
import static com.swirlds.common.system.status.PlatformStatus.ACTIVE;
import static com.swirlds.common.system.status.PlatformStatus.CHECKING;
import static com.swirlds.common.system.status.PlatformStatus.FREEZING;
import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.status.PlatformStatus;
import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.StartUpEventFrozenManager;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.tipset.ThrottledTipsetEventCreator;
import com.swirlds.platform.event.tipset.TipsetEventCreator;
import com.swirlds.platform.eventhandling.EventTransactionPool;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ThrottledTipsetEventCreator Tests")
class ThrottledTipsetEventCreatorTests {

    @Test
    @DisplayName("PassThrough Test")
    void passThroughTest() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final EventTransactionPool transactionPool = mock(EventTransactionPool.class);
        final QueueThread<EventIntakeTask> eventIntakeQueue = mock(QueueThread.class);
        final Supplier<PlatformStatus> platformStatusSupplier = () -> ACTIVE;
        final StartUpEventFrozenManager startUpEventFrozenManager = mock(StartUpEventFrozenManager.class);
        when(startUpEventFrozenManager.shouldCreateEvent()).thenAnswer(invocation -> PASS);

        final TipsetEventCreator baseEventCreator = mock(TipsetEventCreator.class);

        final AtomicLong minimumGenerationNonAncient = new AtomicLong(0);
        doAnswer(invocation -> {
                    minimumGenerationNonAncient.set(invocation.getArgument(0));
                    return null;
                })
                .when(baseEventCreator)
                .setMinimumGenerationNonAncient(anyLong());

        final AtomicReference<EventImpl> latestEvent = new AtomicReference<>(null);
        doAnswer(invocation -> {
                    latestEvent.set(invocation.getArgument(0));
                    return null;
                })
                .when(baseEventCreator)
                .registerEvent(any());

        final ThrottledTipsetEventCreator eventCreator = new ThrottledTipsetEventCreator(
                platformContext,
                Time.getCurrent(),
                transactionPool,
                eventIntakeQueue,
                platformStatusSupplier,
                startUpEventFrozenManager,
                baseEventCreator);

        for (int i = 0; i < 10; i++) {
            eventCreator.setMinimumGenerationNonAncient(i);
            assertEquals(i, minimumGenerationNonAncient.get());
        }

        for (int i = 0; i < 10; i++) {
            final EventImpl gossipEvent = mock(EventImpl.class);
            eventCreator.registerEvent(gossipEvent);
            assertSame(gossipEvent, latestEvent.get());
        }
    }

    @Test
    @DisplayName("Blocked by StartUpFrozenManager Test")
    void blockedByStartUpFrozenManagerTest() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final EventTransactionPool transactionPool = mock(EventTransactionPool.class);
        final QueueThread<EventIntakeTask> eventIntakeQueue = mock(QueueThread.class);
        final Supplier<PlatformStatus> platformStatusSupplier = () -> ACTIVE;

        final AtomicReference<EventCreationRuleResponse> shouldCreateEvent =
                new AtomicReference<>(EventCreationRuleResponse.DONT_CREATE);
        final StartUpEventFrozenManager startUpEventFrozenManager = mock(StartUpEventFrozenManager.class);
        when(startUpEventFrozenManager.shouldCreateEvent()).thenAnswer(invocation -> shouldCreateEvent.get());

        final AtomicInteger eventCreationCount = new AtomicInteger(0);
        final TipsetEventCreator baseEventCreator = mock(TipsetEventCreator.class);
        when(baseEventCreator.maybeCreateEvent()).thenAnswer(invocation -> {
            eventCreationCount.incrementAndGet();
            return null;
        });

        final ThrottledTipsetEventCreator eventCreator = new ThrottledTipsetEventCreator(
                platformContext,
                Time.getCurrent(),
                transactionPool,
                eventIntakeQueue,
                platformStatusSupplier,
                startUpEventFrozenManager,
                baseEventCreator);

        assertFalse(eventCreator.isEventCreationPermitted());
        eventCreator.maybeCreateEvent();
        assertEquals(0, eventCreationCount.get());

        shouldCreateEvent.set(PASS);

        assertTrue(eventCreator.isEventCreationPermitted());
        eventCreator.maybeCreateEvent();
        assertEquals(1, eventCreationCount.get());
    }

    @Test
    @DisplayName("Blocked by Freeze Test")
    void blockedByFreeze() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final QueueThread<EventIntakeTask> eventIntakeQueue = mock(QueueThread.class);
        final Supplier<PlatformStatus> platformStatusSupplier = () -> FREEZING;
        final StartUpEventFrozenManager startUpEventFrozenManager = mock(StartUpEventFrozenManager.class);
        when(startUpEventFrozenManager.shouldCreateEvent()).thenAnswer(invocation -> PASS);

        final AtomicInteger numSignatureTransactions = new AtomicInteger(0);
        final EventTransactionPool transactionPool = mock(EventTransactionPool.class);
        when(transactionPool.numSignatureTransEvent()).thenAnswer(invocation -> numSignatureTransactions.get());

        final AtomicInteger eventCreationCount = new AtomicInteger(0);
        final TipsetEventCreator baseEventCreator = mock(TipsetEventCreator.class);
        when(baseEventCreator.maybeCreateEvent()).thenAnswer(invocation -> {
            eventCreationCount.incrementAndGet();
            return null;
        });

        final ThrottledTipsetEventCreator eventCreator = new ThrottledTipsetEventCreator(
                platformContext,
                Time.getCurrent(),
                transactionPool,
                eventIntakeQueue,
                platformStatusSupplier,
                startUpEventFrozenManager,
                baseEventCreator);

        assertFalse(eventCreator.isEventCreationPermitted());
        eventCreator.maybeCreateEvent();
        assertEquals(0, eventCreationCount.get());

        numSignatureTransactions.set(1);

        assertTrue(eventCreator.isEventCreationPermitted());
        eventCreator.maybeCreateEvent();
        assertEquals(1, eventCreationCount.get());
    }

    @Test
    @DisplayName("Blocked by Status Test")
    void blockedByStatus() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final EventTransactionPool transactionPool = mock(EventTransactionPool.class);
        final QueueThread<EventIntakeTask> eventIntakeQueue = mock(QueueThread.class);
        final StartUpEventFrozenManager startUpEventFrozenManager = mock(StartUpEventFrozenManager.class);
        when(startUpEventFrozenManager.shouldCreateEvent()).thenAnswer(invocation -> PASS);

        final AtomicReference<PlatformStatus> status = new AtomicReference<>();

        final AtomicInteger eventCreationCount = new AtomicInteger(0);
        final TipsetEventCreator baseEventCreator = mock(TipsetEventCreator.class);
        when(baseEventCreator.maybeCreateEvent()).thenAnswer(invocation -> {
            eventCreationCount.incrementAndGet();
            return null;
        });

        final ThrottledTipsetEventCreator eventCreator = new ThrottledTipsetEventCreator(
                platformContext,
                Time.getCurrent(),
                transactionPool,
                eventIntakeQueue,
                status::get,
                startUpEventFrozenManager,
                baseEventCreator);

        int expectedEventCreationCount = 0;

        for (final PlatformStatus platformStatus : PlatformStatus.values()) {
            if (platformStatus == FREEZING) {
                // this is checked in another test, don't bother checking
                continue;
            }

            status.set(platformStatus);

            if (platformStatus == ACTIVE || platformStatus == CHECKING) {
                assertTrue(eventCreator.isEventCreationPermitted());
                expectedEventCreationCount++;
            } else {
                assertFalse(eventCreator.isEventCreationPermitted());
            }
            eventCreator.maybeCreateEvent();
            assertEquals(expectedEventCreationCount, eventCreationCount.get());
        }
    }

    @Test
    @DisplayName("No Rate Limit Test")
    void noRateLimitTest() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final Time time = new FakeTime();

        final EventTransactionPool transactionPool = mock(EventTransactionPool.class);
        final QueueThread<EventIntakeTask> eventIntakeQueue = mock(QueueThread.class);
        final StartUpEventFrozenManager startUpEventFrozenManager = mock(StartUpEventFrozenManager.class);
        when(startUpEventFrozenManager.shouldCreateEvent()).thenAnswer(invocation -> PASS);

        final AtomicInteger eventCreationCount = new AtomicInteger(0);
        final TipsetEventCreator baseEventCreator = mock(TipsetEventCreator.class);
        when(baseEventCreator.maybeCreateEvent()).thenAnswer(invocation -> {
            eventCreationCount.incrementAndGet();
            return mock(GossipEvent.class);
        });

        final ThrottledTipsetEventCreator eventCreator = new ThrottledTipsetEventCreator(
                platformContext,
                time,
                transactionPool,
                eventIntakeQueue,
                () -> ACTIVE,
                startUpEventFrozenManager,
                baseEventCreator);

        // Ask for a bunch of events to be created without advancing the time.
        for (int i = 0; i < 100; i++) {
            eventCreator.maybeCreateEvent();
            assertEquals(i + 1, eventCreationCount.get());
        }
    }

    @Test
    @DisplayName("Rate Limit Test")
    void rateLimitTest() {
        final Random random = getRandomPrintSeed();

        final int maxRate = 100;
        final Duration period = Duration.ofSeconds(1).dividedBy(maxRate);

        final Configuration configuration = new TestConfigBuilder()
                .withValue("event.creation.maxCreationRate", maxRate)
                .getOrCreateConfig();

        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(configuration)
                .build();

        final FakeTime time = new FakeTime();

        final EventTransactionPool transactionPool = mock(EventTransactionPool.class);
        final QueueThread<EventIntakeTask> eventIntakeQueue = mock(QueueThread.class);
        final StartUpEventFrozenManager startUpEventFrozenManager = mock(StartUpEventFrozenManager.class);
        when(startUpEventFrozenManager.shouldCreateEvent()).thenAnswer(invocation -> PASS);

        final AtomicInteger eventCreationAttemptCount = new AtomicInteger(0);
        final TipsetEventCreator baseEventCreator = mock(TipsetEventCreator.class);
        final AtomicBoolean createNonNullEvent = new AtomicBoolean(false);
        when(baseEventCreator.maybeCreateEvent()).thenAnswer(invocation -> {
            eventCreationAttemptCount.incrementAndGet();
            if (createNonNullEvent.get()) {
                return mock(GossipEvent.class);
            } else {
                return null;
            }
        });

        final ThrottledTipsetEventCreator eventCreator = new ThrottledTipsetEventCreator(
                platformContext,
                time,
                transactionPool,
                eventIntakeQueue,
                () -> ACTIVE,
                startUpEventFrozenManager,
                baseEventCreator);

        boolean eligibleForEventCreation = true;
        int expectedAttemptCount = 0;

        for (int i = 0; i < 100; i++) {
            System.out.println(i);

            final boolean tickForwards = random.nextBoolean();
            if (tickForwards) {
                time.tick(period.plusMillis(random.nextInt(10)));

                // Any time the time advances, we are eligible to create a new event.
                eligibleForEventCreation = true;
            }

            // Sometimes the tipset algorithm will create an event, sometimes it will not.
            createNonNullEvent.set(random.nextBoolean());

            if (eligibleForEventCreation) {
                // If we are eligible to create a new event, an attempt should be made to create an event.
                expectedAttemptCount++;
            }

            eventCreator.maybeCreateEvent();
            assertEquals(expectedAttemptCount, eventCreationAttemptCount.get());

            if (eligibleForEventCreation && createNonNullEvent.get()) {
                // If we actually manage to create an event, we will not be eligible again until time advances.
                eligibleForEventCreation = false;
            }
        }
    }
}
