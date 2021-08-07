package com.hedera.services.state.merkle.v3.files;

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
    private final String storeName;

    public MemoryIndexDiskKeyValueStore(Path storeDir, String storeName, int dataValueSizeBytes) throws IOException {
        this.storeName = storeName;
        // create store dir
        Files.createDirectories(storeDir);
        // create file collection
        fileCollection = new DataFileCollection(storeDir,storeName,dataValueSizeBytes, new DataFileReaderFactory() {
            public DataFileReader newDataFileReader(Path path) throws IOException {
                return new DataFileReaderThreadLocal(path);
            }

            public DataFileReader newDataFileReader(Path path, DataFileMetadata metadata) throws IOException {
                return new DataFileReaderThreadLocal(path, metadata);
            }
        });
    }

    /**
     * Merge all read only files
     *
     * @throws IOException if there was a problem mergeing
     */
    public void mergeAll() throws IOException {
        List<DataFileReader> filesToMerge = fileCollection.getAllFullyWrittenFiles();
        System.out.println("Starting to merge " + filesToMerge.size()+" files in collection "+storeName);
        fileCollection.mergeOldFiles(moves -> {
            // update index with all moved data
            moves.forEachKeyValue((key, move) -> {
                index.put(key, move[1]);
            });
        }, filesToMerge);
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
