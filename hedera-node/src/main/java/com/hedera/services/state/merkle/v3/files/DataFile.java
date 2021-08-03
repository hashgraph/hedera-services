package com.hedera.services.state.merkle.v3.files;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A Data File, containing a number of data items. Each data item is written to the file like this:
 *
 * <ul>
 *     <li>integer - value data length in bytes</li>
 *     <li>long    - key data</li>
 *     <li>bytes   - value data</li>
 *     <li>bytes   - padding till end of nearest blockSize</li>
 * </ul>
 *
 * At the end of the file in last 4096 byte block is a footer. Containing:
 *
 * <ul>
 *     <li>integer  - file format version</li>
 *     <li>integer  - block size</li>
 *     <li>long     - minimum valid key at point this file was written</li>
 *     <li>long     - maximum valid key at point this file was written</li>
 * </ul>
 *
 * The blockSize is specified in the constructor, it should be a divisor of the 4096 byte page size. Ideally
 *
 */
@SuppressWarnings({"DuplicatedCode", "jol"})
public class DataFile implements AutoCloseable, Comparable<DataFile> {
    static final int PAGE_SIZE = 4096;
    static final int FOOTER_SIZE = PAGE_SIZE;
    private static final String FILE_EXTENSION = ".jdb";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss-SSS");
    public enum DataToRead{KEY,VALUE,KEY_VALUE}
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int KEY_SIZE = Long.BYTES;
    private final Path path;
    private final int blockSize;
    private final boolean isMergeFile;
    private final int index;
    private final long indexShifted;
    private final Date date;
    private boolean isReadOnly;
    private long minimumValidKey;
    private long maximumValidKey;
    private long dataItemCount = 0;
    private SeekableByteChannel seekableByteChannel;
    private ByteBuffer tempWriteBuffer = null;

    /**
     * Create a new data file in the given directory, in append mode.
     *
     * @param filePrefix string prefix for all files, must not contain "_" chars
     * @param dataFileDir the path to directory to create the data file in
     * @param index the index number for this file
     * @param blockSize the size of blocks, used for storing a block offsets. 4096 must be divisible by blockSize
     * @param isMergeFile true if this is a merge file, false if it is a new data file that has not been merged
     */
    public DataFile(String filePrefix, Path dataFileDir, int index, int blockSize, boolean isMergeFile) {
//        if (PAGE_SIZE % blockSize != 0) throw new IllegalArgumentException("Page size "+PAGE_SIZE+" is not divisible by blockSize");
        if (filePrefix.contains("_")) throw new IllegalArgumentException("filePrefix can not contain underscore character.");
        this.index = index;
        this.indexShifted = (long)(index+1) << 32;
        this.date = new Date();
        this.blockSize = blockSize;
        this.isMergeFile = isMergeFile;
        this.isReadOnly = false;
        this.path = dataFileDir.resolve(filePrefix+"_"+index+"_"+DATE_FORMAT.format(new Date())+FILE_EXTENSION);
        if (Files.exists(path)) throw new IllegalArgumentException("There is a file in the way of creating new data file at ["+this.path.toAbsolutePath()+"].");
        try {
            seekableByteChannel = Files.newByteChannel(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Open an existing data file read only, if it doesn't exist it is opened in append mode.
     *
     * @param path the path to the data file
     */
    public DataFile(Path path) {
        if (!Files.exists(path)) throw new IllegalArgumentException("Tried to open a non existent data file ["+path.toAbsolutePath()+"].");
        this.path = path;
        this.isReadOnly = true;
        try {
            // parse
            String fileName = path.getFileName().toString();
            String[] parts = fileName.substring(0,fileName.length()-FILE_EXTENSION.length()).split("_");
            this.index = Integer.parseInt(parts[1]);
            this.indexShifted = (long)(this.index+1) << 32;
            this.date = DATE_FORMAT.parse(parts[2]);
            // open
            seekableByteChannel = Files.newByteChannel(path, StandardOpenOption.READ);
            // read footer
            seekableByteChannel.position(seekableByteChannel.size()-FOOTER_SIZE);
            ByteBuffer buf = ByteBuffer.allocate(FOOTER_SIZE);
            seekableByteChannel.read(buf);
            buf.rewind();
            // extract footer data
            //noinspection unused
            int readFormatVersion = buf.getInt();
            this.blockSize = buf.getInt();
            this.isMergeFile = buf.get() == 1;
            this.dataItemCount = buf.getLong();
            this.minimumValidKey = buf.getLong();
            this.maximumValidKey = buf.getLong();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check if a file at path, is a data file based on name
     *
     * @param filePrefix the prefix for the set of data files
     * @param path the path to the data file
     * @return true if the name starts with prefix and has right extension
     */
    public static boolean isDataFile(String filePrefix, Path path) {
        String fileName = path.getFileName().toString();
        return fileName.startsWith(filePrefix) && fileName.endsWith(FILE_EXTENSION);
    }

    /**
     * Compares this Data File to another based on index
     */
    @Override
    public int compareTo(@NotNull DataFile o) {
        return Integer.compare(this.index,o.index);
    }

    /**
     * Get if this file is a merge file
     *
     * @return true if this is a merge file, false if it is a new data file that has not been merged
     */
    public boolean isMergeFile() {
        return isMergeFile;
    }

    /**
     * Get if this file is read only, this is after writing finished.
     *
     * @return true if file is read only
     */
    public boolean isReadOnly() {
        return isReadOnly;
    }

    /**
     * Get this files index, out of a set of data files
     *
     * @return this files index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Get the date this file was created
     *
     * @return the date and time this file was created
     */
    public Date getDate() {
        return date;
    }

    /**
     * Get the number of data items this file contains
     *
     * @return the number of data items this file contains
     */
    public long getDataItemCount() {
        return dataItemCount;
    }

    /**
     * Get the minimum valid key value at the point this file was finished writing. This is useful during merge to
     * easily delete keys that are no longer valid.
     *
     * @return the minimum valid key value
     */
    public long getMinimumValidKey() {
        return minimumValidKey;
    }

    /**
     * Get the maximum valid key value at the point this file was finished writing. This is useful during merge to
     * easily delete keys that are no longer valid.
     *
     * @return the maximum valid key value
     */
    public long getMaximumValidKey() {
        return maximumValidKey;
    }

    /**
     * Get the size of this file in bytes
     *
     * @return file size in bytes
     * @throws IOException If there was a problem reading file size from filesystem
     */
    public synchronized long getSize() throws IOException {
        return seekableByteChannel.size();
    }

    /**
     * Get the path to this data file
     *
     * @return this files path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Create a iterator to iterate over the data items in this data file. It opens its own file handle so can be used
     * in a separate thread. It must therefore be closed when you are finished with it.
     *
     * @return new data item iterator
     */
    public DataFileBlockIterator createIterator() {
        return new DataFileBlockIterator(path, blockSize, indexShifted, dataItemCount);
    }

    /**
     * Common code for store methods
     *
     *  - Check we are in correct state for writing
     *  - calculate block offset and data size
     *  - prep temp buffer
     *
     * @param dataValueSize the size in bytes of data value that store is going to write
     * @return the blockOffset of where data will be written
     * @throws IOException if we are not in correct state
     */
    private int startStoring(int dataValueSize) throws IOException {
        if (isReadOnly) throw new IOException("Tried to store data in read only data file "+path);
        if (seekableByteChannel == null) throw new IOException("Tried to store data on closed data file "+path);
        // find offset for the start of this new data item, we assume we always write data in a whole number of blocks
        long byteOffset = seekableByteChannel.position();
        int blockOffset = (int)(byteOffset/blockSize);
        // calculate data size to write
        int dataSize = (int)Math.ceil((Integer.BYTES + KEY_SIZE + dataValueSize) / (double)blockSize) * blockSize;
        // get temp buffer
        tempWriteBuffer = (tempWriteBuffer == null || tempWriteBuffer.capacity() < dataSize) ?
                                ByteBuffer.allocate(dataSize) : tempWriteBuffer.clear();
        // set the limit, this means we pad to the nearest block size
        tempWriteBuffer.limit(dataSize);
        // write data header to temp buffer
        tempWriteBuffer.putInt(dataValueSize);
        return blockOffset;
    }

    /**
     * Store data in provided ByteBuffer from its current position to its limit.
     *
     * @param key a long key
     * @param dataToStore buffer containing data to be written, must be rewound
     * @return the data location of written data
     * @throws IOException If there was a problem appending data to file
     */
    public synchronized long storeData(long key, ByteBuffer dataToStore) throws IOException {
        // start storing process, prepares tempWriteBuffer, writes block header data, returns byteOffset
        int blockOffset = startStoring(dataToStore.remaining());
        // write key and value
        tempWriteBuffer.putLong(key);
        tempWriteBuffer.put(dataToStore);
        // write temp buffer to file
        tempWriteBuffer.rewind();
        seekableByteChannel.write(tempWriteBuffer);
        // increment data item counter
        dataItemCount ++;
        // return the offset where we wrote the data
        return indexShifted | blockOffset;
    }

    /**
     * Store data in provided ByteBuffer from its current position to its limit.
     *
     * @param blockBuffer buffer containing whole block to be written
     * @return the data location of written data
     * @throws IOException If there was a problem appending data to file
     */
    public synchronized long storeData(ByteBuffer blockBuffer) throws IOException {
        long byteOffset = seekableByteChannel.position();
        int blockOffset = (int)(byteOffset/blockSize);
        // write temp buffer to file
        seekableByteChannel.write(blockBuffer);
        // increment data item counter
        dataItemCount ++;
        // return the offset where we wrote the data
        return indexShifted | blockOffset;
    }

    /**
     * When you finished append to a new file, call this to seal the file and make it read only for reading.
     *
     * @throws IOException If there was a problem sealing file or opening again as read only
     */
    public synchronized void finishWriting(long minimumValidKey, long maximumValidKey) throws IOException {
        if (seekableByteChannel == null) throw new IOException("Tried to finish writing on closed data file "+path);
        // store min & max keys
        this.minimumValidKey = minimumValidKey;
        this.maximumValidKey = maximumValidKey;
        // make sure tempWriteBuffer has been created and is big enough for footer data.
        if (tempWriteBuffer == null || tempWriteBuffer.limit() < FOOTER_SIZE) {
            tempWriteBuffer = ByteBuffer.allocate(FOOTER_SIZE);
        }
        // pad the end of file till we are a whole number of pages
        long paddingBytesNeeded = PAGE_SIZE - ((dataItemCount * blockSize) % PAGE_SIZE);
        tempWriteBuffer.position(0);
        for (int i = 0; i < paddingBytesNeeded; i++)  tempWriteBuffer.put((byte)0);
        tempWriteBuffer.flip();
        seekableByteChannel.write(tempWriteBuffer);
        // write any metadata to end of file.
        // reset tempWriteBuffer position and limit to save exactly one page
        tempWriteBuffer.position(0);
        tempWriteBuffer.limit(FOOTER_SIZE);
        // fill footer metadata buffer
        tempWriteBuffer.putInt(FILE_FORMAT_VERSION);
        tempWriteBuffer.putInt(blockSize);
        tempWriteBuffer.put(isMergeFile ? (byte)1 : 0);
        tempWriteBuffer.putLong(dataItemCount);
        tempWriteBuffer.putLong(minimumValidKey);
        tempWriteBuffer.putLong(maximumValidKey);
        tempWriteBuffer.rewind();
        // write footer to file
        seekableByteChannel.write(tempWriteBuffer);
        // close and reopen file as read only
        seekableByteChannel.close();
        seekableByteChannel = Files.newByteChannel(path, StandardOpenOption.READ);
        isReadOnly = true;
    }

    /**
     * Read a data block from file at startBlockOffset. The data returned is defined by DataToRead
     *
     * @param bufferToReadInto a byte buffer big enough to contain the read data, that has been rewound. If it is too
     *                         small it will be filled up to remaining bytes.
     * @param dataLocation the file index combined with the offset for the starting block of the data in the file.
     * @param dataToRead The chosen data that should be read into bufferToReadInto
     * @throws IOException If there was a problem reading from data file
     */
    public synchronized void readData(ByteBuffer bufferToReadInto, long dataLocation, DataToRead dataToRead) throws IOException {
        if (!isReadOnly) throw new IOException("Tried to read data from a non-read-only file "+path);
        if (seekableByteChannel == null) throw new IOException("Tried to read from closed data file "+path);
        int startBlockOffset = (int)(dataLocation & 0x00000000ffffffffL);
        long offset = (long)startBlockOffset*(long)blockSize+Integer.BYTES;
        if(dataToRead == DataToRead.KEY) {
            bufferToReadInto.limit(bufferToReadInto.position()+ KEY_SIZE); // set limit so we only read hash
        } else if (dataToRead == DataToRead.VALUE) {
            // jump over key
            offset += KEY_SIZE;
        }
        // TODO for non KEY only we read up to bufferToReadInto.limit bytes as we do not know value size, maybe we should read size int and set limit but it will be slower...
        seekableByteChannel.position(offset);
        seekableByteChannel.read(bufferToReadInto);
        // get buffer ready for a reader
        bufferToReadInto.rewind();
    }

    /**
     * Close this data file, it can not be used once closed.
     */
    public synchronized void close() {
        try {
            seekableByteChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        seekableByteChannel = null;
    }

    /**
     * Close this file and delete it
     *
     * @throws IOException If there was a problem deleting the file
     */
    public synchronized void closeAndDelete() throws IOException {
        seekableByteChannel.close();
        Files.delete(path);
    }

    // =================================================================================================================
    // DataFileBlockIterator

    /**
     * Iterator class for iterating over data items in a DataFile. It is designed to be used in a while(iter.next()){..}
     * loop and you can then read the data items info for current item with getBlocksKey, getBlocksDataLocation and
     * getBlockData.
     */
    public static final class DataFileBlockIterator {
        private final SeekableByteChannel seekableByteChannel;
        private final ByteBuffer blockBuffer;
        private final long dataItemCount;
        private final long fileIndexShifted;
        private int currentBlock = -1;
        private long key;

        public DataFileBlockIterator(Path path, int blockSize, long indexShifted, long dataItemCount) {
            try {
                this.fileIndexShifted = indexShifted;
                this.seekableByteChannel = Files.newByteChannel(path, StandardOpenOption.READ);
                this.dataItemCount = dataItemCount;
                this.blockBuffer = ByteBuffer.allocate(blockSize);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void close() throws IOException {
            seekableByteChannel.close();
        }

        /**
         * move on to next block
         *
         * @return true if a block was read or false if the end of the file has been reached.
         * @throws IOException If there was a problem reading from file.
         */
        public boolean next() throws IOException {
            if (currentBlock >= dataItemCount -1) return false; // we have reached the end
            // read block from file
            blockBuffer.clear();
            seekableByteChannel.read(blockBuffer);
            // read key from buffer
            blockBuffer.rewind();
            key = blockBuffer.getLong(Integer.SIZE);
            // increment block
            currentBlock ++;
            return true;
        }

        /**
         * Get current blocks data
         *
         * @return ByteBuffer containing the key and value data
         */
        public ByteBuffer getBlockData() {
            blockBuffer.rewind();
            return blockBuffer;
        }

        /**
         * Get the data location for current block
         *
         * @return file index and block index combined location
         */
        public long getBlocksDataLocation() {
            return fileIndexShifted | currentBlock;
        }

        /**
         * Get current blocks key
         *
         * @return the key for current block
         */
        public long getBlocksKey() {
            return key;
        }
    }
}
