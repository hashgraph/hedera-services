package com.hedera.services.state.merkle.v3.files;

import com.hedera.services.state.merkle.v3.offheap.OffHeapLongList;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * DataFileCollection manages a set of data files and the compaction of them over time.
 */
public class DataFileCollection {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss.SSS") ;
    private final Path storeDir;
    private final String storeName;
    private final int blockSize;
    private final List<DataFile> files = new CopyOnWriteArrayList<>();
    private DataFile currentDataFileForWriting = null;
    private long currentDataFileForWritingKey;

    public DataFileCollection(Path storeDir, String storeName, int blockSize) throws IOException {
        this.storeDir = storeDir;
        this.storeName = storeName;
        this.blockSize = blockSize;
        // create store dir
        Files.createDirectories(storeDir);
    }

    public void close() throws IOException {
        for(DataFile file:files) file.close();
    }

    public boolean isOpenForWriting() {
        return currentDataFileForWriting != null;
    }

    public void startWriting() throws IOException {
        if (currentDataFileForWriting != null) throw new IOException("Tried to start writing when we were already writing.");
        currentDataFileForWriting = new DataFile(storeDir.resolve(storeName+"_"+DATE_FORMAT.format(new Date())+".data"),blockSize,Long.BYTES);
        currentDataFileForWritingKey = (files.size()+1L) << 32; // add one to file index so that a dataLocation=0 is special non-valid
    }

    public void endWriting() throws IOException {
        if (currentDataFileForWriting == null) throw new IOException("Tried to end writing when we never started writing.");
        currentDataFileForWriting.finishWriting();
        files.add(currentDataFileForWriting);
        currentDataFileForWriting = null;
        currentDataFileForWritingKey = 0;
    }

    public long storeData(long key, ByteBuffer data) throws IOException {
        if (currentDataFileForWriting == null) throw new IOException("Tried to put data when we never started writing.");
        // store key,hash and data in current file and get the offset where it was stored
        int storageOffset = currentDataFileForWriting.storeData(key, data);
        // calculate he data location key for current file and the offset
        return currentDataFileForWritingKey | storageOffset;
    }

    public boolean readData(long dataLocation, ByteBuffer toReadDataInto, DataFile.DataToRead dataToRead) throws IOException {
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
