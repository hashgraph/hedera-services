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
import static com.swirlds.platform.event.AncientMode.BIRTH_ROUND_THRESHOLD;
import static com.swirlds.platform.event.AncientMode.GENERATION_THRESHOLD;
import static com.swirlds.platform.event.preconsensus.PcesFileManager.NO_LOWER_BOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.TestRecycleBin;
import com.swirlds.common.test.fixtures.TransactionGenerator;
import com.swirlds.common.test.fixtures.io.FileManipulation;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.config.TransactionConfig_;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.preconsensus.DefaultPcesSequencer;
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
import com.swirlds.platform.eventhandling.EventConfig_;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import com.swirlds.platform.test.fixtures.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.fixtures.event.source.StandardEventSource;
import com.swirlds.platform.wiring.DoneStreamingPcesTrigger;
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
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("PcesWriter Tests")
class PcesWriterTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    private final NodeId selfId = new NodeId(0);
    private final int numEvents = 1_000;

    protected static Stream<Arguments> buildArguments() {
        return Stream.of(Arguments.of(GENERATION_THRESHOLD), Arguments.of(BIRTH_ROUND_THRESHOLD));
    }

    /**
     * Perform verification on a stream written by a {@link PcesWriter}.
     *
     * @param events             the events that were written to the stream
     * @param platformContext    the platform context
     * @param truncatedFileCount the expected number of truncated files
     * @param ancientMode        the ancient mode
     */
    private void verifyStream(
            @NonNull final List<GossipEvent> events,
            @NonNull final PlatformContext platformContext,
            final int truncatedFileCount,
            @NonNull final AncientMode ancientMode)
            throws IOException {

        long lastAncientIdentifier = Long.MIN_VALUE;
        for (final GossipEvent event : events) {
            lastAncientIdentifier = Math.max(lastAncientIdentifier, event.getAncientIndicator(ancientMode));
        }

        final PcesFileTracker pcesFiles = PcesFileReader.readFilesFromDisk(
                platformContext,
                TestRecycleBin.getInstance(),
                PcesUtilities.getDatabaseDirectory(platformContext, selfId),
                0,
                false,
                ancientMode);

        // Verify that the events were written correctly
        final PcesMultiFileIterator eventsIterator = pcesFiles.getEventIterator(0, 0);
        for (final GossipEvent event : events) {
            assertTrue(eventsIterator.hasNext());
            assertEquals(event, eventsIterator.next());
        }

        assertFalse(eventsIterator.hasNext());
        assertEquals(truncatedFileCount, eventsIterator.getTruncatedFileCount());

        // Make sure things look good when iterating starting in the middle of the stream that was written
        final long startingLowerBound = lastAncientIdentifier / 2;
        final IOIterator<GossipEvent> eventsIterator2 = pcesFiles.getEventIterator(startingLowerBound, 0);
        for (final GossipEvent event : events) {
            if (event.getAncientIndicator(ancientMode) < startingLowerBound) {
                continue;
            }
            assertTrue(eventsIterator2.hasNext());
            assertEquals(event, eventsIterator2.next());
        }
        assertFalse(eventsIterator2.hasNext());

        // Iterating from a high ancient indicator should yield no events
        final IOIterator<GossipEvent> eventsIterator3 = pcesFiles.getEventIterator(lastAncientIdentifier + 1, 0);
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
            assertTrue(file.getLowerBound() <= file.getUpperBound());
            assertTrue(file.getLowerBound() >= previousMinimum);
            previousMinimum = file.getLowerBound();
            assertTrue(file.getUpperBound() >= previousMaximum);
            previousMaximum = file.getUpperBound();

            final IOIterator<GossipEvent> fileEvents = file.iterator(0);
            while (fileEvents.hasNext()) {
                final GossipEvent event = fileEvents.next();
                assertTrue(event.getAncientIndicator(ancientMode) >= file.getLowerBound());
                assertTrue(event.getAncientIndicator(ancientMode) <= file.getUpperBound());
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
    static StandardGraphGenerator buildGraphGenerator(
            @NonNull final PlatformContext platformContext, @NonNull final Random random) {
        Objects.requireNonNull(platformContext);
        final TransactionGenerator transactionGenerator = buildTransactionGenerator();

        return new StandardGraphGenerator(
                platformContext,
                random.nextLong(),
                new StandardEventSource().setTransactionGenerator(transactionGenerator),
                new StandardEventSource().setTransactionGenerator(transactionGenerator),
                new StandardEventSource().setTransactionGenerator(transactionGenerator),
                new StandardEventSource().setTransactionGenerator(transactionGenerator));
    }

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("");
        StaticSoftwareVersion.setSoftwareVersion(new BasicSoftwareVersion(1));
    }

    @AfterAll
    static void afterAll() {
        StaticSoftwareVersion.reset();
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

    @NonNull
    private PlatformContext buildContext(@NonNull final AncientMode ancientMode) {
        final Configuration configuration = new TestConfigBuilder()
                .withValue(PcesConfig_.DATABASE_DIRECTORY, testDirectory)
                .withValue(PcesConfig_.PREFERRED_FILE_SIZE_MEGABYTES, 5)
                .withValue(TransactionConfig_.MAX_TRANSACTION_BYTES_PER_EVENT, Integer.MAX_VALUE)
                .withValue(TransactionConfig_.MAX_TRANSACTION_COUNT_PER_EVENT, Integer.MAX_VALUE)
                .withValue(TransactionConfig_.TRANSACTION_MAX_BYTES, Integer.MAX_VALUE)
                .withValue(EventConfig_.USE_BIRTH_ROUND_ANCIENT_THRESHOLD, ancientMode == BIRTH_ROUND_THRESHOLD)
                .getOrCreateConfig();

        final Metrics metrics = new NoOpMetrics();

        return new DefaultPlatformContext(
                configuration, metrics, CryptographyHolder.get(), new FakeTime(Duration.ofMillis(1)));
    }

    /**
     * Pass the most recent durable sequence number to the durability nexus.
     * <p>
     * The intention of this method is to simply pass the return value from any writer call that returns a sequence
     * number. This simulates the components being wired together.
     *
     * @param mostRecentDurableSequenceNumber the most recent durable sequence number
     * @param eventDurabilityNexus            the event durability nexus
     */
    private static void passValueToDurabilityNexus(
            @Nullable final Long mostRecentDurableSequenceNumber,
            @NonNull final EventDurabilityNexus eventDurabilityNexus) {
        if (mostRecentDurableSequenceNumber != null) {
            eventDurabilityNexus.setLatestDurableSequenceNumber(mostRecentDurableSequenceNumber);
        }
    }

    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("Standard Operation Test")
    void standardOperationTest(@NonNull final AncientMode ancientMode) throws IOException {

        final Random random = RandomUtils.getRandomPrintSeed();

        final PlatformContext platformContext = buildContext(ancientMode);

        final StandardGraphGenerator generator = buildGraphGenerator(platformContext, random);
        final int stepsUntilAncient = random.nextInt(50, 100);
        final PcesSequencer sequencer = new DefaultPcesSequencer();
        final PcesFileTracker pcesFiles = new PcesFileTracker(ancientMode);

        final PcesFileManager fileManager = new PcesFileManager(platformContext, pcesFiles, selfId, 0);
        final PcesWriter writer = new PcesWriter(platformContext, fileManager);
        final EventDurabilityNexus eventDurabilityNexus = new EventDurabilityNexus();

        final List<GossipEvent> events = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEventWithoutIndex().getBaseEvent());
        }

        writer.beginStreamingNewEvents(new DoneStreamingPcesTrigger());

        final Collection<GossipEvent> rejectedEvents = new HashSet<>();

        long lowerBound = ancientMode.selectIndicator(0, 1);
        final Iterator<GossipEvent> iterator = events.iterator();
        while (iterator.hasNext()) {
            final GossipEvent event = iterator.next();

            sequencer.assignStreamSequenceNumber(event);
            passValueToDurabilityNexus(writer.writeEvent(event), eventDurabilityNexus);

            lowerBound = Math.max(lowerBound, event.getAncientIndicator(ancientMode) - stepsUntilAncient);

            writer.updateNonAncientEventBoundary(new NonAncientEventWindow(1, lowerBound, lowerBound, ancientMode));

            if (event.getAncientIndicator(ancientMode) < lowerBound) {
                // Although it's not common, it's possible that the generator will generate
                // an event that is ancient (since it isn't aware of what we consider to be ancient)
                rejectedEvents.add(event);
                iterator.remove();
            }

            // request a flush sometimes
            if (random.nextInt(10) == 0) {
                passValueToDurabilityNexus(
                        writer.submitFlushRequest(event.getStreamSequenceNumber()), eventDurabilityNexus);
            }
        }

        passValueToDurabilityNexus(
                writer.submitFlushRequest(events.getLast().getStreamSequenceNumber()), eventDurabilityNexus);

        events.forEach(event -> assertTrue(eventDurabilityNexus.isEventDurable(event)));
        rejectedEvents.forEach(event -> assertFalse(eventDurabilityNexus.isEventDurable(event)));

        verifyStream(events, platformContext, 0, ancientMode);

        writer.closeCurrentMutableFile();
    }

    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("Ancient Event Test")
    void ancientEventTest(@NonNull final AncientMode ancientMode) throws IOException {

        final Random random = RandomUtils.getRandomPrintSeed();

        final PlatformContext platformContext = buildContext(ancientMode);
        final StandardGraphGenerator generator = buildGraphGenerator(platformContext, random);

        final int stepsUntilAncient =
                ancientMode == GENERATION_THRESHOLD ? random.nextInt(50, 100) : random.nextInt(5, 10);
        final PcesSequencer sequencer = new DefaultPcesSequencer();
        final PcesFileTracker pcesFiles = new PcesFileTracker(ancientMode);

        final PcesFileManager fileManager = new PcesFileManager(platformContext, pcesFiles, selfId, 0);
        final PcesWriter writer = new PcesWriter(platformContext, fileManager);
        final EventDurabilityNexus eventDurabilityNexus = new EventDurabilityNexus();

        // We will add this event at the very end, it should be ancient by then
        final GossipEvent ancientEvent = generator.generateEventWithoutIndex().getBaseEvent();

        final List<GossipEvent> events = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEventWithoutIndex().getBaseEvent());
        }

        writer.beginStreamingNewEvents(new DoneStreamingPcesTrigger());

        final Collection<GossipEvent> rejectedEvents = new HashSet<>();

        long lowerBound = ancientMode.selectIndicator(0, 1);
        final Iterator<GossipEvent> iterator = events.iterator();
        while (iterator.hasNext()) {
            final GossipEvent event = iterator.next();

            sequencer.assignStreamSequenceNumber(event);
            passValueToDurabilityNexus(writer.writeEvent(event), eventDurabilityNexus);

            lowerBound = Math.max(lowerBound, event.getAncientIndicator(ancientMode) - stepsUntilAncient);

            writer.updateNonAncientEventBoundary(new NonAncientEventWindow(1, lowerBound, lowerBound, ancientMode));

            if (event.getAncientIndicator(ancientMode) < lowerBound) {
                // Although it's not common, it's actually possible that the generator will generate
                // an event that is ancient (since it isn't aware of what we consider to be ancient)
                rejectedEvents.add(event);
                iterator.remove();
            }

            // request a flush sometimes
            if (random.nextInt(10) == 0) {
                passValueToDurabilityNexus(
                        writer.submitFlushRequest(event.getStreamSequenceNumber()), eventDurabilityNexus);
            }
        }

        passValueToDurabilityNexus(
                writer.submitFlushRequest(events.getLast().getStreamSequenceNumber()), eventDurabilityNexus);

        // Add the ancient event
        sequencer.assignStreamSequenceNumber(ancientEvent);
        if (lowerBound > ancientEvent.getAncientIndicator(ancientMode)) {
            // This is probably not possible... but just in case make sure this event is ancient
            try {
                writer.updateNonAncientEventBoundary(new NonAncientEventWindow(
                        1,
                        ancientEvent.getAncientIndicator(ancientMode) + 1,
                        ancientEvent.getAncientIndicator(ancientMode) + 1,
                        ancientMode));
            } catch (final IllegalArgumentException e) {
                // ignore, more likely than not this event is way older than the actual ancient threshold
            }
        }

        passValueToDurabilityNexus(writer.writeEvent(ancientEvent), eventDurabilityNexus);
        rejectedEvents.add(ancientEvent);

        events.forEach(event -> assertTrue(eventDurabilityNexus.isEventDurable(event)));
        rejectedEvents.forEach(event -> assertFalse(eventDurabilityNexus.isEventDurable(event)));

        verifyStream(events, platformContext, 0, ancientMode);

        writer.closeCurrentMutableFile();
    }

    /**
     * In this test, keep adding events without increasing the first non-ancient threshold. This will force the
     * preferred span per file to eventually be reached and exceeded.
     */
    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("Overflow Test")
    void overflowTest(@NonNull final AncientMode ancientMode) throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final PlatformContext platformContext = buildContext(ancientMode);

        final StandardGraphGenerator generator = buildGraphGenerator(platformContext, random);
        final PcesSequencer sequencer = new DefaultPcesSequencer();
        final PcesFileTracker pcesFiles = new PcesFileTracker(ancientMode);

        final PcesFileManager fileManager = new PcesFileManager(platformContext, pcesFiles, selfId, 0);
        final PcesWriter writer = new PcesWriter(platformContext, fileManager);
        final EventDurabilityNexus eventDurabilityNexus = new EventDurabilityNexus();

        final List<GossipEvent> events = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEventWithoutIndex().getBaseEvent());
        }

        writer.beginStreamingNewEvents(new DoneStreamingPcesTrigger());

        for (final GossipEvent event : events) {
            sequencer.assignStreamSequenceNumber(event);
            passValueToDurabilityNexus(writer.writeEvent(event), eventDurabilityNexus);
        }

        passValueToDurabilityNexus(
                writer.submitFlushRequest(events.getLast().getStreamSequenceNumber()), eventDurabilityNexus);

        writer.closeCurrentMutableFile();

        verifyStream(events, platformContext, 0, ancientMode);

        // Without advancing the first non-ancient threshold,
        // we should never be able to increase the lower bound from 0.
        for (final Iterator<PcesFile> it = pcesFiles.getFileIterator(0, 0); it.hasNext(); ) {
            final PcesFile file = it.next();
            assertEquals(0, file.getLowerBound());
        }
    }

    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("beginStreamingEvents() Test")
    void beginStreamingEventsTest(@NonNull final AncientMode ancientMode) throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final PlatformContext platformContext = buildContext(ancientMode);

        final StandardGraphGenerator generator = buildGraphGenerator(platformContext, random);
        final int stepsUntilAncient = random.nextInt(50, 100);
        final PcesSequencer sequencer = new DefaultPcesSequencer();
        final PcesFileTracker pcesFiles = new PcesFileTracker(ancientMode);

        final PcesFileManager fileManager = new PcesFileManager(platformContext, pcesFiles, selfId, 0);
        final PcesWriter writer = new PcesWriter(platformContext, fileManager);
        final EventDurabilityNexus eventDurabilityNexus = new EventDurabilityNexus();

        final List<GossipEvent> events = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEventWithoutIndex().getBaseEvent());
        }

        // We intentionally do not call writer.beginStreamingNewEvents(). This should cause all events
        // passed into the writer to be more or less ignored.

        long lowerBound = ancientMode.selectIndicator(0, 1);
        for (final GossipEvent event : events) {
            sequencer.assignStreamSequenceNumber(event);
            passValueToDurabilityNexus(writer.writeEvent(event), eventDurabilityNexus);

            lowerBound = Math.max(lowerBound, event.getAncientIndicator(ancientMode) - stepsUntilAncient);
            writer.updateNonAncientEventBoundary(new NonAncientEventWindow(1, lowerBound, lowerBound, ancientMode));
        }

        assertTrue(eventDurabilityNexus.isEventDurable(events.getLast()));

        // We shouldn't find any events in the stream.
        assertFalse(() ->
                pcesFiles.getFileIterator(PcesFileManager.NO_LOWER_BOUND, 0).hasNext());

        writer.closeCurrentMutableFile();
    }

    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("Discontinuity Test")
    void discontinuityTest(@NonNull final AncientMode ancientMode) throws IOException {
        for (final boolean truncateLastFile : List.of(true, false)) {
            beforeEach();

            final Random random = RandomUtils.getRandomPrintSeed();

            final PlatformContext platformContext = buildContext(ancientMode);

            final StandardGraphGenerator generator = buildGraphGenerator(platformContext, random);
            final int stepsUntilAncient = random.nextInt(50, 100);
            final PcesSequencer sequencer = new DefaultPcesSequencer();
            final PcesFileTracker pcesFiles = new PcesFileTracker(ancientMode);

            final PcesFileManager fileManager = new PcesFileManager(platformContext, pcesFiles, selfId, 0);
            final PcesWriter writer = new PcesWriter(platformContext, fileManager);
            final EventDurabilityNexus eventDurabilityNexus = new EventDurabilityNexus();

            final List<GossipEvent> eventsBeforeDiscontinuity = new LinkedList<>();
            final List<GossipEvent> eventsAfterDiscontinuity = new LinkedList<>();
            for (int i = 0; i < numEvents; i++) {
                final GossipEvent event = generator.generateEventWithoutIndex().getBaseEvent();
                if (i < numEvents / 2) {
                    eventsBeforeDiscontinuity.add(event);
                } else {
                    eventsAfterDiscontinuity.add(event);
                }
            }

            writer.beginStreamingNewEvents(new DoneStreamingPcesTrigger());

            final Collection<GossipEvent> rejectedEvents = new HashSet<>();

            long lowerBound = ancientMode.selectIndicator(0, 1);
            final Iterator<GossipEvent> iterator1 = eventsBeforeDiscontinuity.iterator();
            while (iterator1.hasNext()) {
                final GossipEvent event = iterator1.next();

                sequencer.assignStreamSequenceNumber(event);
                passValueToDurabilityNexus(writer.writeEvent(event), eventDurabilityNexus);

                lowerBound = Math.max(lowerBound, event.getAncientIndicator(ancientMode) - stepsUntilAncient);
                writer.updateNonAncientEventBoundary(new NonAncientEventWindow(1, lowerBound, lowerBound, ancientMode));

                if (event.getAncientIndicator(ancientMode) < lowerBound) {
                    // Although it's not common, it's actually possible that the generator will generate
                    // an event that is ancient (since it isn't aware of what we consider to be ancient)
                    rejectedEvents.add(event);
                    iterator1.remove();
                }

                // request a flush sometimes
                if (random.nextInt(10) == 0) {
                    passValueToDurabilityNexus(
                            writer.submitFlushRequest(event.getStreamSequenceNumber()), eventDurabilityNexus);
                }
            }

            passValueToDurabilityNexus(writer.registerDiscontinuity(100), eventDurabilityNexus);
            eventsBeforeDiscontinuity.forEach(event -> assertTrue(eventDurabilityNexus.isEventDurable(event)));

            if (truncateLastFile) {
                // Remove a single byte from the last file. This will corrupt the last event that was written.
                final Iterator<PcesFile> it = pcesFiles.getFileIterator(NO_LOWER_BOUND, 0);
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
                passValueToDurabilityNexus(writer.writeEvent(event), eventDurabilityNexus);

                lowerBound = Math.max(lowerBound, event.getAncientIndicator(ancientMode) - stepsUntilAncient);
                writer.updateNonAncientEventBoundary(new NonAncientEventWindow(1, lowerBound, lowerBound, ancientMode));

                if (event.getAncientIndicator(ancientMode) < lowerBound) {
                    // Although it's not common, it's actually possible that the generator will generate
                    // an event that is ancient (since it isn't aware of what we consider to be ancient)
                    rejectedEvents.add(event);
                    iterator2.remove();
                }

                // request a flush sometimes
                if (random.nextInt(10) == 0) {
                    passValueToDurabilityNexus(
                            writer.submitFlushRequest(event.getStreamSequenceNumber()), eventDurabilityNexus);
                }
            }

            passValueToDurabilityNexus(
                    writer.submitFlushRequest(eventsAfterDiscontinuity.getLast().getStreamSequenceNumber()),
                    eventDurabilityNexus);

            assertTrue(eventDurabilityNexus.isEventDurable(
                    eventsAfterDiscontinuity.get(eventsAfterDiscontinuity.size() - 1)));
            eventsAfterDiscontinuity.forEach(event -> assertTrue(eventDurabilityNexus.isEventDurable(event)));
            rejectedEvents.forEach(event -> assertFalse(eventDurabilityNexus.isEventDurable(event)));

            verifyStream(eventsBeforeDiscontinuity, platformContext, truncateLastFile ? 1 : 0, ancientMode);

            writer.closeCurrentMutableFile();
        }
    }

    /**
     * In this test, increase the lower bound as events are added. When this happens, we should never have to include
     * more than the preferred number of events in each file.
     */
    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("Advance Non Ancient Boundary Test")
    void advanceNonAncientBoundaryTest(@NonNull final AncientMode ancientMode) throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final PlatformContext platformContext = buildContext(ancientMode);
        final FakeTime time = (FakeTime) platformContext.getTime();

        final StandardGraphGenerator generator = buildGraphGenerator(platformContext, random);
        final int stepsUntilAncient = random.nextInt(50, 100);
        final PcesSequencer sequencer = new DefaultPcesSequencer();
        final PcesFileTracker pcesFiles = new PcesFileTracker(ancientMode);

        final PcesFileManager fileManager = new PcesFileManager(platformContext, pcesFiles, selfId, 0);
        final PcesWriter writer = new PcesWriter(platformContext, fileManager);
        final EventDurabilityNexus eventDurabilityNexus = new EventDurabilityNexus();

        final List<GossipEvent> events = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEventWithoutIndex().getBaseEvent());
        }

        writer.beginStreamingNewEvents(new DoneStreamingPcesTrigger());

        final Set<GossipEvent> rejectedEvents = new HashSet<>();

        long lowerBound = ancientMode.selectIndicator(0, 1);
        for (final GossipEvent event : events) {
            sequencer.assignStreamSequenceNumber(event);

            passValueToDurabilityNexus(writer.writeEvent(event), eventDurabilityNexus);
            assertFalse(eventDurabilityNexus.isEventDurable(event));

            time.tick(Duration.ofSeconds(1));

            if (event.getAncientIndicator(ancientMode) < lowerBound) {
                // This event is ancient and will have been rejected.
                rejectedEvents.add(event);
            }

            lowerBound = Math.max(lowerBound, event.getAncientIndicator(ancientMode) - stepsUntilAncient);
            writer.updateNonAncientEventBoundary(new NonAncientEventWindow(1, lowerBound, lowerBound, ancientMode));

            // request a flush sometimes
            if (random.nextInt(10) == 0) {
                passValueToDurabilityNexus(
                        writer.submitFlushRequest(event.getStreamSequenceNumber()), eventDurabilityNexus);
            }
        }

        passValueToDurabilityNexus(
                writer.submitFlushRequest(events.getLast().getStreamSequenceNumber()), eventDurabilityNexus);

        // Remove the rejected events from the list
        events.removeIf(rejectedEvents::contains);

        events.forEach(event -> assertTrue(eventDurabilityNexus.isEventDurable(event)));
        rejectedEvents.forEach(event -> assertFalse(eventDurabilityNexus.isEventDurable(event)));
        verifyStream(events, platformContext, 0, ancientMode);

        // Advance the time so that all files are GC eligible according to the clock.
        time.tick(Duration.ofDays(1));

        // Prune old files.
        final long lowerBoundToStore = events.getLast().getAncientIndicator(ancientMode) / 2;
        writer.setMinimumAncientIdentifierToStore(lowerBoundToStore);

        // We shouldn't see any files that are incapable of storing events above the minimum
        final PcesFileTracker pcesFiles2 = PcesFileReader.readFilesFromDisk(
                platformContext,
                TestRecycleBin.getInstance(),
                PcesUtilities.getDatabaseDirectory(platformContext, selfId),
                0,
                false,
                ancientMode);

        pcesFiles2
                .getFileIterator(NO_LOWER_BOUND, 0)
                .forEachRemaining(file -> assertTrue(file.getUpperBound() >= lowerBoundToStore));

        writer.closeCurrentMutableFile();

        // Since we were very careful to always advance the first non-ancient threshold, we should
        // find lots of files with a lower bound exceeding 0.
        boolean foundNonZeroBoundary = false;
        for (final Iterator<PcesFile> fileIterator = pcesFiles2.getFileIterator(0, 0); fileIterator.hasNext(); ) {
            final PcesFile file = fileIterator.next();
            if (file.getLowerBound() > 0) {
                foundNonZeroBoundary = true;
                break;
            }
        }
        assertTrue(foundNonZeroBoundary);
    }

    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("Flush request test")
    void flushRequestTest(@NonNull final AncientMode ancientMode) throws IOException {
        final PlatformContext platformContext = buildContext(ancientMode);
        final PcesFileManager fileManager =
                new PcesFileManager(platformContext, new PcesFileTracker(ancientMode), selfId, 0);
        final PcesWriter writer = new PcesWriter(platformContext, fileManager);

        writer.beginStreamingNewEvents(new DoneStreamingPcesTrigger());

        final List<GossipEvent> events = new ArrayList<>();
        for (long i = 0; i < 9; i++) {
            final GossipEvent event = mock(GossipEvent.class);
            when(event.getStreamSequenceNumber()).thenReturn(i);
            events.add(event);
        }

        assertNull(writer.submitFlushRequest(1), "No event has been written to flush");
        assertEquals(
                1,
                writer.writeEvent(events.get(1)),
                "Writing an event with a sequence number already requested to flush should flush immediately");
        assertNull(
                writer.writeEvent(events.get(2)),
                "Writing an event with sequence number not requested to flush should not flush");
        assertEquals(
                2,
                writer.submitFlushRequest(2),
                "Requesting a flush for a sequence number already written should flush immediately");
        assertNull(writer.submitFlushRequest(4), "No event has been written to flush");
        assertNull(
                writer.writeEvent(events.get(3)),
                "Pending flush request for a later sequence number shouldn't cause a flush");
        assertNull(
                writer.submitFlushRequest(5), "New flush request for a later sequence number shouldn't cause a flush");
        assertEquals(
                5,
                writer.writeEvent(events.get(5)),
                "Intermediate flushes of a lower sequence number shouldn't hinder a later flush request");
        assertNull(writer.submitFlushRequest(6), "No event has been written to flush");
        assertNull(writer.submitFlushRequest(8), "No event has been written to flush");
        assertEquals(
                7,
                writer.writeEvent(events.get(7)),
                "Pending flush request for an earlier sequence number should cause a flush");
        assertEquals(
                8, writer.writeEvent(events.get(8)), "Flush requests for later sequences numbers should be maintained");
    }
}
