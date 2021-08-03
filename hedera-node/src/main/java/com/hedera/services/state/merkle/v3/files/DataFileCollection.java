package com.hedera.services.state.merkle.v3.files;

import com.hedera.services.state.merkle.v3.files.DataFile.DataFileBlockIterator;
import org.eclipse.collections.api.map.primitive.ImmutableLongObjectMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * DataFileCollection manages a set of data files and the compaction of them over time.
 */
public class DataFileCollection {
    private static final long GB = 1024*1024*1024;
    private static final long MAX_MERGED_FILE_SIZE = 100*GB;
    private final Path storeDir;
    private final String storeName;
    private final int blockSize;
    private volatile IndexedFileList indexedFileList;
    private DataFile currentDataFileForWriting = null;

    public DataFileCollection(Path storeDir, String storeName, int blockSize) throws IOException {
        this.storeDir = storeDir;
        this.storeName = storeName;
        this.blockSize = blockSize;
        // check if exists, if so open existing files
        if (Files.exists(storeDir)) {
            if (!Files.isDirectory(storeDir)) throw new IOException("Tried to DataFileCollection with a storage directory that is not a directory. ["+storeDir.toAbsolutePath()+"]");
            final DataFile[] dataFiles = Files.list(storeDir)
                        .filter(path -> DataFile.isDataFile(storeName,path))
                        .map(DataFile::new)
                        .sorted()
                        .toArray(DataFile[]::new);
            if (dataFiles.length > 0) {
                indexedFileList = new IndexedFileList(dataFiles[0].getIndex(), dataFiles);
            }
        } else {
            // create store dir
            Files.createDirectories(storeDir);
        }
    }

    /**
     * Merges all the old data files
     *
     * @param locationChangeHandler takes a map of moves from old location to new location. Once it is finished and
     *                              returns it is assumed all readers will no longer be looking in old location, so old
     *                              files can be safely deleted.
     */
    public void mergeOldFiles(Consumer<ImmutableLongObjectMap<long[]>> locationChangeHandler) throws IOException {
        // gather all readOnly files up to the first non-read-only file.
        List<DataFile> allReadOnlyFiles = new ArrayList<>(indexedFileList.files.length);
        for (int i = 0; i < indexedFileList.files.length; i++) {
            DataFile file = indexedFileList.files[i];
            if (file.isReadOnly()) {
                allReadOnlyFiles.add(file);
            } else {
                break;
            }
        }
        // create new map for keeping track of moves
        LongObjectHashMap<long[]> movesMap = new LongObjectHashMap<>();
        // Create a list for new files and open first new file for writing
        DataFile newFile = newDataFile(true);
        // get the most recent min and max key
        long minimumValidKey = indexedFileList.files[indexedFileList.files.length-1].getMinimumValidKey();
        long maximumValidKey = indexedFileList.files[indexedFileList.files.length-1].getMaximumValidKey();
        // open iterators, first iterator will be on oldest file
        List<DataFileBlockIterator> blockIterators = allReadOnlyFiles.stream()
                .map(DataFile::createIterator)
                .collect(Collectors.toList());
        // move all iterators to first block
        ListIterator<DataFileBlockIterator> blockIteratorsIterator = blockIterators.listIterator();
        while (blockIteratorsIterator.hasNext()) {
            DataFileBlockIterator dataFileBlockIterator =  blockIteratorsIterator.next();
            try {
                if (!dataFileBlockIterator.next()) {
                    // we have finished reading this file so don't need it iterate it next time
                    dataFileBlockIterator.close();
                    blockIteratorsIterator.remove();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // while we still have data left to read
        while(!blockIterators.isEmpty()) {
            // find the lowest key any iterator has and the newest iterator that has that key
            long lowestKey = Long.MAX_VALUE;
            DataFileBlockIterator newestIteratorWithLowestKey = null;
            for (DataFileBlockIterator blockIterator : blockIterators) {
                long key = blockIterator.getBlocksKey();
                if (key < lowestKey) {
                    lowestKey = key;
                    newestIteratorWithLowestKey = blockIterator;
                }
            }
            assert newestIteratorWithLowestKey != null;
            // write that key from newest iterator to new merge file
            long newDataLocation = newFile.storeData(newestIteratorWithLowestKey.getBlockData());
            // check if newFile is full
            if (newFile.getSize() >= MAX_MERGED_FILE_SIZE) {
                newFile.finishWriting(minimumValidKey, maximumValidKey);
                newFile = newDataFile(true);
            }
            // add to movesMap
            movesMap.put(lowestKey, new long[]{newestIteratorWithLowestKey.getBlocksDataLocation(), newDataLocation});
            // move all iterators on that contained lowestKey
            blockIteratorsIterator = blockIterators.listIterator();
            while (blockIteratorsIterator.hasNext()) {
                DataFileBlockIterator dataFileBlockIterator =  blockIteratorsIterator.next();
                if (dataFileBlockIterator.getBlocksKey() == lowestKey) {
                    try {
                        if (!dataFileBlockIterator.next()) {
                            // we have finished reading this file so don't need it iterate it next time
                            dataFileBlockIterator.close();
                            blockIteratorsIterator.remove();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        // close current file
        newFile.finishWriting(minimumValidKey, maximumValidKey);
        // call locationChangeHandler
        locationChangeHandler.accept(movesMap.toImmutable());
        // delete old files
        for(DataFile file: allReadOnlyFiles) {
            file.closeAndDelete();
        }
    }

    /**
     * Close all the data files
     */
    public void close() {
        for(DataFile file:this.indexedFileList.files) file.close();
    }

    /**
     * Start writing a new data file
     *
     * @throws IOException If there was a problem opening a new data file
     */
    public void startWriting() throws IOException {
        if (currentDataFileForWriting != null) throw new IOException("Tried to start writing when we were already writing.");
        currentDataFileForWriting = newDataFile(false);
    }

    /**
     * End writing current data file
     *
     * @param minimumValidKey The minimum valid data key at this point in time, can be used for cleaning out old data
     * @param maximumValidKey The maximum valid data key at this point in time, can be used for cleaning out old data
     * @throws IOException If there was a problem closing the data file
     */
    public void endWriting(long minimumValidKey, long maximumValidKey) throws IOException {
        if (currentDataFileForWriting == null) throw new IOException("Tried to end writing when we never started writing.");
        currentDataFileForWriting.finishWriting(minimumValidKey, maximumValidKey);
        currentDataFileForWriting = null;
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
    public boolean readData(long dataLocation, ByteBuffer toReadDataInto, DataFile.DataToRead dataToRead) throws IOException {
        // check if found
        if (dataLocation == 0) return false;
        // split up location
        int fileIndex = (int)(dataLocation >> 32) -1;
        // check if file for fileIndex exists
        DataFile file = null;
        if (fileIndex < 0 | indexedFileList == null || (file  = indexedFileList.getFile(fileIndex)) == null)
            throw new IOException("Got a data location from index for a file that doesn't exist. dataLocation="+
                    Long.toHexString(dataLocation)+" fileIndex="+fileIndex+" file="+file);
        // read data
        file.readData(toReadDataInto,dataLocation,dataToRead);
        return true;
    }

    // =================================================================================================================
    // Private API

    private synchronized DataFile newDataFile(boolean isMergeFile) {
        final int fileIndex = (indexedFileList == null) ? 0 :  indexedFileList.nextFileIndex();
        DataFile newFile =  new DataFile(storeName,storeDir, fileIndex, blockSize, isMergeFile);
        this.indexedFileList = new IndexedFileList(this.indexedFileList,newFile);
        return newFile;
    }

    private static class IndexedFileList {
        private final int firstFileIndex;
        private final DataFile[] files;

        public IndexedFileList(int firstFileIndex, DataFile[] files) {
            this.firstFileIndex = firstFileIndex;
            this.files = files;
        }

        public IndexedFileList(IndexedFileList oldIndexedFileList, DataFile newDataFile) {
            if (oldIndexedFileList == null) {
                this.firstFileIndex = 0;
                this.files = new DataFile[]{newDataFile};
            } else {
                this.firstFileIndex = oldIndexedFileList.firstFileIndex;
                this.files = new DataFile[oldIndexedFileList.files.length+1];
                System.arraycopy(oldIndexedFileList.files,0,this.files,0,oldIndexedFileList.files.length);
                this.files[oldIndexedFileList.files.length] = newDataFile;
            }
        }

        public DataFile getFile(int index) {
            if (index < firstFileIndex) return null;
            int localIndex = index-firstFileIndex;
            if (localIndex >= files.length) return null;
            return files[localIndex];
        }

        public int nextFileIndex() {
            return firstFileIndex + files.length;
        }
    }
}
