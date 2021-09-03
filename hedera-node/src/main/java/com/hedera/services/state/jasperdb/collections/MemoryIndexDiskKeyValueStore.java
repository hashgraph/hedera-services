package com.hedera.services.state.jasperdb.collections;

import com.hedera.services.state.jasperdb.files.DataFileCollection;
import com.hedera.services.state.jasperdb.files.DataFileCollection.LoadedDataCallback;
import com.hedera.services.state.jasperdb.files.DataFileReader;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import static com.hedera.services.state.jasperdb.files.DataFileCommon.*;

/**
 * A specialized map like data store with long keys. The index is in RAM and the data is stored in a set o files on
 * disk. It assumes that the keys long start at 0. If they do not then a lot of RAM will be wasted.
 *
 * There is an assumption that keys are a contiguous range of incrementing numbers. This allows easy deletion during
 * merging by accepting any key/value with a key outside this range is not needed any more. This design comes from being
 * used where keys are leaf paths in a binary tree.
 */
public class MemoryIndexDiskKeyValueStore implements AutoCloseable {
    /**
     * This is useful for debugging and validating but is too expensive to enable in production.
     */
    private static final boolean ENABLE_DEEP_VALIDATION = false;
    /**
     * Off-heap index mapping, it uses our key as the index within the list and the value is the dataLocation in
     * fileCollection where the key/value pair is stored.
     */
    private final LongList index = new LongListHeap();
    /** On disk set of DataFiles that contain our key/value pairs */
    private final DataFileCollection fileCollection;
    /**
     * The name for the data store, this allows more than one data store in a single directory. Also, useful for
     * identifying what files are used by what part of the code.
     */
    private final String storeName;
    /** A temporary key used for start/end streaming put */
    private long tempKey;

    /**
     * Construct a new MemoryIndexDiskKeyValueStore
     *
     * @param storeDir The directory to store data files in
     * @param storeName The name for the data store, this allows more than one data store in a single directory.
     * @param dataValueSizeBytes the size in bytes for data values being stored. It can be set to
     *                           DataFileCommon.VARIABLE_DATA_SIZE if you want to store variable size data values.
     * @param loadedDataCallback call back for handing loaded data from existing files on startup. Can be null if not needed.
     * @throws IOException If there was a problem opening data files
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
     * @param filterForFilesToMerge filter to choose which subset of files to merge
     * @throws IOException if there was a problem merging
     */
    public void mergeAll(Function<List<DataFileReader>,List<DataFileReader>> filterForFilesToMerge) throws IOException {
        final long START = System.currentTimeMillis();
        final List<DataFileReader> allFilesBefore = fileCollection.getAllFullyWrittenFiles();
        final List<DataFileReader> filesToMerge = filterForFilesToMerge.apply(allFilesBefore);
        final int size = filesToMerge == null ? 0 : filesToMerge.size();
        if (size < 2) {
            System.out.println("Mo meed to merge as only "+size+" files.");
            return;
        }
        double filesToMergeSizeMb = filesToMerge.stream().mapToDouble(file -> {
            try {
                return file.getSize();
            } catch (IOException e) {
                e.printStackTrace();
                return 0;
            }
        }).sum() / MB;
        System.out.printf("Starting merging %,d files in collection %s total %,.2f Gb...\n",size,storeName,filesToMergeSizeMb/1024);
        if (ENABLE_DEEP_VALIDATION) startChecking();
        final List<Path> newFilesCreated = fileCollection.mergeFiles(
                // update index with all moved data
                moves -> moves.forEach((key, oldValue, newValue) -> {
                   boolean casSuccessful = index.putIfEqual(key, oldValue, newValue);
                   if (ENABLE_DEEP_VALIDATION) checkItem(casSuccessful, key, oldValue, newValue);
                }),
                filesToMerge);
        if (ENABLE_DEEP_VALIDATION) endChecking(filesToMerge);


        final double tookSeconds = (double) (System.currentTimeMillis() - START) / 1000d;

        double mergedFilesCreatedSizeMb = newFilesCreated.stream().mapToDouble(path -> {
            try {
                return Files.size(path);
            } catch (IOException e) {
                e.printStackTrace();
                return 0;
            }
        }).sum() / MB;

        System.out.printf("Merged %,.2f Gb files into %,.2f Gb files in %,.2f seconds. Read at %,.2f Mb/sec Written at %,.2f\n        filesToMerge = %s\n       allFilesBefore = %s\n       allFilesAfter = %s\n",
                filesToMergeSizeMb / 1024d,
                mergedFilesCreatedSizeMb / 1024d,
                tookSeconds,
                filesToMergeSizeMb / tookSeconds,
                mergedFilesCreatedSizeMb / tookSeconds,
                Arrays.toString(filesToMerge.stream().map(reader -> reader.getMetadata().getIndex()).toArray()),
                Arrays.toString(allFilesBefore.stream().map(reader -> reader.getMetadata().getIndex()).toArray()),
                Arrays.toString(fileCollection.getAllFullyWrittenFiles().stream().map(reader -> reader.getMetadata().getIndex()).toArray())
        );
    }

    /**
     * Start a writing session ready for calls to put()
     *
     * @throws IOException If there was a problem opening a writing session
     */
    public void startWriting() throws IOException {
        fileCollection.startWriting();
    }

    /**
     * Put a value into this store, you must be in a writing session started with startWriting()
     *
     * @param key The key to store value for
     * @param data Buffer containing the data's value, it should have its position and limit set correctly
     * @throws IOException If there was a problem write key/value to the store
     */
    public void put(long key, ByteBuffer data) throws IOException {
        long dataLocation = fileCollection.storeData(key,data);
        // store data location in index
        index.put(key,dataLocation);
    }

    /**
     * Start streaming put of an item. You will need to call endStreamingPut() after each item you write to the stream.
     *
     * @param key The key to store value for
     * @param dataItemSize The size of the data item you are going to write, this is total number of bytes. Only needed
     *                     when in hasVariableDataSize mode.
     * @return direct access to stream to file
     */
    public synchronized SerializableDataOutputStream startStreamingPut(long key, int dataItemSize) throws IOException {
        this.tempKey = key;
        return fileCollection.startStreamingItem(key,dataItemSize);
    }

    /**
     * End streaming put of an item.
     */
    public synchronized void endStreamingPut() throws IOException {
        long dataLocation = fileCollection.endStreamingItem();
        // store data location in index
        index.put(tempKey,dataLocation);
    }

    /**
     * End s a session of writing
     *
     * @param minimumValidKey The minimum valid key at this point in time.
     * @param maximumValidKey The maximum valid key at this point in time.
     * @throws IOException If there was a problem closing the writing session
     */
    public void endWriting(long minimumValidKey, long maximumValidKey) throws IOException {
        fileCollection.endWriting(minimumValidKey, maximumValidKey);
    }

    /**
     * Get a value by reading it from disk and storing it into toReadDataInto
     *
     * @param key The key to find and read value for
     * @param toReadDataInto The buffer to fill with value
     * @return true if the value was read or false if not found
     * @throws IOException If there was a problem reading the value from file
     */
    public boolean get(long key, ByteBuffer toReadDataInto) throws IOException {
        // check if out of range
        if (key < fileCollection.getMinimumValidKey() || key > fileCollection.getMaximumValidKey()) {
            if (ENABLE_DEEP_VALIDATION && key != 0) {
                System.err.println("get path ["+key+"] that is not in index any more."+
                        ((key < fileCollection.getMinimumValidKey()) ? "\n      Key is less than min "+fileCollection.getMinimumValidKey()+". " : "") +
                        ((key > fileCollection.getMaximumValidKey()) ? "\n      Key is greater than max "+fileCollection.getMaximumValidKey()+". " : "")
                );
            }
            return false;
        }
        // get from index
        long dataLocation = index.get(key, 0);
        // check if found
        if (dataLocation == 0) {
            if (ENABLE_DEEP_VALIDATION && key != 0) {
                System.err.println("get path ["+key+"] that is not in index any more."+
                        ((key < fileCollection.getMinimumValidKey()) ? "\n      Key is less than min "+fileCollection.getMinimumValidKey()+". " : "") +
                        ((key > fileCollection.getMaximumValidKey()) ? "\n      Key is greater than max "+fileCollection.getMaximumValidKey()+". " : "")
                );
                new Exception().printStackTrace();
            }
            return false;
        }
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

    /**
     * Get a value by reading it from disk into a byte buffer and returning it. This is needed for variable size data
     * because we do not know the length to preallocate the buffer.
     *
     * @param key The key to find and read value for
     * @return ByteBuffer buffer containing data if the value was read or null if not found
     * @throws IOException If there was a problem reading the value from file
     */
    public ByteBuffer get(long key) throws IOException {
        // check if out of range
        if (key < fileCollection.getMinimumValidKey() || key > fileCollection.getMaximumValidKey()) {
            if (ENABLE_DEEP_VALIDATION && key != 0) {
                System.err.println("get path ["+key+"] that is not in index any more."+
                        ((key < fileCollection.getMinimumValidKey()) ? "\n      Key is less than min "+fileCollection.getMinimumValidKey()+". " : "") +
                        ((key > fileCollection.getMaximumValidKey()) ? "\n      Key is greater than max "+fileCollection.getMaximumValidKey()+". " : "")
                );
            }
            return null;
        }
        // get from index
        long dataLocation = index.get(key, 0);
        // check if found
        if (dataLocation == 0) {
            if (ENABLE_DEEP_VALIDATION && key != 0) {
                System.err.println("get path ["+key+"] that is not in index any more."+
                        ((key < fileCollection.getMinimumValidKey()) ? "\n      Key is less than min "+fileCollection.getMinimumValidKey()+". " : "") +
                        ((key > fileCollection.getMaximumValidKey()) ? "\n      Key is greater than max "+fileCollection.getMaximumValidKey()+". " : "")
                );
                new Exception().printStackTrace();
            }
            return null;
        }
        // read data
        try {
            return fileCollection.readData(dataLocation, DataFileReader.DataToRead.VALUE);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("MemoryIndexDiskKeyValueStore.get key="+key);
            printDataLinkValidation(index, fileCollection.getAllFullyWrittenFiles(Integer.MAX_VALUE));
            throw e;
        }
    }

    /**
     * Close all files being used
     *
     * @throws IOException If there was a problem closing files
     */
    public void close() throws IOException {
        fileCollection.close();
    }

    // =================================================================================================================
    // Debugging Tools, these can be enabled with the ENABLE_DEEP_VALIDATION flag above

    /** Debugging store of misses that failed compare and swap */
    private LongObjectHashMap<CasMiss> casMisses;
    /** Debugging store of how many keys were checked */
    private LongLongHashMap keyCount;

    /** Start collecting data for debugging integrity checking */
    private void startChecking() {
        casMisses = new LongObjectHashMap<>();
        keyCount = new LongLongHashMap();
    }

    /** Check a item for debugging integrity checking */
    private void checkItem(boolean casSuccessful, long key, long oldValue, long newValue) {
        if (!casSuccessful) {
            CasMiss miss = new CasMiss(
                    oldValue,
                    newValue,
                    index.get(key,0)
            );
            casMisses.put(key,miss);
        }
        keyCount.addToValue(key,1);
    }

    /** End debugging integrity checking and print results */
    private void endChecking(List<DataFileReader> filesToMerge) {
        // set of merged files
        SortedSet<Integer> mergedFileIds = new TreeSet<>();
        for(var file:filesToMerge) mergedFileIds.add(file.getMetadata().getIndex());

        for (long key = 0; key < index.size(); key++) {
            long value = index.get(key,-1);
            if (mergedFileIds.contains(fileIndexFromDataLocation(value))) { // only entries for deleted files
                CasMiss miss = casMisses.get(key);
                if (miss != null) {
                    System.out.println("MISS "+
                            "key = " + key+
                            "value = " + dataLocationToString(value)+
                            "from = " + dataLocationToString(miss.fileMovingFrom)+
                            ", to = "+dataLocationToString(miss.fileMovingTo)+
                            ", current = "+dataLocationToString(miss.currentFile));
                } else {
                    System.out.println("MISSING NOT MISS "+
                            "key = " + key+
                            "value = " + dataLocationToString(value));
                }
            }
        }
        keyCount.forEachKeyValue((key, count) -> {
            if(count > 1) System.out.println("OH DEAR! Key ["+key+"] has count of ["+count+"]");
        });
        printDataLinkValidation(index,fileCollection.getAllFullyWrittenFiles());
    }

    /**
     * POJO for storing a miss when compare and swap fails
     */
    private static class CasMiss {
        public long fileMovingFrom;
        public long fileMovingTo;
        public long currentFile;

        public CasMiss(long fileMovingFrom, long fileMovingTo, long currentFile) {
            this.fileMovingFrom = fileMovingFrom;
            this.fileMovingTo = fileMovingTo;
            this.currentFile = currentFile;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CasMiss casMiss = (CasMiss) o;
            return fileMovingFrom == casMiss.fileMovingFrom && fileMovingTo == casMiss.fileMovingTo && currentFile == casMiss.currentFile;
        }

        @Override
        public int hashCode() {
            return Objects.hash(fileMovingFrom, fileMovingTo, currentFile);
        }
    }
}
