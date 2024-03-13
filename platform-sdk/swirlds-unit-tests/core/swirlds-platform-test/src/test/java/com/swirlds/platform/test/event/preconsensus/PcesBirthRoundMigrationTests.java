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
import static com.swirlds.platform.event.AncientMode.BIRTH_ROUND_THRESHOLD;
import static com.swirlds.platform.event.AncientMode.GENERATION_THRESHOLD;
import static com.swirlds.platform.event.preconsensus.PcesBirthRoundMigration.findPcesFiles;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

        System.out.println(testDirectory); // TODO remove
    }

    @AfterEach
    void afterEach() throws IOException {
        if (Files.exists(testDirectory)) {
            FileUtils.deleteDirectory(testDirectory);
        }

        StaticSoftwareVersion.reset();
    }

    private record PcesFilesWritten(@NonNull List<PcesFile> files, @NonNull List<GossipEvent> events) {}

    /**
     * Generate a bunch of PCES files in the legacy format.
     *
     * @param random the random number generator
     * @return a list of events that were written to disk
     */
    @NonNull
    private PcesFilesWritten generateLegacyPcesFiles(@NonNull final Random random) throws IOException {

        final int eventCount = 1000;
        final int fileCount = 10;
        final int eventsPerFile = eventCount / fileCount;
        final Instant startingTime = randomInstant(random);

        final StandardGraphGenerator generator = new StandardGraphGenerator(
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
        for (int fileIndex = 0; fileIndex < fileCount; fileIndex++) {

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
                    0,
                    fullPcesPath);
            files.add(file);

            final PcesMutableFile mutableFile = file.getMutableFile();
            for (final GossipEvent event : fileEvents) {
                mutableFile.writeEvent(event);
            }
            mutableFile.close();
        }

        return new PcesFilesWritten(files, events);
    }

    /**
     * Validate the basic migration workflow.
     */
    @Test
    void standardMigrationTest() throws IOException {
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

        final PcesFilesWritten filesWritten = generateLegacyPcesFiles(random);

        // Choose the generation from the middle event as minimum judge generation prior to migration.
        // This will put roughly half of events on either side of the migration boundary.
        final long middleGeneration =
                filesWritten.events.get(filesWritten.events.size() / 2).getGeneration();

        final long migrationRound = random.nextLong(1, 1000);

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
    }

    void botchedMigrationRecoveryTest() {}

    void genesisWithBirthRoundsTest() {}

    void migrationAlreadyCompletedTest() {}

    void migrationWithDiscontinuities() {}
}
