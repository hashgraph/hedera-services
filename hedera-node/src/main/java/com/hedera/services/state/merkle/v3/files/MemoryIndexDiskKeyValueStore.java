package com.hedera.services.state.merkle.v3.files;

import com.hedera.services.state.merkle.v3.offheap.OffHeapLongList;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A map like data store with long keys. The index is in RAM and the data is stored in a set o files on disk.
 */
public class MemoryIndexDiskKeyValueStore {
    private final OffHeapLongList index = new OffHeapLongList();
    private final DataFileCollection fileCollection;

    public MemoryIndexDiskKeyValueStore(Path storeDir, String storeName, int blockSize) throws IOException {
        // create store dir
        Files.createDirectories(storeDir);
        // create file collection
        fileCollection = new DataFileCollection(storeDir,storeName,blockSize);
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

    public boolean get(long key, ByteBuffer toReadDataInto, DataFile.DataToRead dataToRead) throws IOException {
        long dataLocation = index.get(key, 0);
        // check if found
        if (dataLocation == 0) return false;
        // read data
        return fileCollection.readData(dataLocation,toReadDataInto,dataToRead);
    }
}
