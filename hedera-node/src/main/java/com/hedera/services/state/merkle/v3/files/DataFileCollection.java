package com.hedera.services.state.merkle.v3.files;

import com.hedera.services.state.merkle.v3.collections.ImmutableIndexedObjectListUsingArray;
import org.eclipse.collections.api.map.primitive.ImmutableLongObjectMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.hedera.services.state.merkle.v3.files.DataFileCommon.*;

/**
 * DataFileCollection manages a set of data files and the compaction of them over time. It stores key,value pairs and
 * returns a long representing the location it was stored. You can then retrieve that key/value pair later using the
 * location you got when storing. There is not understanding of what the keys mean and no way to look up data by key.
 * The reason the keys are separate from the values is so that we can merge matching keys. We only keep the newest
 * key/value pair for any matching key. It may look like a map, but it is not. You need an external index outside this
 * class to be able to store key -> data location mappings.
 */
@SuppressWarnings("unused")
public class DataFileCollection {
    private final Path storeDir;
    private final String storeName;
    private final int dataItemValueSize;
    private final boolean loadedFromExistingFiles;
    private final DataFileReaderFactory dataFileReaderFactory;
    private final AtomicInteger nextFileIndex = new AtomicInteger();
    private final AtomicLong minimumValidKey = new AtomicLong();
    private final AtomicLong maximumValidKey = new AtomicLong();
    private final AtomicReference<ImmutableIndexedObjectListUsingArray<DataFileReader>> indexedFileList = new AtomicReference<>();
    private final AtomicReference<DataFileWriter> currentDataFileWriter = new AtomicReference<>();
    private final AtomicReference<Instant> lastMerge = new AtomicReference<>(Instant.ofEpochSecond(0));
    /**
     * Lock to prevent:
     *   - two concurrent writers
     *   - creating of new files by writers while we are getting a new file for merging
     *   - concurrent index updates by writers and merge call-back
     */
    private final ReentrantLock writeLock = new ReentrantLock();

    /**
     * Construct a new DataFileCollection with the default DataFileReaderFactory
     *
     * @param storeDir The directory to store data files
     * @param storeName Name for the data files, allowing more than one DataFileCollection to share a directory
     * @param dataItemValueSize the size in bytes for data values being stored. It can be set to
     *                          DataFileCommon.VARIABLE_DATA_SIZE if you want to store variable size data values.
     * @param loadedDataCallback call back for rebuilding indexes from existing files, can be null if not needed.
     * @throws IOException If there was a problem creating new data set or opening existing one
     */
    public DataFileCollection(Path storeDir, String storeName, int dataItemValueSize,
                              LoadedDataCallback loadedDataCallback) throws IOException {
         this(storeDir,storeName,dataItemValueSize, loadedDataCallback, new DataFileReaderFactory() {
             public DataFileReader newDataFileReader(Path path) throws IOException {
                 return new DataFileReaderThreadLocal(path);
             }

             public DataFileReader newDataFileReader(Path path, DataFileMetadata metadata) throws IOException {
                 return new DataFileReaderThreadLocal(path, metadata);
             }
         });
    }

    /**
     * Construct a new DataFileCollection with custom DataFileReaderFactory
     *
     * @param storeDir The directory to store data files
     * @param storeName Name for the data files, allowing more than one DataFileCollection to share a directory
     * @param dataItemValueSize the size in bytes for data values being stored.
     * @param loadedDataCallback call back for rebuilding indexes from existing files, can be null if not needed.
     * @param dataFileReaderFactory factory to use for creating data file readers, allows pluggable reader implementations
     * @throws IOException If there was a problem creating new data set or opening existing one
     */
    public DataFileCollection(Path storeDir, String storeName, int dataItemValueSize,
                              LoadedDataCallback loadedDataCallback, DataFileReaderFactory dataFileReaderFactory)
                              throws IOException {
        this.storeDir = storeDir;
        this.storeName = storeName;
        this.dataItemValueSize = dataItemValueSize;
        this.dataFileReaderFactory = dataFileReaderFactory;
        // check if exists, if so open existing files
        if (Files.exists(storeDir)) {
            if (!Files.isDirectory(storeDir)) throw new IOException("Tried to DataFileCollection with a storage directory that is not a directory. ["+storeDir.toAbsolutePath()+"]");
            final DataFileReader[] dataFileReaders = Files.list(storeDir)
                        .filter(path -> isFullyWrittenDataFile(storeName,path))
                        .map(path -> {
                            try {
                                return dataFileReaderFactory.newDataFileReader(path);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .sorted()
                        .toArray(DataFileReader[]::new);
            if (dataFileReaders.length > 0) {
                System.out.println("Loading existing set of ["+dataFileReaders.length+"] data files for DataFileCollection ["+storeName+"] ...");
                indexedFileList.set(new ImmutableIndexedObjectListUsingArray<>(dataFileReaders));
                // work out what the next index would be, the highest current index plus one
                nextFileIndex.set(dataFileReaders[dataFileReaders.length-1].getMetadata().getIndex() + 1);
                // we loaded an existing set of files
                this.loadedFromExistingFiles = true;
                // now call indexEntryCallback
                if (loadedDataCallback != null) {
                    // now iterate over every file and every key
                    for(var file:dataFileReaders) {
                        try (DataFileIterator iterator = new DataFileIterator(file.getPath(), file.getMetadata())) {
                            while(iterator.next()) {
                                loadedDataCallback.newIndexEntry(
                                        iterator.getDataItemsKey(),
                                        iterator.getDataItemsDataLocation(),
                                        iterator.getDataItemData());
                            }
                        }
                    }
                }
                System.out.println("Finished existing data files for DataFileCollection ["+storeName+"]");
            } else {
                // next file will have index zero as we did not find any files even though the directory existed
                nextFileIndex.set(0);
                this.loadedFromExistingFiles = false;
            }
        } else {
            // create store dir
            Files.createDirectories(storeDir);
            // next file will have index zero
            nextFileIndex.set(0);
            this.loadedFromExistingFiles = false;
        }
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
     * Get a list of all files in this collection that have been fully finished writing and are read only
     *
     * @param maxSizeMb all files returned are smaller than this number of MB
     */
    public List<DataFileReader> getAllFullyWrittenFiles(int maxSizeMb) {
        final var indexedFileList = this.indexedFileList.get();
        if (maxSizeMb == Integer.MAX_VALUE) return indexedFileList.stream().collect(Collectors.toList());
        return indexedFileList == null ? Collections.emptyList() : indexedFileList.stream()
                .filter(file -> {
                    try {
                        return file.getSize() < maxSizeMb;
                    } catch (IOException e) {
                        e.printStackTrace();
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Merges all files in filesToMerge
     *
     * @param locationChangeHandler takes a map of moves from old location to new location. Once it is finished and
     *                              returns it is assumed all readers will no longer be looking in old location, so old
     *                              files can be safely deleted.
     * @param filesToMerge list of files to merge
     */
    public synchronized void mergeFile(Consumer<ImmutableLongObjectMap<long[]>> locationChangeHandler,
                                       List<DataFileReader> filesToMerge) throws IOException {
        final var indexedFileList = this.indexedFileList.get();
        // check if anything new has been written since we last merged
        if (filesToMerge.size() < 2 || indexedFileList.getLast().getMetadata().getCreationDate().isBefore(lastMerge.get())) {
            // nothing to do we have merged since the last data update
            System.out.println("Merge not needed as no data changed since last merge in DataFileCollection ["+storeName+"]");
            System.out.println(" last file creation ="+indexedFileList.getLast().getMetadata().getCreationDate()+" , lastMerge="+lastMerge.get());
            return;
        }
        // create a merge time stamp, this timestamp has to be after any writer has finished
        writeLock.lock(); System.out.println("WRITE LOCK - LOCK - MERGE FILE get merge time");
        final Instant mergeTime = Instant.now();
        writeLock.unlock(); System.out.println("WRITE LOCK - UNLOCK - MERGE FILE get merge time");
        // update last merge time
        lastMerge.set(mergeTime);
        // create new map for keeping track of moves
        LongObjectHashMap<long[]> movesMap = new LongObjectHashMap<>();
        // Open a new merge file for writing
        DataFileWriter newFileWriter = newDataFile(mergeTime,true);
        // get the most recent min and max key
        final DataFileMetadata mostRecentDataFileMetadata = indexedFileList.getLast().getMetadata();
        long minimumValidKey = mostRecentDataFileMetadata.getMinimumValidKey();
        long maximumValidKey = mostRecentDataFileMetadata.getMaximumValidKey();
        // open iterators, first iterator will be on oldest file
        List<DataFileIterator> blockIterators = filesToMerge.stream()
                .map(DataFileReader::createIterator)
                .collect(Collectors.toList());
        // move all iterators to first block
        ListIterator<DataFileIterator> blockIteratorsIterator = blockIterators.listIterator();
        while (blockIteratorsIterator.hasNext()) {
            DataFileIterator dataFileIterator =  blockIteratorsIterator.next();
            try {
                if (!dataFileIterator.next()) {
                    // we have finished reading this file so don't need it iterate it next time
                    dataFileIterator.close();
                    blockIteratorsIterator.remove();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // while we still have data left to read
        while(!blockIterators.isEmpty()) {
            // find the lowest key any iterator has and the newest iterator that has that key
            long lowestKey = Long.MAX_VALUE;
            Instant newestIteratorTime = Instant.EPOCH;
            DataFileIterator newestIteratorWithLowestKey = null;
            for (DataFileIterator blockIterator : blockIterators) { // find the lowest key any iterator has
                final long key = blockIterator.getDataItemsKey();
                if (key < lowestKey) lowestKey = key;
            }
            for (DataFileIterator blockIterator : blockIterators) { // find which iterator is the newest that has the lowest key
                long key = blockIterator.getDataItemsKey();
                if (key == lowestKey && blockIterator.getDataFileCreationDate().isAfter(newestIteratorTime)) {
                    newestIteratorTime = blockIterator.getDataFileCreationDate();
                    newestIteratorWithLowestKey = blockIterator;
                }
            }
            assert newestIteratorWithLowestKey != null;
            // write that key from newest iterator to new merge file
            long newDataLocation = newFileWriter.storeData(newestIteratorWithLowestKey.getDataItemData());
            // check if newFile is full
            if (newFileWriter.getFileSizeEstimate() >= MAX_DATA_FILE_SIZE) {
                // finish writing current file, add it for reading then open new file for writing
                final DataFileMetadata metadata = newFileWriter.finishWriting(minimumValidKey, maximumValidKey);
                addNewDataFileReader(newFileWriter.getPath(), metadata);
                newFileWriter = newDataFile(mergeTime,true);
            }
            // add to movesMap
            movesMap.put(lowestKey, new long[]{newestIteratorWithLowestKey.getDataItemsDataLocation(), newDataLocation});
            // move all iterators on that contained lowestKey
            blockIteratorsIterator = blockIterators.listIterator();
            while (blockIteratorsIterator.hasNext()) {
                DataFileIterator dataFileIterator =  blockIteratorsIterator.next();
                if (dataFileIterator.getDataItemsKey() == lowestKey) {
                    try {
                        if (!dataFileIterator.next()) {
                            // we have finished reading this file so don't need it iterate it next time
                            dataFileIterator.close();
                            blockIteratorsIterator.remove();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        // close current file
        final DataFileMetadata metadata = newFileWriter.finishWriting(minimumValidKey, maximumValidKey);
        // add it for reading
        addNewDataFileReader(newFileWriter.getPath(), metadata);
        // call locationChangeHandler with the write-lock so no writes are changing the index while we are
        writeLock.lock(); System.out.println("WRITE LOCK - LOCK - MERGE FILE location change handler");
        locationChangeHandler.accept(movesMap.toImmutable());
        writeLock.unlock(); System.out.println("WRITE LOCK - UNLOCK - MERGE FILE location change handler");
        // delete old files
        deleteFiles(filesToMerge);
    }

    /**
     * Close all the data files
     */
    public void close() throws IOException {
        // finish writing if we still are
        var currentDataFileForWriting = this.currentDataFileWriter.get();
        if (currentDataFileForWriting != null ) {
            currentDataFileForWriting.finishWriting(Long.MIN_VALUE, Long.MAX_VALUE);
        }
        final var fileList = this.indexedFileList.getAndSet(null);
        if (fileList != null) {
            for (var file : (Iterable<DataFileReader>) fileList.stream()::iterator) {
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
        var currentDataFileWriter = this.currentDataFileWriter.get();
        if (currentDataFileWriter != null) throw new IOException("Tried to start writing when we were already writing.");
        writeLock.lock(); System.out.println("WRITE LOCK - LOCK - START WRITING");
        this.currentDataFileWriter.set(newDataFile(Instant.now(), false));
    }

    /**
     * End writing current data file
     *
     * @param minimumValidKey The minimum valid data key at this point in time, can be used for cleaning out old data
     * @param maximumValidKey The maximum valid data key at this point in time, can be used for cleaning out old data
     * @throws IOException If there was a problem closing the data file
     */
    public void endWriting(long minimumValidKey, long maximumValidKey) throws IOException {
        this.minimumValidKey.set(minimumValidKey);
        this.maximumValidKey.set(maximumValidKey);
        var currentDataFileWriter = this.currentDataFileWriter.getAndSet(null);
        if (currentDataFileWriter == null) throw new IOException("Tried to end writing when we never started writing.");
        // finish writing the file and write its footer
        DataFileMetadata metadata = currentDataFileWriter.finishWriting(minimumValidKey, maximumValidKey);
        // open reader on newly written file and add it to indexedFileList ready to be read.
        addNewDataFileReader(currentDataFileWriter.getPath(), metadata);
        writeLock.unlock(); System.out.println("WRITE LOCK - UNLOCK - END WRITING");
    }

    /**
     * Store a data item into the current file opened with startWriting().
     *
     * @param key the key for the data item
     * @param data the data items data, it will be written from position() to limit() of ByteBuffer
     * @return the location where data item was stored. This contains both the file and the location within the file.
     * @throws IOException If there was a problem writing this data item to the file.
     */
    public long storeData(long key, ByteBuffer data) throws IOException {
        var currentDataFileForWriting = this.currentDataFileWriter.get();
        if (currentDataFileForWriting == null) throw new IOException("Tried to put data when we never started writing.");
        // TODO detect if the file is full and start a new one if needed
        // store key,hash and data in current file and get the offset where it was stored
        return currentDataFileForWriting.storeData(key, data);
    }

    /**
     * Read a data item from any file that has finished being written.
     *
     * @param dataLocation the location of the data item to read. This contains both the file and the location within
     *                     the file.
     * @param toReadDataInto Byte buffer to read data into. Data will be read up to the remaining() bytes in the
     *                       ByteBuffer or the maximum amount of stored data, which ever is less.
     * @param dataToRead What data you want to read, key, value or both
     * @return true if the data location was found in files
     * @throws IOException If there was a problem reading the data item.
     */
    public boolean readData(long dataLocation, ByteBuffer toReadDataInto, DataFileReader.DataToRead dataToRead) throws IOException {
        // check if found
        if (dataLocation == 0) return false;
        // split up location
        int fileIndex = fileIndexFromDataLocation(dataLocation);
        // check if file for fileIndex exists
        DataFileReader file;
        final var currentIndexedFileList = this.indexedFileList.get();
        if (fileIndex < 0 || currentIndexedFileList == null || (file  = currentIndexedFileList.get(fileIndex)) == null) {
            throw new IOException("Got a data location from index for a file that doesn't exist. dataLocation=" +
                   DataFileCommon.dataLocationToString(dataLocation) + " fileIndex=" + fileIndex +
                    " minimumValidKey=" + minimumValidKey.get() + " maximumValidKey=" + maximumValidKey.get() +
                    "\ncurrentIndexedFileList=" + currentIndexedFileList);
        }
        // read data
        file.readData(toReadDataInto,dataLocation,dataToRead);
        return true;
    }

    // =================================================================================================================
    // Index Callback Class

    /**
     * Simple callback class during reading an existing set of files during startup, so that indexes can be built
     */
    public interface LoadedDataCallback {
        /**
         * Add an index entry for the given key and data location and value
         */
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
    DataFileReader getDataFile(int index) {
        final var fileList = this.indexedFileList.get();
        return fileList == null ? null : fileList.get(index);
    }

    /**
     * Create and add a new data file reader to end of indexedFileList
     *
     * @param filePath the path for the new data file
     * @param metadata the metadata for the new file
     */
    private void addNewDataFileReader(Path filePath, DataFileMetadata metadata) {
        this.indexedFileList.getAndUpdate(
                currentFileList -> {
                    try {
                        DataFileReader newDataFileReader = dataFileReaderFactory.newDataFileReader(filePath,metadata);
                        return (currentFileList == null) ?
                            new ImmutableIndexedObjectListUsingArray<>(Collections.singletonList(newDataFileReader)) :
                            currentFileList.withAddedObject(newDataFileReader);
                    } catch (IOException e) {
                        throw new RuntimeException(e); // TODO something better?
                    }
                });
    }

    /**
     * Delete a list of files from indexedFileList and then from disk
     *
     * @param filesToDelete the list of files to delete
     * @throws IOException If there was a problem deleting the files
     */
    private void deleteFiles(List<DataFileReader> filesToDelete) throws IOException {
        // remove files from index
        this.indexedFileList.getAndUpdate(
                currentFileList -> (currentFileList == null) ? null : currentFileList.withDeletingObjects(filesToDelete));
        // now close and delete all the files
        for(DataFileReader fileReader: filesToDelete) {
            fileReader.close();
            Files.delete(fileReader.getPath());
        }
    }

    /**
     * Create a new data file writer
     *
     * @param isMergeFile if the new file is a merge file or not
     * @return the newly created data file
     */
    private DataFileWriter newDataFile(Instant creationTime, boolean isMergeFile) throws IOException {
        return new DataFileWriter(storeName,storeDir,nextFileIndex.getAndIncrement(),dataItemValueSize,creationTime,isMergeFile);
    }

}
