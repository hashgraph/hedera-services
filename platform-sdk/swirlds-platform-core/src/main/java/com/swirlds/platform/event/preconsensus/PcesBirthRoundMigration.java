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

package com.swirlds.platform.event.preconsensus;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.platform.event.AncientMode.BIRTH_ROUND_THRESHOLD;
import static com.swirlds.platform.event.AncientMode.GENERATION_THRESHOLD;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is capable of migrating a PCES in generation mode to a PCES in birth round mode.
 */
public final class PcesBirthRoundMigration {

    private static final Logger logger = LogManager.getLogger(PcesBirthRoundMigration.class);

    private PcesBirthRoundMigration() {}

    /**
     * Migrate a PCES in generation mode to a PCES in birth round mode.
     *
     * @param platformContext                        the platform context
     * @param migrationRound                         the round at which the migration is occurring, this will be equal
     *                                               to the round number of the initial state
     * @param minimumJudgeGenerationInMigrationRound the minimum judge generation in the migration round
     */
    public static void migrateToBirthRoundMode(
            @NonNull final PlatformContext platformContext,
            @NonNull final RecycleBin recycleBin,
            @NonNull final NodeId selfId,
            final long migrationRound,
            final long minimumJudgeGenerationInMigrationRound)
            throws IOException {

        logger.info(
                STARTUP.getMarker(),
                "Migrating PCES to birth round mode. Migration round: {}, minimum judge "
                        + "generation in migration round: {}.",
                migrationRound,
                minimumJudgeGenerationInMigrationRound);

        if (hasMigrationAlreadyCompleted()) {
            logger.error(EXCEPTION.getMarker(), "PCES birth round migration has already been completed.");
            cleanUpOldFilesAfterBotchedMigration();
            return;
        }

        final PcesFileTracker originalFiles = new PcesFileTracker(GENERATION_THRESHOLD);
        makeBackupFiles(recycleBin, originalFiles);

        final List<GossipEvent> eventsToMigrate =
                readEventsToBeMigrated(originalFiles, minimumJudgeGenerationInMigrationRound, migrationRound);

        if (eventsToMigrate.isEmpty()) {
            logger.error(EXCEPTION.getMarker(), "No events to migrate. PCES birth round migration aborted.");
            return;
        }

        migrateEvents(platformContext, selfId, eventsToMigrate, migrationRound);
        cleanUpOldFiles(originalFiles);

        logger.info(STARTUP.getMarker(), "PCES birth round migration complete.");
    }

    /**
     * Check if the migration has already been completed.
     *
     * @return true if the migration has already been completed, false otherwise
     */
    private static boolean hasMigrationAlreadyCompleted() {
        final PcesFileTracker tracker = new PcesFileTracker(BIRTH_ROUND_THRESHOLD);
        return tracker.getFileCount() > 0;
    }

    /**
     * If we observe at least one PCES file in birth round mode, it's possible that the node crashed before it finished
     * cleaning up the old files. This method will clean up any old files that may have been left behind.
     */
    private static void cleanUpOldFilesAfterBotchedMigration() throws IOException {
        final PcesFileTracker tracker = new PcesFileTracker(GENERATION_THRESHOLD);
        logger.info(
                STARTUP.getMarker(),
                "PCES birth round migration has already been completed. Cleaning up old files. There are {} old "
                        + "file(s) to be deleted.",
                tracker.getFileCount());
        cleanUpOldFiles(tracker);
    }

    /**
     * Copy PCES files into recycle bin. A measure to reduce the chances of permanent data loss in the event of a
     * migration failure.
     */
    private static void makeBackupFiles(
            @NonNull final RecycleBin recycleBin, @NonNull final PcesFileTracker originalFiles) throws IOException {

        logger.info(
                STARTUP.getMarker(),
                "There are {} original PCES files prior to migration. Copying files to the recycle bin in case of "
                        + "migration failure.",
                originalFiles.getFileCount());

        final Path copyDirectory = TemporaryFileBuilder.buildTemporaryDirectory("pces-backup");
        final Iterator<PcesFile> originalFileIterator = originalFiles.getFileIterator();
        while (originalFileIterator.hasNext()) {
            final PcesFile file = originalFileIterator.next();
            final Path copy = copyDirectory.resolve(file.getFileName());
            Files.copy(file.getPath(), copy);
        }

        recycleBin.recycle(copyDirectory);
    }

    /**
     * Read all events that will be non-ancient after migration.
     *
     * @param originalFiles                          the original files
     * @param minimumJudgeGenerationInMigrationRound the minimum judge generation in the migration round
     * @param migrationRound                         the migration round
     * @return the events to be migrated
     */
    @NonNull
    private static List<GossipEvent> readEventsToBeMigrated(
            @NonNull final PcesFileTracker originalFiles,
            final long minimumJudgeGenerationInMigrationRound,
            final long migrationRound)
            throws IOException {

        // Gather all events that will be non-ancient after migration. Write them to a new PCES file.
        // The number of events that qualify are expected to be small, so we can gather them all in memory.

        final IOIterator<GossipEvent> iterator =
                originalFiles.getEventIterator(minimumJudgeGenerationInMigrationRound, migrationRound);
        final List<GossipEvent> eventsToMigrate = new ArrayList<>();
        while (iterator.hasNext()) {
            eventsToMigrate.add(iterator.next());
        }
        logger.info(STARTUP.getMarker(), "Found {} events meeting criteria for migration.", eventsToMigrate.size());

        return eventsToMigrate;
    }

    /**
     * Migrate the required events to a new PCES file.
     *
     * @param platformContext the platform context
     * @param selfId          the self ID
     * @param eventsToMigrate the events to migrate
     * @param migrationRound  the migration round
     */
    private static void migrateEvents(
            @NonNull final PlatformContext platformContext,
            @NonNull final NodeId selfId,
            @NonNull final List<GossipEvent> eventsToMigrate,
            final long migrationRound)
            throws IOException {

        // First, write the data to a temporary file. If we crash, easier to recover if this operation is atomic.
        final Path temporaryFile = TemporaryFileBuilder.buildTemporaryFile("new-pces-file");
        final SerializableDataOutputStream outputStream = new SerializableDataOutputStream(
                new BufferedOutputStream(new FileOutputStream(temporaryFile.toFile())));
        outputStream.writeInt(PcesMutableFile.FILE_VERSION);
        for (final GossipEvent event : eventsToMigrate) {
            outputStream.writeSerializable(event, false);
        }
        outputStream.close();

        // Next, move the temporary file to its final location.
        final PcesFile file = PcesFile.of(
                BIRTH_ROUND_THRESHOLD,
                platformContext.getTime().now(),
                0,
                migrationRound,
                migrationRound,
                migrationRound,
                PcesUtilities.getDatabaseDirectory(platformContext, selfId));
        Files.move(temporaryFile, file.getPath(), ATOMIC_MOVE);
        logger.info(STARTUP.getMarker(), "Events written to file {}.", file.getFileName());
    }

    /**
     * Clean up old files..
     *
     * @param originalFiles the original files to be deleted
     */
    private static void cleanUpOldFiles(@NonNull final PcesFileTracker originalFiles) throws IOException {
        logger.info(STARTUP.getMarker(), "Cleaning up old files.");
        final Iterator<PcesFile> originalFileIterator = originalFiles.getFileIterator();
        while (originalFileIterator.hasNext()) {
            originalFileIterator.next().deleteFile(null);
        }
    }
}
