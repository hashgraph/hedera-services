package com.hedera.services.state.merkle.v3.files;

import com.hedera.services.state.merkle.v3.files.DataFileCollection.LoadedDataCallback;
import com.hedera.services.state.merkle.v3.offheap.OffHeapLongList;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A map like data store with long keys. The index is in RAM and the data is stored in a set o files on disk. It assumes
 * that the keys long start at 0. If they do not then a lot of RAM will be wasted.
 */
public class MemoryIndexDiskKeyValueStore implements AutoCloseable {
    /**
     * Off-heap index mapping, it uses our key as the index within the list and the value is the dataLocation in
     * fileCollection where the key/value pair is stored.
     */
    private final OffHeapLongList index = new OffHeapLongList();
    /** On disk set of DataFiles that contain our key/value pairs */
    private final DataFileCollection fileCollection;
    /**
     * The name for the data store, this allows more than one data store in a single directory. Also, useful for
     * identifying what files are used by what part of the code.
     */
    private final String storeName;

    /**
     * Construct a new MemoryIndexDiskKeyValueStore
     *
     * @param storeDir The directory to store data files in
     * @param storeName The name for the data store, this allows more than one data store in a single directory.
     * @param dataValueSizeBytes the size in bytes for data values being stored. It can be set to
     *                           DataFileCommon.VARIABLE_DATA_SIZE if you want to store variable size data values.
     * @param loadedDataCallback call back for handing loaded data from existing files on startup. Can be null if not needed.
     * @throws IOException
     */
    public MemoryIndexDiskKeyValueStore(Path storeDir, String storeName, int dataValueSizeBytes,
                                        LoadedDataCallback loadedDataCallback) throws IOException {
        this.storeName = storeName;
        // create store dir
        Files.createDirectories(storeDir);
        // create file collection
        fileCollection = new DataFileCollection(storeDir,storeName,dataValueSizeBytes, (key, dataLocation, dataValue) -> {
            index.put(key,dataLocation);
            if (loadedDataCallback != null) loadedDataCallback.newIndexEntry(key,dataLocation,dataValue);
        });
    }

    /**
     * Merge all read only files
     *
     * @param maxSizeMb all files returned are smaller than this number of MB
     * @throws IOException if there was a problem merging
     */
    public void mergeAll(int maxSizeMb) throws IOException {
        final List<DataFileReader> filesToMerge = fileCollection.getAllFullyWrittenFiles(maxSizeMb);
        final int size = filesToMerge == null ? 0 : filesToMerge.size();
        if (size > 1) {
            System.out.println("Merging " + size+" files in collection "+storeName);
            fileCollection.mergeFile(
                    // update index with all moved data
                    moves -> moves.forEachKeyValue((key, move) -> index.putIfEqual(key, move[0], move[1])),
                    filesToMerge);
        }
    }

    public void close() throws IOException {
        fileCollection.close();
    }

    public void startWriting() throws IOException {
        fileCollection.startWriting();
    }

    public void endWriting(long minimumValidKey, long maximumValidKey) throws IOException {
        fileCollection.endWriting(minimumValidKey, maximumValidKey);
    }

    public void put(long key, ByteBuffer data) throws IOException {
        long dataLocation = fileCollection.storeData(key,data);
        // store data location in index
        index.put(key,dataLocation);
    }

    public boolean get(long key, ByteBuffer toReadDataInto) throws IOException {
        long dataLocation = index.get(key, 0);
        // check if found
        if (dataLocation == 0) return false;
        // read data
        return fileCollection.readData(dataLocation,toReadDataInto, DataFileReader.DataToRead.VALUE);
    }
}
