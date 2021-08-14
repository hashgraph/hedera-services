package com.hedera.services.state.jasperdb.collections;

import com.hedera.services.state.jasperdb.files.DataFileCollection;
import com.hedera.services.state.jasperdb.files.DataFileCollection.LoadedDataCallback;
import com.hedera.services.state.jasperdb.files.DataFileReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.hedera.services.state.jasperdb.files.DataFileCommon.MB;
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

    private final AtomicReference<long[]> lastMinAndMaxValidKey = new AtomicReference<>(null);

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
        double totalSize = filesToMerge.stream().mapToDouble(file -> {
            try {
                return file.getSize();
            } catch (IOException e) {
                e.printStackTrace();
                return 0;
            }
        }).sum() / (MB * 1024D);
        System.out.printf("Starting merging %,d files in collection %s total %,.2f Gb...\n",size,storeName,totalSize);
        fileCollection.mergeFiles(
                // update index with all moved data
                moves -> moves.forEachKeyValue((key, move) -> {
                   index.putIfEqual(key, move[0], move[1]);
                }),
                filesToMerge);
        System.out.printf("Merged %,.2f Gb files in %,.2f seconds\n        filesToMerge = %s\n       allFilesBefore = %s\n       allFilesAfter = %s\n",
                totalSize,
                (double) (System.currentTimeMillis() - START) / 1000d,
                Arrays.toString(filesToMerge.stream().map(reader -> reader.getMetadata().getIndex()).toArray()),
                Arrays.toString(allFilesBefore.stream().map(reader -> reader.getMetadata().getIndex()).toArray()),
                Arrays.toString(fileCollection.getAllFullyWrittenFiles().stream().map(reader -> reader.getMetadata().getIndex()).toArray())
        );
        //printDataLinkValidation(index,allFilesAfter);
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
        // we can now clear any indexes out of range
//        lastMinAndMaxValidKey.getAndUpdate(currentMinMax -> {
//            if (currentMinMax != null) {
//                final long lastMin = currentMinMax[0];
//                final long lastMax = currentMinMax[1];
//                if (lastMin < minimumValidKey) {
//                    // start has grown so clear
//                    index.clear(lastMin,minimumValidKey-lastMin-1);
//                }
//                if (maximumValidKey < lastMax) {
//                    // end has shrunk so clear
//                    index.clear(maximumValidKey+1,lastMax-maximumValidKey);
//                }
//            }
//            return new long[]{minimumValidKey,maximumValidKey};
//        });
    }

    public boolean get(long key, ByteBuffer toReadDataInto) throws IOException {
        // check if out of range
        if (key < fileCollection.getMinimumValidKey() || key > fileCollection.getMaximumValidKey()) {
            if (key != 0) {
                System.err.println("get path ["+key+"] that is not in index any more."+
                        ((key < fileCollection.getMinimumValidKey()) ? "\n      Key is less than min "+fileCollection.getMinimumValidKey()+". " : "") +
                        ((key > fileCollection.getMaximumValidKey()) ? "\n      Key is greater than max "+fileCollection.getMaximumValidKey()+". " : "")
                );
                new Exception().printStackTrace();
            }
            return false;
        }
        // get from index
        long dataLocation = index.get(key, 0);
        // check if found
        if (dataLocation == 0) {
            if (key != 0) {
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

    public void close() throws IOException {
        fileCollection.close();
    }
}
