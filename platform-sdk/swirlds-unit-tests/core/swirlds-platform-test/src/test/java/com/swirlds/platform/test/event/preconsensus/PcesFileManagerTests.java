/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import static com.swirlds.platform.event.preconsensus.PcesFileManager.NO_MINIMUM_GENERATION;
import static com.swirlds.platform.test.event.preconsensus.PcesFileReaderTests.createDummyFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.TestRecycleBin;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.event.preconsensus.PcesConfig_;
import com.swirlds.platform.event.preconsensus.PcesFile;
import com.swirlds.platform.event.preconsensus.PcesFileManager;
import com.swirlds.platform.event.preconsensus.PcesFileReader;
import com.swirlds.platform.event.preconsensus.PcesFileTracker;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link PcesFileManager}
 */
@DisplayName("PcesFileManager Tests")
class PcesFileManagerTests {
    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    private Random random;

    private final int fileCount = 100;

    private List<PcesFile> files;

    private Path fileDirectory = null;

    @BeforeEach
    void beforeEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
        fileDirectory = testDirectory.resolve("data").resolve("0");
        random = getRandomPrintSeed();
        files = new ArrayList<>();
    }

    private PlatformContext buildContext() {
        final Configuration configuration = new TestConfigBuilder()
                .withValue(PcesConfig_.DATABASE_DIRECTORY, testDirectory.resolve("data"))
                .withValue(
                        "event.preconsensus.recycleBinDirectory",
                        testDirectory.resolve("recycle")) // FUTURE: No property defined for value
                .withValue(PcesConfig_.PREFERRED_FILE_SIZE_MEGABYTES, 5)
                .withValue(PcesConfig_.PERMIT_GAPS, false)
                .getOrCreateConfig();

        final Metrics metrics = new NoOpMetrics();

        return new DefaultPlatformContext(configuration, metrics, CryptographyHolder.get(), Time.getCurrent());
    }

    @Test
    @DisplayName("Generate Descriptors With Manager Test")
    void generateDescriptorsWithManagerTest() throws IOException {
        final PlatformContext platformContext = buildContext();

        final long maxDelta = random.nextLong(10, 20);
        long minimumGeneration = random.nextLong(0, 1000);
        long maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + maxDelta);
        Instant timestamp = Instant.now();

        final PcesFileManager generatingManager =
                new PcesFileManager(platformContext, Time.getCurrent(), new PcesFileTracker(), new NodeId(0), 0);

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

        final PcesFileTracker fileTracker =
                PcesFileReader.readFilesFromDisk(buildContext(), TestRecycleBin.getInstance(), fileDirectory, 0, false);

        assertIteratorEquality(files.iterator(), fileTracker.getFileIterator(NO_MINIMUM_GENERATION, 0));
    }

    @Test
    @DisplayName("Incremental Pruning By Generation Test")
    void incrementalPruningByGenerationTest() throws IOException {
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

        final PcesFile firstFile = files.getFirst();
        final PcesFile middleFile = files.get(middleFileIndex);
        final PcesFile lastFile = files.getLast();

        // Set the far in the future, we want all files to be GC eligible by temporal reckoning.
        final FakeTime time = new FakeTime(lastFile.getTimestamp().plus(Duration.ofHours(1)), Duration.ZERO);

        final PcesFileTracker fileTracker =
                PcesFileReader.readFilesFromDisk(buildContext(), TestRecycleBin.getInstance(), fileDirectory, 0, false);
        final PcesFileManager manager = new PcesFileManager(platformContext, time, fileTracker, new NodeId(0), 0);

        assertIteratorEquality(files.iterator(), fileTracker.getFileIterator(NO_MINIMUM_GENERATION, 0));

        // Increase the pruned generation a little at a time,
        // until the middle file is almost GC eligible but not quite.
        for (long generation = firstFile.getMaximumGeneration() - 100;
                generation <= middleFile.getMaximumGeneration();
                generation++) {

            manager.pruneOldFiles(generation);

            // Parse files afresh to make sure we aren't "cheating" by just
            // removing the in-memory descriptor without also removing the file on disk
            final PcesFileTracker freshFileTracker = PcesFileReader.readFilesFromDisk(
                    buildContext(), TestRecycleBin.getInstance(), fileDirectory, 0, false);

            final PcesFile firstUnPrunedFile = freshFileTracker.getFirstFile();

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

            // iterate over all files in the fresh file tracker to make sure they match expected
            assertIteratorEquality(
                    expectedFiles.iterator(), freshFileTracker.getFileIterator(NO_MINIMUM_GENERATION, 0));
        }

        // Now, prune files so that the middle file is no longer needed.
        manager.pruneOldFiles(middleFile.getMaximumGeneration() + 1);

        // Parse files afresh to make sure we aren't "cheating" by just
        // removing the in-memory descriptor without also removing the file on disk
        final PcesFileTracker freshFileTracker =
                PcesFileReader.readFilesFromDisk(buildContext(), TestRecycleBin.getInstance(), fileDirectory, 0, false);

        final PcesFile firstUnPrunedFile = freshFileTracker.getFirstFile();

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

        final PcesFile firstFile = files.getFirst();
        final PcesFile middleFile = files.get(middleFileIndex);
        final PcesFile lastFile = files.getLast();

        // Set the clock before the first file is not garbage collection eligible
        final FakeTime time = new FakeTime(firstFile.getTimestamp().plus(Duration.ofMinutes(59)), Duration.ZERO);

        final PcesFileTracker fileTracker =
                PcesFileReader.readFilesFromDisk(buildContext(), TestRecycleBin.getInstance(), fileDirectory, 0, false);
        final PcesFileManager manager = new PcesFileManager(platformContext, time, fileTracker, new NodeId(0), 0);

        assertIteratorEquality(files.iterator(), fileTracker.getFileIterator(NO_MINIMUM_GENERATION, 0));

        // Increase the timestamp a little at a time. We should gradually delete files up until
        // all files before the middle file have been deleted.
        final Instant endingTime =
                middleFile.getTimestamp().plus(Duration.ofMinutes(60).minus(Duration.ofNanos(1)));
        while (time.now().isBefore(endingTime)) {
            manager.pruneOldFiles(lastFile.getMaximumGeneration() + 1);

            // Parse files afresh to make sure we aren't "cheating" by just
            // removing the in-memory descriptor without also removing the file on disk
            final PcesFileTracker freshFileTracker = PcesFileReader.readFilesFromDisk(
                    buildContext(), TestRecycleBin.getInstance(), fileDirectory, 0, false);

            final PcesFile firstUnPrunedFile = freshFileTracker.getFirstFile();

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
            assertIteratorEquality(
                    expectedFiles.iterator(), freshFileTracker.getFileIterator(NO_MINIMUM_GENERATION, 0));

            time.tick(Duration.ofSeconds(random.nextInt(1, 20)));
        }

        // Now, increase the generation by a little and try again.
        // The middle file should now be garbage collection eligible.
        manager.pruneOldFiles(lastFile.getMaximumGeneration() + 1);

        // Parse files afresh to make sure we aren't "cheating" by just
        // removing the in-memory descriptor without also removing the file on disk
        final PcesFileTracker freshFileTracker =
                PcesFileReader.readFilesFromDisk(buildContext(), TestRecycleBin.getInstance(), fileDirectory, 0, false);

        final PcesFile firstUnPrunedFile = freshFileTracker.getFirstFile();

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
