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

import com.swirlds.common.context.PlatformContext;
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
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.preconsensus.PcesBirthRoundMigration;
import com.swirlds.platform.event.preconsensus.PcesConfig_;
import com.swirlds.platform.event.preconsensus.PcesFile;
import com.swirlds.platform.event.preconsensus.PcesMutableFile;
import com.swirlds.platform.test.fixtures.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.fixtures.event.source.StandardEventSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
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

    @BeforeEach
    void beforeEach() throws IOException {
        if (Files.exists(testDirectory)) {
            FileUtils.deleteDirectory(testDirectory);
        }

        Files.createDirectories(testDirectory);
        recycleBinPath = testDirectory.resolve("recycle-bin");
        pcesPath = testDirectory.resolve("pces").resolve("0"); // simulate node 0
        temporaryFilePath = testDirectory.resolve("tmp");
    }

    @AfterEach
    void afterEach() throws IOException {
        if (Files.exists(testDirectory)) {
            FileUtils.deleteDirectory(testDirectory);
        }
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

        final List<PcesFile> files = new ArrayList<>();
        for (int fileIndex = 0; fileIndex < fileCount; fileIndex++) {

            final List<GossipEvent> fileEvents =
                    events.subList(fileIndex * eventsPerFile, (fileIndex + 1) * eventsPerFile);

            final long lowerGenerationBound = fileEvents.getFirst().getGeneration();
            final long upperGenerationBound = fileEvents.getLast().getGeneration();

            final PcesFile file = PcesFile.of(
                    AncientMode.GENERATION_THRESHOLD,
                    startingTime.plusSeconds(fileIndex),
                    fileIndex,
                    lowerGenerationBound,
                    upperGenerationBound,
                    0,
                    pcesPath);
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

        final PlatformContext platformContext = TestPlatformContextBuilder.create()
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

        // TODO create PCES files
        // TODO migrate
        // TODO verify recycled files
        // TODO verify existence of new files
        // TODO verify there are no unexpected old files

    }

    void botchedMigrationRecoveryTest() {}

    void genesisWithBirthRoundsTest() {}

    void migrationAlreadyCompletedTest() {}

    void migrationWithDiscontinuities() {}
}
