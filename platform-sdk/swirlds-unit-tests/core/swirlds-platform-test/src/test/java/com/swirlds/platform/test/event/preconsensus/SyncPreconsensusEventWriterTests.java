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

import static com.swirlds.platform.test.event.preconsensus.AsyncPreconsensusEventWriterTests.buildGraphGenerator;
import static com.swirlds.platform.test.event.preconsensus.AsyncPreconsensusEventWriterTests.verifyStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.time.Time;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.TestRecycleBin;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.event.preconsensus.PreconsensusEventFile;
import com.swirlds.platform.event.preconsensus.PreconsensusEventFileManager;
import com.swirlds.platform.event.preconsensus.PreconsensusEventStreamSequencer;
import com.swirlds.platform.event.preconsensus.PreconsensusEventWriter;
import com.swirlds.platform.event.preconsensus.SyncPreconsensusEventWriter;
import com.swirlds.platform.test.event.generator.StandardGraphGenerator;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("SyncPreconsensusEventWriter Tests")
class SyncPreconsensusEventWriterTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("");
    }

    @BeforeEach
    void beforeEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
        Files.createDirectories(testDirectory);
    }

    @AfterEach
    void afterEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
    }

    private PlatformContext buildContext() {
        final Configuration configuration = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .withValue("event.preconsensus.preferredFileSizeMegabytes", 5)
                .withValue("transaction.maxTransactionBytesPerEvent", Integer.MAX_VALUE)
                .withValue("transaction.maxTransactionCountPerEvent", Integer.MAX_VALUE)
                .withValue("transaction.transactionMaxBytes", Integer.MAX_VALUE)
                .withValue("transaction.maxAddressSizeAllowed", Integer.MAX_VALUE)
                .getOrCreateConfig();

        final Metrics metrics = new NoOpMetrics();

        return new DefaultPlatformContext(configuration, metrics, CryptographyHolder.get());
    }

    @Test
    @DisplayName("Standard Operation Test")
    void standardOperationTest() throws IOException, InterruptedException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 1_000;
        final int generationsUntilAncient = random.nextInt(50, 100);

        final StandardGraphGenerator generator = buildGraphGenerator(random);

        final List<EventImpl> events = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().convertToEventImpl());
        }

        final PlatformContext platformContext = buildContext();

        final PreconsensusEventFileManager fileManager = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0));

        final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();
        final PreconsensusEventWriter writer = new SyncPreconsensusEventWriter(platformContext, fileManager);

        writer.start();
        writer.beginStreamingNewEvents();

        long minimumGenerationNonAncient = 0;
        final Iterator<EventImpl> iterator = events.iterator();
        while (iterator.hasNext()) {
            final EventImpl event = iterator.next();

            sequencer.assignStreamSequenceNumber(event);

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsUntilAncient);
            writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient);

            if (event.getGeneration() < minimumGenerationNonAncient) {
                // Although it's not common, it's actually possible that the generator will generate
                // an event that is ancient (since it isn't aware of what we consider to be ancient)
                iterator.remove();
            }

            writer.writeEvent(event);
        }

        writer.requestFlush();

        assertTrue(writer.waitUntilDurable(events.get(events.size() - 1), Duration.ofSeconds(1)));
        assertTrue(writer.isEventDurable(events.get(events.size() - 1)));

        verifyStream(events, platformContext, 0, false);

        writer.stop();
    }

    @Test
    @DisplayName("Stop Flushes Events Test")
    void stopFlushesEventsTest() throws IOException, InterruptedException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 1_000;
        final int generationsUntilAncient = random.nextInt(50, 100);

        final StandardGraphGenerator generator = buildGraphGenerator(random);

        final List<EventImpl> events = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().convertToEventImpl());
        }

        final PlatformContext platformContext = buildContext();

        final PreconsensusEventFileManager fileManager = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0));

        final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();
        final PreconsensusEventWriter writer = new SyncPreconsensusEventWriter(platformContext, fileManager);

        writer.start();
        writer.beginStreamingNewEvents();

        long minimumGenerationNonAncient = 0;
        final Iterator<EventImpl> iterator = events.iterator();
        while (iterator.hasNext()) {
            final EventImpl event = iterator.next();
            sequencer.assignStreamSequenceNumber(event);

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsUntilAncient);
            writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient);

            if (event.getGeneration() < minimumGenerationNonAncient) {
                // Although it's not common, it's actually possible that the generator will generate
                // an event that is ancient (since it isn't aware of what we consider to be ancient)
                iterator.remove();
            }

            writer.writeEvent(event);
        }

        writer.stop();

        // Since we are not using threads, the stream should be flushed when we reach this point
        assertTrue(writer.isEventDurable(events.get(events.size() - 1)));

        verifyStream(events, platformContext, 0, false);
    }

    @Test
    @DisplayName("Ancient Event Test")
    void ancientEventTest() throws IOException, InterruptedException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 1_000;
        final int generationsUntilAncient = random.nextInt(10, 20);

        final StandardGraphGenerator generator = buildGraphGenerator(random);

        // We will add this event at the very end, it should be ancient by then
        final EventImpl ancientEvent = generator.generateEvent().convertToEventImpl();

        final List<EventImpl> events = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().convertToEventImpl());
        }

        final PlatformContext platformContext = buildContext();

        final PreconsensusEventFileManager fileManager = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0));

        final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();
        final PreconsensusEventWriter writer = new SyncPreconsensusEventWriter(platformContext, fileManager);

        writer.start();
        writer.beginStreamingNewEvents();

        long minimumGenerationNonAncient = 0;
        final Iterator<EventImpl> iterator = events.iterator();
        while (iterator.hasNext()) {
            final EventImpl event = iterator.next();

            sequencer.assignStreamSequenceNumber(event);

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsUntilAncient);
            writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient);

            if (event.getGeneration() < minimumGenerationNonAncient) {
                // Although it's not common, it's actually possible that the generator will generate
                // an event that is ancient (since it isn't aware of what we consider to be ancient)
                iterator.remove();
            }

            writer.writeEvent(event);
        }

        // Add the ancient event
        sequencer.assignStreamSequenceNumber(ancientEvent);
        if (minimumGenerationNonAncient > ancientEvent.getGeneration()) {
            // This is probably not possible... but just in case make sure this event is ancient
            try {
                writer.setMinimumGenerationNonAncient(ancientEvent.getGeneration() + 1);
            } catch (final IllegalArgumentException e) {
                // ignore, more likely than not this event is way older than the actual ancient generation
            }
        }

        writer.writeEvent(ancientEvent);
        assertEquals(EventImpl.STALE_EVENT_STREAM_SEQUENCE_NUMBER, ancientEvent.getStreamSequenceNumber());

        writer.requestFlush();

        assertTrue(writer.waitUntilDurable(events.get(events.size() - 1), Duration.ofSeconds(1)));
        assertTrue(writer.isEventDurable(events.get(events.size() - 1)));

        verifyStream(events, platformContext, 0, false);

        writer.stop();
    }

    /**
     * In this test, keep adding events without increasing the first non-ancient generation. This will force the
     * preferred generations per file to eventually be reached and exceeded.
     */
    @Test
    @DisplayName("Overflow Test")
    void overflowTest() throws IOException, InterruptedException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 1_000;

        final StandardGraphGenerator generator = buildGraphGenerator(random);

        final List<EventImpl> events = new ArrayList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().convertToEventImpl());
        }

        final PlatformContext platformContext = buildContext();

        final PreconsensusEventFileManager fileManager = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0));

        final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();
        final PreconsensusEventWriter writer = new SyncPreconsensusEventWriter(platformContext, fileManager);

        writer.start();
        writer.beginStreamingNewEvents();

        for (final EventImpl event : events) {
            sequencer.assignStreamSequenceNumber(event);
            writer.writeEvent(event);
        }

        writer.stop();

        verifyStream(events, platformContext, 0, false);

        // Without advancing the first non-ancient generation,
        // we should never be able to increase the minimum generation from 0.
        for (final Iterator<PreconsensusEventFile> it = fileManager.getFileIterator(0, false); it.hasNext(); ) {
            final PreconsensusEventFile file = it.next();
            assertEquals(0, file.getMinimumGeneration());
        }
    }

    @Test
    @DisplayName("beginStreamingEvents() Test")
    void beginStreamingEventsTest() throws IOException, InterruptedException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 1_000;
        final int generationsUntilAncient = random.nextInt(50, 100);

        final StandardGraphGenerator generator = buildGraphGenerator(random);

        final List<EventImpl> events = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().convertToEventImpl());
        }

        final PlatformContext platformContext = buildContext();

        final PreconsensusEventFileManager fileManager = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0));

        final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();
        final PreconsensusEventWriter writer = new SyncPreconsensusEventWriter(platformContext, fileManager);

        writer.start();

        // We intentionally do not call writer.beginStreamingNewEvents(). This should cause all events
        // passed into the writer to be more or less ignored.

        long minimumGenerationNonAncient = 0;
        for (EventImpl event : events) {
            sequencer.assignStreamSequenceNumber(event);

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsUntilAncient);
            writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient);

            writer.writeEvent(event);
        }

        writer.requestFlush();

        assertTrue(writer.waitUntilDurable(events.get(events.size() - 1), Duration.ofSeconds(1)));
        assertTrue(writer.isEventDurable(events.get(events.size() - 1)));

        // We shouldn't find any events in the stream.
        assertFalse(() -> fileManager
                .getFileIterator(PreconsensusEventFileManager.NO_MINIMUM_GENERATION, false)
                .hasNext());

        writer.stop();
    }

    @Test
    @DisplayName("Discontinuity Test")
    void discontinuityTest() throws IOException, InterruptedException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 1_000;
        final int generationsUntilAncient = random.nextInt(50, 100);

        final StandardGraphGenerator generator = buildGraphGenerator(random);

        final List<EventImpl> eventsBeforeDiscontinuity = new LinkedList<>();
        final List<EventImpl> eventsAfterDiscontinuity = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            final EventImpl event = generator.generateEvent().convertToEventImpl();
            if (i < numEvents / 2) {
                eventsBeforeDiscontinuity.add(event);
            } else {
                eventsAfterDiscontinuity.add(event);
            }
        }

        final PlatformContext platformContext = buildContext();

        final PreconsensusEventFileManager fileManager = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0));

        final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();
        final PreconsensusEventWriter writer = new SyncPreconsensusEventWriter(platformContext, fileManager);

        writer.start();
        writer.beginStreamingNewEvents();

        long minimumGenerationNonAncient = 0;
        final Iterator<EventImpl> iterator1 = eventsBeforeDiscontinuity.iterator();
        while (iterator1.hasNext()) {
            final EventImpl event = iterator1.next();

            sequencer.assignStreamSequenceNumber(event);

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsUntilAncient);
            writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient);

            if (event.getGeneration() < minimumGenerationNonAncient) {
                // Although it's not common, it's actually possible that the generator will generate
                // an event that is ancient (since it isn't aware of what we consider to be ancient)
                iterator1.remove();
            }

            writer.writeEvent(event);
        }

        writer.registerDiscontinuity();

        final Iterator<EventImpl> iterator2 = eventsAfterDiscontinuity.iterator();
        while (iterator2.hasNext()) {
            final EventImpl event = iterator2.next();

            sequencer.assignStreamSequenceNumber(event);

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsUntilAncient);
            writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient);

            if (event.getGeneration() < minimumGenerationNonAncient) {
                // Although it's not common, it's actually possible that the generator will generate
                // an event that is ancient (since it isn't aware of what we consider to be ancient)
                iterator2.remove();
            }

            writer.writeEvent(event);
        }

        writer.requestFlush();

        assertTrue(writer.waitUntilDurable(
                eventsAfterDiscontinuity.get(eventsAfterDiscontinuity.size() - 1), Duration.ofSeconds(1)));
        assertTrue(writer.isEventDurable(eventsAfterDiscontinuity.get(eventsAfterDiscontinuity.size() - 1)));

        verifyStream(eventsBeforeDiscontinuity, platformContext, 0, true);

        writer.stop();
    }
}
