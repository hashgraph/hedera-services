/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.files;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.MERKLE_DB;
import static com.swirlds.merkledb.files.DataFileCommon.formatSizeBytes;
import static com.swirlds.merkledb.files.DataFileCommon.getSizeOfFiles;
import static com.swirlds.merkledb.files.DataFileCommon.getSizeOfFilesByPath;
import static com.swirlds.merkledb.files.DataFileCommon.logMergeStats;

import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.units.UnitConstants;
import com.swirlds.merkledb.CompactionType;
import com.swirlds.merkledb.KeyRange;
import com.swirlds.merkledb.NanoClock;
import com.swirlds.merkledb.collections.CASableLongIndex;
import com.swirlds.merkledb.config.MerkleDbConfig;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is responsible performing compaction of data files in a {@link DataFileCollection}.
 * The compaction is supposed to happen in the background and can be paused and resumed with {@link #pauseCompaction()}
 * and {@link #resumeCompaction()} to prevent compaction from interfering with snapshots.
 */
public class DataFileCompactor {

    private static final Logger logger = LogManager.getLogger(DataFileCompactor.class);

    /**
     * Since {@code com.swirlds.platform.Browser} populates settings, and it is loaded before any
     * application classes that might instantiate a data source, the {@link ConfigurationHolder}
     * holder will have been configured by the time this static initializer runs.
     */
    private static final MerkleDbConfig config = ConfigurationHolder.getConfigData(MerkleDbConfig.class);

    public static final int DEFAULT_COMPACTION_LEVEL = 0;
    public static final int MAX_FIRST_LEVEL_FILES_ALLOWED = 64;

    /** The data file collection to compact */
    private final DataFileCollection<?> dataFileCollection;

    /**
     * A lock used for synchronization between snapshots and compactions. While a compaction is in
     * progress, it runs on its own without any synchronization. However, a few critical sections
     * are protected with this lock: to create a new compaction writer/reader when compaction is
     * started, to copy data items to the current writer and update the corresponding index item,
     * and to close the compaction writer. This mechanism allows snapshots to effectively put
     * compaction on hold, which is critical as snapshots should be as fast as possible, while
     * compactions are just background processes.
     */
    private final Semaphore snapshotCompactionLock = new Semaphore(1);

    /** Start time of the current compaction, or null if compaction isn't running */
    private final AtomicReference<Instant> currentCompactionStartTime = new AtomicReference<>();
    /**
     * Current data file writer during compaction, or null if compaction isn't running. The writer
     * is created at compaction start. If compaction is interrupted by a snapshot, the writer is
     * closed before the snapshot, and then a new writer / new file is created after the snapshot is
     * taken.
     */
    private final AtomicReference<DataFileWriter<?>> currentCompactionWriter = new AtomicReference<>();
    /** Currrent data file reader for the compaction writer above. */
    private final AtomicReference<DataFileReader<?>> currentCompactionReader = new AtomicReference<>();
    /**
     * The list of new files created during compaction. Usually, all files to process are compacted
     * to a single new file, but if compaction is interrupted by a snapshot, there may be more than
     * one file created.
     */
    private final List<Path> newCompactedFiles = new ArrayList<>();

    /**
     * Indicates whether compaction is in progress at the time when {@link #pauseCompaction()}
     * is called. This flag is then checked in {@link DataFileCompactor#resumeCompaction()} )} to start a new
     * compacted file or not.
     */
    private final AtomicBoolean compactionWasInProgress = new AtomicBoolean(false);

    /**
     * This variable keeps track of the compaction level that was in progress at the time when it was suspended.
     * Once the compaction is resumed, this level is used to start a new compacted file, and then it's reset to 0.
     */
    private final AtomicInteger compactionLevelInProgress = new AtomicInteger(0);

    /** When was the last medium-sized merge, only touched from single merge thread. */
    private Instant lastMediumMerge;

    /** When was the last full merge, only touched from single merge thread. */
    private Instant lastFullMerge;

    /** A nanosecond-precise Clock */
    private final NanoClock clock = new NanoClock();

    public DataFileCompactor(final DataFileCollection<?> dataFileCollection) {
        this.dataFileCollection = dataFileCollection;
        // Compute initial merge periods to a randomized value of now +/- 50% of merge period. So
        // each node will do
        // medium and full merges at random times.
        lastMediumMerge = Instant.now()
                .minus(config.mediumMergePeriod() / 2, config.mergePeriodUnit())
                .plus((long) (config.mediumMergePeriod() * Math.random()), config.mergePeriodUnit());
        lastFullMerge = Instant.now()
                .minus(config.fullMergePeriod() / 2, config.mergePeriodUnit())
                .plus((long) (config.fullMergePeriod() * Math.random()), config.mergePeriodUnit());
    }

    /**
     * Compacts all files in filesToCompact.
     *
     * @param index          takes a map of moves from old location to new location. Once it is finished and
     *                       returns it is assumed all readers will no longer be looking in old location, so old files
     *                       can be safely deleted.
     * @param filesToCompact   list of files to compact
     * @return list of files created during the compaction
     * @throws IOException          If there was a problem with the compaction
     * @throws InterruptedException If the compaction thread was interrupted
     */
    // visible for testing
    synchronized List<Path> compactFiles(final CASableLongIndex index, final List<DataFileReader<?>> filesToCompact)
            throws IOException, InterruptedException {
        if (filesToCompact.size() < getMinNumberOfFilesToMerge()) {
            // nothing to do we have merged since the last data update
            logger.debug(
                    MERKLE_DB.getMarker(),
                    "No files were available for merging [{}]",
                    dataFileCollection.getStoreName());
            return Collections.emptyList();
        }

        int currentLevel = filesToCompact.get(0).getMetadata().getCompactionLevel();
        // create a merge time stamp, this timestamp is the newest time of the set of files we are
        // merging
        final Instant startTime = filesToCompact.stream()
                .map(file -> file.getMetadata().getCreationDate())
                .max(Instant::compareTo)
                .orElseGet(Instant::now);
        snapshotCompactionLock.acquire();
        try {
            currentCompactionStartTime.set(startTime);
            newCompactedFiles.clear();
            startNewCompactionFile(currentLevel + 1);
        } finally {
            snapshotCompactionLock.release();
        }

        // We need a map to find readers by file index below. It doesn't have to be synchronized
        // as it will be accessed in this thread only, so it can be a simple HashMap or alike.
        // However, standard Java maps can only work with Integer, not int (yet), so auto-boxing
        // will put significant load on GC. Let's do something different
        int minFileIndex = Integer.MAX_VALUE;
        int maxFileIndex = 0;
        for (final DataFileReader<?> r : filesToCompact) {
            minFileIndex = Math.min(minFileIndex, r.getIndex());
            maxFileIndex = Math.max(maxFileIndex, r.getIndex());
        }
        final int firstIndexInc = minFileIndex;
        final int lastIndexExc = maxFileIndex + 1;
        final DataFileReader<?>[] readers = new DataFileReader[lastIndexExc - firstIndexInc];
        for (DataFileReader<?> r : filesToCompact) {
            readers[r.getIndex() - firstIndexInc] = r;
        }

        boolean allDataItemsProcessed = false;
        try {
            final KeyRange keyRange = dataFileCollection.getValidKeyRange();
            index.forEach((path, dataLocation) -> {
                if (!keyRange.withinRange(path)) {
                    return;
                }
                final int fileIndex = DataFileCommon.fileIndexFromDataLocation(dataLocation);
                if ((fileIndex < firstIndexInc) || (fileIndex >= lastIndexExc)) {
                    return;
                }
                final DataFileReader<?> reader = readers[fileIndex - firstIndexInc];
                if (reader == null) {
                    return;
                }
                final long fileOffset = DataFileCommon.byteOffsetFromDataLocation(dataLocation);
                // Take the lock. If a snapshot is started in a different thread, this call
                // will block until the snapshot is done. The current file will be flushed,
                // and current data file writer and reader will point to a new file
                snapshotCompactionLock.acquire();
                try {
                    final DataFileWriter<?> newFileWriter = currentCompactionWriter.get();
                    long serializationVersion = reader.getMetadata().getSerializationVersion();
                    final long newLocation = newFileWriter.writeCopiedDataItem(
                            serializationVersion, reader.readDataItemBytes(fileOffset));
                    // update the index
                    index.putIfEqual(path, dataLocation, newLocation);
                } catch (final IOException z) {
                    logger.error(EXCEPTION.getMarker(), "Failed to copy data item {} / {}", fileIndex, fileOffset, z);
                    throw z;
                } finally {
                    snapshotCompactionLock.release();
                }
            });
            allDataItemsProcessed = true;
        } finally {
            // Even if the thread is interrupted, make sure the new compacted file is properly closed
            // and is included to future compactions
            snapshotCompactionLock.acquire();
            try {
                // Finish writing the last file. In rare cases, it may be an empty file
                finishCurrentCompactionFile();
                // Clear compaction start time
                currentCompactionStartTime.set(null);
                if (allDataItemsProcessed) {
                    // Close the readers and delete compacted files
                    dataFileCollection.deleteFiles(filesToCompact);
                }
            } finally {
                snapshotCompactionLock.release();
            }
        }

        return newCompactedFiles;
    }

    // visible for testing
    int getMinNumberOfFilesToMerge() {
        return config.minNumberOfFilesInMerge();
    }

    /**
     * Opens a new file for writing during compaction. This method is called, when compaction is
     * started. If compaction is interrupted and resumed by data source snapshot using {@link
     * #pauseCompaction()} and {@link #resumeCompaction()}, a new file is created for writing using
     * this method before compaction is resumed.
     *
     * This method must be called under snapshot/compaction lock.
     *
     * @throws IOException If an I/O error occurs
     */
    private void startNewCompactionFile(int compactionLevel) throws IOException {
        final Instant startTime = currentCompactionStartTime.get();
        assert startTime != null;
        final DataFileWriter<?> newFileWriter = dataFileCollection.newDataFile(startTime, compactionLevel);
        currentCompactionWriter.set(newFileWriter);
        final Path newFileCreated = newFileWriter.getPath();
        newCompactedFiles.add(newFileCreated);
        final DataFileMetadata newFileMetadata = newFileWriter.getMetadata();
        final DataFileReader<?> newFileReader =
                dataFileCollection.addNewDataFileReader(newFileCreated, newFileMetadata);
        currentCompactionReader.set(newFileReader);
    }

    /**
     * Closes the current compaction file. This method is called in the end of compaction process,
     * and also before a snapshot is taken to make sure the current file is fully written and safe
     * to include to snapshots.
     *
     * This method must be called under snapshot/compaction lock.
     *
     * @throws IOException If an I/O error occurs
     */
    private void finishCurrentCompactionFile() throws IOException {
        currentCompactionWriter.get().finishWriting();
        currentCompactionWriter.set(null);
        // Now include the file in future compactions
        currentCompactionReader.get().setFileCompleted();
        currentCompactionReader.set(null);
    }

    /**
     * Puts file compaction on hold, if it's currently in progress. If not in progress, it will
     * prevent compaction from starting until {@link #resumeCompaction()} is called. The most
     * important thing this method does is it makes data files consistent and read only, so they can
     * be included to snapshots as easily as to create hard links. In particular, if compaction is
     * in progress, and a new data file is being written to, this file is flushed to disk, no files
     * are created and no index entries are updated until compaction is resumed.
     *
     * This method should not be called on the compaction thread.
     *
     * <b>This method must be always balanced with and called before {@link DataFileCompactor#resumeCompaction()}. If
     * there are more / less calls to resume compactions than to pause, or if they are called in a
     * wrong order, it will result in deadlocks.</b>
     *
     * @see #resumeCompaction()
     * @throws IOException If an I/O error occurs
     */
    public void pauseCompaction() throws IOException {
        snapshotCompactionLock.acquireUninterruptibly();
        // Check if compaction is currently in progress. If so, flush and close the current file, so
        // it's included to the snapshot
        final DataFileWriter<?> compactionWriter = currentCompactionWriter.get();
        if (compactionWriter != null) {
            compactionWasInProgress.set(true);
            compactionLevelInProgress.set(compactionWriter.getMetadata().getCompactionLevel());
            finishCurrentCompactionFile();
            // Don't start a new compaction file here, as it would be included to snapshots, but
            // it shouldn't, as it isn't fully written yet. Instead, a new file will be started
            // right after snapshot is taken, in resumeCompaction()
        }
        // Don't release the lock here, it will be done later in resumeCompaction(). If there is no
        // compaction currently running, the lock will prevent starting a new one until snapshot is
        // done
    }

    /**
     * Resumes compaction previously put on hold with {@link #pauseCompaction()}. If there was no
     * compaction running at that moment, but new compaction was started (and blocked) since {@link
     * #pauseCompaction()}, this new compaction is resumed.
     *
     * <b>This method must be always balanced with and called after {@link #pauseCompaction()}. If
     * there are more / less calls to resume compactions than to pause, or if they are called in a
     * wrong order, it will result in deadlocks.</b>
     *
     * @throws IOException If an I/O error occurs
     */
    public void resumeCompaction() throws IOException {
        try {
            if (compactionWasInProgress.getAndSet(false)) {
                assert currentCompactionWriter.get() == null;
                assert currentCompactionReader.get() == null;
                startNewCompactionFile(compactionLevelInProgress.getAndSet(0));
            }
        } finally {
            snapshotCompactionLock.release();
        }
    }

    /**
     * Compact (merge) all files that match the given filter.
     *
     * @param reportDurationMetricFunction function to report how long compaction took, in ms
     * @param reportSavedSpaceMetricFunction function to report how much space was compacted, in Mb
     * @throws IOException if there was a problem merging
     * @throws InterruptedException if the merge thread was interupted
     */
    @SuppressWarnings("unchecked")
    public void compact(
            CASableLongIndex index,
            @Nullable final BiConsumer<CompactionType, Long> reportDurationMetricFunction,
            @Nullable final BiConsumer<CompactionType, Double> reportSavedSpaceMetricFunction)
            throws IOException, InterruptedException {
        Instant timestamp = Instant.now(clock);

        final UnaryOperator<List<DataFileReader<?>>> filesToCompactFilter;
        final CompactionType compactionLevel;
        final String storeName = dataFileCollection.getStoreName();
        final List<? extends DataFileReader<?>> allCompactableFiles = dataFileCollection.getAllCompletedFiles();

        if (isTimeForFullMerge(timestamp)) {
            lastFullMerge = timestamp;
            /* Filter nothing during a full merge */
            filesToCompactFilter = dataFileReaders -> dataFileReaders;
            compactionLevel = CompactionType.FULL;
            logger.info(MERKLE_DB.getMarker(), "[{}] Starting Large Merge", storeName);
        } else if (isTimeForMediumMerge(timestamp)
                ||
                // This is a temporary solution for the intense load (like hammer tests) where we create too many files.
                // It will be removed once the solution comes with #7501 is implemented. The plan is to have a
                // compaction list.
                // That is, before we run a compaction we create a list of files from (possibly) all levels to be
                // compacted.
                // See https://www.notion.so/swirldslabs/Compaction-Improvements-247726614d924fbaa34aa82a157a2f20 for
                // details

                readersOfLevel(1)
                                .apply((List<DataFileReader<?>>) allCompactableFiles)
                                .size()
                        > MAX_FIRST_LEVEL_FILES_ALLOWED) {
            lastMediumMerge = timestamp;
            filesToCompactFilter = readersOfLevel(1);
            compactionLevel = CompactionType.MEDIUM;
            logger.info(MERKLE_DB.getMarker(), "[{}] Starting Medium Merge", storeName);
        } else {
            filesToCompactFilter = readersOfLevel(0);
            compactionLevel = CompactionType.SMALL;
            logger.info(MERKLE_DB.getMarker(), "[{}] Starting Small Merge", storeName);
        }

        final List<DataFileReader<?>> filesToCompact =
                filesToCompactFilter.apply((List<DataFileReader<?>>) allCompactableFiles);
        if (filesToCompact == null) {
            // nothing to do
            return;
        }
        final int filesCount = filesToCompact.size();
        if (filesCount < getMinNumberOfFilesToMerge()) {
            logger.debug(
                    MERKLE_DB.getMarker(),
                    "[{}] No need to merge as {} is less than the minimum {} files to merge.",
                    storeName,
                    filesCount,
                    getMinNumberOfFilesToMerge());
            return;
        }

        final long start = System.currentTimeMillis();

        final long filesToMergeSize = getSizeOfFiles(filesToCompact);
        logger.debug(
                MERKLE_DB.getMarker(),
                "[{}] Starting merging {} files / {}",
                storeName,
                filesCount,
                formatSizeBytes(filesToMergeSize));

        final List<Path> newFilesCreated = compactFiles(index, filesToCompact);

        final long end = System.currentTimeMillis();
        final long tookMillis = end - start;
        if (reportDurationMetricFunction != null) {
            reportDurationMetricFunction.accept(compactionLevel, tookMillis);
        }

        final long mergedFilesSize = getSizeOfFilesByPath(newFilesCreated);
        if (reportSavedSpaceMetricFunction != null) {
            reportSavedSpaceMetricFunction.accept(
                    compactionLevel, (filesToMergeSize - mergedFilesSize) * UnitConstants.BYTES_TO_MEBIBYTES);
        }

        logMergeStats(storeName, tookMillis, filesToCompact, filesToMergeSize, newFilesCreated, dataFileCollection);
        logger.debug(
                MERKLE_DB.getMarker(),
                "[{}] Finished merging {} files / {} in {} ms",
                storeName,
                filesCount,
                formatSizeBytes(filesToMergeSize),
                tookMillis);
    }

    private boolean isTimeForFullMerge(final Instant startMerge) {
        return startMerge
                .minus(config.fullMergePeriod(), config.mergePeriodUnit())
                .isAfter(lastFullMerge);
    }

    boolean isTimeForMediumMerge(final Instant startMerge) {
        return startMerge
                .minus(config.mediumMergePeriod(), config.mergePeriodUnit())
                .isAfter(lastMediumMerge);
    }

    /**
     * Create a filter to only return all new files that belong to certain compaction level
     *
     * @param expectedCompactionLevel compaction level to filter by
     * @return filter to filter list of files
     */
    public static UnaryOperator<List<DataFileReader<?>>> readersOfLevel(int expectedCompactionLevel) {
        return dataFileReaders -> dataFileReaders.stream()
                .filter(file -> file.getMetadata().getCompactionLevel() == expectedCompactionLevel)
                .collect(Collectors.toList());
    }
}
