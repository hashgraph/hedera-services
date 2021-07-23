package com.hedera.services.state.merkle.v3.files;

import com.hedera.services.state.merkle.v3.offheap.OffHeapLongList;
import com.swirlds.common.crypto.Hash;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MemoryIndexDiskKeyValueStore {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss.SSS") ;
    private final Path storeDir;
    private final String storeName;
    private final int blockSize;
    private final int hashSize;
    private final OffHeapLongList index = new OffHeapLongList();
    private final List<DataFile> files = new CopyOnWriteArrayList<>();
    private DataFile currentDataFileForWriting = null;
    private long currentDataFileForWritingKey;

    public MemoryIndexDiskKeyValueStore(Path storeDir, String storeName, int blockSize, int hashSize) {
        this.storeDir = storeDir;
        this.storeName = storeName;
        this.blockSize = blockSize;
        this.hashSize = hashSize;
    }

    public void startWriting() throws IOException {
        if (currentDataFileForWriting != null) throw new IOException("Tried to start writing when we were already writing.");
        currentDataFileForWriting = new DataFile(storeDir.resolve(storeName+"_"+DATE_FORMAT.format(new Date())+".data"),blockSize,Long.BYTES, hashSize);
        currentDataFileForWritingKey = (files.size()+1L) << 32; // add one to file index so that a dataLocation=0 is special non-valid
    }

    public void endWriting() throws IOException {
        if (currentDataFileForWriting == null) throw new IOException("Tried to end writing when we never started writing.");
        currentDataFileForWriting.finishWriting();
        files.add(currentDataFileForWriting);
        currentDataFileForWriting = null;
        currentDataFileForWritingKey = 0;
    }

    public void put(long key, Hash hash, ByteBuffer data) throws IOException {
        if (currentDataFileForWriting == null) throw new IOException("Tried to put data when we never started writing.");
        // store key,hash and data in current file and get the offset where it was stored
        int storageOffset = currentDataFileForWriting.storeData(key, hash, data);
        // calculate he data location key for current file and the offset
        long dataLocation = currentDataFileForWritingKey | storageOffset;
        // store data location in index
        index.put(key,dataLocation);
    }

    public boolean get(long key, ByteBuffer toReadDataInto, DataFile.DataToRead dataToRead) throws IOException {
        long dataLocation = index.get(key);
        // check if found
        if (dataLocation == 0) return false;
        // split up location
        int fileIndex = (int)(dataLocation >> 32) -1;
        int blockOffset = (int)(dataLocation & 0x00000000ffffffffL);
        // check we have file at index
        if (fileIndex < 0 | fileIndex > (files.size()-1))
            throw new IOException("Got a data location from index for a file that doesn't exist. dataLocation="+
                    Long.toHexString(dataLocation)+" fileIndex="+fileIndex+" blockOffset="+blockOffset+" numOfFiles="+files.size());
        //
        files.get(fileIndex).readData(toReadDataInto,blockOffset,dataToRead);
        return true;
    }
}
