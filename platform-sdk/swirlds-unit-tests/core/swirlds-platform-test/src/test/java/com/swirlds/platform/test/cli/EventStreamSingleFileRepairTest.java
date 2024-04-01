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

package com.swirlds.platform.test.cli;

import static com.swirlds.platform.recovery.internal.EventStreamSingleFileRepairer.DAMAGED_SUFFIX;
import static com.swirlds.platform.recovery.internal.EventStreamSingleFileRepairer.REPAIRED_SUFFIX;
import static com.swirlds.platform.test.consensus.ConsensusTestArgs.DEFAULT_PLATFORM_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.recovery.internal.EventStreamSingleFileIterator;
import com.swirlds.platform.recovery.internal.EventStreamSingleFileRepairer;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.test.consensus.GenerateConsensus;
import com.swirlds.platform.test.fixtures.stream.StreamUtils;
import com.swirlds.platform.test.simulated.RandomSigner;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Deque;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A unit test to validate the repair of a damaged event stream that has been saved to disk.
 */
class EventStreamSingleFileRepairTest {

    /**
     * The temporary directory to use for the unit test.
     */
    @TempDir
    Path tmpDir;

    @BeforeAll
    static void beforeAll() {
        StaticSoftwareVersion.setSoftwareVersion(new BasicSoftwareVersion(1));
    }

    @AfterAll
    static void afterAll() {
        StaticSoftwareVersion.reset();
    }

    /**
     * Generates events, feeds them to consensus, then writes these consensus events to stream files. Once the files are
     * written, it picks the last file, attempts to repair it with no effect, truncates the file, and then repairs it.
     */
    @Test
    void repairFileTest() throws IOException, ConstructableRegistryException {

        createEventStreamFiles();

        // get last file in the event stream and prepare for running repair tests.
        Path pathToLastFile;
        try (final Stream<Path> walk = Files.walk(tmpDir)) {
            pathToLastFile = walk.filter(path -> path.toString().endsWith(".evts"))
                    .reduce(((path, path2) -> path2.compareTo(path) < 0 ? path : path2))
                    .orElseThrow();
        }
        assertNotNull(pathToLastFile, "Path was not provided to the last file in the event stream.");
        final File lastFile = pathToLastFile.toFile();
        final File backupFile = Path.of(pathToLastFile + ".backup").toFile();
        final File repairedFile = Path.of(pathToLastFile + REPAIRED_SUFFIX).toFile();
        final File damagedFile = Path.of(pathToLastFile + DAMAGED_SUFFIX).toFile();

        setupAndAssertCleanStateForUnitTest(lastFile, backupFile, repairedFile, damagedFile);
        assertFileDamage(lastFile, false);
        final EventStreamSingleFileRepairer repairer1 = attemptRepairOfFile(lastFile, false);
        assertBehaviorOnGoodEventStreamFile(lastFile, backupFile, repairedFile, damagedFile, repairer1);
        assertFileDamage(lastFile, false);

        damageTheLastFile(lastFile);
        assertFileDamage(lastFile, true);
        final EventStreamSingleFileRepairer repairer2 = attemptRepairOfFile(lastFile, true);
        assertBehaviorOnDamagedEventStreamFile(lastFile, repairedFile, damagedFile, repairer2);
        assertFileDamage(lastFile, false);
    }

    /**
     * Records a new event stream to files on disk.
     */
    private void createEventStreamFiles() throws ConstructableRegistryException {
        final Random random = RandomUtils.getRandomPrintSeed();
        final int numNodes = 10;
        final int numEvents = 100_000;
        final Duration eventStreamWindowSize = Duration.ofSeconds(1);

        // setup
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");

        // generate consensus events
        final Deque<ConsensusRound> rounds = GenerateConsensus.generateConsensusRounds(
                DEFAULT_PLATFORM_CONTEXT, numNodes, numEvents, random.nextLong());
        if (rounds.isEmpty()) {
            Assertions.fail("events are excepted to reach consensus");
        }

        // write event stream
        StreamUtils.writeRoundsToStream(tmpDir, new RandomSigner(random), eventStreamWindowSize, rounds);
    }

    /**
     * Verifies that the last file exists, creates a backup of the last file, and ensures that none of the other files
     * exist at the start of the unit test.
     *
     * @param lastFile     the last file in the event stream.
     * @param backupFile   the backup file to create.
     * @param repairedFile the repaired file that must not exist.
     * @param damagedFile  the damaged file that must not exist.
     * @throws IOException if any of the file operations throw an exception.
     */
    private void setupAndAssertCleanStateForUnitTest(
            final File lastFile, final File backupFile, final File repairedFile, final File damagedFile)
            throws IOException {
        assertTrue(lastFile.exists(), "The last file must exist.");

        Files.deleteIfExists(backupFile.toPath());
        assertFalse(backupFile.exists(), "The backup of the last file must not exist.");

        // create the backup of the lastFile
        Files.copy(lastFile.toPath(), backupFile.toPath());
        assertTrue(backupFile.exists(), "The backup of the last file must exist at this point");

        Files.deleteIfExists(repairedFile.toPath());
        assertFalse(repairedFile.exists(), "The repaired file exists before we start the unit test.");

        Files.deleteIfExists(damagedFile.toPath());
        assertFalse(damagedFile.exists(), "The damaged file exists before running the unit test.");
    }

    /**
     * Damages the given file by truncating it in half and losing the trailing hash in the event stream.
     *
     * @param lastFile the file to damage
     * @throws IOException if an exception occurs during file truncation.
     */
    private void damageTheLastFile(File lastFile) throws IOException {
        long originalSize = Files.size(lastFile.toPath());
        try (FileOutputStream out = new FileOutputStream(lastFile, true)) {
            FileChannel outChan = out.getChannel();
            outChan.truncate(originalSize / 2);
        }
        long newSize = Files.size(lastFile.toPath());
        assertTrue(newSize < (originalSize / 2 + 1), "The file should be truncated to half its original size.");
    }

    /**
     * Asserts that the damage state of the last file in the event stream is equal to the given isDamaged state.
     *
     * @param lastFile  the last file in the event stream.
     * @param isDamaged the expected state of the file.
     * @throws IOException if any file operations throw an IOException exception.
     */
    private void assertFileDamage(final File lastFile, final boolean isDamaged) throws IOException {
        try (final EventStreamSingleFileIterator iterator =
                new EventStreamSingleFileIterator(lastFile.toPath(), true)) {
            assertDoesNotThrow(
                    () -> {
                        while (iterator.hasNext()) {
                            iterator.next();
                        }
                        assertEquals(isDamaged, iterator.isDamaged(), "the damage state must be " + isDamaged);
                    },
                    "Unexpected exception during iteration.");
        }
    }

    /**
     * Repairs the given event stream file using an EventStreamSingleFileRepairer and asserts that the result of
     * repairing the state matches the given expected repair state.
     *
     * @param lastFile    the event stream file to attempt to repair.
     * @param repairState the expected return value from calling
     *                    {@link EventStreamSingleFileRepairer#repair() repairer.repair()}
     * @return the {@link EventStreamSingleFileRepairer} used to attempt to repair the file.
     * @throws IOException if attempting to repair the file causes an IOException.
     */
    private EventStreamSingleFileRepairer attemptRepairOfFile(final File lastFile, final boolean repairState)
            throws IOException {
        final EventStreamSingleFileRepairer repairer = new EventStreamSingleFileRepairer(lastFile);
        assertDoesNotThrow(
                () -> assertEquals(repairState, repairer.repair(), "The result of repairing the file is wrong."),
                "The result of repairing the file should have been " + repairState);
        return repairer;
    }

    /**
     * Tests the state of the files after attempting to repair an event stream file that is not damaged.
     *
     * @param lastFile     the event stream file resulting from the repair attempt.
     * @param backupFile   the backup of the event stream file at the start of the unit test.
     * @param repairedFile the temporary file created during the repair attempt.
     * @param damagedFile  the backup of the event stream file if it was found to have been damaged.
     * @param repairer     the EventStreamSingleFileRepairer used to attempt to repair the last event stream file.
     * @throws IOException if any file operations throw an IOException.
     */
    private void assertBehaviorOnGoodEventStreamFile(
            final File lastFile,
            final File backupFile,
            final File repairedFile,
            final File damagedFile,
            final EventStreamSingleFileRepairer repairer)
            throws IOException {
        assertTrue(repairer.getEventCount() > 2, "There should be more than 2 events.");
        assertFalse(damagedFile.exists(), "The damaged file should not exist after repairing a good file.");
        assertFalse(repairedFile.exists(), "The repaired file should not exist after repairing a good file.");
        assertEquals(
                Files.mismatch(lastFile.toPath(), backupFile.toPath()),
                -1,
                "The last file has been modified from the backup file.");
    }

    /**
     * Tests the state of the files after attempting to repair an event stream file that is damaged.
     *
     * @param lastFile     the event stream file resulting from the repair attempt.
     * @param repairedFile the temporary file created during the repair attempt.
     * @param damagedFile  the backup of the event stream file if it was found to have been damaged.
     * @param repairer     the EventStreamSingleFileRepairer used to attempt to repair the last event stream file.
     * @throws IOException if any file operations throw an IOException.
     */
    private void assertBehaviorOnDamagedEventStreamFile(
            final File lastFile,
            final File repairedFile,
            final File damagedFile,
            final EventStreamSingleFileRepairer repairer)
            throws IOException {
        assertTrue(repairer.getEventCount() > 0, "The event count of the damaged file must be > 0");
        assertTrue(damagedFile.exists(), "The damage file must exists after a successful repair.");
        assertFalse(repairedFile.exists(), "The repaired file must not exist after a successful repair.");
        assertTrue(
                Files.mismatch(lastFile.toPath(), damagedFile.toPath()) >= 0,
                "The original file and damaged must not match.");
    }
}
