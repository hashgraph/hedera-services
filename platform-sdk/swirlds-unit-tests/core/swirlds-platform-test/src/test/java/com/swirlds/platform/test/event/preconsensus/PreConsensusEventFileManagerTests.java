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

import static com.swirlds.common.test.AssertionUtils.assertIteratorEquality;
import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.common.test.metrics.NoOpMetrics;
import com.swirlds.common.time.OSTime;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.platform.event.preconsensus.PreConsensusEventFile;
import com.swirlds.platform.event.preconsensus.PreConsensusEventFileManager;
import com.swirlds.platform.event.preconsensus.PreConsensusEventStreamConfig;
import com.swirlds.platform.event.preconsensus.PreconsensusEventMetrics;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PreConsensusEventFileManager Tests")
class PreConsensusEventFileManagerTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    @BeforeEach
    void beforeEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
    }

    @AfterEach
    void afterEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
    }

    /**
     * Create a dummy file.
     *
     * @param descriptor
     * 		a description of the file
     */
    private void createDummyFile(final PreConsensusEventFile descriptor) throws IOException {
        final Path parentDir = descriptor.path().getParent();
        if (!Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        final SerializableDataOutputStream out = new SerializableDataOutputStream(
                new FileOutputStream(descriptor.path().toFile()));
        out.writeNormalisedString("foo bar baz");
        out.close();
    }

    private PreconsensusEventMetrics buildMetrics() {
        return new PreconsensusEventMetrics(new NoOpMetrics());
    }

    @Test
    @DisplayName("Maximum Less Than Minimum Test")
    void maximumLessThanMinimumTest() throws IOException {

        createDummyFile(PreConsensusEventFile.of(0, 0, 1, Instant.now(), testDirectory));

        createDummyFile(PreConsensusEventFile.of(1, 10, 5, Instant.now(), testDirectory));

        createDummyFile(PreConsensusEventFile.of(2, 10, 20, Instant.now(), testDirectory));

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        assertThrows(
                IllegalStateException.class,
                () -> new PreConsensusEventFileManager(OSTime.getInstance(), config, buildMetrics()));
    }

    @Test
    @DisplayName("Minimum Decreases Test")
    void minimumDecreasesTest() throws IOException {

        createDummyFile(PreConsensusEventFile.of(0, 5, 10, Instant.now(), testDirectory));

        createDummyFile(PreConsensusEventFile.of(1, 4, 11, Instant.now(), testDirectory));

        createDummyFile(PreConsensusEventFile.of(2, 10, 20, Instant.now(), testDirectory));

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        assertThrows(
                IllegalStateException.class,
                () -> new PreConsensusEventFileManager(OSTime.getInstance(), config, buildMetrics()));
    }

    @Test
    @DisplayName("Maximum Decreases Test")
    void maximumDecreasesTest() throws IOException {

        createDummyFile(PreConsensusEventFile.of(0, 5, 10, Instant.now(), testDirectory));

        createDummyFile(PreConsensusEventFile.of(1, 6, 9, Instant.now(), testDirectory));

        createDummyFile(PreConsensusEventFile.of(2, 10, 20, Instant.now(), testDirectory));

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        assertThrows(
                IllegalStateException.class,
                () -> new PreConsensusEventFileManager(OSTime.getInstance(), config, buildMetrics()));
    }

    @Test
    @DisplayName("Timestamp Decreases Test")
    void timestampDecreasesTest() throws IOException {

        createDummyFile(PreConsensusEventFile.of(0, 5, 10, Instant.ofEpochMilli(1000), testDirectory));

        createDummyFile(PreConsensusEventFile.of(1, 6, 11, Instant.ofEpochMilli(500), testDirectory));

        createDummyFile(PreConsensusEventFile.of(2, 7, 12, Instant.ofEpochMilli(2000), testDirectory));

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        assertThrows(
                IllegalStateException.class,
                () -> new PreConsensusEventFileManager(OSTime.getInstance(), config, buildMetrics()));
    }

    @Test
    @DisplayName("Read Files In Order Test")
    void readFilesInOrderTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PreConsensusEventFile> files = new ArrayList<>();

        // Intentionally pick values close to wrapping around the 3 digit to 4 digit sequence number.
        // This will cause the files not to line up alphabetically, and this is a scenario that the
        // code should be able to handle.
        final long firstSequenceNumber = random.nextLong(950, 1000);

        final long maxDelta = random.nextLong(10, 20);
        long minimumGeneration = random.nextLong(0, 1000);
        final long nonExistentGeneration = minimumGeneration - 1;
        long maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + maxDelta);
        Instant timestamp = Instant.now();

        for (long sequenceNumber = firstSequenceNumber;
                sequenceNumber < firstSequenceNumber + fileCount;
                sequenceNumber++) {

            final PreConsensusEventFile file = PreConsensusEventFile.of(
                    sequenceNumber, minimumGeneration, maximumGeneration, timestamp, testDirectory);

            minimumGeneration = random.nextLong(minimumGeneration, maximumGeneration + 1);
            maximumGeneration =
                    Math.max(maximumGeneration, random.nextLong(minimumGeneration, minimumGeneration + maxDelta));
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            files.add(file);
            createDummyFile(file);
        }

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        final PreConsensusEventFileManager manager =
                new PreConsensusEventFileManager(OSTime.getInstance(), config, buildMetrics());

        assertIteratorEquality(
                files.iterator(), manager.getFileIterator(PreConsensusEventFileManager.NO_MINIMUM_GENERATION, false));

        assertIteratorEquality(
                files.iterator(), manager.getFileIterator(PreConsensusEventFileManager.NO_MINIMUM_GENERATION, true));

        // attempt to start a non-existent generation
        assertIteratorEquality(files.iterator(), manager.getFileIterator(nonExistentGeneration, false));

        assertThrows(IllegalStateException.class, () -> manager.getFileIterator(nonExistentGeneration, true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Read Files In Order Gap Test")
    void readFilesInOrderGapTest(final boolean permitGaps) throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PreConsensusEventFile> files = new ArrayList<>();

        // Intentionally pick values close to wrapping around the 3 digit to 4 digit sequence number.
        // This will cause the files not to line up alphabetically, and this is a scenario that the
        // code should be able to handle.
        final long firstSequenceNumber = random.nextLong(950, 1000);

        final long maxDelta = random.nextLong(10, 20);
        long minimumGeneration = random.nextLong(0, 1000);
        long maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + maxDelta);
        Instant timestamp = Instant.now();

        // Skip the file right in the middle of the sequence
        final long sequenceNumberToSkip = (2 * firstSequenceNumber + fileCount) / 2;

        for (long sequenceNumber = firstSequenceNumber;
                sequenceNumber < firstSequenceNumber + fileCount;
                sequenceNumber++) {

            final PreConsensusEventFile file = PreConsensusEventFile.of(
                    sequenceNumber, minimumGeneration, maximumGeneration, timestamp, testDirectory);

            minimumGeneration = random.nextLong(minimumGeneration, maximumGeneration + 1);
            maximumGeneration =
                    Math.max(maximumGeneration, random.nextLong(minimumGeneration, minimumGeneration + maxDelta));
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            if (sequenceNumber == sequenceNumberToSkip) {
                // Intentionally don't write a file
                continue;
            }

            files.add(file);
            createDummyFile(file);
        }

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .withValue("event.preconsensus.permitGaps", permitGaps)
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        if (permitGaps) {
            // Gaps are allowed. We should see all files except for the one that was skipped.
            final PreConsensusEventFileManager manager =
                    new PreConsensusEventFileManager(OSTime.getInstance(), config, buildMetrics());

            assertIteratorEquality(
                    files.iterator(), manager.getFileIterator(PreConsensusEventFileManager.NO_MINIMUM_GENERATION));
        } else {
            // Gaps are not allowed.
            assertThrows(
                    IllegalStateException.class,
                    () -> new PreConsensusEventFileManager(OSTime.getInstance(), config, buildMetrics()));
        }
    }

    @Test
    @DisplayName("Read Files From Middle Test")
    void readFilesFromMiddleTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PreConsensusEventFile> files = new ArrayList<>();

        // Intentionally pick values close to wrapping around the 3 digit to 4 digit sequence number.
        // This will cause the files not to line up alphabetically, and this is a scenario that the
        // code should be able to handle.
        final long firstSequenceNumber = random.nextLong(950, 1000);

        final long maxDelta = random.nextLong(10, 20);
        long minimumGeneration = random.nextLong(0, 1000);
        long maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + maxDelta);
        Instant timestamp = Instant.now();

        for (long sequenceNumber = firstSequenceNumber;
                sequenceNumber < firstSequenceNumber + fileCount;
                sequenceNumber++) {

            final PreConsensusEventFile file = PreConsensusEventFile.of(
                    sequenceNumber, minimumGeneration, maximumGeneration, timestamp, testDirectory);

            minimumGeneration = random.nextLong(minimumGeneration, maximumGeneration + 1);
            maximumGeneration =
                    Math.max(maximumGeneration, random.nextLong(minimumGeneration, minimumGeneration + maxDelta));
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            files.add(file);
            createDummyFile(file);
        }

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        final PreConsensusEventFileManager manager =
                new PreConsensusEventFileManager(OSTime.getInstance(), config, buildMetrics());

        // For this test, we want to iterate over files so that we are guaranteed to observe every event
        // with a generation greater than or equal to the target generation. Choose a generation that falls
        // roughly in the middle of the sequence of files.
        final long targetGeneration =
                (files.get(0).maximumGeneration() + files.get(fileCount - 1).maximumGeneration()) / 2;

        final List<PreConsensusEventFile> iteratedFiles = new ArrayList<>();
        manager.getFileIterator(targetGeneration).forEachRemaining(iteratedFiles::add);

        // Find the index in the file list that was returned first by the iterator
        int indexOfFirstFile = 0;
        for (; indexOfFirstFile < fileCount; indexOfFirstFile++) {
            if (files.get(indexOfFirstFile).equals(iteratedFiles.get(0))) {
                break;
            }
        }

        // The file immediately before the returned file should not contain any targeted events
        assertTrue(files.get(indexOfFirstFile - 1).maximumGeneration() < targetGeneration);

        // The first file returned from the iterator should
        // have a maximum generation greater than or equal to the target generation.
        assertTrue(iteratedFiles.get(0).maximumGeneration() >= targetGeneration);

        // Make sure that the iterator returns files in the correct order.
        final List<PreConsensusEventFile> expectedFiles = new ArrayList<>(iteratedFiles.size());
        for (int index = indexOfFirstFile; index < fileCount; index++) {
            expectedFiles.add(files.get(index));
        }
        assertIteratorEquality(expectedFiles.iterator(), iteratedFiles.iterator());
    }

    /**
     * Similar to the other test that starts iteration in the middle, except that files will have the same
     * generational bounds with high probability. Not a scenario we are likely to encounter in production,
     * but it's a tricky edge case we need to handle elegantly.
     */
    @Test
    @DisplayName("Read Files From Middle Repeating Generations Test")
    void readFilesFromMiddleRepeatingGenerationsTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PreConsensusEventFile> files = new ArrayList<>();

        // Intentionally pick values close to wrapping around the 3 digit to 4 digit sequence number.
        // This will cause the files not to line up alphabetically, and this is a scenario that the
        // code should be able to handle.
        final long firstSequenceNumber = random.nextLong(950, 1000);

        final long maxDelta = random.nextLong(10, 20);
        long minimumGeneration = random.nextLong(0, 1000);
        long maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + maxDelta);
        Instant timestamp = Instant.now();

        for (long sequenceNumber = firstSequenceNumber;
                sequenceNumber < firstSequenceNumber + fileCount;
                sequenceNumber++) {

            final PreConsensusEventFile file = PreConsensusEventFile.of(
                    sequenceNumber, minimumGeneration, maximumGeneration, timestamp, testDirectory);

            // Advance the generation bounds only 10% of the time
            if (random.nextLong() < 0.1) {
                minimumGeneration = random.nextLong(minimumGeneration, maximumGeneration + 1);
                maximumGeneration =
                        Math.max(maximumGeneration, random.nextLong(minimumGeneration, minimumGeneration + maxDelta));
            }
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            files.add(file);
            createDummyFile(file);
        }
        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        final PreConsensusEventFileManager manager =
                new PreConsensusEventFileManager(OSTime.getInstance(), config, buildMetrics());

        // For this test, we want to iterate over files so that we are guaranteed to observe every event
        // with a generation greater than or equal to the target generation. Choose a generation that falls
        // roughly in the middle of the sequence of files.
        final long targetGeneration =
                (files.get(0).maximumGeneration() + files.get(fileCount - 1).maximumGeneration()) / 2;

        final List<PreConsensusEventFile> iteratedFiles = new ArrayList<>();
        manager.getFileIterator(targetGeneration).forEachRemaining(iteratedFiles::add);

        // Find the index in the file list that was returned first by the iterator
        int indexOfFirstFile = 0;
        for (; indexOfFirstFile < fileCount; indexOfFirstFile++) {
            if (files.get(indexOfFirstFile).equals(iteratedFiles.get(0))) {
                break;
            }
        }

        // The file immediately before the returned file should not contain any targeted events
        assertTrue(files.get(indexOfFirstFile - 1).maximumGeneration() < targetGeneration);

        // The first file returned from the iterator should
        // have a maximum generation greater than or equal to the target generation.
        assertTrue(iteratedFiles.get(0).maximumGeneration() >= targetGeneration);

        // Make sure that the iterator returns files in the correct order.
        final List<PreConsensusEventFile> expectedFiles = new ArrayList<>(iteratedFiles.size());
        for (int index = indexOfFirstFile; index < fileCount; index++) {
            expectedFiles.add(files.get(index));
        }
        assertIteratorEquality(expectedFiles.iterator(), iteratedFiles.iterator());
    }

    @Test
    @DisplayName("Read Files From High Generation Test")
    void readFilesFromHighGeneration() throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PreConsensusEventFile> files = new ArrayList<>();

        // Intentionally pick values close to wrapping around the 3 digit to 4 digit sequence number.
        // This will cause the files not to line up alphabetically, and this is a scenario that the
        // code should be able to handle.
        final long firstSequenceNumber = random.nextLong(950, 1000);

        final long maxDelta = random.nextLong(10, 20);
        long minimumGeneration = random.nextLong(0, 1000);
        long maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + maxDelta);
        Instant timestamp = Instant.now();

        for (long sequenceNumber = firstSequenceNumber;
                sequenceNumber < firstSequenceNumber + fileCount;
                sequenceNumber++) {

            final PreConsensusEventFile file = PreConsensusEventFile.of(
                    sequenceNumber, minimumGeneration, maximumGeneration, timestamp, testDirectory);

            minimumGeneration = random.nextLong(minimumGeneration, maximumGeneration + 1);
            maximumGeneration =
                    Math.max(maximumGeneration, random.nextLong(minimumGeneration, minimumGeneration + maxDelta));
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            files.add(file);
            createDummyFile(file);
        }

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        final PreConsensusEventFileManager manager =
                new PreConsensusEventFileManager(OSTime.getInstance(), config, buildMetrics());

        // Request a generation higher than all files in the data store
        final long targetGeneration = files.get(fileCount - 1).maximumGeneration() + 1;

        final Iterator<PreConsensusEventFile> iterator = manager.getFileIterator(targetGeneration);

        assertFalse(iterator.hasNext());
    }

    @Test
    @DisplayName("Generate Descriptors With Manager Test")
    void generateDescriptorsWithManagerTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PreConsensusEventFile> files = new ArrayList<>();

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        final long maxDelta = random.nextLong(10, 20);
        long minimumGeneration = random.nextLong(0, 1000);
        long maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + maxDelta);
        Instant timestamp = Instant.now();

        final PreConsensusEventFileManager generatingManager =
                new PreConsensusEventFileManager(OSTime.getInstance(), config, buildMetrics());
        for (int i = 0; i < fileCount; i++) {

            final PreConsensusEventFile file =
                    generatingManager.getNextFileDescriptor(minimumGeneration, maximumGeneration);

            assertTrue(file.minimumGeneration() >= minimumGeneration);
            assertTrue(file.maximumGeneration() >= maximumGeneration);

            // Intentionally allow generations to be chosen that may not be legal (i.e. a generation decreases)
            minimumGeneration = random.nextLong(minimumGeneration - 1, maximumGeneration + 1);
            maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + maxDelta);
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            files.add(file);
            createDummyFile(file);
        }

        final PreConsensusEventFileManager manager =
                new PreConsensusEventFileManager(OSTime.getInstance(), config, buildMetrics());

        assertIteratorEquality(
                files.iterator(), manager.getFileIterator(PreConsensusEventFileManager.NO_MINIMUM_GENERATION));
    }

    @Test
    @DisplayName("Incremental Pruning By Generation Test")
    void incrementalPruningByGenerationTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PreConsensusEventFile> files = new ArrayList<>();

        // Intentionally pick values close to wrapping around the 3 digit to 4 digit sequence number.
        // This will cause the files not to line up alphabetically, and this is a scenario that the
        // code should be able to handle.
        final long firstSequenceNumber = random.nextLong(950, 1000);

        final long maxDelta = random.nextLong(10, 20);
        long minimumGeneration = random.nextLong(0, 1000);
        long maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + maxDelta);
        Instant timestamp = Instant.now();

        for (long sequenceNumber = firstSequenceNumber;
                sequenceNumber < firstSequenceNumber + fileCount;
                sequenceNumber++) {

            final PreConsensusEventFile file = PreConsensusEventFile.of(
                    sequenceNumber, minimumGeneration, maximumGeneration, timestamp, testDirectory);

            minimumGeneration = random.nextLong(minimumGeneration, maximumGeneration + 1);
            maximumGeneration =
                    Math.max(maximumGeneration, random.nextLong(minimumGeneration, minimumGeneration + maxDelta));
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            files.add(file);
            createDummyFile(file);
        }

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .withValue("event.preconsensus.minimumRetentionPeriod", "1h")
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        // Choose a file in the middle. The goal is to incrementally purge all files before this file, but not
        // to purge this file or any of the ones after it.
        final int middleFileIndex = fileCount / 2;

        final PreConsensusEventFile firstFile = files.get(0);
        final PreConsensusEventFile middleFile = files.get(middleFileIndex);
        final PreConsensusEventFile lastFile = files.get(files.size() - 1);

        // Set the far in the future, we want all files to be GC eligible by temporal reckoning.
        final FakeTime time = new FakeTime(lastFile.timestamp().plus(Duration.ofHours(1)), Duration.ZERO);

        final PreConsensusEventFileManager manager = new PreConsensusEventFileManager(time, config, buildMetrics());

        assertIteratorEquality(
                files.iterator(), manager.getFileIterator(PreConsensusEventFileManager.NO_MINIMUM_GENERATION));

        // Increase the pruned generation a little at a time,
        // until the middle file is almost GC eligible but not quite.
        for (long generation = firstFile.maximumGeneration() - 100;
                generation <= middleFile.maximumGeneration();
                generation++) {

            manager.pruneOldFiles(generation);

            // Parse files with a new manager to make sure we aren't "cheating" by just
            // removing the in-memory descriptor without also removing the file on disk
            final List<PreConsensusEventFile> parsedFiles = new ArrayList<>();
            new PreConsensusEventFileManager(OSTime.getInstance(), config, buildMetrics())
                    .getFileIterator(PreConsensusEventFileManager.NO_MINIMUM_GENERATION)
                    .forEachRemaining(parsedFiles::add);

            final PreConsensusEventFile firstUnPrunedFile = parsedFiles.get(0);

            int firstUnPrunedIndex = -1;
            for (int index = 0; index < files.size(); index++) {
                if (files.get(index).equals(firstUnPrunedFile)) {
                    firstUnPrunedIndex = index;
                    break;
                }
            }

            // Check the first file that wasn't pruned
            assertTrue(firstUnPrunedIndex <= middleFileIndex);
            if (firstUnPrunedIndex < middleFileIndex) {
                assertTrue(firstUnPrunedFile.maximumGeneration() >= generation);
            }

            // Check the file right before the first un-pruned file.
            if (firstUnPrunedIndex > 0) {
                final PreConsensusEventFile lastPrunedFile = files.get(firstUnPrunedIndex - 1);
                assertTrue(lastPrunedFile.maximumGeneration() < generation);
            }

            // Check all remaining files to make sure we didn't accidentally delete something from the end
            final List<PreConsensusEventFile> expectedFiles = new ArrayList<>();
            for (int index = firstUnPrunedIndex; index < fileCount; index++) {
                expectedFiles.add(files.get(index));
            }
            assertEquals(expectedFiles, parsedFiles);
        }

        // Now, prune files so that the middle file is no longer needed.
        manager.pruneOldFiles(middleFile.maximumGeneration() + 1);

        // Parse files with a new manager to make sure we aren't "cheating" by just
        // removing the in-memory descriptor without also removing the file on disk
        final List<PreConsensusEventFile> parsedFiles = new ArrayList<>();
        new PreConsensusEventFileManager(OSTime.getInstance(), config, buildMetrics())
                .getFileIterator(PreConsensusEventFileManager.NO_MINIMUM_GENERATION)
                .forEachRemaining(parsedFiles::add);

        final PreConsensusEventFile firstUnPrunedFile = parsedFiles.get(0);

        int firstUnPrunedIndex = -1;
        for (int index = 0; index < files.size(); index++) {
            if (files.get(index).equals(firstUnPrunedFile)) {
                firstUnPrunedIndex = index;
                break;
            }
        }

        assertEquals(middleFileIndex + 1, firstUnPrunedIndex);
    }

    @Test
    @DisplayName("Incremental Pruning By Timestamp Test")
    void incrementalPruningByTimestampTest() throws IOException {
        final Random random = getRandomPrintSeed(0);

        final int fileCount = 100;

        final List<PreConsensusEventFile> files = new ArrayList<>();

        // Intentionally pick values close to wrapping around the 3 digit to 4 digit sequence number.
        // This will cause the files not to line up alphabetically, and this is a scenario that the
        // code should be able to handle.
        final long firstSequenceNumber = random.nextLong(950, 1000);

        final long maxDelta = random.nextLong(10, 20);
        long minimumGeneration = random.nextLong(0, 1000);
        long maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + maxDelta);
        Instant timestamp = Instant.now();

        for (long sequenceNumber = firstSequenceNumber;
                sequenceNumber < firstSequenceNumber + fileCount;
                sequenceNumber++) {

            final PreConsensusEventFile file = PreConsensusEventFile.of(
                    sequenceNumber, minimumGeneration, maximumGeneration, timestamp, testDirectory);

            minimumGeneration = random.nextLong(minimumGeneration, maximumGeneration + 1);
            maximumGeneration =
                    Math.max(maximumGeneration, random.nextLong(minimumGeneration, minimumGeneration + maxDelta));
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            files.add(file);
            createDummyFile(file);
        }

        final PreConsensusEventStreamConfig config = new TestConfigBuilder()
                .withValue("event.preconsensus.databaseDirectory", testDirectory)
                .withValue("event.preconsensus.minimumRetentionPeriod", "1h")
                .getOrCreateConfig()
                .getConfigData(PreConsensusEventStreamConfig.class);

        // Choose a file in the middle. The goal is to incrementally purge all files before this file, but not
        // to purge this file or any of the ones after it.
        final int middleFileIndex = fileCount / 2;

        final PreConsensusEventFile firstFile = files.get(0);
        final PreConsensusEventFile middleFile = files.get(middleFileIndex);
        final PreConsensusEventFile lastFile = files.get(files.size() - 1);

        // Set the clock before the first file is not garbage collection eligible
        final FakeTime time = new FakeTime(firstFile.timestamp().plus(Duration.ofMinutes(59)), Duration.ZERO);

        final PreConsensusEventFileManager manager = new PreConsensusEventFileManager(time, config, buildMetrics());

        assertIteratorEquality(
                files.iterator(), manager.getFileIterator(PreConsensusEventFileManager.NO_MINIMUM_GENERATION));

        // Increase the timestamp a little at a time. We should gradually delete files up until
        // all files before the middle file have been deleted.
        final Instant endingTime =
                middleFile.timestamp().plus(Duration.ofMinutes(60).minus(Duration.ofNanos(1)));
        while (time.now().isBefore(endingTime)) {
            manager.pruneOldFiles(lastFile.maximumGeneration() + 1);

            // Parse files with a new manager to make sure we aren't "cheating" by just
            // removing the in-memory descriptor without also removing the file on disk
            final List<PreConsensusEventFile> parsedFiles = new ArrayList<>();
            new PreConsensusEventFileManager(OSTime.getInstance(), config, buildMetrics())
                    .getFileIterator(PreConsensusEventFileManager.NO_MINIMUM_GENERATION)
                    .forEachRemaining(parsedFiles::add);

            final PreConsensusEventFile firstUnPrunedFile = parsedFiles.get(0);

            int firstUnPrunedIndex = -1;
            for (int index = 0; index < files.size(); index++) {
                if (files.get(index).equals(firstUnPrunedFile)) {
                    firstUnPrunedIndex = index;
                    break;
                }
            }

            // Check the first file that wasn't pruned
            assertTrue(firstUnPrunedIndex <= middleFileIndex);
            if (firstUnPrunedIndex < middleFileIndex
                    && files.get(firstUnPrunedIndex).maximumGeneration() < middleFile.maximumGeneration()) {
                assertTrue(CompareTo.isGreaterThanOrEqualTo(
                        firstUnPrunedFile.timestamp().plus(Duration.ofHours(1)), time.now()));
            }

            // Check the file right before the first un-pruned file.
            if (firstUnPrunedIndex > 0) {
                final PreConsensusEventFile lastPrunedFile = files.get(firstUnPrunedIndex - 1);
                assertTrue(lastPrunedFile.timestamp().isBefore(timestamp));
            }

            // Check all remaining files to make sure we didn't accidentally delete something from the end
            final List<PreConsensusEventFile> expectedFiles = new ArrayList<>();
            for (int index = firstUnPrunedIndex; index < fileCount; index++) {
                expectedFiles.add(files.get(index));
            }
            assertEquals(expectedFiles, parsedFiles);

            time.tick(Duration.ofSeconds(random.nextInt(1, 20)));
        }

        // Now, increase the generation by a little and try again.
        // The middle file should now be garbage collection eligible.
        manager.pruneOldFiles(lastFile.maximumGeneration() + 1);

        // Parse files with a new manager to make sure we aren't "cheating" by just
        // removing the in-memory descriptor without also removing the file on disk
        final List<PreConsensusEventFile> parsedFiles = new ArrayList<>();
        new PreConsensusEventFileManager(OSTime.getInstance(), config, buildMetrics())
                .getFileIterator(PreConsensusEventFileManager.NO_MINIMUM_GENERATION)
                .forEachRemaining(parsedFiles::add);

        final PreConsensusEventFile firstUnPrunedFile = parsedFiles.get(0);

        int firstUnPrunedIndex = -1;
        for (int index = 0; index < files.size(); index++) {
            if (files.get(index).equals(firstUnPrunedFile)) {
                firstUnPrunedIndex = index;
                break;
            }
        }

        assertEquals(middleFileIndex + 1, firstUnPrunedIndex);
    }
}
