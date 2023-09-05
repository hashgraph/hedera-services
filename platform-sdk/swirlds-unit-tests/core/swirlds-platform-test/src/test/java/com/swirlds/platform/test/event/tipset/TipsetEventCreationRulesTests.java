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
import static com.swirlds.common.system.UptimeData.NO_ROUND;
import static com.swirlds.common.system.status.PlatformStatus.ACTIVE;
import static com.swirlds.common.system.status.PlatformStatus.CHECKING;
import static com.swirlds.common.system.status.PlatformStatus.FREEZING;
import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.status.PlatformStatus;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.StartUpEventFrozenManager;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.tipset.TipsetEventCreator;
import com.swirlds.platform.event.tipset.rules.AggregateTipsetEventCreationRules;
import com.swirlds.platform.event.tipset.rules.ReconnectStateSavedRule;
import com.swirlds.platform.event.tipset.rules.TipsetEventCreationRule;
import com.swirlds.platform.event.tipset.rules.TipsetMaximumRateRule;
import com.swirlds.platform.event.tipset.rules.TipsetPlatformStatusRule;
import com.swirlds.platform.eventhandling.TransactionPool;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Tipset Event Creation Rules Tests")
class TipsetEventCreationRulesTests {

    @Test
    @DisplayName("Empty Aggregate Test")
    void emptyAggregateTest() {
        final TipsetEventCreationRule rule = AggregateTipsetEventCreationRules.of();
        assertTrue(rule.isEventCreationPermitted());

        // should not throw
        rule.eventWasCreated();
    }

    @Test
    @DisplayName("Aggregate Test")
    void aggregateTest() {
        final TipsetEventCreationRule rule1 = mock(TipsetEventCreationRule.class);
        when(rule1.isEventCreationPermitted()).thenAnswer(invocation -> true);
        final AtomicInteger rule1Count = new AtomicInteger(0);
        doAnswer(invocation -> {
                    rule1Count.incrementAndGet();
                    return null;
                })
                .when(rule1)
                .eventWasCreated();

        final TipsetEventCreationRule rule2 = mock(TipsetEventCreationRule.class);
        when(rule2.isEventCreationPermitted()).thenAnswer(invocation -> true);
        final AtomicInteger rule2Count = new AtomicInteger(0);
        doAnswer(invocation -> {
                    rule2Count.incrementAndGet();
                    return null;
                })
                .when(rule2)
                .eventWasCreated();

        final TipsetEventCreationRule rule3 = mock(TipsetEventCreationRule.class);
        when(rule3.isEventCreationPermitted()).thenAnswer(invocation -> true);
        final AtomicInteger rule3Count = new AtomicInteger(0);
        doAnswer(invocation -> {
                    rule3Count.incrementAndGet();
                    return null;
                })
                .when(rule3)
                .eventWasCreated();

        final TipsetEventCreationRule rule4 = mock(TipsetEventCreationRule.class);
        when(rule4.isEventCreationPermitted()).thenAnswer(invocation -> true);
        final AtomicInteger rule4Count = new AtomicInteger(0);
        doAnswer(invocation -> {
                    rule4Count.incrementAndGet();
                    return null;
                })
                .when(rule4)
                .eventWasCreated();

        final TipsetEventCreationRule aggregateRule = AggregateTipsetEventCreationRules.of(rule1, rule2, rule3, rule4);

        assertTrue(aggregateRule.isEventCreationPermitted());

        when(rule3.isEventCreationPermitted()).thenAnswer(invocation -> false);
        assertFalse(aggregateRule.isEventCreationPermitted());

        when(rule2.isEventCreationPermitted()).thenAnswer(invocation -> false);
        assertFalse(aggregateRule.isEventCreationPermitted());

        when(rule1.isEventCreationPermitted()).thenAnswer(invocation -> false);
        assertFalse(aggregateRule.isEventCreationPermitted());

        when(rule4.isEventCreationPermitted()).thenAnswer(invocation -> false);
        assertFalse(aggregateRule.isEventCreationPermitted());

        aggregateRule.eventWasCreated();
        assertEquals(1, rule1Count.get());
        assertEquals(1, rule2Count.get());
        assertEquals(1, rule3Count.get());
        assertEquals(1, rule4Count.get());
    }

    @Test
    @DisplayName("Blocked by StartUpFrozenManager Test")
    void blockedByStartUpFrozenManagerTest() {
        final TransactionPool transactionPool = mock(TransactionPool.class);
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

        final TipsetPlatformStatusRule rule =
                new TipsetPlatformStatusRule(platformStatusSupplier, transactionPool, startUpEventFrozenManager);

        assertFalse(rule.isEventCreationPermitted());

        shouldCreateEvent.set(PASS);

        assertTrue(rule.isEventCreationPermitted());
    }

    @Test
    @DisplayName("Blocked by Freeze Test")
    void blockedByFreeze() {
        final Supplier<PlatformStatus> platformStatusSupplier = () -> FREEZING;
        final StartUpEventFrozenManager startUpEventFrozenManager = mock(StartUpEventFrozenManager.class);
        when(startUpEventFrozenManager.shouldCreateEvent()).thenAnswer(invocation -> PASS);

        final AtomicInteger numSignatureTransactions = new AtomicInteger(0);
        final TransactionPool transactionPool = mock(TransactionPool.class);
        when(transactionPool.hasBufferedSignatureTransactions())
                .thenAnswer(invocation -> numSignatureTransactions.get() > 0);

        final AtomicInteger eventCreationCount = new AtomicInteger(0);
        final TipsetEventCreator baseEventCreator = mock(TipsetEventCreator.class);
        when(baseEventCreator.maybeCreateEvent()).thenAnswer(invocation -> {
            eventCreationCount.incrementAndGet();
            return null;
        });

        final TipsetEventCreationRule rule =
                new TipsetPlatformStatusRule(platformStatusSupplier, transactionPool, startUpEventFrozenManager);

        assertFalse(rule.isEventCreationPermitted());
        numSignatureTransactions.set(1);
        assertTrue(rule.isEventCreationPermitted());
    }

    @Test
    @DisplayName("Blocked by Status Test")
    void blockedByStatus() {
        final TransactionPool transactionPool = mock(TransactionPool.class);
        final StartUpEventFrozenManager startUpEventFrozenManager = mock(StartUpEventFrozenManager.class);
        when(startUpEventFrozenManager.shouldCreateEvent()).thenAnswer(invocation -> PASS);

        final AtomicReference<PlatformStatus> status = new AtomicReference<>();

        final AtomicInteger eventCreationCount = new AtomicInteger(0);
        final TipsetEventCreator baseEventCreator = mock(TipsetEventCreator.class);
        when(baseEventCreator.maybeCreateEvent()).thenAnswer(invocation -> {
            eventCreationCount.incrementAndGet();
            return null;
        });

        final TipsetEventCreationRule rule =
                new TipsetPlatformStatusRule(status::get, transactionPool, startUpEventFrozenManager);

        for (final PlatformStatus platformStatus : PlatformStatus.values()) {
            if (platformStatus == FREEZING) {
                // this is checked in another test, don't bother checking
                continue;
            }

            status.set(platformStatus);

            if (platformStatus == ACTIVE || platformStatus == CHECKING) {
                assertTrue(rule.isEventCreationPermitted());
            } else {
                assertFalse(rule.isEventCreationPermitted());
            }
        }
    }

    @Test
    @DisplayName("No Rate Limit Test")
    void noRateLimitTest() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final Time time = new FakeTime();

        final StartUpEventFrozenManager startUpEventFrozenManager = mock(StartUpEventFrozenManager.class);
        when(startUpEventFrozenManager.shouldCreateEvent()).thenAnswer(invocation -> PASS);

        final AtomicInteger eventCreationCount = new AtomicInteger(0);
        final TipsetEventCreator baseEventCreator = mock(TipsetEventCreator.class);
        when(baseEventCreator.maybeCreateEvent()).thenAnswer(invocation -> {
            eventCreationCount.incrementAndGet();
            return mock(GossipEvent.class);
        });

        final TipsetEventCreationRule rule = new TipsetMaximumRateRule(platformContext, time);

        // Ask for a bunch of events to be created without advancing the time.
        for (int i = 0; i < 100; i++) {
            assertTrue(rule.isEventCreationPermitted());
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

        final StartUpEventFrozenManager startUpEventFrozenManager = mock(StartUpEventFrozenManager.class);
        when(startUpEventFrozenManager.shouldCreateEvent()).thenAnswer(invocation -> PASS);

        final TipsetEventCreationRule rule = new TipsetMaximumRateRule(platformContext, time);

        int millisSinceLastEvent = (int) period.toMillis();
        for (int i = 0; i < 100; i++) {
            final boolean tickForwards = random.nextBoolean();
            if (tickForwards) {
                final int millisToTick = random.nextInt(5);
                time.tick(Duration.ofMillis(millisToTick));
                millisSinceLastEvent += millisToTick;
            }

            if (millisSinceLastEvent >= period.toMillis()) {
                assertTrue(rule.isEventCreationPermitted());

                // Sometimes create an event. Sometimes don't.
                if (random.nextBoolean()) {
                    rule.eventWasCreated();
                    millisSinceLastEvent = 0;
                }
            } else {
                assertFalse(rule.isEventCreationPermitted());
            }
        }
    }

    @Test
    @DisplayName("ReconnectSavedStateRule Test")
    void reconnectSavedStateRuleTest() {
        final AtomicLong lastReconnectRound = new AtomicLong(NO_ROUND);
        final AtomicLong lastSavedRound = new AtomicLong(NO_ROUND);

        final ReconnectStateSavedRule rule = new ReconnectStateSavedRule(lastReconnectRound::get, lastSavedRound::get);

        // No reconnects or state saves done yet
        assertTrue(rule.isEventCreationPermitted());

        // State saved, no reconnects
        lastReconnectRound.set(NO_ROUND);
        lastSavedRound.set(1);
        assertTrue(rule.isEventCreationPermitted());

        // Reconnect done, no state saved
        lastReconnectRound.set(1);
        lastSavedRound.set(NO_ROUND);
        assertFalse(rule.isEventCreationPermitted());

        // Reconnect done, state saved prior to reconnect
        lastReconnectRound.set(1);
        lastSavedRound.set(0);
        assertFalse(rule.isEventCreationPermitted());

        // reconnect state saved
        lastReconnectRound.set(1);
        lastSavedRound.set(1);
        assertTrue(rule.isEventCreationPermitted());

        // state saved after reconnect
        lastReconnectRound.set(1);
        lastSavedRound.set(2);
        assertTrue(rule.isEventCreationPermitted());
    }
}
