/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.utility.Units.GIBIBYTES_TO_BYTES;
import static com.swirlds.common.utility.Units.MEBIBYTES_TO_BYTES;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.MERKLE_DB;
import static com.swirlds.merkledb.KeyRange.INVALID_KEY_RANGE;
import static com.swirlds.merkledb.files.DataFileCommon.byteOffsetFromDataLocation;
import static com.swirlds.merkledb.files.DataFileCommon.fileIndexFromDataLocation;
import static com.swirlds.merkledb.files.DataFileCommon.isFullyWrittenDataFile;
import static java.util.Collections.singletonList;

import com.swirlds.merkledb.KeyRange;
import com.swirlds.merkledb.Snapshotable;
import com.swirlds.merkledb.collections.CASableLongIndex;
import com.swirlds.merkledb.collections.ImmutableIndexedObjectList;
import com.swirlds.merkledb.collections.ImmutableIndexedObjectListUsingArray;
import com.swirlds.merkledb.collections.LongList;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import com.swirlds.merkledb.settings.MerkleDbSettings;
import com.swirlds.merkledb.settings.MerkleDbSettingsFactory;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * DataFileCollection manages a set of data files and the compaction of them over time. It stores
 * data items which are key,value pairs and returns a long representing the location it was stored.
 * You can then retrieve that data item later using the location you got when storing. There is not
 * understanding of what the keys mean and no way to look up data by key. The reason the keys are
 * separate from the values is so that we can merge data items with matching keys. We only keep the
 * newest data item for any matching key. It may look like a map, but it is not. You need an
 * external index outside this class to be able to store key-to-data location mappings.
 *
 * The keys are assumed to be a contiguous block of long values. We do not have an explicit way
 * of deleting data, we depend on the range of valid keys. Any data items with keys outside the
 * current valid range will be deleted the next time they are merged. This works for our VirtualMap
 * use cases where the key is always a path and there is a valid range of path keys for internal and
 * leaf nodes. It allows very easy and efficient deleting without the need to maintain a list of
 * deleted keys.
 *
 * @param <D> type for data items
 */
@SuppressWarnings({"unused", "unchecked"})
public class DataFileCollection<D> implements Snapshotable {
    private static final Logger logger = LogManager.getLogger(DataFileCollection.class);

    /**
     * Since {@code com.swirlds.platform.Browser} populates settings, and it is loaded before any
     * application classes that might instantiate a data source, the {@link MerkleDbSettingsFactory}
     * holder will have been configured by the time this static initializer runs.
     */
    private static final MerkleDbSettings settings = MerkleDbSettingsFactory.get();

    /**
     * Maximum number of data items that can be in a data file. This is dictated by the maximum size
     * of the movesMap used during merge, which in turn is limited by the maximum RAM to be used for
     * merging.
     */
    private static final int MOVE_LIST_CHUNK_SIZE = 500_000;
    /** The version number for format of current data files. */
    private static final int METADATA_FILE_FORMAT_VERSION = 1;
    /**
     * Metadata file name suffix. Full metadata file name is storeName + suffix. If legacy store
     * name is provided, and metadata file with the name above isn't found, metadata file with name
     * legacyStoreName + suffix is tried.
     */
    private static final String METADATA_FILENAME_SUFFIX = "_metadata.dfc";
    /**
     * Maximum number of items that can be in a data file, this is computed based on the max ram we
     * are willing to use while merging.
     */
    private static final int MAX_DATA_FILE_NUM_ITEMS = (int)
            Math.max((settings.getMaxRamUsedForMergingGb() * GIBIBYTES_TO_BYTES) / (Long.BYTES * 3), Integer.MAX_VALUE);
    /** The number of times to retry index based reads */
    private static final int NUM_OF_READ_RETRIES = 5;

    /** The directory to store data files */
    private final Path storeDir;
    /**
     * Base name for the data files, allowing more than one DataFileCollection to share a directory
     */
    private final String storeName;
    /**
     * Another base name for the data files. If files with this base name exist, they are loaded by
     * this file collection at startup. New files will have storeName as the prefix, not
     * legacyStoreName *
     */
    private final String legacyStoreName;
    /** Serializer responsible for serializing/deserializing data items into and out of files */
    private final DataItemSerializer<D> dataItemSerializer;
    /** True if this DataFileCollection was loaded from an existing set of files */
    private final boolean loadedFromExistingFiles;
    /** The index to use for the next file we create */
    private final AtomicInteger nextFileIndex = new AtomicInteger();
    /** The range of valid data item keys for data currently stored by this data file collection. */
    private volatile KeyRange validKeyRange = INVALID_KEY_RANGE;

    /**
     * The list of current files in this data file collection. The files are added to this list
     * during flushes in {@link #endWriting(long, long)}, after the file is completely written. They
     * are also added during compaction in {@link #compactFiles(CASableLongIndex, List)}, even
     * before compaction is complete. In the end of compaction, all the compacted files are removed
     * from this list.
     *
     * The list is used to read data items and to make snapshots. Reading from the file, which is
     * being written to during compaction, is possible because both readers and writers use Java
     * file channel APIs. Snapshots are an issue, though. Snapshots must be as fast as possible, so
     * they are implemented as to make hard links in the target folder to all data files in
     * collection. If compaction is in progress, the last file in the list isn't fully written yet,
     * so it can't be hard linked easily. To solve it, before a snapshot is taken, the current
     * compaction file is flushed to disk, and compaction is put on hold using {@link
     * #snapshotCompactionLock} and then resumed after snapshot is complete.
     */
    private final AtomicReference<ImmutableIndexedObjectList<DataFileReader<D>>> dataFiles = new AtomicReference<>();

    /**
     * The current open file writer, if we are in the middle of writing a new file during flush, or
     * null if not writing.
     */
    private final AtomicReference<DataFileWriter<D>> currentDataFileWriter = new AtomicReference<>();
    /**
     * Data file reader for the file, which is being written with the writer above, or null if not
     * writing. The reader is created right after writing to the file is started.
     */
    private final AtomicReference<DataFileReader<D>> currentDataFileReader = new AtomicReference<>();
    /** Constructor for creating ImmutableIndexedObjectLists */
    private final Function<List<DataFileReader<D>>, ImmutableIndexedObjectList<DataFileReader<D>>>
            indexedObjectListConstructor;
    /**
     * Set if all indexes of new files currently being written. This is only maintained if logging
     * is trace level.
     */
    private final ConcurrentSkipListSet<Integer> setOfNewFileIndexes =
            logger.isTraceEnabled() ? new ConcurrentSkipListSet<>() : null;

    // Compactions

    /** Start time of the current compaction, or null if compaction isn't running */
    private final AtomicReference<Instant> currentCompactionStartTime = new AtomicReference<>();
    /**
     * Current data file writer during compaction, or null if compaction isn't running. The writer
     * is created at compaction start. If compaction is interrupted by a snapshot, the writer is
     * closed before the snapshot, and then a new writer / new file is created after the snapshot is
     * taken.
     */
    private final AtomicReference<DataFileWriter<D>> currentCompactionWriter = new AtomicReference<>();
    /** Currrent data file reader for the compaction writer above. */
    private final AtomicReference<DataFileReader<D>> currentCompactionReader = new AtomicReference<>();
    /**
     * The list of new files created during compaction. Usually, all files to process are compacted
     * to a single new file, but if compaction is interrupted by a snapshot, there may be more than
     * one file created.
     */
    private final List<Path> newCompactedFiles = new ArrayList<>();
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
    /**
     * Indicates whether compaction is in progress at the time when {@link #pauseCompaction()}
     * is called. This flag is then checked in {@link #resumeCompaction()} to start a new
     * compacted file or not.
     */
    private final AtomicBoolean compactionWasInProgress = new AtomicBoolean(false);

    /**
     * Construct a new DataFileCollection.
     *
     * @param storeDir The directory to store data files
     * @param storeName Base name for the data files, allowing more than one DataFileCollection to
     *     share a directory
     * @param dataItemSerializer Serializer responsible for serializing/deserializing data items
     *     into and out of files.
     * @param loadedDataCallback Callback for rebuilding indexes from existing files, can be null if
     *     not needed. Using this is expensive as it requires all files to be read and parsed.
     * @throws IOException If there was a problem creating new data set or opening existing one
     */
    public DataFileCollection(
            final Path storeDir,
            final String storeName,
            final DataItemSerializer<D> dataItemSerializer,
            final LoadedDataCallback loadedDataCallback)
            throws IOException {
        this(
                storeDir,
                storeName,
                null,
                dataItemSerializer,
                loadedDataCallback,
                ImmutableIndexedObjectListUsingArray::new);
    }

    /**
     * Construct a new DataFileCollection with a custom legacy store name. If data files and/or
     * metadata file exist with the legacy store name prefix, they will be processed by this file
     * collection. New data files will be written with {@code storeName} as the prefix.
     *
     * @param storeDir The directory to store data files
     * @param storeName Base name for the data files, allowing more than one DataFileCollection to
     *     share a directory
     * @param legacyStoreName Base name for the data files. If not null, data files with this prefix
     *     are processed by this file collection at startup same way as files prefixed with
     *     storeName
     * @param dataItemSerializer Serializer responsible for serializing/deserializing data items
     *     into and out of files.
     * @param loadedDataCallback Callback for rebuilding indexes from existing files, can be null if
     *     not needed. Using this is expensive as it requires all files to be read and parsed.
     * @throws IOException If there was a problem creating new data set or opening existing one
     */
    public DataFileCollection(
            final Path storeDir,
            final String storeName,
            final String legacyStoreName,
            final DataItemSerializer<D> dataItemSerializer,
            final LoadedDataCallback loadedDataCallback)
            throws IOException {
        this(
                storeDir,
                storeName,
                legacyStoreName,
                dataItemSerializer,
                loadedDataCallback,
                ImmutableIndexedObjectListUsingArray::new);
    }

    /**
     * Construct a new DataFileCollection with custom legacy store name and indexed object list
     * constructor. If data files and/or metadata file exist with the legacy store name prefix, they
     * will be processed by this file collection. New data files will be written with {@code
     * storeName} as the prefix.
     *
     * @param storeDir The directory to store data files
     * @param storeName Base name for the data files, allowing more than one DataFileCollection to
     *     share a directory
     * @param legacyStoreName Base name for the data files. If not null, data files with this prefix
     *     are processed by this file collection at startup same way as files prefixed with
     *     storeName
     * @param dataItemSerializer Serializer responsible for serializing/deserializing data items
     *     into and out of files.
     * @param loadedDataCallback Callback for rebuilding indexes from existing files, can be null if
     *     not needed. Using this is expensive as it requires all files to be read and parsed.
     * @param indexedObjectListConstructor Constructor for creating ImmutableIndexedObjectList
     *     instances.
     * @throws IOException If there was a problem creating new data set or opening existing one
     */
    protected DataFileCollection(
            final Path storeDir,
            final String storeName,
            final String legacyStoreName,
            final DataItemSerializer<D> dataItemSerializer,
            final LoadedDataCallback loadedDataCallback,
            final Function<List<DataFileReader<D>>, ImmutableIndexedObjectList<DataFileReader<D>>>
                    indexedObjectListConstructor)
            throws IOException {
        this.storeDir = storeDir;
        this.storeName = storeName;
        this.legacyStoreName = legacyStoreName;
        this.dataItemSerializer = dataItemSerializer;
        this.indexedObjectListConstructor = indexedObjectListConstructor;

        // check if exists, if so open existing files
        if (Files.exists(storeDir)) {
            loadedFromExistingFiles = tryLoadFromExistingStore(loadedDataCallback);
        } else {
            loadedFromExistingFiles = false;
            // create store dir
            Files.createDirectories(storeDir);
            // next file will have index zero
            nextFileIndex.set(0);
        }
    }

    /**
     * Get the valid range of keys for data items currently stored by this data file collection. Any
     * data items with keys below this can be deleted during a merge.
     *
     * @return valid key range
     */
    public KeyRange getValidKeyRange() {
        return validKeyRange;
    }

    /**
     * Get if this data file collection was loaded from an existing set of files or if it was a new
     * empty collection
     *
     * @return true if loaded from existing, false if new set of files
     */
    public boolean isLoadedFromExistingFiles() {
        return loadedFromExistingFiles;
    }

    /** Get the number of files in this DataFileCollection */
    public int getNumOfFiles() {
        return dataFiles.get().size();
    }

    /**
     * Get a list of all files in this collection that have been fully finished writing, are read
     * only, ready to be compacted, and don't exceed the specified size in MB.
     *
     * @param maxSizeMb all files returned are smaller than this number of MB
     */
    public List<DataFileReader<D>> getAllCompletedFiles(final int maxSizeMb) {
        final ImmutableIndexedObjectList<DataFileReader<D>> activeIndexedFiles = dataFiles.get();
        if (activeIndexedFiles == null) {
            return Collections.emptyList();
        }
        Stream<DataFileReader<D>> filesStream = activeIndexedFiles.stream();
        filesStream = filesStream.filter(DataFileReader::isFileCompleted);
        if (maxSizeMb != Integer.MAX_VALUE) {
            final long maxSizeBytes = maxSizeMb * (long) MEBIBYTES_TO_BYTES;
            filesStream = filesStream.filter(file -> file.getSize() < maxSizeBytes);
        }
        return filesStream.toList();
    }

    /**
     * Get a list of all files in this collection that have been fully finished writing, are read
     * only and ready to be compacted.
     */
    public List<DataFileReader<D>> getAllCompletedFiles() {
        return getAllCompletedFiles(Integer.MAX_VALUE);
    }

    /**
     * Get statistics for sizes of all files
     *
     * @return statistics for sizes of all fully written files, in bytes
     */
    public LongSummaryStatistics getAllCompletedFilesSizeStatistics() {
        final ImmutableIndexedObjectList<DataFileReader<D>> activeIndexedFiles = dataFiles.get();
        return activeIndexedFiles == null
                ? new LongSummaryStatistics()
                : activeIndexedFiles.stream()
                        .filter(DataFileReader::isFileCompleted)
                        .mapToLong(DataFileReader::getSize)
                        .summaryStatistics();
    }

    /**
     * Merges all files in filesToMerge.
     *
     * @param index takes a map of moves from old location to new location. Once it is finished and
     *     returns it is assumed all readers will no longer be looking in old location, so old files
     *     can be safely deleted.
     * @param filesToMerge list of files to merge
     * @return list of files created during the merge
     * @throws IOException If there was a problem merging
     * @throws InterruptedException If the merge thread was interrupted
     */
    public synchronized List<Path> compactFiles(
            final CASableLongIndex index, final List<DataFileReader<D>> filesToMerge)
            throws IOException, InterruptedException {
        if (filesToMerge.size() < 2) {
            // nothing to do we have merged since the last data update
            logger.debug(MERKLE_DB.getMarker(), "No files were available for merging [{}]", storeName);
            return Collections.emptyList();
        }

        // create a merge time stamp, this timestamp is the newest time of the set of files we are
        // merging
        final Instant startTime = filesToMerge.stream()
                .map(file -> file.getMetadata().getCreationDate())
                .max(Instant::compareTo)
                .get();
        snapshotCompactionLock.acquire();
        try {
            currentCompactionStartTime.set(startTime);
            newCompactedFiles.clear();
            startNewCompactionFile();
        } finally {
            snapshotCompactionLock.release();
        }

        // We need a map to find readers by file index below. It doesn't have to be synchronized
        // as it will be accessed in this thread only, so it can be a simple HashMap or alike.
        // However, standard Java maps can only work with Integer, not int (yet), so auto-boxing
        // will put significant load on GC. Let's do something different
        int minFileIndex = Integer.MAX_VALUE;
        int maxFileIndex = 0;
        for (final DataFileReader<D> r : filesToMerge) {
            minFileIndex = Math.min(minFileIndex, r.getIndex());
            maxFileIndex = Math.max(maxFileIndex, r.getIndex());
        }
        final int firstIndexInc = minFileIndex;
        final int lastIndexExc = maxFileIndex + 1;
        final DataFileReader<D>[] readers = new DataFileReader[lastIndexExc - firstIndexInc];
        for (DataFileReader<D> r : filesToMerge) {
            readers[r.getIndex() - firstIndexInc] = r;
        }

        final KeyRange keyRange = validKeyRange;
        index.forEach((path, dataLocation) -> {
            if (!keyRange.withinRange(path)) {
                return;
            }
            final int fileIndex = DataFileCommon.fileIndexFromDataLocation(dataLocation);
            if ((fileIndex < firstIndexInc) || (fileIndex >= lastIndexExc)) {
                return;
            }
            final DataFileReader<D> reader = readers[fileIndex - firstIndexInc];
            if (reader == null) {
                return;
            }
            final long fileOffset = DataFileCommon.byteOffsetFromDataLocation(dataLocation);
            snapshotCompactionLock.acquire();
            try {
                // Take the lock. If a snapshot is started in a different thread, this call
                // will block until the snapshot is done. The current file will be flushed,
                // and current data file writer and reader will point to a new file
                final DataFileWriter<D> newFileWriter = currentCompactionWriter.get();
                final long newLocation = newFileWriter.writeCopiedDataItem(
                        reader.getMetadata().getSerializationVersion(), reader.readDataItemBytes(fileOffset));
                // update the index
                index.putIfEqual(path, dataLocation, newLocation);
            } catch (final IOException z) {
                logger.error(EXCEPTION.getMarker(), "Failed to copy data item {} / {}", fileIndex, fileOffset, z);
                throw z;
            } finally {
                snapshotCompactionLock.release();
            }
        });

        snapshotCompactionLock.acquire();
        try {
            // Finish writing the last file. In rare cases, it may be an empty file
            finishCurrentCompactionFile();
            // Clear compaction start time
            currentCompactionStartTime.set(null);
            // Close the readers and delete compacted files
            deleteFiles(new HashSet<>(filesToMerge));
        } finally {
            snapshotCompactionLock.release();
        }

        return newCompactedFiles;
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
    private void startNewCompactionFile() throws IOException {
        final Instant startTime = currentCompactionStartTime.get();
        assert startTime != null;
        final DataFileWriter<D> newFileWriter = newDataFile(startTime);
        currentCompactionWriter.set(newFileWriter);
        final Path newFileCreated = newFileWriter.getPath();
        newCompactedFiles.add(newFileCreated);
        final DataFileMetadata newFileMetadata = newFileWriter.getMetadata();
        final DataFileReader<D> newFileReader = addNewDataFileReader(newFileCreated, newFileMetadata);
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
     * <b>This method must be always balanced with and called before {@link #resumeCompaction()}. If
     * there are more / less calls to resume compactions than to pause, or if they are called in a
     * wrong order, it will result in deadlocks.</b>
     *
     * @throws IOException If an I/O error occurs
     */
    public void pauseCompaction() throws IOException {
        snapshotCompactionLock.acquireUninterruptibly();
        // Check if compaction is currently in progress. If so, flush and close the current file, so
        // it's included to the snapshot
        final DataFileWriter<D> compactionWriter = currentCompactionWriter.get();
        if (compactionWriter != null) {
            compactionWasInProgress.set(true);
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
                startNewCompactionFile();
            }
        } finally {
            snapshotCompactionLock.release();
        }
    }

    /** Close all the data files */
    public void close() throws IOException {
        // finish writing if we still are
        final DataFileWriter<D> currentDataFileForWriting = currentDataFileWriter.getAndSet(null);
        if (currentDataFileForWriting != null) {
            currentDataFileForWriting.finishWriting();
        }
        // calling startSnapshot causes the metadata file to be written
        saveMetadata(storeDir);
        // close all files
        final ImmutableIndexedObjectList<DataFileReader<D>> fileList = dataFiles.getAndSet(null);
        if (fileList != null) {
            for (final DataFileReader<D> file : (Iterable<DataFileReader<D>>) fileList.stream()::iterator) {
                file.close();
            }
        }
    }

    /**
     * Start writing a new data file
     *
     * @throws IOException If there was a problem opening a new data file
     */
    public void startWriting() throws IOException {
        final DataFileWriter<D> activeDataFileWriter = currentDataFileWriter.get();
        if (activeDataFileWriter != null) {
            throw new IOException("Tried to start writing when we were already writing.");
        }
        final DataFileWriter<D> writer = newDataFile(Instant.now());
        currentDataFileWriter.set(writer);
        final DataFileMetadata metadata = writer.getMetadata();
        final DataFileReader<D> reader = addNewDataFileReader(writer.getPath(), metadata);
        currentDataFileReader.set(reader);
    }

    /**
     * Store a data item into the current file opened with startWriting().
     *
     * @param dataItem The data item to write into file
     * @return the location where data item was stored. This contains both the file and the location
     *     within the file.
     * @throws IOException If there was a problem writing this data item to the file.
     */
    public long storeDataItem(final D dataItem) throws IOException {
        final DataFileWriter<D> currentDataFileForWriting = currentDataFileWriter.get();
        if (currentDataFileForWriting == null) {
            throw new IOException("Tried to put data " + dataItem + " when we never started writing.");
        }
        /* FUTURE WORK - https://github.com/swirlds/swirlds-platform/issues/3926 */
        return currentDataFileForWriting.storeDataItem(dataItem);
    }

    /**
     * End writing current data file and returns the corresponding reader. The reader isn't marked
     * as completed (fully written, read only, and ready to compact), as the caller may need some
     * additional processing, e.g. to update indices, before the file can be compacted.
     *
     * @param minimumValidKey The minimum valid data key at this point in time, can be used for
     *     cleaning out old data
     * @param maximumValidKey The maximum valid data key at this point in time, can be used for
     *     cleaning out old data
     * @throws IOException If there was a problem closing the data file
     */
    public DataFileReader<D> endWriting(final long minimumValidKey, final long maximumValidKey) throws IOException {
        validKeyRange = new KeyRange(minimumValidKey, maximumValidKey);
        final DataFileWriter<D> dataWriter = currentDataFileWriter.getAndSet(null);
        if (dataWriter == null) {
            throw new IOException("Tried to end writing when we never started writing.");
        }
        // finish writing the file and write its footer
        dataWriter.finishWriting();
        final DataFileReader<D> dataReader = currentDataFileReader.getAndSet(null);
        if (logger.isTraceEnabled()) {
            final DataFileMetadata metadata = dataReader.getMetadata();
            setOfNewFileIndexes.remove(metadata.getIndex());
        }
        return dataReader;
    }

    /**
     * Read a data item from any file that has finished being written. This is not 100% thread safe
     * with concurrent merging, it is possible it will throw a ClosedChannelException or return
     * null. So it should be retried if those happen.
     *
     * @param dataLocation the location of the data item to read. This contains both the file and
     *     the location within the file.
     * @return Data item if the data location was found in files. <br>
     *     <br>
     *     A null is returned :
     *     <ol>
     *       <li>if not found
     *       <li>if deserialize flag is false
     *     </ol>
     *
     * @throws IOException If there was a problem reading the data item.
     * @throws ClosedChannelException In the very rare case merging closed the file between us
     *     checking if file is open and reading
     */
    protected D readDataItem(final long dataLocation) throws IOException {
        // check if found
        if (dataLocation == 0) {
            return null;
        }
        // split up location
        final int fileIndex = fileIndexFromDataLocation(dataLocation);
        // check if file for fileIndex exists
        DataFileReader<D> file;
        final ImmutableIndexedObjectList<DataFileReader<D>> currentIndexedFileList = dataFiles.get();
        if (fileIndex < 0 || currentIndexedFileList == null || (file = currentIndexedFileList.get(fileIndex)) == null) {
            throw new IOException("Got a data location from index for a file that doesn't exist. "
                    + "dataLocation="
                    + DataFileCommon.dataLocationToString(dataLocation)
                    + " fileIndex="
                    + fileIndex
                    + " validKeyRange="
                    + validKeyRange
                    + "\ncurrentIndexedFileList="
                    + currentIndexedFileList);
        }
        // read data, check at last second that file is not closed
        if (file.isOpen()) {
            return file.readDataItem(dataLocation);
        } else {
            // Let's log this as it should happen very rarely but if we see it a lot then we should
            // have a rethink.
            logger.warn(
                    EXCEPTION.getMarker(), "Store [{}] DataFile was closed while trying to read from file", storeName);
            return null;
        }
    }

    /**
     * Read a data item from any file that has finished being written. Uses a LongList that maps
     * key-&gt;dataLocation, this allows for multiple retries going back to the index each time. The
     * allows us to cover the cracks where threads can slip though.
     *
     * This depends on the fact that LongList has a nominal value of
     * LongList.IMPERMISSIBLE_VALUE=0 for non-existent values.
     *
     * @param index key-&gt;dataLocation index
     * @param keyIntoIndex The key to lookup in index
     * @return Data item if the data location was found in files. If contained in the index but not
     *     in files after a number of retries then an exception is thrown. <br>
     *     A null is returned if not found in index
     *
     * @throws IOException If there was a problem reading the data item.
     */
    public D readDataItemUsingIndex(final LongList index, final long keyIntoIndex) throws IOException {
        // Try reading up to 5 times, 99.999% should work first try but there is a small chance the
        // file was closed by
        // merging when we are half way though reading, and we will see  file.isOpen() = false or a
        // ClosedChannelException. Doing a retry should get a different result because dataLocation
        // should be different
        // on the next try, because merging had a chance to update it to the new file.
        for (int retries = 0; retries < NUM_OF_READ_RETRIES; retries++) {
            // get from index
            final long dataLocation = index.get(keyIntoIndex, LongList.IMPERMISSIBLE_VALUE);
            // check if found
            if (dataLocation == LongList.IMPERMISSIBLE_VALUE) {
                return null;
            }
            // read data
            try {
                final D readData = readDataItem(dataLocation);
                // check we actually read data, this could be null if the file was closed half way
                // though us reading
                if ((readData != null)) {
                    return readData;
                }
            } catch (final IOException e) {
                // For up to 5 retries we ignore this exception because next retry should get a new
                // file location from
                // index. So should never hit a closed file twice.
                final int currentRetry = retries;
                // Log as much useful information that we can to help diagnose this problem before
                // throwing exception.
                logger.warn(
                        EXCEPTION.getMarker(),
                        () -> {
                            final String currentFiles = dataFiles.get() == null
                                    ? "UNKNOWN"
                                    : dataFiles.get().prettyPrintedIndices();

                            return "Store ["
                                    + storeName
                                    + "] had IOException while trying to read "
                                    + "key ["
                                    + keyIntoIndex
                                    + "] at "
                                    + "offset ["
                                    + byteOffsetFromDataLocation(dataLocation)
                                    + "] from "
                                    + "file ["
                                    + fileIndexFromDataLocation(dataLocation)
                                    + "] "
                                    + "on retry ["
                                    + currentRetry
                                    + "]. "
                                    + "Current files are ["
                                    + currentFiles
                                    + "]"
                                    + ", validKeyRange="
                                    + validKeyRange
                                    + ", storeDir=["
                                    + storeDir.toAbsolutePath()
                                    + "]";
                        },
                        e);
            }
        }
        throw new IOException("Read failed after 5 retries");
    }

    /** {@inheritDoc} */
    @Override
    public void snapshot(final Path snapshotDirectory) throws IOException {
        saveMetadata(snapshotDirectory);
        final List<DataFileReader<D>> snapshotIndexedFiles = getAllCompletedFiles();
        for (final DataFileReader<D> fileReader : snapshotIndexedFiles) {
            final Path existingFile = fileReader.getPath();
            Files.createLink(snapshotDirectory.resolve(existingFile.getFileName()), existingFile);
        }
    }

    /**
     * Get the set of new file indexes. This is only callable if trace logging is enabled.
     *
     * @return Direct access to set of all indexes of files that are currently being written
     */
    Set<Integer> getSetOfNewFileIndexes() {
        if (logger.isTraceEnabled()) {
            return setOfNewFileIndexes;
        } else {
            throw new IllegalStateException("getSetOfNewFileIndexes can only be called if trace logging is enabled");
        }
    }

    // =================================================================================================================
    // Index Callback Class

    /**
     * Simple callback class during reading an existing set of files during startup, so that indexes
     * can be built
     */
    @FunctionalInterface
    public interface LoadedDataCallback {
        /** Add an index entry for the given key and data location and value */
        void newIndexEntry(long key, long dataLocation, ByteBuffer dataValue);
    }

    // =================================================================================================================
    // Private API

    /**
     * Used by tests to get data files for checking
     *
     * @param index data file index
     * @return the data file if one exists at that index
     */
    DataFileReader<D> getDataFile(final int index) {
        final ImmutableIndexedObjectList<DataFileReader<D>> fileList = dataFiles.get();
        return fileList == null ? null : fileList.get(index);
    }

    /**
     * Create and add a new data file reader to end of indexedFileList
     *
     * @param filePath the path for the new data file
     * @param metadata The metadata for the file at filePath, to save reading from file
     * @return The newly added DataFileReader.
     */
    private DataFileReader<D> addNewDataFileReader(final Path filePath, final DataFileMetadata metadata)
            throws IOException {
        final DataFileReader<D> newDataFileReader = new DataFileReader<>(filePath, dataItemSerializer, metadata);
        dataFiles.getAndUpdate(currentFileList -> {
            try {
                return (currentFileList == null)
                        ? indexedObjectListConstructor.apply(singletonList(newDataFileReader))
                        : currentFileList.withAddedObject(newDataFileReader);
            } catch (final IllegalArgumentException e) {
                logger.error(EXCEPTION.getMarker(), "Failed when updated the indexed files list", e);
                throw e;
            }
        });
        return newDataFileReader;
    }

    /**
     * Delete a list of files from indexedFileList and then from disk
     *
     * @param filesToDelete the list of files to delete
     * @throws IOException If there was a problem deleting the files
     */
    private void deleteFiles(final Set<DataFileReader<D>> filesToDelete) throws IOException {
        // remove files from index
        dataFiles.getAndUpdate(currentFileList ->
                (currentFileList == null) ? null : currentFileList.withDeletedObjects(filesToDelete));
        // now close and delete all the files
        for (final DataFileReader<D> fileReader : filesToDelete) {
            fileReader.close();
            Files.delete(fileReader.getPath());
        }
    }

    /**
     * Create a new data file writer
     *
     * @param creationTime The creation time for the data in the new file. It could be now or old in
     *     case of merge.
     * @return the newly created data file
     */
    private DataFileWriter<D> newDataFile(final Instant creationTime) throws IOException {
        final int newFileIndex = nextFileIndex.getAndIncrement();
        if (logger.isTraceEnabled()) {
            setOfNewFileIndexes.add(newFileIndex);
        }
        return new DataFileWriter<>(storeName, storeDir, newFileIndex, dataItemSerializer, creationTime);
    }

    /**
     * Saves the metadata to the given directory. A valid database must have the metadata.
     *
     * @param directory The location to save the metadata. The directory will be created (and all
     *     parent directories) if needed.
     * @throws IOException Thrown if a lower level IOException occurs.
     */
    private void saveMetadata(final Path directory) throws IOException {
        Files.createDirectories(directory);
        // write metadata, this will be incredibly fast, and we need to capture min and max key
        // while in save lock
        try (final DataOutputStream metaOut =
                new DataOutputStream(Files.newOutputStream(directory.resolve(storeName + METADATA_FILENAME_SUFFIX)))) {
            final KeyRange keyRange = validKeyRange;
            metaOut.writeInt(METADATA_FILE_FORMAT_VERSION);
            metaOut.writeLong(keyRange.getMinValidKey());
            metaOut.writeLong(keyRange.getMaxValidKey());
            metaOut.flush();
        }
    }

    private boolean tryLoadFromExistingStore(final LoadedDataCallback loadedDataCallback) throws IOException {
        if (!Files.isDirectory(storeDir)) {
            throw new IOException("Tried to initialize DataFileCollection with a storage "
                    + "directory that is not a directory. ["
                    + storeDir.toAbsolutePath()
                    + "]");
        }
        try (final Stream<Path> storePaths = Files.list(storeDir)) {
            final Path[] fullWrittenFilePaths = storePaths
                    .filter(path ->
                            isFullyWrittenDataFile(storeName, path) || isFullyWrittenDataFile(legacyStoreName, path))
                    .toArray(Path[]::new);
            final DataFileReader<D>[] dataFileReaders = new DataFileReader[fullWrittenFilePaths.length];
            try {
                for (int i = 0; i < fullWrittenFilePaths.length; i++) {
                    dataFileReaders[i] = new DataFileReader<>(fullWrittenFilePaths[i], dataItemSerializer);
                }
                // sort the readers into data file index order
                Arrays.sort(dataFileReaders);
            } catch (final IOException e) {
                // clean up any successfully created readers
                for (final DataFileReader<D> dataFileReader : dataFileReaders) {
                    if (dataFileReader != null) {
                        dataFileReader.close();
                    }
                }
                // rethrow exception now that we have cleaned up
                throw e;
            }
            if (dataFileReaders.length > 0) {
                loadFromExistingFiles(dataFileReaders, loadedDataCallback);
                return true;
            } else {
                // next file will have index zero as we did not find any files even though the
                // directory existed
                nextFileIndex.set(0);
                return false;
            }
        }
    }

    private void loadFromExistingFiles(
            final DataFileReader<D>[] dataFileReaders, final LoadedDataCallback loadedDataCallback) throws IOException {
        logger.info(
                MERKLE_DB.getMarker(),
                "Loading existing set of [{}] data files for DataFileCollection [{}]",
                dataFileReaders.length,
                storeName);
        // read metadata
        Path metaDataFile = storeDir.resolve(storeName + METADATA_FILENAME_SUFFIX);
        boolean loadedLegacyMetadata = false;
        if (!Files.exists(metaDataFile)) {
            // try loading using legacy name
            metaDataFile = storeDir.resolve(legacyStoreName + METADATA_FILENAME_SUFFIX);
            loadedLegacyMetadata = true;
        }
        if (Files.exists(metaDataFile)) {
            try (final DataInputStream metaIn = new DataInputStream(Files.newInputStream(metaDataFile))) {
                final int fileVersion = metaIn.readInt();
                if (fileVersion != METADATA_FILE_FORMAT_VERSION) {
                    throw new IOException("Tried to read a file with incompatible file format version ["
                            + fileVersion
                            + "], expected ["
                            + METADATA_FILE_FORMAT_VERSION
                            + "].");
                }
                validKeyRange = new KeyRange(metaIn.readLong(), metaIn.readLong());
            }
            if (loadedLegacyMetadata) {
                Files.delete(metaDataFile);
            }
        } else {
            logger.warn(
                    EXCEPTION.getMarker(),
                    "Loading existing set of data files but no metadata file was found in [{}]",
                    storeDir.toAbsolutePath());
        }
        // create indexed file list
        dataFiles.set(indexedObjectListConstructor.apply(List.of(dataFileReaders)));
        // work out what the next index would be, the highest current index plus one
        nextFileIndex.set(getMaxFileReaderIndex(dataFileReaders) + 1);
        // now call indexEntryCallback
        if (loadedDataCallback != null) {
            // now iterate over every file and every key
            for (final DataFileReader<D> file : dataFileReaders) {
                try (final DataFileIterator iterator =
                        new DataFileIterator(file.getPath(), file.getMetadata(), dataItemSerializer)) {
                    while (iterator.next()) {
                        loadedDataCallback.newIndexEntry(
                                iterator.getDataItemsKey(),
                                iterator.getDataItemsDataLocation(),
                                iterator.getDataItemData());
                    }
                }
            }
        }
        // Mark all files we loaded as being available for compactions
        for (final DataFileReader<D> dataFileReader : dataFileReaders) {
            dataFileReader.setFileCompleted();
        }
        logger.info(
                MERKLE_DB.getMarker(), "Finished loading existing data files for DataFileCollection [{}]", storeName);
    }

    private int getMaxFileReaderIndex(final DataFileReader<D>[] dataFileReaders) {
        int maxIndex = -1;
        for (final DataFileReader<D> reader : dataFileReaders) {
            maxIndex = Math.max(maxIndex, reader.getIndex());
        }
        return maxIndex;
    }
}
