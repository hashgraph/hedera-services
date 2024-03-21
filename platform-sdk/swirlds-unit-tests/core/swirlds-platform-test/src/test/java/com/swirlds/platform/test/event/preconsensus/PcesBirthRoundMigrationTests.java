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

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomInstant;
import static com.swirlds.platform.consensus.ConsensusConstants.ROUND_FIRST;
import static com.swirlds.platform.event.AncientMode.BIRTH_ROUND_THRESHOLD;
import static com.swirlds.platform.event.AncientMode.GENERATION_THRESHOLD;
import static com.swirlds.platform.event.preconsensus.PcesBirthRoundMigration.findPcesFiles;
import static com.swirlds.platform.test.consensus.ConsensusTestArgs.DEFAULT_PLATFORM_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.io.config.RecycleBinConfig_;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.io.utility.RecycleBinImpl;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.preconsensus.PcesBirthRoundMigration;
import com.swirlds.platform.event.preconsensus.PcesConfig_;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PcesBirthRoundMigrationTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    private Path testDirectory;

    private Path recycleBinPath;
    private Path pcesPath;
    private Path temporaryFilePath;

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
    }

    @BeforeEach
    void beforeEach() throws IOException {
        if (Files.exists(testDirectory)) {
            FileUtils.deleteDirectory(testDirectory);
        }

        Files.createDirectories(testDirectory);
        recycleBinPath = testDirectory.resolve("recycle-bin");
        pcesPath = testDirectory.resolve("pces");
        temporaryFilePath = testDirectory.resolve("tmp");

        StaticSoftwareVersion.setSoftwareVersion(new BasicSoftwareVersion(0));
    }

    @AfterEach
    void afterEach() throws IOException {
        if (Files.exists(testDirectory)) {
            FileUtils.deleteDirectory(testDirectory);
        }

        StaticSoftwareVersion.reset();
    }

    /**
     * Describes the PCES stream that was writtne by {@link #generateLegacyPcesFiles(Random, DiscontinuityType)}
     *
     * @param files                  a list of files that were written
     * @param events                 a list of all events in the stream, if there is a discontinuty then only include
     *                               events after the discontinuity
     */
    private record PcesFilesWritten(@NonNull List<PcesFile> files, @NonNull List<GossipEvent> events) {}

    /**
     * Generate a bunch of PCES files in the legacy format.
     *
     * @param random            the random number generator
     * @param discontinuityType whether to introduce a discontinuity in the stream and the type of the discontinuity
     * @return a list of events that were written to disk
     */
    @NonNull
    private PcesFilesWritten generateLegacyPcesFiles(
            @NonNull final Random random, final DiscontinuityType discontinuityType) throws IOException {

        final int eventCount = 1000;
        final int fileCount = 10;
        final int eventsPerFile = eventCount / fileCount;
        final Instant startingTime = randomInstant(random);

        final StandardGraphGenerator generator = new StandardGraphGenerator(
                DEFAULT_PLATFORM_CONTEXT,
                random.nextLong(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource(),
                new StandardEventSource());

        final List<GossipEvent> events = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            events.add(generator.generateEvent().getBaseEvent());
        }
        events.sort(Comparator.comparingLong(GossipEvent::getGeneration));

        final Path fullPcesPath = pcesPath.resolve("0");

        final List<PcesFile> files = new ArrayList<>();
        long origin = 0;
        boolean discontinutiyIntroduced = false;
        final List<GossipEvent> postDiscontinuityEvents = new ArrayList<>();
        for (int fileIndex = 0; fileIndex < fileCount; fileIndex++) {

            if (discontinuityType == DiscontinuityType.IN_EVENTS_THAT_ARE_NOT_MIGRATED && fileIndex == fileCount / 3) {
                // introduce a discontinuity in events that are not migrated.
                // Events after the 1/2 way point are migrated, so we won't be to
                // events that we are going to migrate yet.
                origin = random.nextLong(10, 20);
                discontinutiyIntroduced = true;
            }

            if (discontinuityType == DiscontinuityType.IN_EVENTS_THAT_ARE_MIGRATED && fileIndex == 2 * fileCount / 3) {
                // introduce a discontinuity in events that are migrated.
                // Events after the 1/2 way point are migrated, so we will be
                // in the middle of writing migration eligible events.
                origin = random.nextLong(10, 20);
                discontinutiyIntroduced = true;
            }

            final List<GossipEvent> fileEvents =
                    events.subList(fileIndex * eventsPerFile, (fileIndex + 1) * eventsPerFile);

            final long lowerGenerationBound = fileEvents.getFirst().getGeneration();
            final long upperGenerationBound = fileEvents.getLast().getGeneration();

            final PcesFile file = PcesFile.of(
                    GENERATION_THRESHOLD,
                    startingTime.plusSeconds(fileIndex),
                    fileIndex,
                    lowerGenerationBound,
                    upperGenerationBound,
                    origin,
                    fullPcesPath);
            files.add(file);

            final PcesMutableFile mutableFile = file.getMutableFile();
            for (final GossipEvent event : fileEvents) {
                mutableFile.writeEvent(event);
                if (discontinutiyIntroduced || discontinuityType == DiscontinuityType.NONE) {
                    postDiscontinuityEvents.add(event);
                }
            }
            mutableFile.close();
        }

        return new PcesFilesWritten(files, postDiscontinuityEvents);
    }

    private enum DiscontinuityType {
        /**
         * Ordinal 0. No discontinuities.
         */
        NONE,
        /**
         * Ordinal 1. Discontinuity int the middle events that are not migrated. None of the migration eligible events
         * should be effected by the discontinuity.
         */
        IN_EVENTS_THAT_ARE_NOT_MIGRATED,
        /**
         * Ordinal 2. Discontinuity in the middle of events that are migration eligible. None of the events that come
         * before the discontinuity should be migrated.
         */
        IN_EVENTS_THAT_ARE_MIGRATED
    }

    /**
     * Validate the basic migration workflow.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void standardMigrationTest(final int discontinuity) throws IOException {
        final Random random = getRandomPrintSeed();
        final DiscontinuityType discontinuityType = DiscontinuityType.values()[discontinuity];

        final Configuration configuration = new TestConfigBuilder()
                .withValue(RecycleBinConfig_.RECYCLE_BIN_PATH, recycleBinPath)
                .withValue(PcesConfig_.DATABASE_DIRECTORY, pcesPath)
                .getOrCreateConfig();
        TemporaryFileBuilder.overrideTemporaryFileLocation(temporaryFilePath);

        final FakeTime time = new FakeTime();

        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withTime(time)
                .withConfiguration(configuration)
                .build();
        final RecycleBin recycleBin = new RecycleBinImpl(
                configuration,
                platformContext.getMetrics(),
                AdHocThreadManager.getStaticThreadManager(),
                platformContext.getTime(),
                new NodeId(0));

        final PcesFilesWritten filesWritten = generateLegacyPcesFiles(random, discontinuityType);

        // Choose the generation from the middle event as minimum judge generation prior to migration.
        // This will put roughly half of events on either side of the migration boundary.
        final long middleGeneration =
                filesWritten.events.get(filesWritten.events.size() / 2).getGeneration();

        final long migrationRound = random.nextLong(100, 1000);

        PcesBirthRoundMigration.migratePcesToBirthRoundMode(
                platformContext, recycleBin, new NodeId(0), migrationRound, middleGeneration);

        // We should not find any generation based PCES files in the database directory.
        assertTrue(findPcesFiles(pcesPath, GENERATION_THRESHOLD).isEmpty());

        // We should find exactly one birth round based PCES file in the database directory.
        final List<PcesFile> birthRoundFiles = findPcesFiles(pcesPath, BIRTH_ROUND_THRESHOLD);
        final PcesFile birthRoundFile = birthRoundFiles.getFirst();
        assertEquals(1, birthRoundFiles.size());

        // For every original PCES file, we should find a copy of that file in the recycle bin.
        final List<PcesFile> recycleBinFiles = findPcesFiles(recycleBinPath, GENERATION_THRESHOLD);
        assertEquals(filesWritten.files().size(), recycleBinFiles.size());
        final Set<String> recycleBinFileNames = new HashSet<>();
        for (final PcesFile file : recycleBinFiles) {
            recycleBinFileNames.add(file.getFileName());
        }
        assertEquals(filesWritten.files().size(), recycleBinFileNames.size());
        for (final PcesFile file : filesWritten.files()) {
            assertTrue(recycleBinFileNames.contains(file.getFileName()));
        }

        // Read the events in the new file, make sure we see all events with a generation greater than
        // or equal to the middle generation.
        final List<GossipEvent> expectedEvents = new ArrayList<>();
        for (final GossipEvent event : filesWritten.events) {
            if (event.getGeneration() >= middleGeneration) {
                expectedEvents.add(event);
            }
        }
        final IOIterator<GossipEvent> iterator = new PcesFileIterator(birthRoundFile, 1, BIRTH_ROUND_THRESHOLD);
        final List<GossipEvent> actualEvents = new ArrayList<>();
        while (iterator.hasNext()) {
            actualEvents.add(iterator.next());
        }
        assertEquals(expectedEvents, actualEvents);

        // Verify that the new file's parameters are valid.
        assertEquals(BIRTH_ROUND_THRESHOLD, birthRoundFile.getFileType());
        assertEquals(time.now(), birthRoundFile.getTimestamp());
        assertEquals(0, birthRoundFile.getSequenceNumber());
        assertEquals(migrationRound, birthRoundFile.getLowerBound());
        assertEquals(migrationRound, birthRoundFile.getUpperBound());
        assertEquals(migrationRound, birthRoundFile.getOrigin());

        // Running migration a second time should have no side effects.
        final Set<Path> allFiles = new HashSet<>();
        try (final Stream<Path> stream = Files.walk(testDirectory)) {
            stream.forEach(allFiles::add);
        }

        PcesBirthRoundMigration.migratePcesToBirthRoundMode(
                platformContext, recycleBin, new NodeId(0), migrationRound, middleGeneration);

        final Set<Path> allFilesAfterSecondMigration = new HashSet<>();
        try (final Stream<Path> stream = Files.walk(testDirectory)) {
            stream.forEach(allFilesAfterSecondMigration::add);
        }

        assertEquals(allFiles, allFilesAfterSecondMigration);
    }

    @Test
    void genesisWithBirthRoundsTest() throws IOException {
        final Configuration configuration = new TestConfigBuilder()
                .withValue(RecycleBinConfig_.RECYCLE_BIN_PATH, recycleBinPath)
                .withValue(PcesConfig_.DATABASE_DIRECTORY, pcesPath)
                .getOrCreateConfig();
        TemporaryFileBuilder.overrideTemporaryFileLocation(temporaryFilePath);

        final FakeTime time = new FakeTime();

        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withTime(time)
                .withConfiguration(configuration)
                .build();
        final RecycleBin recycleBin = new RecycleBinImpl(
                configuration,
                platformContext.getMetrics(),
                AdHocThreadManager.getStaticThreadManager(),
                platformContext.getTime(),
                new NodeId(0));

        // should not throw
        PcesBirthRoundMigration.migratePcesToBirthRoundMode(
                platformContext, recycleBin, new NodeId(0), ROUND_FIRST, -1);
    }

    @Test
    void botchedMigrationRecoveryTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final Configuration configuration = new TestConfigBuilder()
                .withValue(RecycleBinConfig_.RECYCLE_BIN_PATH, recycleBinPath)
                .withValue(PcesConfig_.DATABASE_DIRECTORY, pcesPath)
                .getOrCreateConfig();
        TemporaryFileBuilder.overrideTemporaryFileLocation(temporaryFilePath);

        final FakeTime time = new FakeTime();

        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withTime(time)
                .withConfiguration(configuration)
                .build();
        final RecycleBin recycleBin = new RecycleBinImpl(
                configuration,
                platformContext.getMetrics(),
                AdHocThreadManager.getStaticThreadManager(),
                platformContext.getTime(),
                new NodeId(0));

        final PcesFilesWritten filesWritten = generateLegacyPcesFiles(random, DiscontinuityType.NONE);

        // Choose the generation from the middle event as minimum judge generation prior to migration.
        // This will put roughly half of events on either side of the migration boundary.
        final long middleGeneration =
                filesWritten.events.get(filesWritten.events.size() / 2).getGeneration();

        final long migrationRound = random.nextLong(1, 1000);

        PcesBirthRoundMigration.migratePcesToBirthRoundMode(
                platformContext, recycleBin, new NodeId(0), migrationRound, middleGeneration);

        // Some funny business: copy the original files back into the PCES database directory.
        // This simulates a crash in the middle of the migration process after we have created
        // the migration file (this is atomic) and before we fully clean up the original files.
        final Path destination = pcesPath.resolve("0");
        try (final Stream<Path> stream = Files.walk(recycleBinPath)) {
            stream.forEach(path -> {
                if (Files.isRegularFile(path)) {
                    try {
                        Files.copy(path, destination.resolve(path.getFileName()));
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        // Run migration again.
        PcesBirthRoundMigration.migratePcesToBirthRoundMode(
                platformContext, recycleBin, new NodeId(0), migrationRound, middleGeneration);

        // We should not find any generation based PCES files in the database directory.
        assertTrue(findPcesFiles(pcesPath, GENERATION_THRESHOLD).isEmpty());

        // We should find exactly one birth round based PCES file in the database directory.
        final List<PcesFile> birthRoundFiles = findPcesFiles(pcesPath, BIRTH_ROUND_THRESHOLD);
        final PcesFile birthRoundFile = birthRoundFiles.getFirst();
        assertEquals(1, birthRoundFiles.size());

        // For every original PCES file, we should find a copy of that file in the recycle bin twice:
        // once from the original migration, and a second from the backup made during the botched recovery cleanup.
        final List<PcesFile> recycleBinFiles = findPcesFiles(recycleBinPath, GENERATION_THRESHOLD);
        assertEquals(filesWritten.files().size() * 2, recycleBinFiles.size());
        final Set<String> recycleBinFileNames = new HashSet<>();
        for (final PcesFile file : recycleBinFiles) {
            recycleBinFileNames.add(file.getFileName());
        }
        assertEquals(filesWritten.files().size(), recycleBinFileNames.size());
        for (final PcesFile file : filesWritten.files()) {
            assertTrue(recycleBinFileNames.contains(file.getFileName()));
        }

        // Read the events in the new file, make sure we see all events with a generation greater than
        // or equal to the middle generation.
        final List<GossipEvent> expectedEvents = new ArrayList<>();
        for (final GossipEvent event : filesWritten.events) {
            if (event.getGeneration() >= middleGeneration) {
                expectedEvents.add(event);
            }
        }
        final IOIterator<GossipEvent> iterator = new PcesFileIterator(birthRoundFile, 1, BIRTH_ROUND_THRESHOLD);
        final List<GossipEvent> actualEvents = new ArrayList<>();
        while (iterator.hasNext()) {
            actualEvents.add(iterator.next());
        }
        assertEquals(expectedEvents, actualEvents);

        // Verify that the new file's parameters are valid.
        assertEquals(BIRTH_ROUND_THRESHOLD, birthRoundFile.getFileType());
        assertEquals(time.now(), birthRoundFile.getTimestamp());
        assertEquals(0, birthRoundFile.getSequenceNumber());
        assertEquals(migrationRound, birthRoundFile.getLowerBound());
        assertEquals(migrationRound, birthRoundFile.getUpperBound());
        assertEquals(migrationRound, birthRoundFile.getOrigin());

        // Running migration a second time should have no side effects.
        final Set<Path> allFiles = new HashSet<>();
        try (final Stream<Path> stream = Files.walk(testDirectory)) {
            stream.forEach(allFiles::add);
        }

        PcesBirthRoundMigration.migratePcesToBirthRoundMode(
                platformContext, recycleBin, new NodeId(0), migrationRound, middleGeneration);

        final Set<Path> allFilesAfterSecondMigration = new HashSet<>();
        try (final Stream<Path> stream = Files.walk(testDirectory)) {
            stream.forEach(allFilesAfterSecondMigration::add);
        }

        assertEquals(allFiles, allFilesAfterSecondMigration);
    }
}
