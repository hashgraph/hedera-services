/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.common.units.DataUnit.UNIT_BYTES;
import static com.swirlds.common.units.DataUnit.UNIT_KILOBYTES;
import static com.swirlds.common.utility.CompareTo.isGreaterThan;
import static com.swirlds.platform.event.preconsensus.PreConsensusEventFileManager.NO_MINIMUM_GENERATION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.time.TimeFacade;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.test.TransactionGenerator;
import com.swirlds.common.test.io.FileManipulation;
import com.swirlds.common.test.metrics.NoOpMetrics;
import com.swirlds.platform.event.preconsensus.AsyncPreConsensusEventWriter;
import com.swirlds.platform.event.preconsensus.PreConsensusEventFile;
import com.swirlds.platform.event.preconsensus.PreConsensusEventFileManager;
import com.swirlds.platform.event.preconsensus.PreConsensusEventMultiFileIterator;
import com.swirlds.platform.event.preconsensus.PreConsensusEventStreamConfig;
import com.swirlds.platform.event.preconsensus.PreConsensusEventWriter;
import com.swirlds.platform.event.preconsensus.PreconsensusEventMetrics;
import com.swirlds.platform.event.preconsensus.PreconsensusEventStreamSequencer;
import com.swirlds.platform.event.preconsensus.SyncPreConsensusEventWriter;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.event.source.StandardEventSource;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("AsyncPreConsensusEventWriter Tests")
class AsyncPreConsensusEventWriterTests {

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

    static PreconsensusEventMetrics buildMetrics() {
        return new PreconsensusEventMetrics(new NoOpMetrics());
    }

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
    static StandardGraphGenerator buildGraphGenerator(final Random random) {
        final TransactionGenerator transactionGenerator = buildTransactionGenerator();

        return new StandardGraphGenerator(
                random.nextLong(),
                new StandardEventSource().setTransactionGenerator(transactionGenerator),
                new StandardEventSource().setTransactionGenerator(transactionGenerator),
                new StandardEventSource().setTransactionGenerator(transactionGenerator),
                new StandardEventSource().setTransactionGenerator(transactionGenerator));
    }

    /**
     * Assert that two events are equal.
     */
    public static void assertEventsAreEqual(final EventImpl expected, final EventImpl actual) {
        assertEquals(expected.getBaseEvent(), actual.getBaseEvent());
        assertEquals(expected.getConsensusData(), actual.getConsensusData());
    }

    /**
     * Perform verification on a stream written by a {@link SyncPreConsensusEventWriter}.
     *
     * @param events the events that were written to the stream
     * @param config the configuration of the writer
     */
    static void verifyStream(
            final List<EventImpl> events, final PreConsensusEventStreamConfig config, final int truncatedFileCount)
            throws IOException {

        long lastGeneration = Long.MIN_VALUE;
        for (final EventImpl event : events) {
            lastGeneration = Math.max(lastGeneration, event.getGeneration());
        }

        final PreConsensusEventFileManager reader =
                new PreConsensusEventFileManager(TimeFacade.getOsTime(), config, buildMetrics());

        // Verify that the events were written correctly
        final PreConsensusEventMultiFileIterator eventsIterator = reader.getEventIterator(0);
        for (final EventImpl event : events) {
            assertTrue(eventsIterator.hasNext());
            assertEventsAreEqual(event, eventsIterator.next());
        }
        assertFalse(eventsIterator.hasNext());
        assertEquals(truncatedFileCount, eventsIterator.getTruncatedFileCount());

        // Make sure things look good when iterating starting in the middle of the stream that was written
        final long startingGeneration = lastGeneration / 2;
        final IOIterator<EventImpl> eventsIterator2 = reader.getEventIterator(startingGeneration);
        for (final EventImpl event : events) {
            if (event.getGeneration() < startingGeneration) {
                continue;
            }
            assertTrue(eventsIterator2.hasNext());
            assertEventsAreEqual(event, eventsIterator2.next());
        }
        assertFalse(eventsIterator2.hasNext());

        // Iterating from a high generation should yield no events
        final IOIterator<EventImpl> eventsIterator3 = reader.getEventIterator(lastGeneration + 1);
        assertFalse(eventsIterator3.hasNext());

        // Do basic validation on event files
        final List<PreConsensusEventFile> files = new ArrayList<>();
        reader.getFileIterator(0).forEachRemaining(files::add);

        // There should be at least 2 files.
        // Certainly many more, but getting the heuristic right on this is non-trivial.
        assertTrue(files.size() >= 2);

        // Sanity check each individual file
        int nextSequenceNumber = 0;
        Instant previousTimestamp = Instant.MIN;
        long previousMinimum = Long.MIN_VALUE;
        long previousMaximum = Long.MIN_VALUE;
        for (final PreConsensusEventFile file : files) {
            assertEquals(nextSequenceNumber, file.sequenceNumber());
            nextSequenceNumber++;
            assertTrue(isGreaterThan(file.timestamp(), previousTimestamp));
            previousTimestamp = file.timestamp();
            assertTrue(file.minimumGeneration() <= file.maximumGeneration());
            assertTrue(file.minimumGeneration() >= previousMinimum);
            previousMinimum = file.minimumGeneration();
            assertTrue(file.maximumGeneration() >= previousMaximum);
            previousMaximum = file.maximumGeneration();

            final IOIterator<EventImpl> fileEvents = file.iterator(0);
            while (fileEvents.hasNext()) {
                final EventImpl event = fileEvents.next();
                assertTrue(event.getGeneration() >= file.minimumGeneration());
                assertTrue(event.getGeneration() <= file.maximumGeneration());
            }
        }
    }

    /**
     * In this test, keep adding events without increasing the first non-ancient generation. This will force the
     * preferred generations per file to eventually be reached and exceeded.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Overflow Test")
    void overflowTest(final boolean artificialPauses) throws IOException, InterruptedException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 1_000;

        final StandardGraphGenerator generator = buildGraphGenerator(random);

        final List<EventImpl> events = new ArrayList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().convertToEventImpl());
        }

        final int idleWaitPeriodMs = 10;

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .withValue("event.preconsensus.preferredFileSizeMegabytes", 5)
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        final PreConsensusEventFileManager fileManager =
                new PreConsensusEventFileManager(TimeFacade.getOsTime(), config, buildMetrics());

        final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();
        final PreConsensusEventWriter writer = new AsyncPreConsensusEventWriter(
                getStaticThreadManager(), config, new SyncPreConsensusEventWriter(config, fileManager));

        writer.start();

        for (final EventImpl event : events) {
            sequencer.assignStreamSequenceNumber(event);
            writer.writeEvent(event);

            // This component is fundamentally asynchronous, and part of its functionality is related
            // to how it handles this. Simulate random pauses now and again to ensure that we handle
            // things gracefully (although we expect for this test to pass regardless of the pauses).
            if (artificialPauses && random.nextDouble() < 0.05) {
                MILLISECONDS.sleep(idleWaitPeriodMs);
            }
        }

        writer.stop();

        verifyStream(events, config, 0);

        // Without advancing the first non-ancient generation,
        // we should never be able to increase the minimum generation from 0.
        for (final Iterator<PreConsensusEventFile> it = fileManager.getFileIterator(0); it.hasNext(); ) {
            final PreConsensusEventFile file = it.next();
            assertEquals(0, file.minimumGeneration());
        }
    }

    private record AdvanceNonAncientGenerationParams(boolean artificialPauses, int generationsUntilAncient) {
        @Override
        public String toString() {
            return "generations until ancient = " + generationsUntilAncient + ", artificial pauses = "
                    + artificialPauses;
        }
    }

    static Stream<Arguments> advanceNonAncientGenerationArguments() {
        final List<Arguments> arguments = new ArrayList<>();

        for (final int generationsUntilAncient : List.of(1, 5, 26, 50)) {
            for (final boolean artificialPauses : List.of(true, false)) {
                arguments.add(
                        Arguments.of(new AdvanceNonAncientGenerationParams(artificialPauses, generationsUntilAncient)));
            }
        }

        return arguments.stream();
    }

    /**
     * In this test, increase the first non-ancient generation as events are added. When this happens, we should never
     * have to include more than the preferred number of events in each file.
     */
    @ParameterizedTest
    @MethodSource("advanceNonAncientGenerationArguments")
    @DisplayName("Advance Non Ancient Generation Test")
    void advanceNonAncientGenerationTest(final AdvanceNonAncientGenerationParams params)
            throws InterruptedException, IOException {

        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 1_000;

        final StandardGraphGenerator generator = buildGraphGenerator(random);

        final List<EventImpl> events = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().convertToEventImpl());
        }

        final int idleWaitPeriodMs = 10;

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .withValue("event.preconsensus.preferredFileSizeMegabytes", 5)
                .withValue("event.preconsensus.idleWaitPeriod", idleWaitPeriodMs + "ms")
                .withValue("event.preconsensus.minimumRetentionPeriod", idleWaitPeriodMs + "1ns")
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        final PreConsensusEventFileManager fileManager =
                new PreConsensusEventFileManager(TimeFacade.getOsTime(), config, buildMetrics());

        final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();
        final PreConsensusEventWriter writer = new AsyncPreConsensusEventWriter(
                getStaticThreadManager(), config, new SyncPreConsensusEventWriter(config, fileManager));

        writer.start();

        final Set<EventImpl> rejectedEvents = new HashSet<>();

        long minimumGenerationNonAncient = 0;
        for (final EventImpl event : events) {

            sequencer.assignStreamSequenceNumber(event);
            assertFalse(writer.isEventDurable(event));
            writer.writeEvent(event);

            if (event.getGeneration() < minimumGenerationNonAncient) {
                // This event is ancient and will have been rejected.
                rejectedEvents.add(event);
            }

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - params.generationsUntilAncient);
            writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient);

            // This component is fundamentally asynchronous, and part of its functionality is related
            // to how it handles this. Simulate random pauses now and again to ensure that we handle
            // things gracefully (although we expect for this test to pass regardless of the pauses).
            if (params.artificialPauses && random.nextDouble() < 0.05) {
                MILLISECONDS.sleep(idleWaitPeriodMs);
            }
        }

        // Remove the rejected events from the list
        events.removeIf(rejectedEvents::contains);

        // Events should become durable as they are written to disk
        writer.requestFlush(events.get(events.size() - 1));
        for (final EventImpl event : events) {
            assertTrue(writer.waitUntilDurable(event, Duration.ofSeconds(1)));
        }

        // Rejected events should never become durable
        for (final EventImpl event : rejectedEvents) {
            assertFalse(writer.isEventDurable(event));
        }

        verifyStream(events, config, 0);

        // Prune old files.
        final long minimumGenerationToStore = events.get(events.size() - 1).getGeneration() / 2;
        writer.setMinimumGenerationToStore(minimumGenerationToStore);

        // We shouldn't see any files that are incapable of storing events above the minimum
        fileManager
                .getFileIterator(NO_MINIMUM_GENERATION)
                .forEachRemaining(file -> assertTrue(file.maximumGeneration() >= minimumGenerationToStore));

        writer.stop();

        // Since we were very careful to always advance the first non-ancient generation, we should
        // find lots of files with a minimum generation exceeding 0.
        boolean foundNonZeroMinimumGeneration = false;
        for (final Iterator<PreConsensusEventFile> it2 = fileManager.getFileIterator(0); it2.hasNext(); ) {
            final PreConsensusEventFile file = it2.next();
            if (file.minimumGeneration() > 0) {
                foundNonZeroMinimumGeneration = true;
                break;
            }
        }
        assertTrue(foundNonZeroMinimumGeneration);
    }

    /**
     * Simulate a node restarting.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Restart Simulation Test")
    void restartSimulationTest(final boolean truncateLastFile) throws InterruptedException, IOException {

        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 1_000;
        final int generationsNonAncient = 5;

        final StandardGraphGenerator generator = buildGraphGenerator(random);

        final List<EventImpl> events1 = new LinkedList<>();
        final List<EventImpl> events2 = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            if (i < numEvents / 2) {
                events1.add(generator.generateEvent().convertToEventImpl());
            } else {
                events2.add(generator.generateEvent().convertToEventImpl());
            }
        }

        final int idleWaitPeriodMs = 10;

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .withValue("event.preconsensus.preferredFileSizeMegabytes", 5)
                .withValue("event.preconsensus.idleWaitPeriod", idleWaitPeriodMs + "ms")
                .withValue("event.preconsensus.minimumRetentionPeriod", idleWaitPeriodMs + "1ns")
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        final PreConsensusEventFileManager fileManager =
                new PreConsensusEventFileManager(TimeFacade.getOsTime(), config, buildMetrics());

        final PreconsensusEventStreamSequencer sequencer1 = new PreconsensusEventStreamSequencer();
        final PreConsensusEventWriter writer1 = new AsyncPreConsensusEventWriter(
                getStaticThreadManager(), config, new SyncPreConsensusEventWriter(config, fileManager));

        writer1.start();

        long minimumGenerationNonAncient = 0;
        final Set<EventImpl> rejectedEvents1 = new HashSet<>();
        for (final EventImpl event : events1) {

            sequencer1.assignStreamSequenceNumber(event);
            assertFalse(writer1.isEventDurable(event));
            writer1.writeEvent(event);

            if (event.getGeneration() < minimumGenerationNonAncient) {
                // This event is ancient and will have been rejected.
                rejectedEvents1.add(event);
            }

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsNonAncient);
            writer1.setMinimumGenerationNonAncient(minimumGenerationNonAncient);
        }

        events1.removeIf(rejectedEvents1::contains);

        // Simulate a restart.
        writer1.stop();

        if (truncateLastFile) {
            // Remove a single byte from the last file. This will corrupt the last event that was written.
            final Iterator<PreConsensusEventFile> it = fileManager.getFileIterator(NO_MINIMUM_GENERATION);
            while (it.hasNext()) {
                final PreConsensusEventFile file = it.next();
                if (!it.hasNext()) {
                    FileManipulation.truncateNBytesFromFile(file.path(), 1);
                }
            }

            events1.remove(events1.size() - 1);
        }

        final PreconsensusEventStreamSequencer sequencer2 = new PreconsensusEventStreamSequencer();
        final PreConsensusEventWriter writer2 = new AsyncPreConsensusEventWriter(
                getStaticThreadManager(), config, new SyncPreConsensusEventWriter(config, fileManager));
        writer2.start();

        final Set<EventImpl> rejectedEvents2 = new HashSet<>();
        for (final EventImpl event : events2) {

            sequencer2.assignStreamSequenceNumber(event);
            assertFalse(writer2.isEventDurable(event));
            writer2.writeEvent(event);

            if (event.getGeneration() < minimumGenerationNonAncient) {
                // This event is ancient and will have been rejected.
                rejectedEvents2.add(event);
            }

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsNonAncient);
            writer2.setMinimumGenerationNonAncient(minimumGenerationNonAncient);
        }

        events2.removeIf(rejectedEvents2::contains);

        // Events should become durable as they are written to disk
        writer2.requestFlush(events2.get(events2.size() - 1));
        for (final EventImpl event : events2) {
            assertTrue(writer2.waitUntilDurable(event, Duration.ofSeconds(1)));
        }

        final List<EventImpl> allEvents = new ArrayList<>();
        allEvents.addAll(events1);
        allEvents.addAll(events2);

        verifyStream(allEvents, config, truncateLastFile ? 1 : 0);
    }
}
