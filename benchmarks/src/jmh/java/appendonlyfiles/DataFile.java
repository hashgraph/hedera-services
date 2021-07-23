package appendonlyfiles;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A Data File, containing a number of data items. Each item is made up of:
 *
 * Int - data length in bytes
 * bytes - key data
 * bytes - value data
 * bytes - padding till end of nearest blockSize
 *
 * At the end of the file in last 4096 byte block is a footer. Containing:
 *
 * Int - file format version
 * Int - key size
 */
@SuppressWarnings("DuplicatedCode")
public class DataFile implements AutoCloseable {
    private static final int FILE_FORMAT_VERSION = 1;
    private final Path path;
    private final int blockSize;
    private final int keySize;
    private boolean isReadOnly;
    private SeekableByteChannel seekableByteChannel = null;
    private ByteBuffer tempWriteBuffer = null;

    /**
     * Open a data file, if it already exists it is opened read only, if it doesn't exist it is opened in append mode.
     *
     * @param path the path to the data file
     * @param blockSize the size of blocks, used for storing a block offsets. 4096 must be divisible by blockSize
     * @param keySize the number of byte to store each key
     */
    public DataFile(Path path, int blockSize, int keySize) throws IOException {
        this.path = path;
        this.blockSize = blockSize;
        this.keySize = keySize;
        if (Files.exists(path)) {
            isReadOnly = true;
            seekableByteChannel = Files.newByteChannel(path, StandardOpenOption.READ);
        } else {
            isReadOnly = false;
            seekableByteChannel = Files.newByteChannel(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    /**
     * Store data in provided ByteBuffer from its current position to its limit.
     *
     * @param key a long key
     * @param dataToStore buffer containing data to be written, must be rewound
     * @return the block offset of where the data was stored
     * @throws IOException If there was a problem appending data to file
     */
    public synchronized int storeData(long key, ByteBuffer dataToStore) throws IOException {
        if (isReadOnly) throw new IOException("Tried to store data in read only data file "+path);
        if (seekableByteChannel == null) throw new IOException("Tried to store data on closed data file "+path);
        if (keySize != Long.SIZE) throw new IOException("Tried to store data with long key when key size was "+keySize+", into data file "+path);
        // find offset for the start of this new data item, we assume we always write data in a whole number of blocks
        long byteOffset = seekableByteChannel.size();
        int blockOffset = (int)(byteOffset/blockSize);
        // calculate data size to write
        int dataValueSize = dataToStore.remaining();
        int dataSize = (int)Math.ceil((Integer.SIZE + keySize + dataValueSize) / (double)blockSize) * blockSize;
        // get temp buffer
        if (tempWriteBuffer == null || tempWriteBuffer.limit() < dataSize) {
            tempWriteBuffer = ByteBuffer.allocate(dataSize);
        }
        tempWriteBuffer.rewind();
        // fill temp buffer
        tempWriteBuffer.putInt(dataValueSize);
        tempWriteBuffer.putLong(key);
        tempWriteBuffer.put(dataToStore);
        // write temp buffer to file]
        tempWriteBuffer.rewind();
        seekableByteChannel.write(tempWriteBuffer);
        // return the offset where we wrote the data
        return blockOffset;
    }

    /**
     * Store data in provided ByteBuffer from its current position to its limit.
     *
     * @param key buffer containing key to be written, must be rewound
     * @param dataToStore buffer containing data to be written, must be rewound
     * @return the block offset of where the data was stored
     * @throws IOException If there was a problem appending data to file
     */
    public synchronized int storeData(ByteBuffer key, ByteBuffer dataToStore) throws IOException {
        if (isReadOnly) throw new IOException("Tried to store data in read only data file "+path);
        if (seekableByteChannel == null) throw new IOException("Tried to store data on closed data file "+path);
        if (keySize != Long.SIZE) throw new IOException("Tried to store data with long key when key size was "+keySize+", into data file "+path);
        // find offset for the start of this new data item, we assume we always write data in a whole number of blocks
        long byteOffset = seekableByteChannel.size();
        int blockOffset = (int)(byteOffset/blockSize);
        // calculate data size to write
        int dataValueSize = dataToStore.remaining();
        int dataSize = (int)Math.ceil((Integer.SIZE + keySize + dataValueSize) / (double)blockSize) * blockSize;
        // get temp buffer
        if (tempWriteBuffer == null || tempWriteBuffer.limit() < dataSize) {
            tempWriteBuffer = ByteBuffer.allocateDirect(dataSize);
        }
        tempWriteBuffer.rewind();
        // fill temp buffer
        tempWriteBuffer.putInt(dataValueSize);
        tempWriteBuffer.put(key);
        tempWriteBuffer.put(dataToStore);
        // write temp buffer to file]
        tempWriteBuffer.rewind();
        seekableByteChannel.write(tempWriteBuffer);
        // return the offset where we wrote the data
        return blockOffset;
    }

    /**
     * When you finished append to a new file, call this to seal the file and make it read only for reading.
     *
     * @throws IOException If there was a problem sealing file or opening again as read only
     */
    public synchronized void finishWriting() throws IOException {
        if (seekableByteChannel == null) throw new IOException("Tried to finish writing on closed data file "+path);
        // write any meta data to end of file.
        if (tempWriteBuffer == null || tempWriteBuffer.limit() < 4096) {
            tempWriteBuffer = ByteBuffer.allocate(4096);
        }
        // fill footer metadata buffer
        tempWriteBuffer.rewind();
        tempWriteBuffer.putInt(FILE_FORMAT_VERSION);
        tempWriteBuffer.putInt(keySize);
        tempWriteBuffer.rewind();
        // write footer to file
        seekableByteChannel.write(tempWriteBuffer);
        // close and reopen file as read only
        seekableByteChannel.close();
        seekableByteChannel = Files.newByteChannel(path, StandardOpenOption.READ);
        isReadOnly = true;
    }

    /**
     * Read a data item from file at startBlockOffset
     *
     * @param bufferToReadInto a byte buffer big enough to contain the read data, that has been rewound. If it is too
     *                         small it will be filled up to remaining bytes.
     * @param startBlockOffset the offset for the starting block of the data in the file.
     * @throws IOException If there was a problem reading from data file
     */
    public synchronized void readData(ByteBuffer bufferToReadInto, int startBlockOffset) throws IOException {
        if (seekableByteChannel == null) throw new IOException("Tried to read from closed data file "+path);
        seekableByteChannel.position((long)startBlockOffset*(long)blockSize+Integer.BYTES);
        seekableByteChannel.read(bufferToReadInto);
    }

    /**
     * Close this data file, it can not be used once closed.
     *
     * @throws IOException If there was a problem closing the file.
     */
    public synchronized void close() throws IOException {
        seekableByteChannel.close();
        seekableByteChannel = null;
    }
}
