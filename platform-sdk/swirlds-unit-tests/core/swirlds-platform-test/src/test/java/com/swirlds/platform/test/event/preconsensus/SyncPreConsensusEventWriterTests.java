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

import static com.swirlds.platform.test.event.preconsensus.AsyncPreConsensusEventWriterTests.buildGraphGenerator;
import static com.swirlds.platform.test.event.preconsensus.AsyncPreConsensusEventWriterTests.verifyStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.context.internal.DefaultPlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.test.metrics.NoOpMetrics;
import com.swirlds.common.time.OSTime;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.event.preconsensus.PreConsensusEventFile;
import com.swirlds.platform.event.preconsensus.PreConsensusEventFileManager;
import com.swirlds.platform.event.preconsensus.PreConsensusEventStreamConfig;
import com.swirlds.platform.event.preconsensus.PreConsensusEventWriter;
import com.swirlds.platform.event.preconsensus.PreconsensusEventStreamSequencer;
import com.swirlds.platform.event.preconsensus.SyncPreConsensusEventWriter;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.event.generator.StandardGraphGenerator;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("SyncPreConsensusEventWriter Tests")
class SyncPreConsensusEventWriterTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("");

        SettingsCommon.maxTransactionBytesPerEvent = Integer.MAX_VALUE;
        SettingsCommon.maxTransactionCountPerEvent = Integer.MAX_VALUE;
        SettingsCommon.transactionMaxBytes = Integer.MAX_VALUE;
        SettingsCommon.maxAddressSizeAllowed = Integer.MAX_VALUE;
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

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .withValue("event.preconsensus.preferredFileSizeMegabytes", 5)
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        final PlatformContext platformContext = buildContext();

        final PreConsensusEventFileManager fileManager =
                new PreConsensusEventFileManager(platformContext, OSTime.getInstance(), 0);

        final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();
        final PreConsensusEventWriter writer = new SyncPreConsensusEventWriter(platformContext, fileManager);

        writer.start();

        long minimumGenerationNonAncient = 0;
        for (final EventImpl event : events) {
            sequencer.assignStreamSequenceNumber(event);

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsUntilAncient);
            writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient);

            writer.writeEvent(event);
        }

        writer.requestFlush(events.get(events.size() - 1));

        // Since we are not using threads, the stream should be flushed when we reach this point
        assertTrue(writer.isEventDurable(events.get(events.size() - 1)));

        verifyStream(events, platformContext, 0);

        writer.stop();
    }

    @Test
    @DisplayName("Multiple Flushes Test")
    void multipleFlushesTest() throws IOException, InterruptedException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 1_000;
        final int generationsUntilAncient = random.nextInt(50, 100);

        final StandardGraphGenerator generator = buildGraphGenerator(random);
        final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();

        final List<EventImpl> events = new LinkedList<>();
        final List<Integer> flushIndexes = new LinkedList<>();
        final Set<EventImpl> flushSet = new HashSet<>();
        for (int i = 0; i < numEvents; i++) {
            final EventImpl event = generator.generateEvent().convertToEventImpl();
            events.add(event);
            sequencer.assignStreamSequenceNumber(event);
            if (i % 100 == 0) {
                flushIndexes.add(i);
                flushSet.add(event);
            }
        }

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .withValue("event.preconsensus.preferredFileSizeMegabytes", 5)
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        final PlatformContext platformContext = buildContext();

        final PreConsensusEventFileManager fileManager =
                new PreConsensusEventFileManager(platformContext, OSTime.getInstance(), 0);

        final PreConsensusEventWriter writer = new SyncPreConsensusEventWriter(platformContext, fileManager);

        for (final int flushIndex : flushIndexes) {
            writer.requestFlush(events.get(flushIndex));
        }

        writer.start();

        long minimumGenerationNonAncient = 0;
        for (final EventImpl event : events) {
            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsUntilAncient);
            writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient);

            writer.writeEvent(event);
            if (flushSet.contains(event)) {
                assertTrue(writer.isEventDurable(event));
            }
        }

        writer.requestFlush(events.get(events.size() - 1));
        assertTrue(writer.isEventDurable(events.get(events.size() - 1)));

        verifyStream(events, platformContext, 0);

        writer.stop();
    }

    @Test
    @DisplayName("Out Of Order Flushes Test")
    void outOfOrderFlushesTest() throws IOException, InterruptedException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 1_000;
        final int generationsUntilAncient = random.nextInt(50, 100);

        final StandardGraphGenerator generator = buildGraphGenerator(random);
        final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();

        final List<EventImpl> events = new LinkedList<>();
        final List<Integer> flushIndexes = new LinkedList<>();
        final Set<EventImpl> flushSet = new HashSet<>();
        for (int i = 0; i < numEvents; i++) {
            final EventImpl event = generator.generateEvent().convertToEventImpl();
            events.add(event);
            sequencer.assignStreamSequenceNumber(event);
            if (i % 100 == 0) {
                flushIndexes.add(i);
                flushSet.add(event);
            }
        }

        Collections.shuffle(flushIndexes, random);

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .withValue("event.preconsensus.preferredFileSizeMegabytes", 5)
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        final PlatformContext platformContext = buildContext();

        final PreConsensusEventFileManager fileManager =
                new PreConsensusEventFileManager(platformContext, OSTime.getInstance(), 0);

        final PreConsensusEventWriter writer = new SyncPreConsensusEventWriter(platformContext, fileManager);

        for (final int flushIndex : flushIndexes) {
            writer.requestFlush(events.get(flushIndex));
        }

        writer.start();

        long minimumGenerationNonAncient = 0;
        for (final EventImpl event : events) {
            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsUntilAncient);
            writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient);

            writer.writeEvent(event);
            if (flushSet.contains(event)) {
                assertTrue(writer.isEventDurable(event));
            }
        }

        writer.requestFlush(events.get(events.size() - 1));
        assertTrue(writer.isEventDurable(events.get(events.size() - 1)));

        verifyStream(events, platformContext, 0);

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

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .withValue("event.preconsensus.preferredFileSizeMegabytes", 5)
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        final PlatformContext platformContext = buildContext();

        final PreConsensusEventFileManager fileManager =
                new PreConsensusEventFileManager(platformContext, OSTime.getInstance(), 0);

        final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();
        final PreConsensusEventWriter writer = new SyncPreConsensusEventWriter(platformContext, fileManager);

        writer.start();

        long minimumGenerationNonAncient = 0;
        for (final EventImpl event : events) {
            sequencer.assignStreamSequenceNumber(event);

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsUntilAncient);
            writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient);

            writer.writeEvent(event);
        }

        writer.stop();

        // Since we are not using threads, the stream should be flushed when we reach this point
        assertTrue(writer.isEventDurable(events.get(events.size() - 1)));

        verifyStream(events, platformContext, 0);
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

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .withValue("event.preconsensus.preferredFileSizeMegabytes", 5)
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        final PlatformContext platformContext = buildContext();

        final PreConsensusEventFileManager fileManager =
                new PreConsensusEventFileManager(platformContext, OSTime.getInstance(), 0);

        final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();
        final PreConsensusEventWriter writer = new SyncPreConsensusEventWriter(platformContext, fileManager);

        writer.start();

        long minimumGenerationNonAncient = 0;
        for (final EventImpl event : events) {
            sequencer.assignStreamSequenceNumber(event);

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsUntilAncient);
            writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient);

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

        writer.requestFlush(events.get(events.size() - 1));

        // Since we are not using threads, the stream should be flushed when we reach this point
        assertTrue(writer.isEventDurable(events.get(events.size() - 1)));

        verifyStream(events, platformContext, 0);

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

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .withValue("event.preconsensus.preferredFileSizeMegabytes", 5)
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        final PlatformContext platformContext = buildContext();

        final PreConsensusEventFileManager fileManager =
                new PreConsensusEventFileManager(platformContext, OSTime.getInstance(), 0);

        final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();
        final PreConsensusEventWriter writer = new SyncPreConsensusEventWriter(platformContext, fileManager);

        writer.start();

        for (final EventImpl event : events) {
            sequencer.assignStreamSequenceNumber(event);
            writer.writeEvent(event);
        }

        writer.stop();

        verifyStream(events, platformContext, 0);

        // Without advancing the first non-ancient generation,
        // we should never be able to increase the minimum generation from 0.
        for (final Iterator<PreConsensusEventFile> it = fileManager.getFileIterator(0); it.hasNext(); ) {
            final PreConsensusEventFile file = it.next();
            assertEquals(0, file.minimumGeneration());
        }
    }
}
