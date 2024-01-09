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
import static com.swirlds.platform.event.preconsensus.PcesFileManager.NO_MINIMUM_GENERATION;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.event.preconsensus.PcesConfig_;
import com.swirlds.platform.event.preconsensus.PcesFile;
import com.swirlds.platform.event.preconsensus.PcesFileReader;
import com.swirlds.platform.event.preconsensus.PcesFileTracker;
import com.swirlds.platform.event.preconsensus.PcesMutableFile;
import com.swirlds.test.framework.config.TestConfigBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
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

@DisplayName("PcesFileReader Tests")
class PcesFileReaderTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    private Path fileDirectory = null;

    private Random random;

    private final int fileCount = 100;

    private List<PcesFile> files;

    @BeforeEach
    void beforeEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
        fileDirectory = testDirectory.resolve("data").resolve("0");
        random = getRandomPrintSeed();
        files = new ArrayList<>();
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
    public static void createDummyFile(final PcesFile descriptor) throws IOException {
        final Path parentDir = descriptor.getPath().getParent();
        if (!Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        final SerializableDataOutputStream out = new SerializableDataOutputStream(
                new FileOutputStream(descriptor.getPath().toFile()));
        out.writeInt(PcesMutableFile.FILE_VERSION);
        out.writeNormalisedString("foo bar baz");
        out.close();
    }

    @Test
    @DisplayName("Read Files In Order Test")
    void readFilesInOrderTest() throws IOException {
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

        final PcesFileTracker fileTracker =
                PcesFileReader.readFilesFromDisk(buildContext(), TestRecycleBin.getInstance(), fileDirectory, 0, false);

        assertIteratorEquality(files.iterator(), fileTracker.getFileIterator(NO_MINIMUM_GENERATION, 0));

        assertIteratorEquality(
                files.iterator(), fileTracker.getFileIterator(files.getFirst().getMaximumGeneration(), 0));

        // attempt to start a non-existent generation
        assertIteratorEquality(files.iterator(), fileTracker.getFileIterator(nonExistentGeneration, 0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Read Files In Order Gap Test")
    void readFilesInOrderGapTest(final boolean permitGaps) throws IOException {
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
            final PcesFileTracker fileTracker = PcesFileReader.readFilesFromDisk(
                    platformContext, TestRecycleBin.getInstance(), fileDirectory, 0, true);
            // Gaps are allowed. We should see all files except for the one that was skipped.
            assertIteratorEquality(files.iterator(), fileTracker.getFileIterator(NO_MINIMUM_GENERATION, 0));
        } else {
            // Gaps are not allowed.
            assertThrows(
                    IllegalStateException.class,
                    () -> PcesFileReader.readFilesFromDisk(
                            platformContext, TestRecycleBin.getInstance(), fileDirectory, 0, permitGaps));
        }
    }

    @Test
    @DisplayName("Read Files From Middle Test")
    void readFilesFromMiddleTest() throws IOException {
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

        final PcesFileTracker fileTracker =
                PcesFileReader.readFilesFromDisk(buildContext(), TestRecycleBin.getInstance(), fileDirectory, 0, false);

        // For this test, we want to iterate over files so that we are guaranteed to observe every event
        // with a generation greater than or equal to the target generation. Choose a generation that falls
        // roughly in the middle of the sequence of files.
        final long targetGeneration = (files.getFirst().getMaximumGeneration()
                        + files.get(fileCount - 1).getMaximumGeneration())
                / 2;

        final List<PcesFile> iteratedFiles = new ArrayList<>();
        fileTracker.getFileIterator(targetGeneration, 0).forEachRemaining(iteratedFiles::add);

        // Find the index in the file list that was returned first by the iterator
        int indexOfFirstFile = 0;
        for (; indexOfFirstFile < fileCount; indexOfFirstFile++) {
            if (files.get(indexOfFirstFile).equals(iteratedFiles.getFirst())) {
                break;
            }
        }

        // The file immediately before the returned file should not contain any targeted events
        assertTrue(files.get(indexOfFirstFile - 1).getMaximumGeneration() < targetGeneration);

        // The first file returned from the iterator should
        // have a maximum generation greater than or equal to the target generation.
        assertTrue(iteratedFiles.getFirst().getMaximumGeneration() >= targetGeneration);

        // Make sure that the iterator returns files in the correct order.
        final List<PcesFile> expectedFiles = new ArrayList<>(iteratedFiles.size());
        for (int index = indexOfFirstFile; index < fileCount; index++) {
            expectedFiles.add(files.get(index));
        }
        assertIteratorEquality(expectedFiles.iterator(), iteratedFiles.iterator());
    }

    /**
     * Similar to the other test that starts iteration in the middle, except that files will have the same
     * generational
     * bounds with high probability. Not a scenario we are likely to encounter in production, but it's a tricky
     * edge
     * case we need to handle elegantly.
     */
    @Test
    @DisplayName("Read Files From Middle Repeating Generations Test")
    void readFilesFromMiddleRepeatingGenerationsTest() throws IOException {
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

        final PcesFileTracker fileTracker =
                PcesFileReader.readFilesFromDisk(buildContext(), TestRecycleBin.getInstance(), fileDirectory, 0, false);

        // For this test, we want to iterate over files so that we are guaranteed to observe every event
        // with a generation greater than or equal to the target generation. Choose a generation that falls
        // roughly in the middle of the sequence of files.
        final long targetGeneration = (files.getFirst().getMaximumGeneration()
                        + files.get(fileCount - 1).getMaximumGeneration())
                / 2;

        final List<PcesFile> iteratedFiles = new ArrayList<>();
        fileTracker.getFileIterator(targetGeneration, 0).forEachRemaining(iteratedFiles::add);

        // Find the index in the file list that was returned first by the iterator
        int indexOfFirstFile = 0;
        for (; indexOfFirstFile < fileCount; indexOfFirstFile++) {
            if (files.get(indexOfFirstFile).equals(iteratedFiles.getFirst())) {
                break;
            }
        }

        // The file immediately before the returned file should not contain any targeted events
        assertTrue(files.get(indexOfFirstFile - 1).getMaximumGeneration() < targetGeneration);

        // The first file returned from the iterator should
        // have a maximum generation greater than or equal to the target generation.
        assertTrue(iteratedFiles.getFirst().getMaximumGeneration() >= targetGeneration);

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

        final PcesFileTracker fileTracker =
                PcesFileReader.readFilesFromDisk(buildContext(), TestRecycleBin.getInstance(), fileDirectory, 0, false);

        // Request a generation higher than all files in the data store
        final long targetGeneration = files.get(fileCount - 1).getMaximumGeneration() + 1;

        final Iterator<PcesFile> iterator = fileTracker.getFileIterator(targetGeneration, 0);
        assertFalse(iterator.hasNext());
    }

    @Test
    @DisplayName("Read Files From Empty Stream Test")
    void readFilesFromEmptyStreamTest() {
        assertThrows(
                NoSuchFileException.class,
                () -> PcesFileReader.readFilesFromDisk(
                        buildContext(), TestRecycleBin.getInstance(), fileDirectory, 0, false));
    }

    /**
     * When fixing a discontinuity, invalid files are moved to a "recycle bin" directory. This method validates
     * that
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
     * In this test, a discontinuity is placed in the middle of the stream. We begin iterating at the first file
     * in the
     * stream.
     */
    @Test
    @DisplayName("Start And First File Discontinuity In Middle Test")
    void startAtFirstFileDiscontinuityInMiddleTest() throws IOException {
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
        final PcesFileTracker fileTracker1 =
                PcesFileReader.readFilesFromDisk(platformContext, recycleBin, fileDirectory, startingRound1, false);
        assertIteratorEquality(
                filesAfterDiscontinuity.iterator(),
                fileTracker1.getFileIterator(NO_MINIMUM_GENERATION, startingRound1));

        // Scenario 2: choose an origin that lands after the discontinuity.
        final long startingRound2 = random.nextLong(origin + 1, origin + 1000);
        final PcesFileTracker fileTracker2 =
                PcesFileReader.readFilesFromDisk(platformContext, recycleBin, fileDirectory, startingRound2, false);
        assertIteratorEquality(
                filesAfterDiscontinuity.iterator(),
                fileTracker2.getFileIterator(NO_MINIMUM_GENERATION, startingRound2));

        // Scenario 3: choose an origin that comes before the discontinuity. This will cause the files
        // after the discontinuity to be deleted.
        final long startingRound3 = random.nextLong(startingOrigin, origin - 1);
        final PcesFileTracker fileTracker3 =
                PcesFileReader.readFilesFromDisk(platformContext, recycleBin, fileDirectory, startingRound3, false);

        assertIteratorEquality(
                filesBeforeDiscontinuity.iterator(),
                fileTracker3.getFileIterator(NO_MINIMUM_GENERATION, startingRound3));

        validateRecycledFiles(filesBeforeDiscontinuity, files, platformContext);

        // Scenario 4: choose an origin that is incompatible with all state files. This will cause all remaining
        // files to be deleted.
        final long startingRound4 = 0;
        final PcesFileTracker fileTracker4 =
                PcesFileReader.readFilesFromDisk(platformContext, recycleBin, fileDirectory, startingRound4, false);

        assertIteratorEquality(
                Collections.emptyIterator(), fileTracker4.getFileIterator(NO_MINIMUM_GENERATION, startingRound4));

        validateRecycledFiles(List.of(), files, platformContext);
    }

    /**
     * In this test, a discontinuity is placed in the middle of the stream. We begin iterating at a file that
     * comes
     * before the discontinuity, but it isn't the first file in the stream.
     */
    @Test
    @DisplayName("Start At Middle File Discontinuity In Middle Test")
    void startAtMiddleFileDiscontinuityInMiddleTest() throws IOException {
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
        final long startGeneration = files.getFirst().getMaximumGeneration();

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
        final PcesFileTracker fileTracker1 =
                PcesFileReader.readFilesFromDisk(platformContext, recycleBin, fileDirectory, startingRound1, false);
        assertIteratorEquality(
                filesAfterDiscontinuity.iterator(), fileTracker1.getFileIterator(startGeneration, startingRound1));

        // Scenario 2: choose an origin that lands after the discontinuity.
        final long startingRound2 = random.nextLong(origin + 1, origin + 1000);
        final PcesFileTracker fileTracker2 =
                PcesFileReader.readFilesFromDisk(platformContext, recycleBin, fileDirectory, startingRound2, false);
        assertIteratorEquality(
                filesAfterDiscontinuity.iterator(), fileTracker2.getFileIterator(startGeneration, startingRound2));

        // Scenario 3: choose an origin that comes before the discontinuity. This will cause the files
        // after the discontinuity to be deleted.
        final long startingRound3 = random.nextLong(startingOrigin, origin - 1);
        final PcesFileTracker fileTracker3 =
                PcesFileReader.readFilesFromDisk(platformContext, recycleBin, fileDirectory, startingRound3, false);
        assertIteratorEquality(
                filesBeforeDiscontinuity.iterator(), fileTracker3.getFileIterator(startGeneration, startingRound3));

        validateRecycledFiles(filesBeforeDiscontinuity, files, platformContext);

        // Scenario 4: choose an origin that is incompatible with all state files. This will cause all remaining
        // files to be deleted.
        final long startingRound4 = 0;
        final PcesFileTracker fileTracker4 =
                PcesFileReader.readFilesFromDisk(platformContext, recycleBin, fileDirectory, startingRound4, false);
        assertIteratorEquality(
                Collections.emptyIterator(), fileTracker4.getFileIterator(startGeneration, startingRound4));

        validateRecycledFiles(List.of(), files, platformContext);
    }

    /**
     * In this test, a discontinuity is placed in the middle of the stream, and we begin iterating on that exact
     * file.
     */
    @Test
    @DisplayName("Start At Middle File Discontinuity In Middle Test")
    void startAtDiscontinuityInMiddleTest() throws IOException {
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
        final long startGeneration = filesAfterDiscontinuity.getFirst().getMaximumGeneration();

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
        final PcesFileTracker fileTracker1 =
                PcesFileReader.readFilesFromDisk(platformContext, recycleBin, fileDirectory, startingRound1, false);
        assertIteratorEquality(
                filesAfterDiscontinuity.iterator(), fileTracker1.getFileIterator(startGeneration, startingRound1));

        // Scenario 2: choose an origin that lands after the discontinuity.
        final long startingRound2 = random.nextLong(origin + 1, origin + 1000);
        final PcesFileTracker fileTracker2 =
                PcesFileReader.readFilesFromDisk(platformContext, recycleBin, fileDirectory, startingRound2, false);
        assertIteratorEquality(
                filesAfterDiscontinuity.iterator(), fileTracker2.getFileIterator(startGeneration, startingRound2));

        // Scenario 3: choose an origin that comes before the discontinuity. This will cause the files
        // after the discontinuity to be deleted.
        final long startingRound3 = random.nextLong(startingOrigin, origin - 1);
        final PcesFileTracker fileTracker3 =
                PcesFileReader.readFilesFromDisk(platformContext, recycleBin, fileDirectory, startingRound3, false);
        // There is no files with a compatible origin and events with generations in the span we want.
        assertIteratorEquality(
                Collections.emptyIterator(), fileTracker3.getFileIterator(startGeneration, startingRound3));

        validateRecycledFiles(filesBeforeDiscontinuity, files, platformContext);

        // Scenario 4: choose an origin that is incompatible with all state files. This will cause all remaining
        // files to be deleted.
        final long startingRound4 = 0;
        final PcesFileTracker fileTracker4 =
                PcesFileReader.readFilesFromDisk(platformContext, recycleBin, fileDirectory, startingRound4, false);
        assertIteratorEquality(
                Collections.emptyIterator(), fileTracker4.getFileIterator(startGeneration, startingRound4));

        validateRecycledFiles(List.of(), files, platformContext);
    }

    /**
     * In this test, a discontinuity is placed in the middle of the stream, and we begin iterating after that
     * file.
     */
    @Test
    @DisplayName("Start After Discontinuity In Middle Test")
    void startAfterDiscontinuityInMiddleTest() throws IOException {
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
            if (sequenceNumber >= discontinuitySequenceNumber) {
                filesAfterDiscontinuity.add(file);
            } else {
                filesBeforeDiscontinuity.add(file);
            }
            createDummyFile(file);
        }

        // Note that the file at index 0 is not the first file in the stream,
        // but it is the first file we want to iterate
        final long startGeneration = filesAfterDiscontinuity.getFirst().getMaximumGeneration();

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
        final PcesFileTracker fileTracker1 =
                PcesFileReader.readFilesFromDisk(platformContext, recycleBin, fileDirectory, startingRound1, false);
        assertIteratorEquality(
                filesAfterDiscontinuity.iterator(), fileTracker1.getFileIterator(startGeneration, startingRound1));

        // Scenario 2: choose an origin that lands after the discontinuity.
        final long startingRound2 = random.nextLong(origin + 1, origin + 1000);
        final PcesFileTracker fileTracker2 =
                PcesFileReader.readFilesFromDisk(platformContext, recycleBin, fileDirectory, startingRound2, false);
        assertIteratorEquality(
                filesAfterDiscontinuity.iterator(), fileTracker2.getFileIterator(startGeneration, startingRound2));

        // Scenario 3: choose an origin that comes before the discontinuity. This will cause the files
        // after the discontinuity to be deleted.
        final long startingRound3 = random.nextLong(startingOrigin, origin - 1);
        final PcesFileTracker fileTracker3 =
                PcesFileReader.readFilesFromDisk(platformContext, recycleBin, fileDirectory, startingRound3, false);
        assertIteratorEquality(
                Collections.emptyIterator(), fileTracker3.getFileIterator(startGeneration, startingRound3));

        validateRecycledFiles(filesBeforeDiscontinuity, files, platformContext);

        // Scenario 4: choose an origin that is incompatible with all state files. This will cause all remaining
        // files to be deleted.
        final long startingRound4 = 0;
        final PcesFileTracker fileTracker4 =
                PcesFileReader.readFilesFromDisk(platformContext, recycleBin, fileDirectory, startingRound4, false);
        assertIteratorEquality(
                Collections.emptyIterator(), fileTracker4.getFileIterator(startGeneration, startingRound4));

        validateRecycledFiles(List.of(), files, platformContext);
    }
}
