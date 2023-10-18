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

package com.swirlds.platform.test.event.preconsensus;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.units.DataUnit.UNIT_BYTES;
import static com.swirlds.common.units.DataUnit.UNIT_KILOBYTES;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.common.test.fixtures.TransactionGenerator;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.preconsensus.PreconsensusEventReplayPipeline;
import com.swirlds.platform.event.validation.EventValidator;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.fixtures.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.fixtures.event.source.StandardEventSource;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PreconsensusEventReplayPipeline Tests")
class PreconsensusEventReplayPipelineTests {

    /**
     * Build a transaction generator.
     */
    private static TransactionGenerator buildTransactionGenerator() {

        final int transactionCount = 10;
        final int averageTransactionSizeInKb = 10;
        final int transactionSizeStandardDeviationInKb = 5;

        return (Random random) -> {
            final ConsensusTransactionImpl[] transactions = new ConsensusTransactionImpl[transactionCount];
            for (int index = 0; index < transactionCount; index++) {

                final int transactionSize = (int) UNIT_KILOBYTES.convertTo(
                        Math.max(
                                1,
                                averageTransactionSizeInKb
                                        + random.nextDouble() * transactionSizeStandardDeviationInKb),
                        UNIT_BYTES);
                final byte[] bytes = new byte[transactionSize];
                random.nextBytes(bytes);

                transactions[index] = new SwirldTransaction(bytes);
            }
            return transactions;
        };
    }

    /**
     * Build an event generator.
     */
    private static StandardGraphGenerator buildGraphGenerator(final Random random) {
        final TransactionGenerator transactionGenerator = buildTransactionGenerator();

        return new StandardGraphGenerator(
                random.nextLong(),
                new StandardEventSource().setTransactionGenerator(transactionGenerator),
                new StandardEventSource().setTransactionGenerator(transactionGenerator),
                new StandardEventSource().setTransactionGenerator(transactionGenerator),
                new StandardEventSource().setTransactionGenerator(transactionGenerator));
    }

    private static IOIterator<GossipEvent> buildIOIterator(@NonNull final List<GossipEvent> events) {
        final Iterator<GossipEvent> baseIterator = events.iterator();
        return new IOIterator<>() {
            @Override
            public boolean hasNext() {
                return baseIterator.hasNext();
            }

            @Override
            public GossipEvent next() {
                return baseIterator.next();
            }
        };
    }

    private static IOIterator<GossipEvent> buildThrowingIOIterator(
            @NonNull final List<GossipEvent> events, final int throwIndex) {
        final Iterator<GossipEvent> baseIterator = events.iterator();
        final AtomicInteger index = new AtomicInteger(0);
        return new IOIterator<>() {
            @Override
            public boolean hasNext() throws IOException {
                if (index.get() == throwIndex) {
                    throw new IOException("intentional Exception");
                } else if (index.get() > throwIndex) {
                    return false;
                }
                return baseIterator.hasNext();
            }

            @Override
            public GossipEvent next() throws IOException {
                if (index.get() == throwIndex) {
                    throw new IOException("intentional Exception");
                } else if (index.get() > throwIndex) {
                    throw new NoSuchElementException();
                }

                index.incrementAndGet();
                return baseIterator.next();
            }
        };
    }

    @Test
    @DisplayName("Basic Behavior Test")
    void basicBehaviorTest() {
        final Random random = getRandomPrintSeed();

        final int eventCount = 100;

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final ThreadManager threadManager = AdHocThreadManager.getStaticThreadManager();

        final StandardGraphGenerator graphGenerator = buildGraphGenerator(random);
        final List<GossipEvent> events = new ArrayList<>(eventCount);

        for (int index = 0; index < eventCount; index++) {
            final EventImpl event = graphGenerator.generateEvent();
            // We want unhashed events for this test
            event.getHashedData().setHash(null);

            // Intentionally cause hashing to take a while. The pipeline should pause appropriately and wait.
            final BaseEventHashedData spyHashedData = spy(event.getBaseEventHashedData());
            final int sleepTime = random.nextInt(10);
            doAnswer((invocation) -> {
                        MILLISECONDS.sleep(sleepTime);
                        invocation.callRealMethod();
                        return null;
                    })
                    .when(spyHashedData)
                    .setHash(any());

            final GossipEvent eventWithSpy = new GossipEvent(spyHashedData, event.getBaseEventUnhashedData());
            events.add(eventWithSpy);
        }

        final List<GossipEvent> receivedEvents = new ArrayList<>(eventCount);
        final AtomicInteger count = new AtomicInteger(0);

        final EventValidator eventValidator = mock(EventValidator.class);
        doAnswer((invocation) -> {
                    final GossipEvent event = invocation.getArgument(0);
                    assertNotNull(event.getHashedData().getHash());
                    assertNotNull(event.getDescriptor());
                    receivedEvents.add(event);
                    count.getAndIncrement();
                    return null;
                })
                .when(eventValidator)
                .validateEvent(any());

        final PreconsensusEventReplayPipeline pipeline = new PreconsensusEventReplayPipeline(
                platformContext, threadManager, buildIOIterator(events), eventValidator);

        pipeline.replayEvents();

        assertEventuallyEquals(
                eventCount, count::get, Duration.ofSeconds(1), "pipeline did not handle events quickly enough");

        for (int index = 0; index < eventCount; index++) {
            assertSame(
                    events.get(index).getHashedData(), receivedEvents.get(index).getHashedData());
        }
    }

    @Test
    @DisplayName("Hash Failure Test")
    void hashFailureTest() {
        final Random random = getRandomPrintSeed();

        final int eventCount = 100;

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final ThreadManager threadManager = AdHocThreadManager.getStaticThreadManager();

        final StandardGraphGenerator graphGenerator = buildGraphGenerator(random);
        final List<GossipEvent> events = new ArrayList<>(eventCount);

        for (int index = 0; index < eventCount; index++) {
            final EventImpl event = graphGenerator.generateEvent();
            // We want unhashed events for this test
            event.getHashedData().setHash(null);

            // Intentionally cause hashing to take a while. The pipeline should pause appropriately and wait.
            final BaseEventHashedData spyHashedData = spy(event.getBaseEventHashedData());
            final int sleepTime = random.nextInt(10);
            final boolean throwException = index == eventCount / 2;
            doAnswer((invocation) -> {
                        if (throwException) {
                            throw new RuntimeException("Intentional Exception");
                        }

                        MILLISECONDS.sleep(sleepTime);
                        invocation.callRealMethod();
                        return null;
                    })
                    .when(spyHashedData)
                    .setHash(any());

            final GossipEvent eventWithSpy = new GossipEvent(spyHashedData, event.getBaseEventUnhashedData());
            events.add(eventWithSpy);
        }

        final List<GossipEvent> receivedEvents = new ArrayList<>(eventCount);
        final AtomicInteger count = new AtomicInteger(0);

        final EventValidator eventValidator = mock(EventValidator.class);
        doAnswer((invocation) -> {
                    final GossipEvent event = invocation.getArgument(0);
                    assertNotNull(event.getHashedData().getHash());
                    assertNotNull(event.getDescriptor());
                    receivedEvents.add(event);
                    count.getAndIncrement();
                    return null;
                })
                .when(eventValidator)
                .validateEvent(any());

        final PreconsensusEventReplayPipeline pipeline = new PreconsensusEventReplayPipeline(
                platformContext, threadManager, buildIOIterator(events), eventValidator);

        assertThrows(IllegalStateException.class, pipeline::replayEvents);

        assertEquals(eventCount / 2, count.get());

        for (int index = 0; index < eventCount / 2; index++) {
            assertSame(
                    events.get(index).getHashedData(), receivedEvents.get(index).getHashedData());
        }
    }

    @Test
    @DisplayName("Ingest Failure Test")
    void ingestFailureTest() {
        final Random random = getRandomPrintSeed();

        final int eventCount = 100;

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final ThreadManager threadManager = AdHocThreadManager.getStaticThreadManager();

        final StandardGraphGenerator graphGenerator = buildGraphGenerator(random);
        final List<GossipEvent> events = new ArrayList<>(eventCount);

        for (int index = 0; index < eventCount; index++) {
            final EventImpl event = graphGenerator.generateEvent();
            // We want unhashed events for this test
            event.getHashedData().setHash(null);

            // Intentionally cause hashing to take a while. The pipeline should pause appropriately and wait.
            final BaseEventHashedData spyHashedData = spy(event.getBaseEventHashedData());
            final int sleepTime = random.nextInt(10);
            doAnswer((invocation) -> {
                        MILLISECONDS.sleep(sleepTime);
                        invocation.callRealMethod();
                        return null;
                    })
                    .when(spyHashedData)
                    .setHash(any());

            final GossipEvent eventWithSpy = new GossipEvent(spyHashedData, event.getBaseEventUnhashedData());
            events.add(eventWithSpy);
        }

        final List<GossipEvent> receivedEvents = new ArrayList<>(eventCount);
        final AtomicInteger count = new AtomicInteger(0);
        final BaseEventHashedData errorEvent = events.get(eventCount / 2).getHashedData();

        final EventValidator eventValidator = mock(EventValidator.class);
        doAnswer((invocation) -> {
                    final GossipEvent event = invocation.getArgument(0);

                    if (event.getHashedData() == errorEvent) {
                        throw new RuntimeException("Intentional Exception");
                    }

                    assertNotNull(event.getHashedData().getHash());
                    assertNotNull(event.getDescriptor());
                    receivedEvents.add(event);
                    count.getAndIncrement();
                    return null;
                })
                .when(eventValidator)
                .validateEvent(any());

        final PreconsensusEventReplayPipeline pipeline = new PreconsensusEventReplayPipeline(
                platformContext, threadManager, buildIOIterator(events), eventValidator);

        assertThrows(IllegalStateException.class, pipeline::replayEvents);

        assertEquals(eventCount / 2, count.get());

        for (int index = 0; index < eventCount / 2; index++) {
            assertSame(
                    events.get(index).getHashedData(), receivedEvents.get(index).getHashedData());
        }
    }

    @Test
    @DisplayName("Parse Failure Test")
    void parseFailureTest() {
        final Random random = getRandomPrintSeed();

        final int eventCount = 100;

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final ThreadManager threadManager = AdHocThreadManager.getStaticThreadManager();

        final StandardGraphGenerator graphGenerator = buildGraphGenerator(random);
        final List<GossipEvent> events = new ArrayList<>(eventCount);

        for (int index = 0; index < eventCount; index++) {
            final EventImpl event = graphGenerator.generateEvent();
            // We want unhashed events for this test
            event.getHashedData().setHash(null);

            // Intentionally cause hashing to take a while. The pipeline should pause appropriately and wait.
            final BaseEventHashedData spyHashedData = spy(event.getBaseEventHashedData());
            final int sleepTime = random.nextInt(10);
            doAnswer((invocation) -> {
                        MILLISECONDS.sleep(sleepTime);
                        invocation.callRealMethod();
                        return null;
                    })
                    .when(spyHashedData)
                    .setHash(any());

            final GossipEvent eventWithSpy = new GossipEvent(spyHashedData, event.getBaseEventUnhashedData());
            events.add(eventWithSpy);
        }

        final List<GossipEvent> receivedEvents = new ArrayList<>(eventCount);
        final AtomicInteger count = new AtomicInteger(0);

        final EventValidator eventValidator = mock(EventValidator.class);
        doAnswer((invocation) -> {
                    final GossipEvent event = invocation.getArgument(0);
                    assertNotNull(event.getHashedData().getHash());
                    assertNotNull(event.getDescriptor());
                    receivedEvents.add(event);
                    count.getAndIncrement();
                    return null;
                })
                .when(eventValidator)
                .validateEvent(any());

        final PreconsensusEventReplayPipeline pipeline = new PreconsensusEventReplayPipeline(
                platformContext, threadManager, buildThrowingIOIterator(events, eventCount / 2), eventValidator);

        assertThrows(UncheckedIOException.class, pipeline::replayEvents);

        assertEquals(eventCount / 2, count.get());

        for (int index = 0; index < eventCount / 2; index++) {
            assertSame(
                    events.get(index).getHashedData(), receivedEvents.get(index).getHashedData());
        }
    }
}
