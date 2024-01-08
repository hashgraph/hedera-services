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
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.preconsensus.PcesFile;
import com.swirlds.platform.event.preconsensus.PcesFileIterator;
import com.swirlds.platform.event.preconsensus.PcesMutableFile;
import com.swirlds.platform.test.fixtures.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.fixtures.event.source.StandardEventSource;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    @DisplayName("Write Then Read Test")
    void writeThenReadTest() throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 100;

        final StandardGraphGenerator generator = new StandardGraphGenerator(
                random.nextLong(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource());

        final List<GossipEvent> events = new ArrayList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        long maximumGeneration = Long.MIN_VALUE;
        for (final GossipEvent event : events) {
            maximumGeneration = Math.max(maximumGeneration, event.getGeneration());
        }

        maximumGeneration += random.nextInt(0, 10);

        final PcesFile file = PcesFile.of(
                RandomUtils.randomInstant(random), random.nextInt(0, 100), 0, maximumGeneration, 0, testDirectory);

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

    @Test
    @DisplayName("Read Files After Minimum Test")
    void readFilesAfterMinimumTest() throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 100;

        final StandardGraphGenerator generator = new StandardGraphGenerator(
                random.nextLong(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource());

        final List<GossipEvent> events = new ArrayList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        long maximumGeneration = Long.MIN_VALUE;
        for (final GossipEvent event : events) {
            maximumGeneration = Math.max(maximumGeneration, event.getGeneration());
        }

        final long middle = maximumGeneration / 2;

        maximumGeneration += random.nextInt(0, 10);

        final PcesFile file = PcesFile.of(
                RandomUtils.randomInstant(random),
                random.nextInt(0, 100),
                0,
                maximumGeneration,
                maximumGeneration,
                testDirectory);

        final PcesMutableFile mutableFile = file.getMutableFile();
        for (final GossipEvent event : events) {
            mutableFile.writeEvent(event);
        }

        mutableFile.close();

        final IOIterator<GossipEvent> iterator = file.iterator(middle);
        final List<GossipEvent> deserializedEvents = new ArrayList<>();
        iterator.forEachRemaining(deserializedEvents::add);

        // We don't want any events with a generation less than the middle
        final Iterator<GossipEvent> it = events.iterator();
        while (it.hasNext()) {
            final GossipEvent event = it.next();
            if (event.getGeneration() < middle) {
                it.remove();
            }
        }

        assertEquals(events.size(), deserializedEvents.size());
        for (int i = 0; i < events.size(); i++) {
            assertEquals(events.get(i), deserializedEvents.get(i));
        }
    }

    @Test
    @DisplayName("Read Empty File Test")
    void readEmptyFileTest() throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final PcesFile file = PcesFile.of(
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
    @ValueSource(booleans = {true, false})
    @DisplayName("Truncated Event Test")
    void truncatedEventTest(final boolean truncateOnBoundary) throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 100;

        final StandardGraphGenerator generator = new StandardGraphGenerator(
                random.nextLong(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource());

        final List<GossipEvent> events = new ArrayList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        long maximumGeneration = Long.MIN_VALUE;
        for (final GossipEvent event : events) {
            maximumGeneration = Math.max(maximumGeneration, event.getGeneration());
        }

        maximumGeneration += random.nextInt(0, 10);

        final PcesFile file = PcesFile.of(
                RandomUtils.randomInstant(random),
                random.nextInt(0, 100),
                0,
                maximumGeneration,
                maximumGeneration,
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

    @Test
    @DisplayName("Corrupted Events Test")
    void corruptedEventsTest() throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 100;

        final StandardGraphGenerator generator = new StandardGraphGenerator(
                random.nextLong(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource());

        final List<GossipEvent> events = new ArrayList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        long maximumGeneration = Long.MIN_VALUE;
        for (final GossipEvent event : events) {
            maximumGeneration = Math.max(maximumGeneration, event.getGeneration());
        }

        maximumGeneration += random.nextInt(0, 10);

        final PcesFile file = PcesFile.of(
                RandomUtils.randomInstant(random), random.nextInt(0, 100), 0, maximumGeneration, 0, testDirectory);

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

    @Test
    @DisplayName("Write Invalid Event Test")
    void writeInvalidEventTest() throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed();

        final int numEvents = 100;

        final StandardGraphGenerator generator = new StandardGraphGenerator(
                random.nextLong(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource());

        final List<GossipEvent> events = new ArrayList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        long minimumGeneration = Long.MAX_VALUE;
        long maximumGeneration = Long.MIN_VALUE;
        for (final GossipEvent event : events) {
            minimumGeneration = Math.min(minimumGeneration, event.getGeneration());
            maximumGeneration = Math.max(maximumGeneration, event.getGeneration());
        }

        // Intentionally choose minimum and maximum generations that do not permit all generated events
        final long restrictedMinimumGeneration = minimumGeneration + (minimumGeneration + maximumGeneration) / 4;
        final long restrictedMaximumGeneration = maximumGeneration - (minimumGeneration + maximumGeneration) / 4;

        final PcesFile file = PcesFile.of(
                RandomUtils.randomInstant(random),
                random.nextInt(0, 100),
                restrictedMinimumGeneration,
                restrictedMaximumGeneration,
                0,
                testDirectory);
        final PcesMutableFile mutableFile = file.getMutableFile();

        final List<GossipEvent> validEvents = new ArrayList<>();
        for (final GossipEvent event : events) {
            if (event.getGeneration() >= restrictedMinimumGeneration
                    && event.getGeneration() <= restrictedMaximumGeneration) {
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

    @Test
    @DisplayName("Span Compression Test")
    void spanCompressionTest() throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed(0);

        final int numEvents = 100;

        final StandardGraphGenerator generator = new StandardGraphGenerator(
                random.nextLong(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource());

        final List<GossipEvent> events = new ArrayList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        long minimumGeneration = Long.MAX_VALUE;
        long maximumGeneration = Long.MIN_VALUE;
        for (final GossipEvent event : events) {
            minimumGeneration = Math.min(minimumGeneration, event.getGeneration());
            maximumGeneration = Math.max(maximumGeneration, event.getGeneration());
        }

        maximumGeneration += random.nextInt(1, 10);

        final PcesFile file = PcesFile.of(
                RandomUtils.randomInstant(random),
                random.nextInt(0, 100),
                minimumGeneration,
                maximumGeneration,
                0,
                testDirectory);

        final PcesMutableFile mutableFile = file.getMutableFile();
        for (final GossipEvent event : events) {
            mutableFile.writeEvent(event);
        }

        mutableFile.close();
        final PcesFile compressedFile = mutableFile.compressGenerationalSpan(0);

        assertEquals(file.getPath().getParent(), compressedFile.getPath().getParent());
        assertEquals(file.getSequenceNumber(), compressedFile.getSequenceNumber());
        assertEquals(file.getMinimumGeneration(), compressedFile.getMinimumGeneration());
        assertTrue(maximumGeneration > compressedFile.getMaximumGeneration());
        assertEquals(
                mutableFile.getUtilizedGenerationalSpan(),
                compressedFile.getMaximumGeneration() - compressedFile.getMinimumGeneration());
        assertNotEquals(file.getPath(), compressedFile.getPath());
        assertNotEquals(file.getMaximumGeneration(), compressedFile.getMaximumGeneration());
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

    @Test
    @DisplayName("Partial Span Compression Test")
    void partialSpanCompressionTest() throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed(0);

        final int numEvents = 100;

        final StandardGraphGenerator generator = new StandardGraphGenerator(
                random.nextLong(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource());

        final List<GossipEvent> events = new ArrayList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }

        long minimumEventGeneration = Long.MAX_VALUE;
        long maximumEventGeneration = Long.MIN_VALUE;
        for (final GossipEvent event : events) {
            minimumEventGeneration = Math.min(minimumEventGeneration, event.getGeneration());
            maximumEventGeneration = Math.max(maximumEventGeneration, event.getGeneration());
        }

        final long maximumFileGeneration = maximumEventGeneration + random.nextInt(10, 20);
        final long uncompressedSpan = 5;

        final PcesFile file = PcesFile.of(
                RandomUtils.randomInstant(random),
                random.nextInt(0, 100),
                minimumEventGeneration,
                maximumFileGeneration,
                0,
                testDirectory);

        final PcesMutableFile mutableFile = file.getMutableFile();
        for (final GossipEvent event : events) {
            mutableFile.writeEvent(event);
        }

        mutableFile.close();
        final PcesFile compressedFile = mutableFile.compressGenerationalSpan(maximumEventGeneration + uncompressedSpan);

        assertEquals(file.getPath().getParent(), compressedFile.getPath().getParent());
        assertEquals(file.getSequenceNumber(), compressedFile.getSequenceNumber());
        assertEquals(file.getMinimumGeneration(), compressedFile.getMinimumGeneration());
        assertEquals(maximumEventGeneration + uncompressedSpan, compressedFile.getMaximumGeneration());
        assertEquals(
                mutableFile.getUtilizedGenerationalSpan(),
                compressedFile.getMaximumGeneration() - compressedFile.getMinimumGeneration() - uncompressedSpan);
        assertNotEquals(file.getPath(), compressedFile.getPath());
        assertNotEquals(file.getMaximumGeneration(), compressedFile.getMaximumGeneration());
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

    @Test
    @DisplayName("Empty File Test")
    void emptyFileTest() throws IOException {
        final PcesFile file = PcesFile.of(Instant.now(), 0, 0, 100, 0, testDirectory);

        final Path path = file.getPath();

        Files.createDirectories(path.getParent());
        assertTrue(path.toFile().createNewFile());
        assertTrue(Files.exists(path));

        final PcesFileIterator iterator = file.iterator(Long.MIN_VALUE);
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }
}
