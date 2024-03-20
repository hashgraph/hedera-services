/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.common.test.fixtures.io.FileManipulation.corruptFile;
import static com.swirlds.common.test.fixtures.io.FileManipulation.truncateFile;
import static com.swirlds.platform.event.AncientMode.BIRTH_ROUND_THRESHOLD;
import static com.swirlds.platform.event.AncientMode.GENERATION_THRESHOLD;
import static com.swirlds.platform.test.consensus.ConsensusTestArgs.BIRTH_ROUND_PLATFORM_CONTEXT;
import static com.swirlds.platform.test.consensus.ConsensusTestArgs.DEFAULT_PLATFORM_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.preconsensus.PcesFile;
import com.swirlds.platform.event.preconsensus.PcesFileIterator;
import com.swirlds.platform.event.preconsensus.PcesMutableFile;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.test.fixtures.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.fixtures.event.source.StandardEventSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
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

@DisplayName("PCES Read Write Tests")
class PcesReadWriteTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

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

    protected static Stream<Arguments> buildArguments() {
        return Stream.of(Arguments.of(GENERATION_THRESHOLD), Arguments.of(BIRTH_ROUND_THRESHOLD));
    }

    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("Write Then Read Test")
    void writeThenReadTest(@NonNull final AncientMode ancientMode) throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 100;

        final StandardGraphGenerator generator = new StandardGraphGenerator(
                ancientMode == GENERATION_THRESHOLD ? DEFAULT_PLATFORM_CONTEXT : BIRTH_ROUND_PLATFORM_CONTEXT,
                random.nextLong(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource());

        final List<GossipEvent> events = new ArrayList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        long upperBound = Long.MIN_VALUE;
        for (final GossipEvent event : events) {
            upperBound = Math.max(upperBound, event.getAncientIndicator(ancientMode));
        }

        upperBound += random.nextInt(0, 10);

        final PcesFile file = PcesFile.of(
                ancientMode,
                RandomUtils.randomInstant(random),
                random.nextInt(0, 100),
                0,
                upperBound,
                0,
                testDirectory);

        final PcesMutableFile mutableFile = file.getMutableFile();
        for (final GossipEvent event : events) {
            mutableFile.writeEvent(event);
        }

        mutableFile.close();

        final IOIterator<GossipEvent> iterator = file.iterator(Long.MIN_VALUE);
        final List<GossipEvent> deserializedEvents = new ArrayList<>();
        iterator.forEachRemaining(deserializedEvents::add);
        assertEquals(events.size(), deserializedEvents.size());
        for (int i = 0; i < events.size(); i++) {
            assertEquals(events.get(i), deserializedEvents.get(i));
        }
    }

    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("Read Files After Minimum Test")
    void readFilesAfterMinimumTest(@NonNull final AncientMode ancientMode) throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 100;

        final StandardGraphGenerator generator = new StandardGraphGenerator(
                ancientMode == GENERATION_THRESHOLD ? DEFAULT_PLATFORM_CONTEXT : BIRTH_ROUND_PLATFORM_CONTEXT,
                random.nextLong(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource());

        final List<GossipEvent> events = new ArrayList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        long upperBound = Long.MIN_VALUE;
        for (final GossipEvent event : events) {
            upperBound = Math.max(upperBound, event.getAncientIndicator(ancientMode));
        }

        final long middle = upperBound / 2;

        upperBound += random.nextInt(0, 10);

        final PcesFile file = PcesFile.of(
                ancientMode,
                RandomUtils.randomInstant(random),
                random.nextInt(0, 100),
                0,
                upperBound,
                upperBound,
                testDirectory);

        final PcesMutableFile mutableFile = file.getMutableFile();
        for (final GossipEvent event : events) {
            mutableFile.writeEvent(event);
        }

        mutableFile.close();

        final IOIterator<GossipEvent> iterator = file.iterator(middle);
        final List<GossipEvent> deserializedEvents = new ArrayList<>();
        iterator.forEachRemaining(deserializedEvents::add);

        // We don't want any events with an ancient indicator less than the middle
        final Iterator<GossipEvent> it = events.iterator();
        while (it.hasNext()) {
            final GossipEvent event = it.next();
            if (event.getAncientIndicator(ancientMode) < middle) {
                it.remove();
            }
        }

        assertEquals(events.size(), deserializedEvents.size());
        for (int i = 0; i < events.size(); i++) {
            assertEquals(events.get(i), deserializedEvents.get(i));
        }
    }

    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("Read Empty File Test")
    void readEmptyFileTest(@NonNull final AncientMode ancientMode) throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final PcesFile file = PcesFile.of(
                ancientMode,
                RandomUtils.randomInstant(random),
                random.nextInt(0, 100),
                random.nextLong(0, 1000),
                random.nextLong(1000, 2000),
                0,
                testDirectory);

        final PcesMutableFile mutableFile = file.getMutableFile();
        mutableFile.close();

        final IOIterator<GossipEvent> iterator = file.iterator(Long.MIN_VALUE);
        assertFalse(iterator.hasNext());
    }

    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("Truncated Event Test")
    void truncatedEventTest(@NonNull final AncientMode ancientMode) throws IOException {
        for (final boolean truncateOnBoundary : List.of(true, false)) {
            final Random random = RandomUtils.getRandomPrintSeed();

            final int numEvents = 100;

            final StandardGraphGenerator generator = new StandardGraphGenerator(
                    ancientMode == GENERATION_THRESHOLD ? DEFAULT_PLATFORM_CONTEXT : BIRTH_ROUND_PLATFORM_CONTEXT,
                    random.nextLong(),
                    new StandardEventSource(),
                    new StandardEventSource(),
                    new StandardEventSource(),
                    new StandardEventSource());

            final List<GossipEvent> events = new ArrayList<>();
            for (int i = 0; i < numEvents; i++) {
                events.add(generator.generateEvent().getBaseEvent());
            }

            long upperBound = Long.MIN_VALUE;
            for (final GossipEvent event : events) {
                upperBound = Math.max(upperBound, event.getAncientIndicator(ancientMode));
            }

            upperBound += random.nextInt(0, 10);

            final PcesFile file = PcesFile.of(
                    ancientMode,
                    RandomUtils.randomInstant(random),
                    random.nextInt(0, 100),
                    0,
                    upperBound,
                    upperBound,
                    testDirectory);

            final Map<Integer /* event index */, Integer /* last byte position */> byteBoundaries = new HashMap<>();

            final PcesMutableFile mutableFile = file.getMutableFile();
            for (int i = 0; i < events.size(); i++) {
                final GossipEvent event = events.get(i);
                mutableFile.writeEvent(event);
                byteBoundaries.put(i, (int) mutableFile.fileSize());
            }

            mutableFile.close();

            final int lastEventIndex =
                    random.nextInt(0, events.size() - 2 /* make sure we always truncate at least one event */);

            final int truncationPosition = byteBoundaries.get(lastEventIndex) + (truncateOnBoundary ? 0 : 1);

            truncateFile(file.getPath(), truncationPosition);

            final PcesFileIterator iterator = file.iterator(Long.MIN_VALUE);
            final List<GossipEvent> deserializedEvents = new ArrayList<>();
            iterator.forEachRemaining(deserializedEvents::add);

            assertEquals(truncateOnBoundary, !iterator.hasPartialEvent());

            assertEquals(lastEventIndex + 1, deserializedEvents.size());

            for (int i = 0; i < deserializedEvents.size(); i++) {
                assertEquals(events.get(i), deserializedEvents.get(i));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("Corrupted Events Test")
    void corruptedEventsTest(@NonNull final AncientMode ancientMode) throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 100;

        final StandardGraphGenerator generator = new StandardGraphGenerator(
                ancientMode == GENERATION_THRESHOLD ? DEFAULT_PLATFORM_CONTEXT : BIRTH_ROUND_PLATFORM_CONTEXT,
                random.nextLong(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource());

        final List<GossipEvent> events = new ArrayList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        long upperBound = Long.MIN_VALUE;
        for (final GossipEvent event : events) {
            upperBound = Math.max(upperBound, event.getAncientIndicator(ancientMode));
        }

        upperBound += random.nextInt(0, 10);

        final PcesFile file = PcesFile.of(
                ancientMode,
                RandomUtils.randomInstant(random),
                random.nextInt(0, 100),
                0,
                upperBound,
                0,
                testDirectory);

        final Map<Integer /* event index */, Integer /* last byte position */> byteBoundaries = new HashMap<>();

        final PcesMutableFile mutableFile = file.getMutableFile();
        for (int i = 0; i < events.size(); i++) {
            final GossipEvent event = events.get(i);
            mutableFile.writeEvent(event);
            byteBoundaries.put(i, (int) mutableFile.fileSize());
        }

        mutableFile.close();

        final int lastEventIndex =
                random.nextInt(0, events.size() - 2 /* make sure we always corrupt at least one event */);

        final int corruptionPosition = byteBoundaries.get(lastEventIndex);

        corruptFile(random, file.getPath(), corruptionPosition);

        final PcesFileIterator iterator = file.iterator(Long.MIN_VALUE);

        for (int i = 0; i <= lastEventIndex; i++) {
            assertEquals(events.get(i), iterator.next());
        }

        assertThrows(IOException.class, iterator::next);
    }

    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("Write Invalid Event Test")
    void writeInvalidEventTest(@NonNull final AncientMode ancientMode) throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 100;

        final StandardGraphGenerator generator = new StandardGraphGenerator(
                ancientMode == GENERATION_THRESHOLD ? DEFAULT_PLATFORM_CONTEXT : BIRTH_ROUND_PLATFORM_CONTEXT,
                random.nextLong(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource());

        final List<GossipEvent> events = new ArrayList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        long lowerBound = Long.MAX_VALUE;
        long upperBound = Long.MIN_VALUE;
        for (final GossipEvent event : events) {
            lowerBound = Math.min(lowerBound, event.getAncientIndicator(ancientMode));
            upperBound = Math.max(upperBound, event.getAncientIndicator(ancientMode));
        }

        // Intentionally choose minimum and maximum boundaries that do not permit all generated events
        final long restrictedLowerBound = lowerBound + (lowerBound + upperBound) / 4;
        final long restrictedUpperBound = upperBound - (lowerBound + upperBound) / 4;

        final PcesFile file = PcesFile.of(
                ancientMode,
                RandomUtils.randomInstant(random),
                random.nextInt(0, 100),
                restrictedLowerBound,
                restrictedUpperBound,
                0,
                testDirectory);
        final PcesMutableFile mutableFile = file.getMutableFile();

        final List<GossipEvent> validEvents = new ArrayList<>();
        for (final GossipEvent event : events) {
            if (event.getAncientIndicator(ancientMode) >= restrictedLowerBound
                    && event.getAncientIndicator(ancientMode) <= restrictedUpperBound) {
                mutableFile.writeEvent(event);
                validEvents.add(event);
            } else {
                assertThrows(IllegalStateException.class, () -> mutableFile.writeEvent(event));
            }
        }

        mutableFile.close();

        final IOIterator<GossipEvent> iterator = file.iterator(Long.MIN_VALUE);
        for (final GossipEvent event : validEvents) {
            assertTrue(iterator.hasNext());
            assertEquals(event, iterator.next());
        }
        assertFalse(iterator.hasNext());
    }

    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("Span Compression Test")
    void spanCompressionTest(@NonNull final AncientMode ancientMode) throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed(0);

        final int numEvents = 100;

        final StandardGraphGenerator generator = new StandardGraphGenerator(
                ancientMode == GENERATION_THRESHOLD ? DEFAULT_PLATFORM_CONTEXT : BIRTH_ROUND_PLATFORM_CONTEXT,
                random.nextLong(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource());

        final List<GossipEvent> events = new ArrayList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        long lowerBound = Long.MAX_VALUE;
        long upperBound = Long.MIN_VALUE;
        for (final GossipEvent event : events) {
            lowerBound = Math.min(lowerBound, event.getAncientIndicator(ancientMode));
            upperBound = Math.max(upperBound, event.getAncientIndicator(ancientMode));
        }

        upperBound += random.nextInt(1, 10);

        final PcesFile file = PcesFile.of(
                ancientMode,
                RandomUtils.randomInstant(random),
                random.nextInt(0, 100),
                lowerBound,
                upperBound,
                0,
                testDirectory);

        final PcesMutableFile mutableFile = file.getMutableFile();
        for (final GossipEvent event : events) {
            mutableFile.writeEvent(event);
        }

        mutableFile.close();
        final PcesFile compressedFile = mutableFile.compressSpan(0);

        assertEquals(file.getPath().getParent(), compressedFile.getPath().getParent());
        assertEquals(file.getSequenceNumber(), compressedFile.getSequenceNumber());
        assertEquals(file.getLowerBound(), compressedFile.getLowerBound());
        assertTrue(upperBound > compressedFile.getUpperBound());
        assertEquals(mutableFile.getUtilizedSpan(), compressedFile.getUpperBound() - compressedFile.getLowerBound());
        assertNotEquals(file.getPath(), compressedFile.getPath());
        assertNotEquals(file.getUpperBound(), compressedFile.getUpperBound());
        assertTrue(Files.exists(compressedFile.getPath()));
        assertFalse(Files.exists(file.getPath()));

        final IOIterator<GossipEvent> iterator = compressedFile.iterator(Long.MIN_VALUE);
        final List<GossipEvent> deserializedEvents = new ArrayList<>();
        iterator.forEachRemaining(deserializedEvents::add);
        assertEquals(events.size(), deserializedEvents.size());
        for (int i = 0; i < events.size(); i++) {
            assertEquals(events.get(i), deserializedEvents.get(i));
        }
    }

    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("Partial Span Compression Test")
    void partialSpanCompressionTest(@NonNull final AncientMode ancientMode) throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed(0);

        final int numEvents = 100;

        final StandardGraphGenerator generator = new StandardGraphGenerator(
                ancientMode == GENERATION_THRESHOLD ? DEFAULT_PLATFORM_CONTEXT : BIRTH_ROUND_PLATFORM_CONTEXT,
                random.nextLong(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource());

        final List<GossipEvent> events = new ArrayList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        long lowerBound = Long.MAX_VALUE;
        long upperBound = Long.MIN_VALUE;
        for (final GossipEvent event : events) {
            lowerBound = Math.min(lowerBound, event.getAncientIndicator(ancientMode));
            upperBound = Math.max(upperBound, event.getAncientIndicator(ancientMode));
        }

        final long maximumFileBoundary = upperBound + random.nextInt(10, 20);
        final long uncompressedSpan = 5;

        final PcesFile file = PcesFile.of(
                ancientMode,
                RandomUtils.randomInstant(random),
                random.nextInt(0, 100),
                lowerBound,
                maximumFileBoundary,
                0,
                testDirectory);

        final PcesMutableFile mutableFile = file.getMutableFile();
        for (final GossipEvent event : events) {
            mutableFile.writeEvent(event);
        }

        mutableFile.close();
        final PcesFile compressedFile = mutableFile.compressSpan(upperBound + uncompressedSpan);

        assertEquals(file.getPath().getParent(), compressedFile.getPath().getParent());
        assertEquals(file.getSequenceNumber(), compressedFile.getSequenceNumber());
        assertEquals(file.getLowerBound(), compressedFile.getLowerBound());
        assertEquals(upperBound + uncompressedSpan, compressedFile.getUpperBound());
        assertEquals(
                mutableFile.getUtilizedSpan(),
                compressedFile.getUpperBound() - compressedFile.getLowerBound() - uncompressedSpan);
        assertNotEquals(file.getPath(), compressedFile.getPath());
        assertNotEquals(file.getUpperBound(), compressedFile.getUpperBound());
        assertTrue(Files.exists(compressedFile.getPath()));
        assertFalse(Files.exists(file.getPath()));

        final IOIterator<GossipEvent> iterator = compressedFile.iterator(Long.MIN_VALUE);
        final List<GossipEvent> deserializedEvents = new ArrayList<>();
        iterator.forEachRemaining(deserializedEvents::add);
        assertEquals(events.size(), deserializedEvents.size());
        for (int i = 0; i < events.size(); i++) {
            assertEquals(events.get(i), deserializedEvents.get(i));
        }
    }

    @ParameterizedTest
    @MethodSource("buildArguments")
    @DisplayName("Empty File Test")
    void emptyFileTest(@NonNull final AncientMode ancientMode) throws IOException {
        final PcesFile file = PcesFile.of(ancientMode, Instant.now(), 0, 0, 100, 0, testDirectory);

        final Path path = file.getPath();

        Files.createDirectories(path.getParent());
        assertTrue(path.toFile().createNewFile());
        assertTrue(Files.exists(path));

        final PcesFileIterator iterator = file.iterator(Long.MIN_VALUE);
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }
}
