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
import static com.swirlds.common.utility.CompareTo.isGreaterThanOrEqualTo;
import static com.swirlds.platform.event.preconsensus.PreconsensusEventFileManager.NO_MINIMUM_GENERATION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.base.time.Time;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.TestRecycleBin;
import com.swirlds.common.test.fixtures.TransactionGenerator;
import com.swirlds.common.test.fixtures.io.FileManipulation;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.event.preconsensus.AsyncPreconsensusEventWriter;
import com.swirlds.platform.event.preconsensus.PreconsensusEventFile;
import com.swirlds.platform.event.preconsensus.PreconsensusEventFileManager;
import com.swirlds.platform.event.preconsensus.PreconsensusEventMultiFileIterator;
import com.swirlds.platform.event.preconsensus.PreconsensusEventStreamSequencer;
import com.swirlds.platform.event.preconsensus.PreconsensusEventWriter;
import com.swirlds.platform.event.preconsensus.SyncPreconsensusEventWriter;
import com.swirlds.platform.test.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.event.source.StandardEventSource;
import com.swirlds.test.framework.config.TestConfigBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
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

@DisplayName("AsyncPreconsensusEventWriter Tests")
class AsyncPreconsensusEventWriterTests {

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
     * Perform verification on a stream written by a {@link SyncPreconsensusEventWriter}.
     *
     * @param events             the events that were written to the stream
     * @param platformContext    the platform context
     * @param truncatedFileCount the expected number of truncated files
     * @param fixDiscontinuities whether to fix discontinuities in the stream
     */
    static void verifyStream(
            @NonNull final List<EventImpl> events,
            @NonNull final PlatformContext platformContext,
            final int truncatedFileCount,
            final boolean fixDiscontinuities)
            throws IOException {

        long lastGeneration = Long.MIN_VALUE;
        for (final EventImpl event : events) {
            lastGeneration = Math.max(lastGeneration, event.getGeneration());
        }

        final PreconsensusEventFileManager reader = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0));

        // Verify that the events were written correctly
        final PreconsensusEventMultiFileIterator eventsIterator = reader.getEventIterator(0, fixDiscontinuities);
        for (final EventImpl event : events) {
            assertTrue(eventsIterator.hasNext());
            assertEventsAreEqual(event, eventsIterator.next());
        }
        assertFalse(eventsIterator.hasNext());
        assertEquals(truncatedFileCount, eventsIterator.getTruncatedFileCount());

        // Make sure things look good when iterating starting in the middle of the stream that was written
        final long startingGeneration = lastGeneration / 2;
        final IOIterator<EventImpl> eventsIterator2 = reader.getEventIterator(startingGeneration, fixDiscontinuities);
        for (final EventImpl event : events) {
            if (event.getGeneration() < startingGeneration) {
                continue;
            }
            assertTrue(eventsIterator2.hasNext());
            assertEventsAreEqual(event, eventsIterator2.next());
        }
        assertFalse(eventsIterator2.hasNext());

        // Iterating from a high generation should yield no events
        final IOIterator<EventImpl> eventsIterator3 = reader.getEventIterator(lastGeneration + 1, fixDiscontinuities);
        assertFalse(eventsIterator3.hasNext());

        // Do basic validation on event files
        final List<PreconsensusEventFile> files = new ArrayList<>();
        reader.getFileIterator(0, false).forEachRemaining(files::add);

        // There should be at least 2 files.
        // Certainly many more, but getting the heuristic right on this is non-trivial.
        assertTrue(files.size() >= 2);

        // Sanity check each individual file
        int nextSequenceNumber = 0;
        Instant previousTimestamp = Instant.MIN;
        long previousMinimum = Long.MIN_VALUE;
        long previousMaximum = Long.MIN_VALUE;
        for (final PreconsensusEventFile file : files) {
            assertEquals(nextSequenceNumber, file.getSequenceNumber());
            nextSequenceNumber++;
            assertTrue(isGreaterThanOrEqualTo(file.getTimestamp(), previousTimestamp));
            previousTimestamp = file.getTimestamp();
            assertTrue(file.getMinimumGeneration() <= file.getMaximumGeneration());
            assertTrue(file.getMinimumGeneration() >= previousMinimum);
            previousMinimum = file.getMinimumGeneration();
            assertTrue(file.getMaximumGeneration() >= previousMaximum);
            previousMaximum = file.getMaximumGeneration();

            final IOIterator<EventImpl> fileEvents = file.iterator(0);
            while (fileEvents.hasNext()) {
                final EventImpl event = fileEvents.next();
                assertTrue(event.getGeneration() >= file.getMinimumGeneration());
                assertTrue(event.getGeneration() <= file.getMaximumGeneration());
            }
        }
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

        final PlatformContext platformContext = buildContext();

        final PreconsensusEventFileManager fileManager = new PreconsensusEventFileManager(
                buildContext(), Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0));

        final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();
        final PreconsensusEventWriter writer = new AsyncPreconsensusEventWriter(
                platformContext,
                getStaticThreadManager(),
                new SyncPreconsensusEventWriter(platformContext, fileManager));

        writer.start();
        writer.beginStreamingNewEvents();

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

        verifyStream(events, platformContext, 0, false);

        // Without advancing the first non-ancient generation,
        // we should never be able to increase the minimum generation from 0.
        for (final Iterator<PreconsensusEventFile> it = fileManager.getFileIterator(0, false); it.hasNext(); ) {
            final PreconsensusEventFile file = it.next();
            assertEquals(0, file.getMinimumGeneration());
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

        final PlatformContext platformContext = buildContext();

        final FakeTime time = new FakeTime(Duration.ofMillis(1));

        final PreconsensusEventFileManager fileManager =
                new PreconsensusEventFileManager(platformContext, time, TestRecycleBin.getInstance(), new NodeId(0));

        final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();
        final AsyncPreconsensusEventWriter writer = new AsyncPreconsensusEventWriter(
                platformContext,
                getStaticThreadManager(),
                new SyncPreconsensusEventWriter(platformContext, fileManager));

        writer.start();
        writer.beginStreamingNewEvents();

        final Set<EventImpl> rejectedEvents = new HashSet<>();

        long minimumGenerationNonAncient = 0;
        for (final EventImpl event : events) {

            sequencer.assignStreamSequenceNumber(event);
            assertFalse(writer.isEventDurable(event));
            writer.writeEvent(event);

            time.tick(Duration.ofSeconds(1));

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
        writer.requestFlush();
        for (final EventImpl event : events) {
            assertTrue(writer.waitUntilDurable(event, Duration.ofSeconds(1)));
        }

        // Rejected events should never become durable
        for (final EventImpl event : rejectedEvents) {
            assertFalse(writer.isEventDurable(event));
        }

        verifyStream(events, platformContext, 0, false);

        // Advance the time so that all files are GC eligible according to the clock.
        time.tick(Duration.ofDays(1));

        // Prune old files.
        final long minimumGenerationToStore = events.get(events.size() - 1).getGeneration() / 2;
        writer.setMinimumGenerationToStore(minimumGenerationToStore);
        writer.waitUntilNotBusy();

        // We shouldn't see any files that are incapable of storing events above the minimum
        fileManager
                .getFileIterator(NO_MINIMUM_GENERATION, false)
                .forEachRemaining(file -> assertTrue(file.getMaximumGeneration() >= minimumGenerationToStore));

        writer.stop();

        // Since we were very careful to always advance the first non-ancient generation, we should
        // find lots of files with a minimum generation exceeding 0.
        boolean foundNonZeroMinimumGeneration = false;
        for (Iterator<PreconsensusEventFile> it2 = fileManager.getFileIterator(0, false); it2.hasNext(); ) {
            final PreconsensusEventFile file = it2.next();
            if (file.getMinimumGeneration() > 0) {
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

        final PlatformContext platformContext = buildContext();

        final PreconsensusEventFileManager fileManager1 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0));

        final PreconsensusEventStreamSequencer sequencer1 = new PreconsensusEventStreamSequencer();
        final PreconsensusEventWriter writer1 = new AsyncPreconsensusEventWriter(
                platformContext,
                getStaticThreadManager(),
                new SyncPreconsensusEventWriter(platformContext, fileManager1));

        writer1.start();
        writer1.beginStreamingNewEvents();

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
            final Iterator<PreconsensusEventFile> it = fileManager1.getFileIterator(NO_MINIMUM_GENERATION, false);
            while (it.hasNext()) {
                final PreconsensusEventFile file = it.next();
                if (!it.hasNext()) {
                    FileManipulation.truncateNBytesFromFile(file.getPath(), 1);
                }
            }

            events1.remove(events1.size() - 1);
        }

        final PreconsensusEventFileManager fileManager2 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0));
        final PreconsensusEventStreamSequencer sequencer2 = new PreconsensusEventStreamSequencer();
        final PreconsensusEventWriter writer2 = new AsyncPreconsensusEventWriter(
                platformContext,
                getStaticThreadManager(),
                new SyncPreconsensusEventWriter(platformContext, fileManager2));
        writer2.start();

        // Write all events currently in the stream, we expect these to be ignored and not written to the stream twice.
        final IOIterator<EventImpl> iterator = fileManager1.getEventIterator(NO_MINIMUM_GENERATION, false);
        while (iterator.hasNext()) {
            final EventImpl next = iterator.next();
            sequencer2.assignStreamSequenceNumber(next);
            writer2.writeEvent(next);

            if (random.nextDouble() < 0.1) {
                writer2.requestFlush();
                assertTrue(writer2.waitUntilDurable(next, Duration.ofSeconds(1)));
            }
        }

        writer2.beginStreamingNewEvents();
        writer2.setMinimumGenerationNonAncient(minimumGenerationNonAncient);

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
        writer2.requestFlush();
        for (final EventImpl event : events2) {
            assertTrue(writer2.waitUntilDurable(event, Duration.ofSeconds(1)));
        }

        final List<EventImpl> allEvents = new ArrayList<>();
        allEvents.addAll(events1);
        allEvents.addAll(events2);

        verifyStream(allEvents, platformContext, truncateLastFile ? 1 : 0, false);
    }
}
