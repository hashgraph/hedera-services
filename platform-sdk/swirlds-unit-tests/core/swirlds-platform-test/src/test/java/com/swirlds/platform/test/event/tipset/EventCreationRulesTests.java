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
import com.swirlds.common.system.status.PlatformStatus;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.EventCreator;
import com.swirlds.platform.event.creation.rules.AggregateEventCreationRules;
import com.swirlds.platform.event.creation.rules.EventCreationRule;
import com.swirlds.platform.event.creation.rules.MaximumRateRule;
import com.swirlds.platform.event.creation.rules.PlatformStatusRule;
import com.swirlds.platform.eventhandling.TransactionPool;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Tipset Event Creation Rules Tests")
class EventCreationRulesTests {

    @Test
    @DisplayName("Empty Aggregate Test")
    void emptyAggregateTest() {
        final EventCreationRule rule = AggregateEventCreationRules.of();
        assertTrue(rule.isEventCreationPermitted());

        // should not throw
        rule.eventWasCreated();
    }

    @Test
    @DisplayName("Aggregate Test")
    void aggregateTest() {
        final EventCreationRule rule1 = mock(EventCreationRule.class);
        when(rule1.isEventCreationPermitted()).thenAnswer(invocation -> true);
        final AtomicInteger rule1Count = new AtomicInteger(0);
        doAnswer(invocation -> {
                    rule1Count.incrementAndGet();
                    return null;
                })
                .when(rule1)
                .eventWasCreated();

        final EventCreationRule rule2 = mock(EventCreationRule.class);
        when(rule2.isEventCreationPermitted()).thenAnswer(invocation -> true);
        final AtomicInteger rule2Count = new AtomicInteger(0);
        doAnswer(invocation -> {
                    rule2Count.incrementAndGet();
                    return null;
                })
                .when(rule2)
                .eventWasCreated();

        final EventCreationRule rule3 = mock(EventCreationRule.class);
        when(rule3.isEventCreationPermitted()).thenAnswer(invocation -> true);
        final AtomicInteger rule3Count = new AtomicInteger(0);
        doAnswer(invocation -> {
                    rule3Count.incrementAndGet();
                    return null;
                })
                .when(rule3)
                .eventWasCreated();

        final EventCreationRule rule4 = mock(EventCreationRule.class);
        when(rule4.isEventCreationPermitted()).thenAnswer(invocation -> true);
        final AtomicInteger rule4Count = new AtomicInteger(0);
        doAnswer(invocation -> {
                    rule4Count.incrementAndGet();
                    return null;
                })
                .when(rule4)
                .eventWasCreated();

        final EventCreationRule aggregateRule = AggregateEventCreationRules.of(rule1, rule2, rule3, rule4);

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
    @DisplayName("Blocked by Freeze Test")
    void blockedByFreeze() {
        final Supplier<PlatformStatus> platformStatusSupplier = () -> FREEZING;

        final AtomicInteger numSignatureTransactions = new AtomicInteger(0);
        final TransactionPool transactionPool = mock(TransactionPool.class);
        when(transactionPool.hasBufferedSignatureTransactions())
                .thenAnswer(invocation -> numSignatureTransactions.get() > 0);

        final AtomicInteger eventCreationCount = new AtomicInteger(0);
        final EventCreator baseEventCreator = mock(EventCreator.class);
        when(baseEventCreator.maybeCreateEvent()).thenAnswer(invocation -> {
            eventCreationCount.incrementAndGet();
            return null;
        });

        final EventCreationRule rule = new PlatformStatusRule(platformStatusSupplier, transactionPool);

        assertFalse(rule.isEventCreationPermitted());
        numSignatureTransactions.set(1);
        assertTrue(rule.isEventCreationPermitted());
    }

    @Test
    @DisplayName("Blocked by Status Test")
    void blockedByStatus() {
        final TransactionPool transactionPool = mock(TransactionPool.class);

        final AtomicReference<PlatformStatus> status = new AtomicReference<>();

        final AtomicInteger eventCreationCount = new AtomicInteger(0);
        final EventCreator baseEventCreator = mock(EventCreator.class);
        when(baseEventCreator.maybeCreateEvent()).thenAnswer(invocation -> {
            eventCreationCount.incrementAndGet();
            return null;
        });

        final EventCreationRule rule = new PlatformStatusRule(status::get, transactionPool);

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

        final AtomicInteger eventCreationCount = new AtomicInteger(0);
        final EventCreator baseEventCreator = mock(EventCreator.class);
        when(baseEventCreator.maybeCreateEvent()).thenAnswer(invocation -> {
            eventCreationCount.incrementAndGet();
            return mock(GossipEvent.class);
        });

        final EventCreationRule rule = new MaximumRateRule(platformContext, time);

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

        final EventCreationRule rule = new MaximumRateRule(platformContext, time);

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
}
