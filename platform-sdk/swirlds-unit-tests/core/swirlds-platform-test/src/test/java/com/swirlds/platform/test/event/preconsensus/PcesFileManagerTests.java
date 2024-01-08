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

import static com.swirlds.common.test.fixtures.AssertionUtils.assertIteratorEquality;
import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.platform.event.preconsensus.PreconsensusEventFileManager.NO_MINIMUM_GENERATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.base.time.Time;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.io.config.RecycleBinConfig;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.io.utility.RecycleBinImpl;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.TestRecycleBin;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.event.preconsensus.PcesConfig_;
import com.swirlds.platform.event.preconsensus.PcesFile;
import com.swirlds.platform.event.preconsensus.PreconsensusEventFileManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PcesFileManager Tests")
class PcesFileManagerTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    private Path fileDirectory = null;

    @BeforeEach
    void beforeEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
        fileDirectory = testDirectory.resolve("data").resolve("0");
    }

    @AfterEach
    void afterEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
    }

    private PlatformContext buildContext() {
        return buildContext(false);
    }

    private PlatformContext buildContext(final boolean permitGaps) {
        final Configuration configuration = new TestConfigBuilder()
                .withValue(PcesConfig_.DATABASE_DIRECTORY, testDirectory.resolve("data"))
                .withValue(
                        "event.preconsensus.recycleBinDirectory",
                        testDirectory.resolve("recycle")) // FUTURE: No property defined for value
                .withValue(PcesConfig_.PREFERRED_FILE_SIZE_MEGABYTES, 5)
                .withValue(PcesConfig_.PERMIT_GAPS, permitGaps)
                .getOrCreateConfig();

        final Metrics metrics = new NoOpMetrics();

        return new DefaultPlatformContext(configuration, metrics, CryptographyHolder.get(), Time.getCurrent());
    }

    /**
     * Create a dummy file.
     *
     * @param descriptor a description of the file
     */
    private void createDummyFile(final PcesFile descriptor) throws IOException {
        final Path parentDir = descriptor.getPath().getParent();
        if (!Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        final SerializableDataOutputStream out = new SerializableDataOutputStream(
                new FileOutputStream(descriptor.getPath().toFile()));
        out.writeNormalisedString("foo bar baz");
        out.close();
    }

    @Test
    @DisplayName("Minimum Decreases Test")
    void minimumDecreasesTest() throws IOException {

        createDummyFile(PcesFile.of(Instant.now(), 0, 5, 10, 0, fileDirectory));

        createDummyFile(PcesFile.of(Instant.now(), 1, 4, 11, 0, fileDirectory));

        createDummyFile(PcesFile.of(Instant.now(), 2, 10, 20, 0, fileDirectory));

        final PlatformContext platformContext = buildContext();

        assertThrows(
                IllegalStateException.class,
                () -> new PreconsensusEventFileManager(
                        platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0));
    }

    @Test
    @DisplayName("Maximum Decreases Test")
    void maximumDecreasesTest() throws IOException {

        createDummyFile(PcesFile.of(Instant.now(), 0, 5, 10, 0, fileDirectory));

        createDummyFile(PcesFile.of(Instant.now(), 1, 6, 9, 0, fileDirectory));

        createDummyFile(PcesFile.of(Instant.now(), 2, 10, 20, 0, fileDirectory));

        final PlatformContext platformContext = buildContext();

        assertThrows(
                IllegalStateException.class,
                () -> new PreconsensusEventFileManager(
                        platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0));
    }

    @Test
    @DisplayName("Timestamp Decreases Test")
    void timestampDecreasesTest() throws IOException {

        createDummyFile(PcesFile.of(Instant.ofEpochMilli(1000), 0, 5, 10, 0, fileDirectory));

        createDummyFile(PcesFile.of(Instant.ofEpochMilli(500), 1, 6, 11, 0, fileDirectory));

        createDummyFile(PcesFile.of(Instant.ofEpochMilli(2000), 2, 7, 12, 0, fileDirectory));

        final PlatformContext platformContext = buildContext();

        assertThrows(
                IllegalStateException.class,
                () -> new PreconsensusEventFileManager(
                        platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0));
    }

    @Test
    @DisplayName("Read Files In Order Test")
    void readFilesInOrderTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PcesFile> files = new ArrayList<>();

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

            final PcesFile file =
                    PcesFile.of(timestamp, sequenceNumber, minimumGeneration, maximumGeneration, 0, fileDirectory);

            minimumGeneration = random.nextLong(minimumGeneration, maximumGeneration + 1);
            maximumGeneration =
                    Math.max(maximumGeneration, random.nextLong(minimumGeneration, minimumGeneration + maxDelta));
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            files.add(file);
            createDummyFile(file);
        }

        final PlatformContext platformContext = buildContext();

        final PreconsensusEventFileManager manager = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0);

        assertIteratorEquality(files.iterator(), manager.getFileIterator(NO_MINIMUM_GENERATION));

        assertIteratorEquality(
                files.iterator(), manager.getFileIterator(files.get(0).getMaximumGeneration()));

        // attempt to start a non-existent generation
        assertIteratorEquality(files.iterator(), manager.getFileIterator(nonExistentGeneration));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true})
    @DisplayName("Read Files In Order Gap Test")
    void readFilesInOrderGapTest(final boolean permitGaps) throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PcesFile> files = new ArrayList<>();

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

            final PcesFile file =
                    PcesFile.of(timestamp, sequenceNumber, minimumGeneration, maximumGeneration, 0, fileDirectory);

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

        final PlatformContext platformContext = buildContext(permitGaps);

        if (permitGaps) {
            // Gaps are allowed. We should see all files except for the one that was skipped.
            final PreconsensusEventFileManager manager = new PreconsensusEventFileManager(
                    platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0);

            assertIteratorEquality(files.iterator(), manager.getFileIterator(NO_MINIMUM_GENERATION));
        } else {
            // Gaps are not allowed.
            assertThrows(
                    IllegalStateException.class,
                    () -> new PreconsensusEventFileManager(
                            platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0));
        }
    }

    @Test
    @DisplayName("Read Files From Middle Test")
    void readFilesFromMiddleTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PcesFile> files = new ArrayList<>();

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

            final PcesFile file =
                    PcesFile.of(timestamp, sequenceNumber, minimumGeneration, maximumGeneration, 0, fileDirectory);

            minimumGeneration = random.nextLong(minimumGeneration, maximumGeneration + 1);
            maximumGeneration =
                    Math.max(maximumGeneration, random.nextLong(minimumGeneration, minimumGeneration + maxDelta));
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            files.add(file);
            createDummyFile(file);
        }

        final PlatformContext platformContext = buildContext();

        final PreconsensusEventFileManager manager = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0);

        // For this test, we want to iterate over files so that we are guaranteed to observe every event
        // with a generation greater than or equal to the target generation. Choose a generation that falls
        // roughly in the middle of the sequence of files.
        final long targetGeneration =
                (files.get(0).getMaximumGeneration() + files.get(fileCount - 1).getMaximumGeneration()) / 2;

        final List<PcesFile> iteratedFiles = new ArrayList<>();
        manager.getFileIterator(targetGeneration).forEachRemaining(iteratedFiles::add);

        // Find the index in the file list that was returned first by the iterator
        int indexOfFirstFile = 0;
        for (; indexOfFirstFile < fileCount; indexOfFirstFile++) {
            if (files.get(indexOfFirstFile).equals(iteratedFiles.get(0))) {
                break;
            }
        }

        // The file immediately before the returned file should not contain any targeted events
        assertTrue(files.get(indexOfFirstFile - 1).getMaximumGeneration() < targetGeneration);

        // The first file returned from the iterator should
        // have a maximum generation greater than or equal to the target generation.
        assertTrue(iteratedFiles.get(0).getMaximumGeneration() >= targetGeneration);

        // Make sure that the iterator returns files in the correct order.
        final List<PcesFile> expectedFiles = new ArrayList<>(iteratedFiles.size());
        for (int index = indexOfFirstFile; index < fileCount; index++) {
            expectedFiles.add(files.get(index));
        }
        assertIteratorEquality(expectedFiles.iterator(), iteratedFiles.iterator());
    }

    /**
     * Similar to the other test that starts iteration in the middle, except that files will have the same generational
     * bounds with high probability. Not a scenario we are likely to encounter in production, but it's a tricky edge
     * case we need to handle elegantly.
     */
    @Test
    @DisplayName("Read Files From Middle Repeating Generations Test")
    void readFilesFromMiddleRepeatingGenerationsTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PcesFile> files = new ArrayList<>();

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

            final PcesFile file =
                    PcesFile.of(timestamp, sequenceNumber, minimumGeneration, maximumGeneration, 0, fileDirectory);

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
        final PlatformContext platformContext = buildContext();

        final PreconsensusEventFileManager manager = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0);

        // For this test, we want to iterate over files so that we are guaranteed to observe every event
        // with a generation greater than or equal to the target generation. Choose a generation that falls
        // roughly in the middle of the sequence of files.
        final long targetGeneration =
                (files.get(0).getMaximumGeneration() + files.get(fileCount - 1).getMaximumGeneration()) / 2;

        final List<PcesFile> iteratedFiles = new ArrayList<>();
        manager.getFileIterator(targetGeneration).forEachRemaining(iteratedFiles::add);

        // Find the index in the file list that was returned first by the iterator
        int indexOfFirstFile = 0;
        for (; indexOfFirstFile < fileCount; indexOfFirstFile++) {
            if (files.get(indexOfFirstFile).equals(iteratedFiles.get(0))) {
                break;
            }
        }

        // The file immediately before the returned file should not contain any targeted events
        assertTrue(files.get(indexOfFirstFile - 1).getMaximumGeneration() < targetGeneration);

        // The first file returned from the iterator should
        // have a maximum generation greater than or equal to the target generation.
        assertTrue(iteratedFiles.get(0).getMaximumGeneration() >= targetGeneration);

        // Make sure that the iterator returns files in the correct order.
        final List<PcesFile> expectedFiles = new ArrayList<>(iteratedFiles.size());
        for (int index = indexOfFirstFile; index < fileCount; index++) {
            expectedFiles.add(files.get(index));
        }
        assertIteratorEquality(expectedFiles.iterator(), iteratedFiles.iterator());
    }

    @Test
    @DisplayName("Read Files From High Generation Test")
    void readFilesFromHighGenerationTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PcesFile> files = new ArrayList<>();

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

            final PcesFile file =
                    PcesFile.of(timestamp, sequenceNumber, minimumGeneration, maximumGeneration, 0, fileDirectory);

            minimumGeneration = random.nextLong(minimumGeneration, maximumGeneration + 1);
            maximumGeneration =
                    Math.max(maximumGeneration, random.nextLong(minimumGeneration, minimumGeneration + maxDelta));
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            files.add(file);
            createDummyFile(file);
        }

        final PlatformContext platformContext = buildContext();

        final PreconsensusEventFileManager manager = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0);

        // Request a generation higher than all files in the data store
        final long targetGeneration = files.get(fileCount - 1).getMaximumGeneration() + 1;

        final Iterator<PcesFile> iterator = manager.getFileIterator(targetGeneration);
        assertFalse(iterator.hasNext());
    }

    @Test
    @DisplayName("Read Files From Empty Stream Test")
    void readFilesFromEmptyStreamTest() throws IOException {
        final PlatformContext platformContext = buildContext();

        final PreconsensusEventFileManager manager = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0);

        final Iterator<PcesFile> iterator = manager.getFileIterator(1234);
        assertFalse(iterator.hasNext());
    }

    @Test
    @DisplayName("Generate Descriptors With Manager Test")
    void generateDescriptorsWithManagerTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PcesFile> files = new ArrayList<>();

        final PlatformContext platformContext = buildContext();

        final long maxDelta = random.nextLong(10, 20);
        long minimumGeneration = random.nextLong(0, 1000);
        long maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + maxDelta);
        Instant timestamp = Instant.now();

        final PreconsensusEventFileManager generatingManager = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0);
        for (int i = 0; i < fileCount; i++) {

            final PcesFile file = generatingManager.getNextFileDescriptor(minimumGeneration, maximumGeneration);

            assertTrue(file.getMinimumGeneration() >= minimumGeneration);
            assertTrue(file.getMaximumGeneration() >= maximumGeneration);

            // Intentionally allow generations to be chosen that may not be legal (i.e. a generation decreases)
            minimumGeneration = random.nextLong(minimumGeneration - 1, maximumGeneration + 1);
            maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + maxDelta);
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            files.add(file);
            createDummyFile(file);
        }

        final PreconsensusEventFileManager manager = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0);

        assertIteratorEquality(files.iterator(), manager.getFileIterator(NO_MINIMUM_GENERATION));
    }

    @Test
    @DisplayName("Incremental Pruning By Generation Test")
    void incrementalPruningByGenerationTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PcesFile> files = new ArrayList<>();

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

            final PcesFile file =
                    PcesFile.of(timestamp, sequenceNumber, minimumGeneration, maximumGeneration, 0, fileDirectory);

            minimumGeneration = random.nextLong(minimumGeneration, maximumGeneration + 1);
            maximumGeneration =
                    Math.max(maximumGeneration + 1, random.nextLong(minimumGeneration, minimumGeneration + maxDelta));
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            files.add(file);
            createDummyFile(file);
        }

        final PlatformContext platformContext = buildContext();

        // Choose a file in the middle. The goal is to incrementally purge all files before this file, but not
        // to purge this file or any of the ones after it.
        final int middleFileIndex = fileCount / 2;

        final PcesFile firstFile = files.get(0);
        final PcesFile middleFile = files.get(middleFileIndex);
        final PcesFile lastFile = files.get(files.size() - 1);

        // Set the far in the future, we want all files to be GC eligible by temporal reckoning.
        final FakeTime time = new FakeTime(lastFile.getTimestamp().plus(Duration.ofHours(1)), Duration.ZERO);

        final PreconsensusEventFileManager manager =
                new PreconsensusEventFileManager(platformContext, time, TestRecycleBin.getInstance(), new NodeId(0), 0);

        assertIteratorEquality(files.iterator(), manager.getFileIterator(NO_MINIMUM_GENERATION));

        // Increase the pruned generation a little at a time,
        // until the middle file is almost GC eligible but not quite.
        for (long generation = firstFile.getMaximumGeneration() - 100;
                generation <= middleFile.getMaximumGeneration();
                generation++) {

            manager.pruneOldFiles(generation);

            // Parse files with a new manager to make sure we aren't "cheating" by just
            // removing the in-memory descriptor without also removing the file on disk
            final List<PcesFile> parsedFiles = new ArrayList<>();
            new PreconsensusEventFileManager(
                            platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0)
                    .getFileIterator(NO_MINIMUM_GENERATION)
                    .forEachRemaining(parsedFiles::add);

            final PcesFile firstUnPrunedFile = parsedFiles.get(0);

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
                assertTrue(firstUnPrunedFile.getMaximumGeneration() >= generation);
            }

            // Check the file right before the first un-pruned file.
            if (firstUnPrunedIndex > 0) {
                final PcesFile lastPrunedFile = files.get(firstUnPrunedIndex - 1);
                assertTrue(lastPrunedFile.getMaximumGeneration() < generation);
            }

            // Check all remaining files to make sure we didn't accidentally delete something from the end
            final List<PcesFile> expectedFiles = new ArrayList<>();
            for (int index = firstUnPrunedIndex; index < fileCount; index++) {
                expectedFiles.add(files.get(index));
            }
            assertEquals(expectedFiles, parsedFiles);
        }

        // Now, prune files so that the middle file is no longer needed.
        manager.pruneOldFiles(middleFile.getMaximumGeneration() + 1);

        // Parse files with a new manager to make sure we aren't "cheating" by just
        // removing the in-memory descriptor without also removing the file on disk
        final List<PcesFile> parsedFiles = new ArrayList<>();
        new PreconsensusEventFileManager(
                        platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0)
                .getFileIterator(NO_MINIMUM_GENERATION)
                .forEachRemaining(parsedFiles::add);

        final PcesFile firstUnPrunedFile = parsedFiles.get(0);

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

        final List<PcesFile> files = new ArrayList<>();

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

            final PcesFile file =
                    PcesFile.of(timestamp, sequenceNumber, minimumGeneration, maximumGeneration, 0, fileDirectory);

            minimumGeneration = random.nextLong(minimumGeneration, maximumGeneration + 1);
            maximumGeneration =
                    Math.max(maximumGeneration, random.nextLong(minimumGeneration, minimumGeneration + maxDelta));
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            files.add(file);
            createDummyFile(file);
        }

        final PlatformContext platformContext = buildContext();

        // Choose a file in the middle. The goal is to incrementally purge all files before this file, but not
        // to purge this file or any of the ones after it.
        final int middleFileIndex = fileCount / 2;

        final PcesFile firstFile = files.get(0);
        final PcesFile middleFile = files.get(middleFileIndex);
        final PcesFile lastFile = files.get(files.size() - 1);

        // Set the clock before the first file is not garbage collection eligible
        final FakeTime time = new FakeTime(firstFile.getTimestamp().plus(Duration.ofMinutes(59)), Duration.ZERO);

        final PreconsensusEventFileManager manager =
                new PreconsensusEventFileManager(platformContext, time, TestRecycleBin.getInstance(), new NodeId(0), 0);

        assertIteratorEquality(files.iterator(), manager.getFileIterator(NO_MINIMUM_GENERATION));

        // Increase the timestamp a little at a time. We should gradually delete files up until
        // all files before the middle file have been deleted.
        final Instant endingTime =
                middleFile.getTimestamp().plus(Duration.ofMinutes(60).minus(Duration.ofNanos(1)));
        while (time.now().isBefore(endingTime)) {
            manager.pruneOldFiles(lastFile.getMaximumGeneration() + 1);

            // Parse files with a new manager to make sure we aren't "cheating" by just
            // removing the in-memory descriptor without also removing the file on disk
            final List<PcesFile> parsedFiles = new ArrayList<>();
            new PreconsensusEventFileManager(
                            platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0)
                    .getFileIterator(NO_MINIMUM_GENERATION)
                    .forEachRemaining(parsedFiles::add);

            final PcesFile firstUnPrunedFile = parsedFiles.get(0);

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
                    && files.get(firstUnPrunedIndex).getMaximumGeneration() < middleFile.getMaximumGeneration()) {
                assertTrue(CompareTo.isGreaterThanOrEqualTo(
                        firstUnPrunedFile.getTimestamp().plus(Duration.ofHours(1)), time.now()));
            }

            // Check the file right before the first un-pruned file.
            if (firstUnPrunedIndex > 0) {
                final PcesFile lastPrunedFile = files.get(firstUnPrunedIndex - 1);
                assertTrue(lastPrunedFile.getTimestamp().isBefore(timestamp));
            }

            // Check all remaining files to make sure we didn't accidentally delete something from the end
            final List<PcesFile> expectedFiles = new ArrayList<>();
            for (int index = firstUnPrunedIndex; index < fileCount; index++) {
                expectedFiles.add(files.get(index));
            }
            assertEquals(expectedFiles, parsedFiles);

            time.tick(Duration.ofSeconds(random.nextInt(1, 20)));
        }

        // Now, increase the generation by a little and try again.
        // The middle file should now be garbage collection eligible.
        manager.pruneOldFiles(lastFile.getMaximumGeneration() + 1);

        // Parse files with a new manager to make sure we aren't "cheating" by just
        // removing the in-memory descriptor without also removing the file on disk
        final List<PcesFile> parsedFiles = new ArrayList<>();
        new PreconsensusEventFileManager(
                        platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0)
                .getFileIterator(NO_MINIMUM_GENERATION)
                .forEachRemaining(parsedFiles::add);

        final PcesFile firstUnPrunedFile = parsedFiles.get(0);

        int firstUnPrunedIndex = -1;
        for (int index = 0; index < files.size(); index++) {
            if (files.get(index).equals(firstUnPrunedFile)) {
                firstUnPrunedIndex = index;
                break;
            }
        }

        assertEquals(middleFileIndex + 1, firstUnPrunedIndex);
    }

    /**
     * When fixing a discontinuity, invalid files are moved to a "recycle bin" directory. This method validates that
     * behavior.
     */
    private void validateRecycledFiles(
            @NonNull final List<PcesFile> filesThatShouldBePresent,
            @NonNull final List<PcesFile> allFiles,
            @NonNull final PlatformContext platformContext)
            throws IOException {

        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        final RecycleBinConfig recycleBinConfig =
                platformContext.getConfiguration().getConfigData(RecycleBinConfig.class);

        final Path recycleBinDirectory = recycleBinConfig.getStorageLocation(stateConfig, new NodeId(0));

        final Set<Path> recycledFiles = new HashSet<>();
        try (final Stream<Path> stream = Files.walk(recycleBinDirectory)) {
            stream.forEach(file -> recycledFiles.add(file.getFileName()));
        }

        final Set<PcesFile> filesThatShouldBePresentSet = new HashSet<>(filesThatShouldBePresent);

        for (final PcesFile file : allFiles) {
            if (filesThatShouldBePresentSet.contains(file)) {
                assertTrue(Files.exists(file.getPath()));
            } else {
                assertTrue(recycledFiles.contains(file.getPath().getFileName()), file.toString());
            }
        }
    }

    /**
     * In this test, a discontinuity is placed in the middle of the stream. We begin iterating at the first file in the
     * stream.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Start And First File Discontinuity In Middle Test")
    void startAtFirstFileDiscontinuityInMiddleTest(final boolean startAtSpecificGeneration) throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PcesFile> files = new ArrayList<>();
        final List<PcesFile> filesBeforeDiscontinuity = new ArrayList<>();
        final List<PcesFile> filesAfterDiscontinuity = new ArrayList<>();

        // Intentionally pick values close to wrapping around the 3 digit to 4 digit sequence number.
        // This will cause the files not to line up alphabetically, and this is a scenario that the
        // code should be able to handle.
        final long firstSequenceNumber = random.nextLong(950, 1000);

        final long maxDelta = random.nextLong(10, 20);
        long minimumGeneration = random.nextLong(0, 1000);
        long maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + maxDelta);
        Instant timestamp = Instant.now();

        final long discontinuitySequenceNumber =
                random.nextLong(firstSequenceNumber + 1, firstSequenceNumber + fileCount - 1);

        final long startingOrigin = random.nextLong(1, 1000);
        long origin = startingOrigin;

        for (long sequenceNumber = firstSequenceNumber;
                sequenceNumber < firstSequenceNumber + fileCount;
                sequenceNumber++) {

            if (sequenceNumber == discontinuitySequenceNumber) {
                origin = random.nextLong(origin + 1, origin + 1000);
            }

            final PcesFile file =
                    PcesFile.of(timestamp, sequenceNumber, minimumGeneration, maximumGeneration, origin, fileDirectory);

            minimumGeneration = random.nextLong(minimumGeneration, maximumGeneration + 1);
            maximumGeneration =
                    Math.max(maximumGeneration, random.nextLong(minimumGeneration, minimumGeneration + maxDelta));
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            files.add(file);
            if (sequenceNumber < discontinuitySequenceNumber) {
                filesBeforeDiscontinuity.add(file);
            } else {
                filesAfterDiscontinuity.add(file);
            }
            createDummyFile(file);
        }

        final PlatformContext platformContext = buildContext();
        final RecycleBinImpl recycleBin = new RecycleBinImpl(
                platformContext.getConfiguration(),
                new NoOpMetrics(),
                getStaticThreadManager(),
                Time.getCurrent(),
                new NodeId(0));
        recycleBin.clear();

        // Scenario 1: choose an origin that lands on the discontinuity exactly.
        final long startingRound1 = origin;
        final PreconsensusEventFileManager manager1 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), recycleBin, new NodeId(0), startingRound1);
        assertIteratorEquality(filesAfterDiscontinuity.iterator(), manager1.getFileIterator(NO_MINIMUM_GENERATION));

        // Scenario 2: choose an origin that lands after the discontinuity.
        final long startingRound2 = random.nextLong(origin + 1, origin + 1000);
        final PreconsensusEventFileManager manager2 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), recycleBin, new NodeId(0), startingRound2);
        assertIteratorEquality(filesAfterDiscontinuity.iterator(), manager2.getFileIterator(NO_MINIMUM_GENERATION));

        // Scenario 3: choose an origin that comes before the discontinuity. This will cause the files
        // after the discontinuity to be deleted.
        final long startingRound3 = random.nextLong(startingOrigin, origin - 1);
        final PreconsensusEventFileManager manager3 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), recycleBin, new NodeId(0), startingRound3);

        assertIteratorEquality(filesBeforeDiscontinuity.iterator(), manager3.getFileIterator(NO_MINIMUM_GENERATION));

        validateRecycledFiles(filesBeforeDiscontinuity, files, platformContext);

        // Scenario 4: choose an origin that is incompatible with all state files. This will cause all remaining
        // files to be deleted.
        final long startingRound4 = 0;
        final PreconsensusEventFileManager manager4 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), recycleBin, new NodeId(0), startingRound4);

        assertIteratorEquality(Collections.emptyIterator(), manager4.getFileIterator(NO_MINIMUM_GENERATION));

        validateRecycledFiles(List.of(), files, platformContext);
    }

    /**
     * In this test, a discontinuity is placed in the middle of the stream. We begin iterating at a file that comes
     * before the discontinuity, but it isn't the first file in the stream.
     */
    @Test
    @DisplayName("Start At Middle File Discontinuity In Middle Test")
    void startAtMiddleFileDiscontinuityInMiddleTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PcesFile> files = new ArrayList<>();
        final List<PcesFile> filesBeforeDiscontinuity = new ArrayList<>();
        final List<PcesFile> filesAfterDiscontinuity = new ArrayList<>();

        // Intentionally pick values close to wrapping around the 3 digit to 4 digit sequence number.
        // This will cause the files not to line up alphabetically, and this is a scenario that the
        // code should be able to handle.
        final int firstSequenceNumber = random.nextInt(950, 1000);

        final long maxDelta = random.nextLong(10, 20);
        long minimumGeneration = random.nextLong(0, 1000);
        long maximumGeneration = random.nextLong(minimumGeneration + 1, minimumGeneration + maxDelta);
        Instant timestamp = Instant.now();

        final int discontinuitySequenceNumber =
                (int) random.nextLong(firstSequenceNumber + 2, firstSequenceNumber + fileCount - 1);

        final int startSequenceNumber = random.nextInt(firstSequenceNumber, discontinuitySequenceNumber - 1);

        final long startingOrigin = random.nextLong(1, 1000);
        long origin = startingOrigin;

        for (long sequenceNumber = firstSequenceNumber;
                sequenceNumber < firstSequenceNumber + fileCount;
                sequenceNumber++) {

            if (sequenceNumber == discontinuitySequenceNumber) {
                origin = random.nextLong(origin + 1, origin + 1000);
            }

            final PcesFile file =
                    PcesFile.of(timestamp, sequenceNumber, minimumGeneration, maximumGeneration, origin, fileDirectory);

            minimumGeneration = random.nextLong(minimumGeneration + 1, maximumGeneration + 1);
            maximumGeneration = random.nextLong(maximumGeneration + 1, maximumGeneration + maxDelta);
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            if (sequenceNumber >= startSequenceNumber) {
                files.add(file);
                if (sequenceNumber < discontinuitySequenceNumber) {
                    filesBeforeDiscontinuity.add(file);
                } else {
                    filesAfterDiscontinuity.add(file);
                }
            }
            createDummyFile(file);
        }

        // Note that the file at index 0 is not the first file in the stream,
        // but it is the first file we want to iterate
        final long startGeneration = files.get(0).getMaximumGeneration();

        final PlatformContext platformContext = buildContext();

        final RecycleBinImpl recycleBin = new RecycleBinImpl(
                platformContext.getConfiguration(),
                new NoOpMetrics(),
                getStaticThreadManager(),
                Time.getCurrent(),
                new NodeId(0));
        recycleBin.clear();

        // Scenario 1: choose an origin that lands on the discontinuity exactly.
        final long startingRound1 = origin;
        final PreconsensusEventFileManager manager1 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), recycleBin, new NodeId(0), startingRound1);
        assertIteratorEquality(filesAfterDiscontinuity.iterator(), manager1.getFileIterator(startGeneration));

        // Scenario 2: choose an origin that lands after the discontinuity.
        final long startingRound2 = random.nextLong(origin + 1, origin + 1000);
        final PreconsensusEventFileManager manager2 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), recycleBin, new NodeId(0), startingRound2);
        assertIteratorEquality(filesAfterDiscontinuity.iterator(), manager2.getFileIterator(startGeneration));

        // Scenario 3: choose an origin that comes before the discontinuity. This will cause the files
        // after the discontinuity to be deleted.
        final long startingRound3 = random.nextLong(startingOrigin, origin - 1);
        final PreconsensusEventFileManager manager3 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), recycleBin, new NodeId(0), startingRound3);

        assertIteratorEquality(filesBeforeDiscontinuity.iterator(), manager3.getFileIterator(startGeneration));

        validateRecycledFiles(filesBeforeDiscontinuity, files, platformContext);

        // Scenario 4: choose an origin that is incompatible with all state files. This will cause all remaining
        // files to be deleted.
        final long startingRound4 = 0;
        final PreconsensusEventFileManager manager4 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), recycleBin, new NodeId(0), startingRound4);

        assertIteratorEquality(Collections.emptyIterator(), manager4.getFileIterator(startGeneration));

        validateRecycledFiles(List.of(), files, platformContext);
    }

    /**
     * In this test, a discontinuity is placed in the middle of the stream, and we begin iterating on that exact file.
     */
    @Test
    @DisplayName("Start At Middle File Discontinuity In Middle Test")
    void startAtDiscontinuityInMiddleTest() throws IOException {
        final Random random = getRandomPrintSeed(4503019787365986869L);

        final int fileCount = 100;

        final List<PcesFile> files = new ArrayList<>();
        final List<PcesFile> filesBeforeDiscontinuity = new ArrayList<>();
        final List<PcesFile> filesAfterDiscontinuity = new ArrayList<>();

        // Intentionally pick values close to wrapping around the 3 digit to 4 digit sequence number.
        // This will cause the files not to line up alphabetically, and this is a scenario that the
        // code should be able to handle.
        final int firstSequenceNumber = random.nextInt(950, 1000);

        // In this test, sequence numbers are intentionally chosen so that the min/max sequence number always
        // increases by at least 1 from file to file. The purpose for this is to make validation logic simpler.

        final long maxDelta = random.nextLong(10, 20);
        long minimumGeneration = random.nextLong(0, 1000);
        long maximumGeneration = random.nextLong(minimumGeneration + 1, minimumGeneration + maxDelta);
        Instant timestamp = Instant.now();

        final int discontinuitySequenceNumber =
                (int) random.nextLong(firstSequenceNumber + 2, firstSequenceNumber + fileCount - 1);

        final long startingOrigin = random.nextLong(1, 1000);
        long origin = startingOrigin;

        for (long sequenceNumber = firstSequenceNumber;
                sequenceNumber < firstSequenceNumber + fileCount;
                sequenceNumber++) {

            if (sequenceNumber == discontinuitySequenceNumber) {
                origin = random.nextLong(origin + 1, origin + 1000);
            }

            final PcesFile file =
                    PcesFile.of(timestamp, sequenceNumber, minimumGeneration, maximumGeneration, origin, fileDirectory);

            minimumGeneration = random.nextLong(minimumGeneration + 1, maximumGeneration + 1);
            maximumGeneration = random.nextLong(maximumGeneration + 1, maximumGeneration + maxDelta);
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            files.add(file);
            if (sequenceNumber < discontinuitySequenceNumber) {
                filesBeforeDiscontinuity.add(file);
            } else {
                filesAfterDiscontinuity.add(file);
            }
            createDummyFile(file);
        }

        // Note that the file at index 0 is not the first file in the stream,
        // but it is the first file we want to iterate
        final long startGeneration = filesAfterDiscontinuity.get(0).getMaximumGeneration();

        final PlatformContext platformContext = buildContext();

        final RecycleBinImpl recycleBin = new RecycleBinImpl(
                platformContext.getConfiguration(),
                new NoOpMetrics(),
                getStaticThreadManager(),
                Time.getCurrent(),
                new NodeId(0));
        recycleBin.clear();

        // Scenario 1: choose an origin that lands on the discontinuity exactly.
        final long startingRound1 = origin;
        final PreconsensusEventFileManager manager1 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), recycleBin, new NodeId(0), startingRound1);
        assertIteratorEquality(filesAfterDiscontinuity.iterator(), manager1.getFileIterator(startGeneration));

        // Scenario 2: choose an origin that lands after the discontinuity.
        final long startingRound2 = random.nextLong(origin + 1, origin + 1000);
        final PreconsensusEventFileManager manager2 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), recycleBin, new NodeId(0), startingRound2);
        assertIteratorEquality(filesAfterDiscontinuity.iterator(), manager2.getFileIterator(startGeneration));

        // Scenario 3: choose an origin that comes before the discontinuity. This will cause the files
        // after the discontinuity to be deleted.
        final long startingRound3 = random.nextLong(startingOrigin, origin - 1);
        final PreconsensusEventFileManager manager3 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), recycleBin, new NodeId(0), startingRound3);

        // There is no files with a compatible origin and events with generations in the span we want.
        assertIteratorEquality(Collections.emptyIterator(), manager3.getFileIterator(startGeneration));

        validateRecycledFiles(filesBeforeDiscontinuity, files, platformContext);

        // Scenario 4: choose an origin that is incompatible with all state files. This will cause all remaining
        // files to be deleted.
        final long startingRound4 = 0;
        final PreconsensusEventFileManager manager4 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), recycleBin, new NodeId(0), startingRound4);

        assertIteratorEquality(Collections.emptyIterator(), manager4.getFileIterator(startGeneration));

        validateRecycledFiles(List.of(), files, platformContext);
    }

    /**
     * In this test, a discontinuity is placed in the middle of the stream, and we begin iterating after that file.
     */
    @Test
    @DisplayName("Start After Discontinuity In Middle Test")
    void startAfterDiscontinuityInMiddleTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PcesFile> allFiles = new ArrayList<>();
        final List<PcesFile> filesBeforeDiscontinuity = new ArrayList<>();
        final List<PcesFile> filesAfterDiscontinuity = new ArrayList<>();

        // Intentionally pick values close to wrapping around the 3 digit to 4 digit sequence number.
        // This will cause the files not to line up alphabetically, and this is a scenario that the
        // code should be able to handle.
        final int firstSequenceNumber = random.nextInt(950, 1000);

        // In this test, sequence numbers are intentionally chosen so that the min/max sequence number always
        // increases by at least 1 from file to file. The purpose for this is to make validation logic simpler.

        final long maxDelta = random.nextLong(10, 20);
        long minimumGeneration = random.nextLong(0, 1000);
        long maximumGeneration = random.nextLong(minimumGeneration + 1, minimumGeneration + maxDelta);
        Instant timestamp = Instant.now();

        final int discontinuitySequenceNumber =
                (int) random.nextLong(firstSequenceNumber + 2, firstSequenceNumber + fileCount - 1);

        final long startingOrigin = random.nextLong(1, 1000);
        long origin = startingOrigin;

        for (long sequenceNumber = firstSequenceNumber;
                sequenceNumber < firstSequenceNumber + fileCount;
                sequenceNumber++) {

            if (sequenceNumber == discontinuitySequenceNumber) {
                origin = random.nextLong(origin + 1, origin + 1000);
            }

            final PcesFile file =
                    PcesFile.of(timestamp, sequenceNumber, minimumGeneration, maximumGeneration, origin, fileDirectory);

            minimumGeneration = random.nextLong(minimumGeneration + 1, maximumGeneration + 1);
            maximumGeneration = random.nextLong(maximumGeneration + 1, maximumGeneration + maxDelta);
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            allFiles.add(file);
            if (sequenceNumber >= discontinuitySequenceNumber) {
                filesAfterDiscontinuity.add(file);
            } else {
                filesBeforeDiscontinuity.add(file);
            }
            createDummyFile(file);
        }

        // Note that the file at index 0 is not the first file in the stream,
        // but it is the first file we want to iterate
        final long startGeneration = filesAfterDiscontinuity.get(0).getMaximumGeneration();

        final PlatformContext platformContext = buildContext();
        final RecycleBinImpl recycleBin = new RecycleBinImpl(
                platformContext.getConfiguration(),
                new NoOpMetrics(),
                getStaticThreadManager(),
                Time.getCurrent(),
                new NodeId(0));
        recycleBin.clear();

        // Scenario 1: choose an origin that lands on the discontinuity exactly.
        final long startingRound1 = origin;
        final PreconsensusEventFileManager manager1 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), recycleBin, new NodeId(0), startingRound1);
        assertIteratorEquality(filesAfterDiscontinuity.iterator(), manager1.getFileIterator(startGeneration));

        // Scenario 2: choose an origin that lands after the discontinuity.
        final long startingRound2 = random.nextLong(origin + 1, origin + 1000);
        final PreconsensusEventFileManager manager2 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), recycleBin, new NodeId(0), startingRound2);
        assertIteratorEquality(filesAfterDiscontinuity.iterator(), manager2.getFileIterator(startGeneration));

        // Scenario 3: choose an origin that comes before the discontinuity. This will cause the files
        // after the discontinuity to be deleted.
        final long startingRound3 = random.nextLong(startingOrigin, origin - 1);
        final PreconsensusEventFileManager manager3 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), recycleBin, new NodeId(0), startingRound3);

        assertIteratorEquality(Collections.emptyIterator(), manager3.getFileIterator(startGeneration));

        validateRecycledFiles(filesBeforeDiscontinuity, allFiles, platformContext);

        // Scenario 4: choose an origin that is incompatible with all state files. This will cause all remaining
        // files to be deleted.
        final long startingRound4 = 0;
        final PreconsensusEventFileManager manager4 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), recycleBin, new NodeId(0), startingRound4);

        assertIteratorEquality(Collections.emptyIterator(), manager4.getFileIterator(startGeneration));

        validateRecycledFiles(List.of(), allFiles, platformContext);
    }

    @Test
    @DisplayName("clear() Test")
    void clearTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final int fileCount = 100;

        final List<PcesFile> files = new ArrayList<>();

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

            final PcesFile file =
                    PcesFile.of(timestamp, sequenceNumber, minimumGeneration, maximumGeneration, 0, fileDirectory);

            minimumGeneration = random.nextLong(minimumGeneration, maximumGeneration + 1);
            maximumGeneration =
                    Math.max(maximumGeneration, random.nextLong(minimumGeneration, minimumGeneration + maxDelta));
            timestamp = timestamp.plusMillis(random.nextInt(1, 100_000));

            files.add(file);
            createDummyFile(file);
        }

        final PlatformContext platformContext = buildContext();

        final PreconsensusEventFileManager manager = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0);

        assertIteratorEquality(files.iterator(), manager.getFileIterator(NO_MINIMUM_GENERATION));

        assertIteratorEquality(
                files.iterator(), manager.getFileIterator(files.get(0).getMaximumGeneration()));

        // attempt to start a non-existent generation
        assertIteratorEquality(files.iterator(), manager.getFileIterator(nonExistentGeneration));

        PreconsensusEventFileManager.clear(platformContext, TestRecycleBin.getInstance(), new NodeId(0));

        // The old manager will have been corrupted (we never clear after instantiation in production envs).
        // Any new manager created will not find any files.

        final PreconsensusEventFileManager manager2 = new PreconsensusEventFileManager(
                platformContext, Time.getCurrent(), TestRecycleBin.getInstance(), new NodeId(0), 0);

        assertIteratorEquality(Collections.emptyIterator(), manager2.getFileIterator(NO_MINIMUM_GENERATION));
    }
}
