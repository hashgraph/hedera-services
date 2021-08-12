package com.hedera.services.state.jasperdb.collections;

import com.hedera.services.state.jasperdb.files.DataFileCollection;
import com.hedera.services.state.jasperdb.files.DataFileCollection.LoadedDataCallback;
import com.hedera.services.state.jasperdb.files.DataFileCommon;
import com.hedera.services.state.jasperdb.files.DataFileReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.hedera.services.state.jasperdb.files.DataFileCommon.printDataLinkValidation;

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
        final List<DataFileReader> allFilesBefore = fileCollection.getAllFullyWrittenFiles(Integer.MAX_VALUE);
        final List<DataFileReader> filesToMerge = fileCollection.getAllFullyWrittenFiles(maxSizeMb);
        final int size = filesToMerge == null ? 0 : filesToMerge.size();
        if (size > 1) {
            System.out.println("Merging " + size+" files in collection "+storeName);
            final int maxMergedFileIndex = filesToMerge.stream().filter(Objects::nonNull).mapToInt(file -> file.getMetadata().getIndex()).summaryStatistics().getMax();
            fileCollection.mergeFile(
                    // update index with all moved data
                    moves -> moves.forEachKeyValue((key, move) -> {
                        // heck it's a hack
                        int newValueFileIndex = DataFileCommon.fileIndexFromDataLocation(move[1]);
                        long currentValue = index.get(key,-1);
                        int currentFileIndex = DataFileCommon.fileIndexFromDataLocation(currentValue);
                        if ((currentValue != move[0]) && (newValueFileIndex < maxMergedFileIndex)) {
                            index.put(key,move[1]);
                            System.out.println("HIT HACK! for key = " + key+" new value data file "+newValueFileIndex+
                                    " is older than maxMergedFileIndex "+maxMergedFileIndex+"\n"+"" +
                                    "       currentValue="+currentValue+" move="+Arrays.toString(move));
                            return;
                        }

//                        // heck it's a hack
//                        int newValueFileIndex = DataFileCommon.fileIndexFromDataLocation(move[1]);
//                        long currentValue = index.get(key,-1);
//                        int currentFileIndex = DataFileCommon.fileIndexFromDataLocation(currentValue);
//                        if ((currentValue != move[0]) && (newValueFileIndex > currentFileIndex)) {
//                            index.put(key,move[1]);
//                            System.out.println("HIT HACK! for key = " + key+" new value data file "+newValueFileIndex+
//                                    " is newer than current data file "+currentFileIndex+"\n"+"" +
//                                    "       currentValue="+currentValue+" move="+Arrays.toString(move));
//                            return;
//                        }
                        // correct
                       index.putIfEqual(key, move[0], move[1]);
//                       if (!index.putIfEqual(key, move[0], move[1])) {
//                           final List<DataFileReader> currentFiles = fileCollection.getAllFullyWrittenFiles(Integer.MAX_VALUE);
//                           System.out.println("PUT FAILED currentValue="+dataLocationToString(currentValue)+
//                                   " expected="+dataLocationToString(move[0])+
//                                   " maxMergedFileIndex="+maxMergedFileIndex+
//                                   " current files = "+ Arrays.toString(currentFiles.stream().map(reader -> reader.getMetadata().getIndex()).toArray()));
//                       }
                    }),
                    filesToMerge);
            final List<DataFileReader> allFilesAfter = fileCollection.getAllFullyWrittenFiles(Integer.MAX_VALUE);
            System.out.println("AFTER MERGE ================================\n"+
                    "       filesToMerge = " + Arrays.toString(filesToMerge.stream().map(reader -> reader.getMetadata().getIndex()).toArray())+"\n"+
                    "       allFilesBefore = " + Arrays.toString(allFilesBefore.stream().map(reader -> reader.getMetadata().getIndex()).toArray())+"\n"+
                    "       allFilesAfter = " + Arrays.toString(allFilesAfter.stream().map(reader -> reader.getMetadata().getIndex()).toArray())+"\n"
            );
            printDataLinkValidation(index,allFilesAfter);
        }
    }

    public void startWriting() throws IOException {
        fileCollection.startWriting();
    }

    public void put(long key, ByteBuffer data) throws IOException {
        long dataLocation = fileCollection.storeData(key,data);
        // store data location in index
        index.put(key,dataLocation);
    }

    public void endWriting(long minimumValidKey, long maximumValidKey) throws IOException {
        fileCollection.endWriting(minimumValidKey, maximumValidKey);
    }

    public boolean get(long key, ByteBuffer toReadDataInto) throws IOException {
        long dataLocation = index.get(key, 0);
        // check if found
        if (dataLocation == 0) return false;
        // read data
        try {
            return fileCollection.readData(dataLocation,toReadDataInto, DataFileReader.DataToRead.VALUE);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("MemoryIndexDiskKeyValueStore.get key="+key);
            printDataLinkValidation(index, fileCollection.getAllFullyWrittenFiles(Integer.MAX_VALUE));
            throw e;
        }
    }

    public void close() throws IOException {
        fileCollection.close();
    }
}
