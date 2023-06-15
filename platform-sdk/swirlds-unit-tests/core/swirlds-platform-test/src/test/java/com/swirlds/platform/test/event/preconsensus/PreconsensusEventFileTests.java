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

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.RandomUtils.randomInstant;
import static com.swirlds.common.test.io.FileManipulation.writeRandomBytes;
import static com.swirlds.platform.event.preconsensus.PreconsensusEventFile.EVENT_FILE_SEPARATOR;
import static com.swirlds.platform.event.preconsensus.PreconsensusEventFile.MAXIMUM_GENERATION_PREFIX;
import static com.swirlds.platform.event.preconsensus.PreconsensusEventFile.MINIMUM_GENERATION_PREFIX;
import static com.swirlds.platform.event.preconsensus.PreconsensusEventFile.SEQUENCE_NUMBER_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.event.preconsensus.PreconsensusEventFile;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("PreconsensusEventFile Tests")
class PreconsensusEventFileTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

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
    @DisplayName("Invalid Parameters Test")
    void invalidParametersTest() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PreconsensusEventFile.of(-1, 1, 2, Instant.now(), Path.of("foo"), false));

        assertThrows(
                IllegalArgumentException.class,
                () -> PreconsensusEventFile.of(1, -1, 2, Instant.now(), Path.of("foo"), false));

        assertThrows(
                IllegalArgumentException.class,
                () -> PreconsensusEventFile.of(1, -2, -1, Instant.now(), Path.of("foo"), false));

        assertThrows(
                IllegalArgumentException.class,
                () -> PreconsensusEventFile.of(1, 1, -1, Instant.now(), Path.of("foo"), false));

        assertThrows(
                IllegalArgumentException.class,
                () -> PreconsensusEventFile.of(1, 2, 1, Instant.now(), Path.of("foo"), false));

        assertThrows(NullPointerException.class, () -> PreconsensusEventFile.of(1, 1, 2, null, Path.of("foo"), false));

        assertThrows(NullPointerException.class, () -> PreconsensusEventFile.of(1, 1, 2, Instant.now(), null, false));
    }

    @Test
    @DisplayName("File Name Test")
    void fileNameTest() {
        final Random random = getRandomPrintSeed();

        int count = 100;
        while (count-- > 0) {
            final long sequenceNumber = random.nextLong(1000);
            final long minimumGeneration = random.nextLong(1000);
            final long maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + 1000);
            final Instant timestamp = RandomUtils.randomInstant(random);
            final boolean discontinuity = random.nextBoolean();

            final String expectedName =
                    timestamp.toString().replace(":", "+") + EVENT_FILE_SEPARATOR + SEQUENCE_NUMBER_PREFIX
                            + sequenceNumber + EVENT_FILE_SEPARATOR + MINIMUM_GENERATION_PREFIX
                            + minimumGeneration + EVENT_FILE_SEPARATOR + MAXIMUM_GENERATION_PREFIX
                            + maximumGeneration + ".pces" + (discontinuity ? "D" : "");

            final PreconsensusEventFile file = PreconsensusEventFile.of(
                    sequenceNumber, minimumGeneration, maximumGeneration, timestamp, Path.of("foo/bar"), discontinuity);

            assertEquals(expectedName, file.getFileName());
            assertEquals(expectedName, file.toString());
        }
    }

    @Test
    @DisplayName("File Path Test")
    void filePathTest() {
        final Random random = getRandomPrintSeed();

        int count = 100;
        while (count-- > 0) {

            final long sequenceNumber = random.nextLong(1000);
            final long minimumGeneration = random.nextLong(1000);
            final long maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + 1000);
            final Instant timestamp = RandomUtils.randomInstant(random);

            final ZonedDateTime zonedDateTime = timestamp.atZone(ZoneId.systemDefault());
            final int year = zonedDateTime.getYear();
            final int month = zonedDateTime.getMonthValue();
            final int day = zonedDateTime.getDayOfMonth();

            final Path expectedPath = Path.of(
                    "foo/bar", String.format("%04d", year), String.format("%02d", month), String.format("%02d", day));

            assertEquals(
                    expectedPath,
                    PreconsensusEventFile.of(
                                    sequenceNumber,
                                    minimumGeneration,
                                    maximumGeneration,
                                    timestamp,
                                    Path.of("foo/bar"),
                                    random.nextBoolean())
                            .getPath()
                            .getParent());
        }
    }

    @Test
    @DisplayName("Parsing Test")
    void parsingTest() throws IOException {
        final Random random = getRandomPrintSeed();

        int count = 100;
        while (count-- > 0) {
            final long sequenceNumber = random.nextLong(1000);
            final long minimumGeneration = random.nextLong(1000);
            final long maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + 1000);
            final Instant timestamp = RandomUtils.randomInstant(random);
            final boolean discontinuity = random.nextBoolean();

            final Path directory = Path.of("foo/bar/baz");

            final PreconsensusEventFile expected = PreconsensusEventFile.of(
                    sequenceNumber, minimumGeneration, maximumGeneration, timestamp, directory, discontinuity);

            final PreconsensusEventFile parsed = PreconsensusEventFile.of(expected.getPath());

            assertEquals(expected, parsed);
            assertEquals(sequenceNumber, parsed.getSequenceNumber());
            assertEquals(minimumGeneration, parsed.getMinimumGeneration());
            assertEquals(maximumGeneration, parsed.getMaximumGeneration());
            assertEquals(timestamp, parsed.getTimestamp());
            assertEquals(discontinuity, parsed.marksDiscontinuity());
        }
    }

    @Test
    @DisplayName("Invalid File Parsing Test")
    void invalidFileParsingTest() {
        // Invalid format
        assertThrows(IOException.class, () -> PreconsensusEventFile.of(Path.of(";laksjdf;laksjdf")));
        assertThrows(IOException.class, () -> PreconsensusEventFile.of(Path.of(".pces")));
        assertThrows(IOException.class, () -> PreconsensusEventFile.of(Path.of("foobar.txt")));
        assertThrows(IOException.class, () -> PreconsensusEventFile.of(Path.of("foobar.pces")));
        assertThrows(IOException.class, () -> PreconsensusEventFile.of(Path.of("foobar.pcesD")));
        assertThrows(
                IOException.class,
                () -> PreconsensusEventFile.of(Path.of("1997-0DERPT21+42+49.730Z_seq443_ming303_maxg884.pces")));
        assertThrows(
                IOException.class,
                () -> PreconsensusEventFile.of(Path.of("1997-02-16T21+42+49.730Z_seq4DERP3_ming303_maxg884.pces")));
        assertThrows(
                IOException.class,
                () -> PreconsensusEventFile.of(Path.of("1997-02-16T21+42+49.730Z_seq443_ming303_maxg8DERP4.pces")));

        // Valid format, invalid data
        assertThrows(
                IOException.class,
                () -> PreconsensusEventFile.of(Path.of("1997-02-16T21+42+49.730Z_seq443_ming884_maxg303.pces")));
    }

    @SuppressWarnings("resource")
    @Test
    @DisplayName("Deletion Test")
    void deletionTest() throws IOException {
        final Random random = getRandomPrintSeed();
        final Instant now = Instant.now();

        // When we start out, the test directory should be empty.
        assertEquals(0, Files.list(testDirectory).count());

        final List<Instant> times = new ArrayList<>();
        times.add(now);
        times.add(now.plus(Duration.ofMinutes(1)));
        times.add(now.plus(Duration.ofMinutes(2)));
        times.add(now.plus(Duration.ofMinutes(3)));
        times.add(now.plus(Duration.ofMinutes(4)));
        times.add(now.plus(Duration.ofMinutes(5)));
        times.add(now.plus(Duration.ofHours(1)));
        times.add(now.plus(Duration.ofDays(1)));
        times.add(now.plus(Duration.ofDays(40)));
        times.add(now.plus(Duration.ofDays(400)));

        final List<PreconsensusEventFile> files = new ArrayList<>();
        for (int index = 0; index < times.size(); index++) {
            final Instant timestamp = times.get(index);
            // We don't care about generations for this test
            final PreconsensusEventFile file =
                    PreconsensusEventFile.of(index, 0, 0, timestamp, testDirectory, random.nextBoolean());

            writeRandomBytes(random, file.getPath(), 100);
            files.add(file);
        }

        // Delete the files in a random order.
        Collections.shuffle(files, random);
        final Set<PreconsensusEventFile> deletedFiles = new HashSet<>();
        for (final PreconsensusEventFile file : files) {

            for (final PreconsensusEventFile fileToCheck : files) {
                if (deletedFiles.contains(fileToCheck)) {
                    assertFalse(Files.exists(fileToCheck.getPath()));
                } else {
                    assertTrue(Files.exists(fileToCheck.getPath()));
                }
            }

            file.deleteFile(testDirectory);

            if (random.nextBoolean()) {
                // Deleting twice shouldn't have any ill effects
                file.deleteFile(testDirectory);
            }

            deletedFiles.add(file);
        }

        // After all files have been deleted, the test directory should be empty again.
        assertEquals(0, Files.list(testDirectory).count());
    }

    @SuppressWarnings("resource")
    @Test
    @DisplayName("Recycle Test")
    void recycleTest() throws IOException {
        final Random random = getRandomPrintSeed();
        final Instant now = Instant.now();

        final Path streamDirectory = testDirectory.resolve("data");
        final NodeId selfId = new NodeId(0);
        final Path recycleDirectory = testDirectory.resolve("recycle");
        final Path actualRecycleDirectory = recycleDirectory.resolve(selfId.toString());

        final Configuration configuration = new TestConfigBuilder()
                .withValue("recycleBin.recycleBinPath", recycleDirectory.toString())
                .getOrCreateConfig();

        final RecycleBin recycleBin = RecycleBin.create(configuration, selfId);

        Files.createDirectories(streamDirectory);
        Files.createDirectories(recycleDirectory);

        // When we start out, the test directory should be empty.
        assertEquals(0, Files.list(streamDirectory).count());

        final List<Instant> times = new ArrayList<>();
        times.add(now);
        times.add(now.plus(Duration.ofMinutes(1)));
        times.add(now.plus(Duration.ofMinutes(2)));
        times.add(now.plus(Duration.ofMinutes(3)));
        times.add(now.plus(Duration.ofMinutes(4)));
        times.add(now.plus(Duration.ofMinutes(5)));
        times.add(now.plus(Duration.ofHours(1)));
        times.add(now.plus(Duration.ofDays(1)));
        times.add(now.plus(Duration.ofDays(40)));
        times.add(now.plus(Duration.ofDays(400)));

        final List<PreconsensusEventFile> files = new ArrayList<>();
        for (int index = 0; index < times.size(); index++) {
            final Instant timestamp = times.get(index);
            // We don't care about generations for this test
            final PreconsensusEventFile file =
                    PreconsensusEventFile.of(index, 0, 0, timestamp, streamDirectory, random.nextBoolean());

            writeRandomBytes(random, file.getPath(), 100);
            files.add(file);
        }

        // Delete the files in a random order.
        Collections.shuffle(files, random);
        final Set<PreconsensusEventFile> deletedFiles = new HashSet<>();
        for (final PreconsensusEventFile file : files) {

            for (final PreconsensusEventFile fileToCheck : files) {
                if (deletedFiles.contains(fileToCheck)) {
                    assertFalse(Files.exists(fileToCheck.getPath()));
                } else {
                    assertTrue(Files.exists(fileToCheck.getPath()));
                }
            }

            file.deleteFile(streamDirectory, recycleBin);

            if (random.nextBoolean()) {
                // Deleting twice shouldn't have any ill effects
                file.deleteFile(streamDirectory, recycleBin);
            }

            deletedFiles.add(file);
        }

        // After all files have been deleted, the test directory should be empty again.
        assertEquals(0, Files.list(streamDirectory).count());

        // All files should have been moved to the recycle directory
        for (final PreconsensusEventFile file : files) {
            assertTrue(
                    Files.exists(actualRecycleDirectory.resolve(file.getPath().getFileName())));
        }
    }

    @Test
    @DisplayName("compareTo() Test")
    void compareToTest() {
        final Random random = getRandomPrintSeed();

        final Path directory = Path.of("foo/bar/baz");

        for (int i = 0; i < 1000; i++) {
            final long sequenceA = random.nextLong(100);
            final long sequenceB = random.nextLong(100);

            final long minimumGenerationA = random.nextLong(100);
            final long minimumGenerationB = random.nextLong(100);

            final long maximumGenerationA = random.nextLong(minimumGenerationA, minimumGenerationA + 100);
            final long maximumGenerationB = random.nextLong(minimumGenerationB, minimumGenerationB + 100);

            final PreconsensusEventFile a = PreconsensusEventFile.of(
                    sequenceA,
                    minimumGenerationA,
                    maximumGenerationA,
                    randomInstant(random),
                    directory,
                    random.nextBoolean());
            final PreconsensusEventFile b = PreconsensusEventFile.of(
                    sequenceB,
                    minimumGenerationB,
                    maximumGenerationB,
                    randomInstant(random),
                    directory,
                    random.nextBoolean());

            assertEquals(Long.compare(sequenceA, sequenceB), a.compareTo(b));
        }
    }

    @Test
    @DisplayName("canContain() Test")
    void canContainTest() {
        final Random random = getRandomPrintSeed();

        final Path directory = Path.of("foo/bar/baz");

        for (int i = 0; i < 1000; i++) {
            final long sequenceNumber = random.nextLong(1000);
            final long minimumGeneration = random.nextLong(1000);
            final long maximumGeneration = random.nextLong(minimumGeneration + 1, minimumGeneration + 1000);
            final Instant timestamp = RandomUtils.randomInstant(random);

            final PreconsensusEventFile file = PreconsensusEventFile.of(
                    sequenceNumber, minimumGeneration, maximumGeneration, timestamp, directory, false);

            // An event with a sequence number that is too small
            assertFalse(file.canContain(minimumGeneration - random.nextLong(1, 100)));

            // An event with a sequence number matching the minimum exactly
            assertTrue(file.canContain(minimumGeneration));

            // An event with a sequence somewhere between the minimum and maximum
            assertTrue(file.canContain(maximumGeneration));

            // An event with a sequence somewhere exactly matching the maximum
            assertTrue(file.canContain(maximumGeneration));

            // An event with a sequence number that is too big
            assertFalse(file.canContain(maximumGeneration + random.nextLong(1, 100)));
        }
    }

    @Test
    @DisplayName("Discontinuity canContain() Test")
    void discontinuityCanContainTest() {
        final Random random = getRandomPrintSeed();

        final Path directory = Path.of("foo/bar/baz");

        for (int i = 0; i < 1000; i++) {
            final long sequenceNumber = random.nextLong(1000);
            final long minimumGeneration = random.nextLong(1000);
            final long maximumGeneration = random.nextLong(minimumGeneration + 1, minimumGeneration + 1000);
            final Instant timestamp = RandomUtils.randomInstant(random);

            final PreconsensusEventFile file = PreconsensusEventFile.of(
                    sequenceNumber, minimumGeneration, maximumGeneration, timestamp, directory, true);

            // An event with a sequence number that is too small
            assertFalse(file.canContain(minimumGeneration - random.nextLong(1, 100)));

            // An event with a sequence number matching the minimum exactly
            assertFalse(file.canContain(minimumGeneration));

            // An event with a sequence somewhere between the minimum and maximum
            assertFalse(file.canContain(maximumGeneration));

            // An event with a sequence somewhere exactly matching the maximum
            assertFalse(file.canContain(maximumGeneration));

            // An event with a sequence number that is too big
            assertFalse(file.canContain(maximumGeneration + random.nextLong(1, 100)));
        }
    }

    @Test
    @DisplayName("Span Compression Test")
    void spanCompressionTest() {
        final Random random = getRandomPrintSeed();

        final Path directory = Path.of("foo/bar/baz");

        final long sequenceNumber = random.nextLong(1000);
        final long minimumGeneration = random.nextLong(1000);
        final long maximumGeneration = random.nextLong(minimumGeneration + 5, minimumGeneration + 1000);
        final boolean discontinuity = random.nextBoolean();
        final Instant timestamp = randomInstant(random);

        final PreconsensusEventFile file = PreconsensusEventFile.of(
                sequenceNumber, minimumGeneration, maximumGeneration, timestamp, directory, discontinuity);

        assertThrows(IllegalArgumentException.class, () -> file.buildFileWithCompressedSpan(minimumGeneration - 1));
        assertThrows(IllegalArgumentException.class, () -> file.buildFileWithCompressedSpan(maximumGeneration + 1));

        final long newMaximumGeneration = random.nextLong(minimumGeneration, maximumGeneration);

        final PreconsensusEventFile compressedFile = file.buildFileWithCompressedSpan(newMaximumGeneration);

        assertEquals(sequenceNumber, compressedFile.getSequenceNumber());
        assertEquals(minimumGeneration, compressedFile.getMinimumGeneration());
        assertEquals(newMaximumGeneration, compressedFile.getMaximumGeneration());
        assertEquals(timestamp, compressedFile.getTimestamp());
        assertEquals(discontinuity, compressedFile.marksDiscontinuity());
    }
}
