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

package com.swirlds.jasperdb.files;

import static com.swirlds.common.utility.Units.GIBIBYTES_TO_BYTES;
import static com.swirlds.common.utility.Units.MEBIBYTES_TO_BYTES;
import static com.swirlds.jasperdb.KeyRange.INVALID_KEY_RANGE;
import static com.swirlds.jasperdb.files.DataFileCommon.byteOffsetFromDataLocation;
import static com.swirlds.jasperdb.files.DataFileCommon.fileIndexFromDataLocation;
import static com.swirlds.jasperdb.files.DataFileCommon.isFullyWrittenDataFile;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.JASPER_DB;
import static java.util.Collections.singletonList;

import com.swirlds.jasperdb.KeyRange;
import com.swirlds.jasperdb.Snapshotable;
import com.swirlds.jasperdb.collections.CASable;
import com.swirlds.jasperdb.collections.ImmutableIndexedObjectList;
import com.swirlds.jasperdb.collections.ImmutableIndexedObjectListUsingArray;
import com.swirlds.jasperdb.collections.LongList;
import com.swirlds.jasperdb.collections.ThreeLongsList;
import com.swirlds.jasperdb.settings.JasperDbSettings;
import com.swirlds.jasperdb.settings.JasperDbSettingsFactory;
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
import java.util.ListIterator;
import java.util.LongSummaryStatistics;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * DataFileCollection manages a set of data files and the compaction of them over time. It stores data items which are
 * key,value pairs and returns a long representing the location it was stored. You can then retrieve that data item
 * later using the location you got when storing. There is not understanding of what the keys mean and no way to look
 * up data by key. The reason the keys are separate from the values is so that we can merge data items with matching
 * keys. We only keep the newest data item for any matching key. It may look like a map, but it is not. You need an
 * external index outside this class to be able to store key-to-data location mappings.
 * <p>
 * The keys are assumed to be a contiguous block of long values. We do not have an explicit way of deleting data, we
 * depend on the range of valid keys. Any data items with keys outside the current valid range will be deleted the next
 * time they are merged. This works for our VirtualMap use cases where the key is always a path and there is a valid
 * range of path keys for internal and leaf nodes. It allows very easy and efficient deleting without the need to
 * maintain a list of deleted keys.
 *
 * @param <D>
 * 		type for data items
 */
@SuppressWarnings({"unused", "unchecked"})
public class DataFileCollection<D> implements Snapshotable {
    private static final Logger logger = LogManager.getLogger(DataFileCollection.class);

    /**
     * Since {@code com.swirlds.platform.Browser} populates settings, and it is loaded before
     * any application classes that might instantiate a data source, the {@link JasperDbSettingsFactory}
     * holder will have been configured by the time this static initializer runs.
     */
    private static final JasperDbSettings settings = JasperDbSettingsFactory.get();

    /**
     * Maximum number of data items that can be in a data file. This is dictated by the maximum size of the movesMap
     * used during merge, which in turn is limited by the maximum RAM to be used for merging.
     */
    private static final int MOVE_LIST_CHUNK_SIZE = 500_000;
    /**
     * The version number for format of current data files.
     */
    private static final int METADATA_FILE_FORMAT_VERSION = 1;
    /**
     * Metadata file name suffix. Full metadata file name is storeName + suffix. If legacy store name
     * is provided, and metadata file with the name above isn't found, metadata file with name
     * legacyStoreName + suffix is tried.
     */
    private static final String METADATA_FILENAME_SUFFIX = "_metadata.dfc";
    /**
     * Maximum number of items that can be in a data file, this is computed based on teh max ram we are willing to use
     * while merging.
     */
    private static final int MAX_DATA_FILE_NUM_ITEMS = (int)
            Math.max((settings.getMaxRamUsedForMergingGb() * GIBIBYTES_TO_BYTES) / (Long.BYTES * 3), Integer.MAX_VALUE);
    /** The number of times to retry index based reads */
    private static final int NUM_OF_READ_RETRIES = 5;

    /** The directory to store data files */
    private final Path storeDir;
    /** Base name for the data files, allowing more than one DataFileCollection to share a directory */
    private final String storeName;
    /** Another base name for the data files. If files with this base name exist, they are loaded by this
     * file collection at startup. New files will have storeName as the prefix, not legacyStoreName **/
    private final String legacyStoreName;
    /** Serializer responsible for serializing/deserializing data items into and out of files */
    private final DataItemSerializer<D> dataItemSerializer;
    /** True if this DataFileCollection was loaded from an existing set of files */
    private final boolean loadedFromExistingFiles;
    /** The index to use for the next file we create */
    private final AtomicInteger nextFileIndex = new AtomicInteger();
    /** The range of valid data item keys for data currently stored by this data file collection. */
    private volatile KeyRange validKeyRange = INVALID_KEY_RANGE;
    /** The list of current files in this data file collection */
    private final AtomicReference<ImmutableIndexedObjectList<DataFileReader<D>>> indexedFileList =
            new AtomicReference<>();
    /** The current open file writer, if we are in the middle of writing a new file or null if not writing */
    private final AtomicReference<DataFileWriter<D>> currentDataFileWriter = new AtomicReference<>();
    /**
     * Constructor for creating ImmutableIndexedObjectLists
     */
    private final Function<List<DataFileReader<D>>, ImmutableIndexedObjectList<DataFileReader<D>>>
            indexedObjectListConstructor;
    /** Set if all indexes of new files currently being written. This is only maintained if logging is trace level */
    private final ConcurrentSkipListSet<Integer> setOfNewFileIndexes =
            logger.isTraceEnabled() ? new ConcurrentSkipListSet<>() : null;

    /**
     * Construct a new DataFileCollection.
     *
     * @param storeDir
     * 		The directory to store data files
     * @param storeName
     * 		Base name for the data files, allowing more than one DataFileCollection to share a directory
     * @param legacyStoreName
     * 		Base name for the data files. If not null, data files with this prefix are processed by this
     * 		file collection at startup same way as files prefixed with storeName
     * @param dataItemSerializer
     * 		Serializer responsible for serializing/deserializing data items into and out of files.
     * @param loadedDataCallback
     * 		Callback for rebuilding indexes from existing files, can be null if not needed. Using
     * 		this is expensive as it requires all files to be read and parsed.
     * @throws IOException
     * 		If there was a problem creating new data set or opening existing one
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
     * Construct a new DataFileCollection.
     *
     * @param storeDir
     * 		The directory to store data files
     * @param storeName
     * 		Base name for the data files, allowing more than one DataFileCollection to share a directory
     * @param legacyStoreName
     * 		Base name for the data files. If not null, data files with this prefix are processed by this
     * 		file collection at startup same way as files prefixed with storeName
     * @param dataItemSerializer
     * 		Serializer responsible for serializing/deserializing data items into and out of files.
     * @param loadedDataCallback
     * 		Callback for rebuilding indexes from existing files, can be null if not needed. Using
     * 		this is expensive as it requires all files to be read and parsed.
     * @param indexedObjectListConstructor
     * 		Constructor for creating ImmutableIndexedObjectList instances.
     * @throws IOException
     * 		If there was a problem creating new data set or opening existing one
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
     * Get the valid range of keys for data items currently stored by this data file collection. Any data items with
     * keys below this can be deleted during a merge.
     *
     * @return valid key range
     */
    public KeyRange getValidKeyRange() {
        return validKeyRange;
    }

    /**
     * Get if this data file collection was loaded from an existing set of files or if it was a new empty collection
     *
     * @return true if loaded from existing, false if new set of files
     */
    public boolean isLoadedFromExistingFiles() {
        return loadedFromExistingFiles;
    }

    /**
     * Get the number of files in this DataFileCollection
     */
    public int getNumOfFiles() {
        return indexedFileList.get().size();
    }

    /**
     * Get a list of all files in this collection that have been fully finished writing and are read only
     *
     * @param maxSizeMb
     * 		all files returned are smaller than this number of MB
     */
    public List<DataFileReader<D>> getAllFullyWrittenFiles(final int maxSizeMb) {
        final ImmutableIndexedObjectList<DataFileReader<D>> activeIndexedFiles = indexedFileList.get();
        if (activeIndexedFiles == null) {
            return Collections.emptyList();
        }
        if (maxSizeMb == Integer.MAX_VALUE) {
            return activeIndexedFiles.stream().collect(Collectors.toList());
        }
        final long maxSizeBytes = maxSizeMb * (long) MEBIBYTES_TO_BYTES;
        return activeIndexedFiles.stream()
                .filter(file -> file.getSize() < maxSizeBytes)
                .collect(Collectors.toList());
    }

    /**
     * Get a list of all files in this collection that have been fully finished writing and are read only
     */
    public List<DataFileReader<D>> getAllFullyWrittenFiles() {
        return getAllFullyWrittenFiles(Integer.MAX_VALUE);
    }

    /**
     * Get a list of all files in this collection that have been fully finished writing and are available for being
     * included in a new merge.
     */
    public List<DataFileReader<D>> getAllFilesAvailableForMerge() {
        final ImmutableIndexedObjectList<DataFileReader<D>> activeIndexedFiles = indexedFileList.get();
        if (activeIndexedFiles == null) {
            return Collections.emptyList();
        }
        return activeIndexedFiles.stream()
                .filter(DataFileReader::getFileAvailableForMerging)
                .collect(Collectors.toList());
    }

    /**
     * Get statistics for sizes of all files
     *
     * @return statistics for sizes of all fully written files, in bytes
     */
    public LongSummaryStatistics getAllFullyWrittenFilesSizeStatistics() {
        final ImmutableIndexedObjectList<DataFileReader<D>> activeIndexedFiles = indexedFileList.get();
        return activeIndexedFiles == null
                ? new LongSummaryStatistics()
                : activeIndexedFiles.stream().mapToLong(DataFileReader::getSize).summaryStatistics();
    }

    /**
     * Merges all files in filesToMerge
     *
     * @param index
     * 		takes a map of moves from old location to new location. Once it is finished and
     * 		returns it is assumed all readers will no longer be looking in old location, so old
     * 		files can be safely deleted.
     * @param filesToMerge
     * 		list of files to merge
     * @param mergingPaused
     * 		Semaphore to monitor if we should pause merging
     * @return list of files created during the merge
     * @throws IOException If there was a problem merging
     * @throws InterruptedException If the merge thread was interrupted
     */
    public synchronized List<Path> mergeFiles(
            final CASable index, final List<DataFileReader<D>> filesToMerge, final Semaphore mergingPaused)
            throws IOException, InterruptedException {
        // Check whether we even need to do anything. Maybe there is only a single file or
        // *no* files that need to be merged.
        if (filesToMerge.size() < 2) {
            // nothing to do we have merged since the last data update
            logger.info(JASPER_DB.getMarker(), "No files were available for merging [{}]", storeName);
            return Collections.emptyList();
        }

        // create a merge time stamp, this timestamp is the newest time of the set of files we are merging
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        final Instant mergeTime = filesToMerge.stream()
                .map(file -> file.getMetadata().getCreationDate())
                .max(Instant::compareTo)
                .get();

        // Create the map used to track moves. The ThreeLongsList uses the array-of-arrays
        // pattern for internal data management, so we can grow without triggering an expensive
        // copy operation.
        final ThreeLongsList movesMap = new ThreeLongsList(MAX_DATA_FILE_NUM_ITEMS, settings.getMoveListChunkSize());
        // create list of paths of files created during merge
        final List<Path> newFilesCreated = new ArrayList<>();
        // Open a new merge file for writing
        DataFileWriter<D> newFileWriter = newDataFile(mergeTime, true);
        newFilesCreated.add(newFileWriter.getPath());
        final AtomicLong lastLowestKeyWritten = new AtomicLong(-1);
        // get the most recent min and max key
        assert indexedFileList.get().size() > 0
                : "The merge files should still be on disk and still be part "
                        + "of indexedFileList, so we should always have something here.";
        final KeyRange keyRange = this.validKeyRange;
        // open iterators, first iterator will be on oldest file
        List<DataFileIterator> blockIterators = new ArrayList<>(filesToMerge.size());
        for (final DataFileReader<D> fileReader : filesToMerge) {
            blockIterators.add(fileReader.createIterator());
        }
        // move all iterators to first block
        ListIterator<DataFileIterator> blockIteratorsIterator = blockIterators.listIterator();
        while (blockIteratorsIterator.hasNext()) {
            DataFileIterator dataFileIterator = blockIteratorsIterator.next();
            try {
                if (!dataFileIterator.next()) {
                    // we have finished reading this file so don't need it iterate it next time
                    dataFileIterator.close();
                    blockIteratorsIterator.remove();
                }
            } catch (IOException e) {
                logger.error(EXCEPTION.getMarker(), "Failed while removing finished data file iterators", e);
            }
        }
        // while we still have data left to read
        long lastLowestKey = -1;
        long[] thisRoundsKeys = new long[blockIterators.size()];
        long[] lastRoundsKeys = new long[blockIterators.size()];
        while (!blockIterators.isEmpty()) {
            // find the lowest key any iterator has
            long lowestKey = Long.MAX_VALUE;
            for (int i = 0; i < blockIterators.size(); i++) {
                final DataFileIterator blockIterator = blockIterators.get(i);
                final long key = blockIterator.getDataItemsKey();
                if (key < lowestKey) {
                    lowestKey = key;
                }
                thisRoundsKeys[i] = key;
            }
            // check keys never decrease, if they do something is very broken like a file has data in non-ascending
            // order
            if (lowestKey <= lastLowestKey) {
                final long lk = lowestKey;
                final long llk = lastLowestKey;
                final long[] trk = thisRoundsKeys;
                final long[] lrk = lastRoundsKeys;
                logger.error(
                        EXCEPTION.getMarker(),
                        () -> String.format(
                                """
								lowestKey=%d lastLowestKey=%d,
								blockIterator keys =%s
								last rounds keys =%s""",
                                lk, llk, Arrays.toString(trk), Arrays.toString(lrk)));
                for (final DataFileIterator blockIterator : blockIterators) {
                    logger.error(EXCEPTION.getMarker(), "blockIterator={}", blockIterator);
                }
                throw new IllegalStateException("This should never happen, lowestKey is less than "
                        + "the last lowestKey. This could mean the files have keys in non-ascending order.");
            }
            lastLowestKey = lowestKey;
            final long[] tmp = lastRoundsKeys;
            lastRoundsKeys = thisRoundsKeys;
            thisRoundsKeys = tmp;
            final long curDataLocation = index.get(lowestKey);
            boolean seen = false;
            // check if that key is in range
            if (keyRange.withinRange(lowestKey)) {
                // find which iterator is the newest that has the lowest key
                DataFileIterator newestIteratorWithLowestKey = null;
                Instant newestIteratorTime = Instant.EPOCH;
                int newestIndex = Integer.MIN_VALUE;
                for (final DataFileIterator blockIterator : blockIterators) {
                    final long key = blockIterator.getDataItemsKey();
                    if (key != lowestKey) continue;
                    seen = seen || blockIterator.getDataItemsDataLocation() == curDataLocation;
                    int cmp = blockIterator.getDataFileCreationDate().compareTo(newestIteratorTime);
                    if (cmp > 0 || (cmp == 0 && blockIterator.getDataFileIndex() > newestIndex)) {
                        newestIteratorWithLowestKey = blockIterator;
                        newestIteratorTime = blockIterator.getDataFileCreationDate();
                        newestIndex = blockIterator.getDataFileIndex();
                    }
                }
                assert newestIteratorWithLowestKey != null;
                // write that key from newest iterator to new merge file
                assert newestIteratorWithLowestKey.getDataItemsKey()
                                > lastLowestKeyWritten.getAndSet(newestIteratorWithLowestKey.getDataItemsKey())
                        : "Fail, we should always be writing data with keys in ascending order.";

                if (seen) {
                    final long newDataLocation = newFileWriter.writeCopiedDataItem(
                            newestIteratorWithLowestKey.getMetadata().getSerializationVersion(),
                            newestIteratorWithLowestKey.getDataItemData());
                    // check if newFile is full
                    if (movesMap.size() > MAX_DATA_FILE_NUM_ITEMS
                            || newFileWriter.getFileSizeEstimate() >= settings.getMaxDataFileBytes()) {
                        // finish writing current file, add it for reading then open new file for writing
                        closeCurrentMergeFile(newFileWriter, index, movesMap, mergingPaused);
                        logger.info(JASPER_DB.getMarker(), "MovesMap.size() = {}", movesMap.size());
                        movesMap.clear();
                        newFileWriter = newDataFile(mergeTime, true);
                        newFilesCreated.add(newFileWriter.getPath());
                        lastLowestKeyWritten.set(-1);
                    }
                    // add to movesMap
                    movesMap.add(lowestKey, curDataLocation, newDataLocation);
                }
            }
            // move all iterators on that contained lowestKey
            blockIteratorsIterator = blockIterators.listIterator();
            while (blockIteratorsIterator.hasNext()) {
                DataFileIterator dataFileIterator = blockIteratorsIterator.next();
                if (dataFileIterator.getDataItemsKey() == lowestKey) {
                    try {
                        if (!dataFileIterator.next()) {
                            // we have finished reading this file so don't need it iterate it next time
                            dataFileIterator.close();
                            blockIteratorsIterator.remove();
                        }
                    } catch (IOException e) {
                        logger.error(EXCEPTION.getMarker(), "Failed to purge iterators containing lowestKey", e);
                    }
                }
            }
        }
        // close current file
        closeCurrentMergeFile(newFileWriter, index, movesMap, mergingPaused);
        // delete old files
        try {
            mergingPaused.acquire();
            deleteFiles(new HashSet<>(filesToMerge));
        } finally {
            mergingPaused.release();
        }
        // return list of files created
        return newFilesCreated;
    }

    /**
     * Close all the data files
     */
    public void close() throws IOException {
        // finish writing if we still are
        final DataFileWriter<D> currentDataFileForWriting = this.currentDataFileWriter.getAndSet(null);
        if (currentDataFileForWriting != null) {
            currentDataFileForWriting.finishWriting();
        }
        // calling startSnapshot causes the metadata file to be written
        saveMetadata(storeDir);
        // close all files
        final ImmutableIndexedObjectList<DataFileReader<D>> fileList = this.indexedFileList.getAndSet(null);
        if (fileList != null) {
            for (DataFileReader<D> file : (Iterable<DataFileReader<D>>) fileList.stream()::iterator) {
                file.close();
            }
        }
    }

    /**
     * Start writing a new data file
     *
     * @throws IOException
     * 		If there was a problem opening a new data file
     */
    public void startWriting() throws IOException {
        final DataFileWriter<D> activeDataFileWriter = currentDataFileWriter.get();
        if (activeDataFileWriter != null) {
            throw new IOException("Tried to start writing when we were already writing.");
        }
        currentDataFileWriter.set(newDataFile(Instant.now(), false));
    }

    /**
     * Store a data item into the current file opened with startWriting().
     *
     * @param dataItem
     * 		The data item to write into file
     * @return the location where data item was stored. This contains both the file and the location within the file.
     * @throws IOException
     * 		If there was a problem writing this data item to the file.
     */
    public long storeDataItem(final D dataItem) throws IOException {
        final DataFileWriter<D> currentDataFileForWriting = this.currentDataFileWriter.get();
        if (currentDataFileForWriting == null) {
            throw new IOException("Tried to put data " + dataItem + " when we never started writing.");
        }
        /* FUTURE WORK - https://github.com/swirlds/swirlds-platform/issues/3926 */
        return currentDataFileForWriting.storeDataItem(dataItem);
    }

    /**
     * End writing current data file
     *
     * @param minimumValidKey
     * 		The minimum valid data key at this point in time, can be used for cleaning out old data
     * @param maximumValidKey
     * 		The maximum valid data key at this point in time, can be used for cleaning out old data
     * @throws IOException
     * 		If there was a problem closing the data file
     */
    public DataFileReader<D> endWriting(final long minimumValidKey, final long maximumValidKey) throws IOException {
        this.validKeyRange = new KeyRange(minimumValidKey, maximumValidKey);
        DataFileWriter<D> activeDataFileWriter = currentDataFileWriter.getAndSet(null);
        if (activeDataFileWriter == null) {
            throw new IOException("Tried to end writing when we never started writing.");
        }
        // finish writing the file and write its footer
        final DataFileMetadata metadata = activeDataFileWriter.finishWriting();
        // open reader on newly written file and add it to indexedFileList ready to be read.
        return addNewDataFileReader(activeDataFileWriter.getPath(), metadata);
    }

    /**
     * Read a data item from any file that has finished being written. This is not 100% thread safe with concurrent
     * merging, it is possible it will throw a ClosedChannelException or return null. So it should be retried if those
     * happen.
     *
     * @param dataLocation
     * 		the location of the data item to read. This contains both the file and the location within
     * 		the file.
     * @return Data item if the data location was found in files or null if not found
     * @throws IOException
     * 		If there was a problem reading the data item.
     * @throws ClosedChannelException
     * 		In the very rare case merging closed the file between us checking if file is open and reading
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
        final ImmutableIndexedObjectList<DataFileReader<D>> currentIndexedFileList = this.indexedFileList.get();
        if (fileIndex < 0 || currentIndexedFileList == null || (file = currentIndexedFileList.get(fileIndex)) == null) {
            throw new IOException("Got a data location from index for a file that doesn't exist. " + "dataLocation="
                    + DataFileCommon.dataLocationToString(dataLocation)
                    + " fileIndex=" + fileIndex
                    + " validKeyRange=" + this.validKeyRange
                    + "\ncurrentIndexedFileList=" + currentIndexedFileList);
        }
        // read data, check at last second that file is not closed
        if (file.isOpen()) {
            return file.readDataItem(dataLocation);
        } else {
            // Let's log this as it should happen very rarely but if we see it a lot then we should have a rethink.
            logger.warn(
                    EXCEPTION.getMarker(), "Store [{}] DataFile was closed while trying to read from file", storeName);
            return null;
        }
    }
    /**
     * Read a data item from any file that has finished being written. Uses a LongList that maps key-&gt;dataLocation,
     * this allows for multiple retries going back to the index each time. The allows us to cover the cracks where
     * threads can slip though.
     * <p>This depends on the fact that LongList has a nominal value of LongList.IMPERMISSIBLE_VALUE=0 for non-existent
     * values.</p>
     *
     * @param index        key-&gt;dataLocation index
     * @param keyIntoIndex The key to lookup in index
     * @return Data item if the data location was found in files. If contained in the
     * index but not in files after a number of retries then an exception is thrown.
     * @throws IOException If there was a problem reading the data item.
     */
    public D readDataItemUsingIndex(final LongList index, final long keyIntoIndex) throws IOException {
        // Try reading up to 5 times, 99.999% should work first try but there is a small chance the file was closed by
        // merging when we are half way though reading, and we will see  file.isOpen() = false or a
        // ClosedChannelException. Doing a retry should get a different result because dataLocation should be different
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
                // check we actually read data, this could be null if the file was closed half way though us reading
                if (readData != null) {
                    return readData;
                }
            } catch (IOException e) {
                // For up to 5 retries we ignore this exception because next retry should get a new file location from
                // index. So should never hit a closed file twice.
                final int currentRetry = retries;
                // Log as much useful information that we can to help diagnose this problem before throwing exception.
                logger.warn(
                        EXCEPTION.getMarker(),
                        () -> {
                            final String currentFiles = indexedFileList.get() == null
                                    ? "UNKNOWN"
                                    : indexedFileList.get().prettyPrintedIndices();

                            return "Store [" + storeName + "] had IOException while trying to read " + "key ["
                                    + keyIntoIndex + "] at " + "offset ["
                                    + byteOffsetFromDataLocation(dataLocation) + "] from " + "file ["
                                    + fileIndexFromDataLocation(dataLocation) + "] " + "on retry ["
                                    + currentRetry + "]. " + "Current files are ["
                                    + currentFiles + "]" + ", validKeyRange="
                                    + this.validKeyRange + ", storeDir=["
                                    + storeDir.toAbsolutePath() + "]";
                        },
                        e);
            }
        }
        throw new IOException("Read failed after 5 retries");
    }

    /**
     * {@inheritDoc}
     */
    public void snapshot(final Path snapshotDirectory) throws IOException {
        saveMetadata(snapshotDirectory);
        final List<DataFileReader<D>> snapshotIndexedFiles = getAllFullyWrittenFiles();
        for (DataFileReader<D> fileReader : snapshotIndexedFiles) {
            Path existingFile = fileReader.getPath();
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
     * Simple callback class during reading an existing set of files during startup, so that indexes can be built
     */
    @FunctionalInterface
    public interface LoadedDataCallback {
        /**
         * Add an index entry for the given key and data location and value
         */
        void newIndexEntry(long key, long dataLocation, ByteBuffer dataValue);
    }

    // =================================================================================================================
    // Private API

    /** Finish a merge file and close it. */
    private void closeCurrentMergeFile(
            final DataFileWriter<D> newFileWriter,
            final CASable index,
            final ThreeLongsList movesMap,
            final Semaphore mergingPaused)
            throws IOException, InterruptedException {
        // close current file
        final DataFileMetadata metadata = newFileWriter.finishWriting();
        // add it for reading
        final DataFileReader<D> dataFileReader = addNewDataFileReader(newFileWriter.getPath(), metadata);
        // apply index changes
        movesMap.forEach((key, oldValue, newValue) -> {
            try {
                mergingPaused.acquire();
                index.putIfEqual(key, oldValue, newValue);
            } finally {
                mergingPaused.release();
            }
        });
        // we have updated all indexes now so can now include this file in future merges
        dataFileReader.setFileAvailableForMerging(true);
    }

    /**
     * Used by tests to get data files for checking
     *
     * @param index
     * 		data file index
     * @return the data file if one exists at that index
     */
    DataFileReader<D> getDataFile(final int index) {
        final ImmutableIndexedObjectList<DataFileReader<D>> fileList = this.indexedFileList.get();
        return fileList == null ? null : fileList.get(index);
    }

    /**
     * Create and add a new data file reader to end of indexedFileList
     *
     * @param filePath
     * 		the path for the new data file
     * @param metadata
     * 		The metadata for the file at filePath, to save reading from file
     * @return The newly added DataFileReader.
     */
    private DataFileReader<D> addNewDataFileReader(final Path filePath, final DataFileMetadata metadata)
            throws IOException {
        final DataFileReader<D> newDataFileReader = new DataFileReader<>(filePath, dataItemSerializer, metadata);
        if (logger.isTraceEnabled()) {
            setOfNewFileIndexes.remove(metadata.getIndex());
        }
        indexedFileList.getAndUpdate(currentFileList -> {
            try {
                return (currentFileList == null)
                        ? indexedObjectListConstructor.apply(singletonList(newDataFileReader))
                        : currentFileList.withAddedObject(newDataFileReader);
            } catch (IllegalArgumentException e) {
                logger.error(EXCEPTION.getMarker(), "Failed when updated the indexed files list", e);
                throw e;
            }
        });
        return newDataFileReader;
    }

    /**
     * Delete a list of files from indexedFileList and then from disk
     *
     * @param filesToDelete
     * 		the list of files to delete
     * @throws IOException
     * 		If there was a problem deleting the files
     */
    private void deleteFiles(final Set<DataFileReader<D>> filesToDelete) throws IOException {
        // remove files from index
        indexedFileList.getAndUpdate(currentFileList ->
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
     * @param creationTime
     * 		The creation time for the data in the new file. It could be now or old in case of merge.
     * @param isMergeFile
     * 		if the new file is a merge file or not
     * @return the newly created data file
     */
    private DataFileWriter<D> newDataFile(final Instant creationTime, final boolean isMergeFile) throws IOException {
        final int newFileIndex = nextFileIndex.getAndIncrement();
        if (logger.isTraceEnabled()) {
            setOfNewFileIndexes.add(newFileIndex);
        }
        return new DataFileWriter<>(storeName, storeDir, newFileIndex, dataItemSerializer, creationTime, isMergeFile);
    }

    /**
     * Saves the metadata to the given directory. A valid database must have the metadata.
     *
     * @param directory
     * 		The location to save the metadata. The directory will be created (and all parent directories) if needed.
     * @throws IOException
     * 		Thrown if a lower level IOException occurs.
     */
    private void saveMetadata(Path directory) throws IOException {
        Files.createDirectories(directory);
        // write metadata, this will be incredibly fast, and we need to capture min and max key while in save lock
        try (DataOutputStream metaOut =
                new DataOutputStream(Files.newOutputStream(directory.resolve(storeName + METADATA_FILENAME_SUFFIX)))) {
            final KeyRange keyRange = this.validKeyRange;
            metaOut.writeInt(METADATA_FILE_FORMAT_VERSION);
            metaOut.writeLong(keyRange.getMinValidKey());
            metaOut.writeLong(keyRange.getMaxValidKey());
            metaOut.flush();
        }
    }

    private boolean tryLoadFromExistingStore(final LoadedDataCallback loadedDataCallback) throws IOException {
        if (!Files.isDirectory(storeDir)) {
            throw new IOException("Tried to initialize DataFileCollection with a storage "
                    + "directory that is not a directory. [" + storeDir.toAbsolutePath() + "]");
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
            } catch (IOException e) {
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
                // next file will have index zero as we did not find any files even though the directory existed
                nextFileIndex.set(0);
                return false;
            }
        }
    }

    private void loadFromExistingFiles(
            final DataFileReader<D>[] dataFileReaders, final LoadedDataCallback loadedDataCallback) throws IOException {
        logger.info(
                JASPER_DB.getMarker(),
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
            try (DataInputStream metaIn = new DataInputStream(Files.newInputStream(metaDataFile))) {
                final int fileVersion = metaIn.readInt();
                if (fileVersion != METADATA_FILE_FORMAT_VERSION) {
                    throw new IOException("Tried to read a file with incompatible file format version [" + fileVersion
                            + "], expected [" + METADATA_FILE_FORMAT_VERSION + "].");
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
        indexedFileList.set(indexedObjectListConstructor.apply(List.of(dataFileReaders)));
        // work out what the next index would be, the highest current index plus one
        int maxIndex = -1;
        for (final DataFileReader<D> reader : dataFileReaders) {
            maxIndex = Math.max(maxIndex, reader.getIndex());
        }
        nextFileIndex.set(maxIndex + 1);
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
        // mark all files we loaded as being available for merging
        for (DataFileReader<D> dataFileReader : dataFileReaders) {
            dataFileReader.setFileAvailableForMerging(true);
        }
        logger.info(
                JASPER_DB.getMarker(), "Finished loading existing data files for DataFileCollection [{}]", storeName);
    }
}
