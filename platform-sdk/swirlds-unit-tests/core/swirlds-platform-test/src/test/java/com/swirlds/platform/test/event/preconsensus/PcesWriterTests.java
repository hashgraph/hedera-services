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

package com.swirlds.platform.test.event.preconsensus;

import static com.swirlds.common.units.DataUnit.UNIT_BYTES;
import static com.swirlds.common.units.DataUnit.UNIT_KILOBYTES;
import static com.swirlds.common.utility.CompareTo.isGreaterThanOrEqualTo;
import static com.swirlds.platform.event.preconsensus.PcesFileManager.NO_MINIMUM_GENERATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.base.time.Time;
import com.swirlds.common.config.TransactionConfig_;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.TestRecycleBin;
import com.swirlds.common.test.fixtures.TransactionGenerator;
import com.swirlds.common.test.fixtures.io.FileManipulation;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.preconsensus.EventDurabilityNexus;
import com.swirlds.platform.event.preconsensus.PcesConfig_;
import com.swirlds.platform.event.preconsensus.PcesFile;
import com.swirlds.platform.event.preconsensus.PcesFileManager;
import com.swirlds.platform.event.preconsensus.PcesFileReader;
import com.swirlds.platform.event.preconsensus.PcesFileTracker;
import com.swirlds.platform.event.preconsensus.PcesMultiFileIterator;
import com.swirlds.platform.event.preconsensus.PcesSequencer;
import com.swirlds.platform.event.preconsensus.PcesUtilities;
import com.swirlds.platform.event.preconsensus.PcesWriter;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import com.swirlds.platform.test.fixtures.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.fixtures.event.source.StandardEventSource;
import com.swirlds.platform.wiring.DoneStreamingPcesTrigger;
import com.swirlds.test.framework.config.TestConfigBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PcesWriter Tests")
class PcesWriterTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    private FakeTime time;
    private final NodeId selfId = new NodeId(0);
    private final int numEvents = 1_000;
    private PlatformContext platformContext;
    private StandardGraphGenerator generator;
    private int generationsUntilAncient;
    private PcesSequencer sequencer;
    private PcesFileTracker pcesFiles;
    private PcesWriter writer;
    private EventDurabilityNexus eventDurabilityNexus;

    /**
     * Perform verification on a stream written by a {@link PcesWriter}.
     *
     * @param events             the events that were written to the stream
     * @param platformContext    the platform context
     * @param truncatedFileCount the expected number of truncated files
     */
    private void verifyStream(
            @NonNull final List<GossipEvent> events,
            @NonNull final PlatformContext platformContext,
            final int truncatedFileCount)
            throws IOException {

        long lastGeneration = Long.MIN_VALUE;
        for (final GossipEvent event : events) {
            lastGeneration = Math.max(lastGeneration, event.getGeneration());
        }

        final PcesFileTracker pcesFiles = PcesFileReader.readFilesFromDisk(
                platformContext,
                TestRecycleBin.getInstance(),
                PcesUtilities.getDatabaseDirectory(platformContext, selfId),
                0,
                false);

        // Verify that the events were written correctly
        final PcesMultiFileIterator eventsIterator = pcesFiles.getEventIterator(0, 0);
        for (final GossipEvent event : events) {
            assertTrue(eventsIterator.hasNext());
            assertEquals(event, eventsIterator.next());
        }

        assertFalse(eventsIterator.hasNext());
        assertEquals(truncatedFileCount, eventsIterator.getTruncatedFileCount());

        // Make sure things look good when iterating starting in the middle of the stream that was written
        final long startingGeneration = lastGeneration / 2;
        final IOIterator<GossipEvent> eventsIterator2 = pcesFiles.getEventIterator(startingGeneration, 0);
        for (final GossipEvent event : events) {
            if (event.getGeneration() < startingGeneration) {
                continue;
            }
            assertTrue(eventsIterator2.hasNext());
            assertEquals(event, eventsIterator2.next());
        }
        assertFalse(eventsIterator2.hasNext());

        // Iterating from a high generation should yield no events
        final IOIterator<GossipEvent> eventsIterator3 = pcesFiles.getEventIterator(lastGeneration + 1, 0);
        assertFalse(eventsIterator3.hasNext());

        // Do basic validation on event files
        final List<PcesFile> files = new ArrayList<>();
        pcesFiles.getFileIterator(0, 0).forEachRemaining(files::add);

        // There should be at least 2 files.
        // Certainly many more, but getting the heuristic right on this is non-trivial.
        assertTrue(files.size() >= 2);

        // Sanity check each individual file
        int nextSequenceNumber = 0;
        Instant previousTimestamp = Instant.MIN;
        long previousMinimum = Long.MIN_VALUE;
        long previousMaximum = Long.MIN_VALUE;
        for (final PcesFile file : files) {
            assertEquals(nextSequenceNumber, file.getSequenceNumber());
            nextSequenceNumber++;
            assertTrue(isGreaterThanOrEqualTo(file.getTimestamp(), previousTimestamp));
            previousTimestamp = file.getTimestamp();
            assertTrue(file.getMinimumGeneration() <= file.getMaximumGeneration());
            assertTrue(file.getMinimumGeneration() >= previousMinimum);
            previousMinimum = file.getMinimumGeneration();
            assertTrue(file.getMaximumGeneration() >= previousMaximum);
            previousMaximum = file.getMaximumGeneration();

            final IOIterator<GossipEvent> fileEvents = file.iterator(0);
            while (fileEvents.hasNext()) {
                final GossipEvent event = fileEvents.next();
                assertTrue(event.getGeneration() >= file.getMinimumGeneration());
                assertTrue(event.getGeneration() <= file.getMaximumGeneration());
            }
        }
    }

    /**
     * Build a transaction generator.
     */
    private static TransactionGenerator buildTransactionGenerator() {

        final int transactionCount = 10;
        final int averageTransactionSizeInKb = 10;
        final int transactionSizeStandardDeviationInKb = 5;

        return (final Random random) -> {
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

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("");
    }

    @BeforeEach
    void beforeEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
        Files.createDirectories(testDirectory);

        platformContext = buildContext();

        final Random random = RandomUtils.getRandomPrintSeed();
        generator = buildGraphGenerator(random);
        generationsUntilAncient = random.nextInt(50, 100);
        sequencer = new PcesSequencer();
        pcesFiles = new PcesFileTracker();

        time = new FakeTime(Duration.ofMillis(1));
        final PcesFileManager fileManager = new PcesFileManager(platformContext, time, pcesFiles, selfId, 0);
        writer = new PcesWriter(platformContext, fileManager);
        eventDurabilityNexus = new EventDurabilityNexus();
    }

    @AfterEach
    void afterEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
    }

    private PlatformContext buildContext() {
        final Configuration configuration = new TestConfigBuilder()
                .withValue(PcesConfig_.DATABASE_DIRECTORY, testDirectory)
                .withValue(PcesConfig_.PREFERRED_FILE_SIZE_MEGABYTES, 5)
                .withValue(TransactionConfig_.MAX_TRANSACTION_BYTES_PER_EVENT, Integer.MAX_VALUE)
                .withValue(TransactionConfig_.MAX_TRANSACTION_COUNT_PER_EVENT, Integer.MAX_VALUE)
                .withValue(TransactionConfig_.TRANSACTION_MAX_BYTES, Integer.MAX_VALUE)
                .getOrCreateConfig();

        final Metrics metrics = new NoOpMetrics();

        return new DefaultPlatformContext(configuration, metrics, CryptographyHolder.get(), Time.getCurrent());
    }

    /**
     * Pass the most recent durable sequence number to the durability nexus.
     * <p>
     * The intention of this method is to simply pass the return value from any writer call that returns a sequence
     * number. This simulates the components being wired together.
     *
     * @param mostRecentDurableSequenceNumber the most recent durable sequence number
     */
    private void passValueToDurabilityNexus(@Nullable final Long mostRecentDurableSequenceNumber) {
        if (mostRecentDurableSequenceNumber != null) {
            eventDurabilityNexus.setLatestDurableSequenceNumber(mostRecentDurableSequenceNumber);
        }
    }

    @Test
    @DisplayName("Standard Operation Test")
    void standardOperationTest() throws IOException {
        final List<GossipEvent> events = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        writer.beginStreamingNewEvents(new DoneStreamingPcesTrigger());

        final Collection<GossipEvent> rejectedEvents = new HashSet<>();

        long minimumGenerationNonAncient = 0;
        final Iterator<GossipEvent> iterator = events.iterator();
        while (iterator.hasNext()) {
            final GossipEvent event = iterator.next();

            sequencer.assignStreamSequenceNumber(event);
            passValueToDurabilityNexus(writer.writeEvent(event));

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsUntilAncient);
            passValueToDurabilityNexus(writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient));

            if (event.getGeneration() < minimumGenerationNonAncient) {
                // Although it's not common, it's possible that the generator will generate
                // an event that is ancient (since it isn't aware of what we consider to be ancient)
                rejectedEvents.add(event);
                iterator.remove();
            }
        }

        events.forEach(event -> assertTrue(eventDurabilityNexus.isEventDurable(event)));
        rejectedEvents.forEach(event -> assertFalse(eventDurabilityNexus.isEventDurable(event)));

        verifyStream(events, platformContext, 0);

        writer.closeCurrentMutableFile();
    }

    @Test
    @DisplayName("Ancient Event Test")
    void ancientEventTest() throws IOException {
        // We will add this event at the very end, it should be ancient by then
        final GossipEvent ancientEvent = generator.generateEvent().getBaseEvent();

        final List<GossipEvent> events = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        writer.beginStreamingNewEvents(new DoneStreamingPcesTrigger());

        final Collection<GossipEvent> rejectedEvents = new HashSet<>();

        long minimumGenerationNonAncient = 0;
        final Iterator<GossipEvent> iterator = events.iterator();
        while (iterator.hasNext()) {
            final GossipEvent event = iterator.next();

            sequencer.assignStreamSequenceNumber(event);
            passValueToDurabilityNexus(writer.writeEvent(event));

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsUntilAncient);
            passValueToDurabilityNexus(writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient));

            if (event.getGeneration() < minimumGenerationNonAncient) {
                // Although it's not common, it's actually possible that the generator will generate
                // an event that is ancient (since it isn't aware of what we consider to be ancient)
                rejectedEvents.add(event);
                iterator.remove();
            }
        }

        // Add the ancient event
        sequencer.assignStreamSequenceNumber(ancientEvent);
        if (minimumGenerationNonAncient > ancientEvent.getGeneration()) {
            // This is probably not possible... but just in case make sure this event is ancient
            try {
                passValueToDurabilityNexus(writer.setMinimumGenerationNonAncient(ancientEvent.getGeneration() + 1));
            } catch (final IllegalArgumentException e) {
                // ignore, more likely than not this event is way older than the actual ancient generation
            }
        }

        passValueToDurabilityNexus(writer.writeEvent(ancientEvent));
        rejectedEvents.add(ancientEvent);
        assertEquals(GossipEvent.STALE_EVENT_STREAM_SEQUENCE_NUMBER, ancientEvent.getStreamSequenceNumber());

        events.forEach(event -> assertTrue(eventDurabilityNexus.isEventDurable(event)));
        rejectedEvents.forEach(event -> assertFalse(eventDurabilityNexus.isEventDurable(event)));

        verifyStream(events, platformContext, 0);

        writer.closeCurrentMutableFile();
    }

    /**
     * In this test, keep adding events without increasing the first non-ancient generation. This will force the
     * preferred generations per file to eventually be reached and exceeded.
     */
    @Test
    @DisplayName("Overflow Test")
    void overflowTest() throws IOException {
        final List<GossipEvent> events = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        writer.beginStreamingNewEvents(new DoneStreamingPcesTrigger());

        for (final GossipEvent event : events) {
            sequencer.assignStreamSequenceNumber(event);
            passValueToDurabilityNexus(writer.writeEvent(event));
        }

        writer.closeCurrentMutableFile();

        verifyStream(events, platformContext, 0);

        // Without advancing the first non-ancient generation,
        // we should never be able to increase the minimum generation from 0.
        for (final Iterator<PcesFile> it = pcesFiles.getFileIterator(0, 0); it.hasNext(); ) {
            final PcesFile file = it.next();
            assertEquals(0, file.getMinimumGeneration());
        }
    }

    @Test
    @DisplayName("beginStreamingEvents() Test")
    void beginStreamingEventsTest() {
        final List<GossipEvent> events = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        // We intentionally do not call writer.beginStreamingNewEvents(). This should cause all events
        // passed into the writer to be more or less ignored.

        long minimumGenerationNonAncient = 0;
        for (final GossipEvent event : events) {
            sequencer.assignStreamSequenceNumber(event);
            passValueToDurabilityNexus(writer.writeEvent(event));

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsUntilAncient);
            passValueToDurabilityNexus(writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient));
        }

        assertTrue(eventDurabilityNexus.isEventDurable(events.get(events.size() - 1)));

        // We shouldn't find any events in the stream.
        assertFalse(() -> pcesFiles
                .getFileIterator(PcesFileManager.NO_MINIMUM_GENERATION, 0)
                .hasNext());

        writer.closeCurrentMutableFile();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Discontinuity Test")
    void discontinuityTest(final boolean truncateLastFile) throws IOException {
        final List<GossipEvent> eventsBeforeDiscontinuity = new LinkedList<>();
        final List<GossipEvent> eventsAfterDiscontinuity = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            final GossipEvent event = generator.generateEvent().getBaseEvent();
            if (i < numEvents / 2) {
                eventsBeforeDiscontinuity.add(event);
            } else {
                eventsAfterDiscontinuity.add(event);
            }
        }

        writer.beginStreamingNewEvents(new DoneStreamingPcesTrigger());

        final Collection<GossipEvent> rejectedEvents = new HashSet<>();

        long minimumGenerationNonAncient = 0;
        final Iterator<GossipEvent> iterator1 = eventsBeforeDiscontinuity.iterator();
        while (iterator1.hasNext()) {
            final GossipEvent event = iterator1.next();

            sequencer.assignStreamSequenceNumber(event);
            passValueToDurabilityNexus(writer.writeEvent(event));

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsUntilAncient);
            passValueToDurabilityNexus(writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient));

            if (event.getGeneration() < minimumGenerationNonAncient) {
                // Although it's not common, it's actually possible that the generator will generate
                // an event that is ancient (since it isn't aware of what we consider to be ancient)
                rejectedEvents.add(event);
                iterator1.remove();
            }
        }

        eventsBeforeDiscontinuity.forEach(event -> assertTrue(eventDurabilityNexus.isEventDurable(event)));

        passValueToDurabilityNexus(writer.registerDiscontinuity(100));

        if (truncateLastFile) {
            // Remove a single byte from the last file. This will corrupt the last event that was written.
            final Iterator<PcesFile> it = pcesFiles.getFileIterator(NO_MINIMUM_GENERATION, 0);
            while (it.hasNext()) {
                final PcesFile file = it.next();
                if (!it.hasNext()) {
                    FileManipulation.truncateNBytesFromFile(file.getPath(), 1);
                }
            }

            eventsBeforeDiscontinuity.remove(eventsBeforeDiscontinuity.size() - 1);
        }

        final Iterator<GossipEvent> iterator2 = eventsAfterDiscontinuity.iterator();
        while (iterator2.hasNext()) {
            final GossipEvent event = iterator2.next();

            sequencer.assignStreamSequenceNumber(event);
            passValueToDurabilityNexus(writer.writeEvent(event));

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsUntilAncient);
            passValueToDurabilityNexus(writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient));

            if (event.getGeneration() < minimumGenerationNonAncient) {
                // Although it's not common, it's actually possible that the generator will generate
                // an event that is ancient (since it isn't aware of what we consider to be ancient)
                rejectedEvents.add(event);
                iterator2.remove();
            }
        }

        assertTrue(
                eventDurabilityNexus.isEventDurable(eventsAfterDiscontinuity.get(eventsAfterDiscontinuity.size() - 1)));
        eventsAfterDiscontinuity.forEach(event -> assertTrue(eventDurabilityNexus.isEventDurable(event)));
        rejectedEvents.forEach(event -> assertFalse(eventDurabilityNexus.isEventDurable(event)));

        verifyStream(eventsBeforeDiscontinuity, platformContext, truncateLastFile ? 1 : 0);

        writer.closeCurrentMutableFile();
    }

    /**
     * In this test, increase the first non-ancient generation as events are added. When this happens, we
     * should never have to include more than the preferred number of events in each file.
     */
    @Test
    @DisplayName("Advance Non Ancient Generation Test")
    void advanceNonAncientGenerationTest() throws IOException {
        final List<GossipEvent> events = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        writer.beginStreamingNewEvents(new DoneStreamingPcesTrigger());

        final Set<GossipEvent> rejectedEvents = new HashSet<>();

        long minimumGenerationNonAncient = 0;
        for (final GossipEvent event : events) {
            sequencer.assignStreamSequenceNumber(event);

            passValueToDurabilityNexus(writer.writeEvent(event));
            assertFalse(eventDurabilityNexus.isEventDurable(event));

            time.tick(Duration.ofSeconds(1));

            if (event.getGeneration() < minimumGenerationNonAncient) {
                // This event is ancient and will have been rejected.
                rejectedEvents.add(event);
            }

            minimumGenerationNonAncient =
                    Math.max(minimumGenerationNonAncient, event.getGeneration() - generationsUntilAncient);
            passValueToDurabilityNexus(writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient));
        }

        // Remove the rejected events from the list
        events.removeIf(rejectedEvents::contains);

        events.forEach(event -> assertTrue(eventDurabilityNexus.isEventDurable(event)));
        rejectedEvents.forEach(event -> assertFalse(eventDurabilityNexus.isEventDurable(event)));
        verifyStream(events, platformContext, 0);

        // Advance the time so that all files are GC eligible according to the clock.
        time.tick(Duration.ofDays(1));

        // Prune old files.
        final long minimumGenerationToStore = events.get(events.size() - 1).getGeneration() / 2;
        writer.setMinimumGenerationToStore(minimumGenerationToStore);

        // We shouldn't see any files that are incapable of storing events above the minimum
        final PcesFileTracker pcesFiles = PcesFileReader.readFilesFromDisk(
                platformContext,
                TestRecycleBin.getInstance(),
                PcesUtilities.getDatabaseDirectory(platformContext, selfId),
                0,
                false);

        pcesFiles
                .getFileIterator(NO_MINIMUM_GENERATION, 0)
                .forEachRemaining(file -> assertTrue(file.getMaximumGeneration() >= minimumGenerationToStore));

        writer.closeCurrentMutableFile();

        // Since we were very careful to always advance the first non-ancient generation, we should
        // find lots of files with a minimum generation exceeding 0.
        boolean foundNonZeroMinimumGeneration = false;
        for (final Iterator<PcesFile> fileIterator = pcesFiles.getFileIterator(0, 0); fileIterator.hasNext(); ) {
            final PcesFile file = fileIterator.next();
            if (file.getMinimumGeneration() > 0) {
                foundNonZeroMinimumGeneration = true;
                break;
            }
        }
        assertTrue(foundNonZeroMinimumGeneration);
    }
}
