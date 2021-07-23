package com.hedera.services.state.merkle.v3.files;

import com.swirlds.common.crypto.Hash;

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
 * bytes - hash data
 * bytes - key data
 * bytes - value data
 * bytes - padding till end of nearest blockSize
 *
 * At the end of the file in last 4096 byte block is a footer. Containing:
 *
 * Int - file format version
 * Int - key size
 * Int - hash size
 * Int - block size
 */
@SuppressWarnings("DuplicatedCode")
public class DataFile implements AutoCloseable {
    public enum DataToRead{HASH,VALUE,KEY_VALUE,HASH_KEY_VALUE};
    private static final int FILE_FORMAT_VERSION = 1;
    private final Path path;
    private final int blockSize;
    private final int keySize;
    private final int hashSize;
    private boolean isReadOnly;
    private SeekableByteChannel seekableByteChannel = null;
    private ByteBuffer tempWriteBuffer = null;

    /**
     * Open a data file, if it already exists it is opened read only, if it doesn't exist it is opened in append mode.
     *  @param path the path to the data file
     * @param blockSize the size of blocks, used for storing a block offsets. 4096 must be divisible by blockSize
     * @param keySize the number of byte to store each key
     * @param hashSize the number of byte to store each hash
     */
    public DataFile(Path path, int blockSize, int keySize, int hashSize) throws IOException {
        if (4096 % blockSize != 0) throw new IOException("4096 is not divisible by blockSize");
        this.path = path;
        this.blockSize = blockSize;
        this.keySize = keySize;
        this.hashSize = hashSize;
        if (Files.exists(path)) {
            isReadOnly = true;
            seekableByteChannel = Files.newByteChannel(path, StandardOpenOption.READ);
        } else {
            isReadOnly = false;
            seekableByteChannel = Files.newByteChannel(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }


    /**
     * Common code for store methods
     *
     *  - Check we are in correct state for writing
     *  -
     * @param hash hash of data
     * @param dataValueSize the size in bytes of data value that store is going to write
     * @return the blockOffset of where data will be written
     * @throws IOException if we are not in correct state
     */
    private int startStoring(Hash hash, int dataValueSize) throws IOException {
        if (isReadOnly) throw new IOException("Tried to store data in read only data file "+path);
        if (seekableByteChannel == null) throw new IOException("Tried to store data on closed data file "+path);
        // find offset for the start of this new data item, we assume we always write data in a whole number of blocks
        long byteOffset = seekableByteChannel.position();
        int blockOffset = (int)(byteOffset/blockSize);
        // calculate data size to write
        int dataSize = (int)Math.ceil((Integer.SIZE + keySize + hashSize + dataValueSize) / (double)blockSize) * blockSize;
        // get temp buffer
        tempWriteBuffer = (tempWriteBuffer == null || tempWriteBuffer.limit() < dataSize) ?
                                ByteBuffer.allocate(dataSize) : tempWriteBuffer.clear();
        // set the limit, this means we pad to nearest block size
        tempWriteBuffer.limit(dataSize);
        // write data header to temp buffer
        tempWriteBuffer.putInt(dataValueSize);
        Hash.toByteBuffer(hash,tempWriteBuffer);
        return blockOffset;
    }

    /**
     * Store data in provided ByteBuffer from its current position to its limit.
     *
     * @param key a long key
     * @param hash hash of data
     * @param dataToStore buffer containing data to be written, must be rewound
     * @return the block offset of where the data was stored
     * @throws IOException If there was a problem appending data to file
     */
    public synchronized int storeData(long key, Hash hash, ByteBuffer dataToStore) throws IOException {
        if (keySize != Long.BYTES) throw new IOException("Tried to store data with long key when key size was "+keySize+", into data file "+path);
        // start storing process, prepares tempWriteBuffer, writes block header data, returns byteOffset
        int blockOffset = startStoring(hash, dataToStore.remaining());
        // write key and value
        tempWriteBuffer.putLong(key);
        tempWriteBuffer.put(dataToStore);
        // write temp buffer to file
        tempWriteBuffer.rewind();
        seekableByteChannel.write(tempWriteBuffer);
        // return the offset where we wrote the data
        return blockOffset;
    }

    /**
     * Store data in provided ByteBuffer from its current position to its limit.
     *
     * @param key buffer containing key to be written, must be rewound
     * @param hash hash of data
     * @param dataToStore buffer containing data to be written, must be rewound
     * @return the block offset of where the data was stored
     * @throws IOException If there was a problem appending data to file
     */
    public synchronized int storeData(ByteBuffer key, Hash hash, ByteBuffer dataToStore) throws IOException {
        if (keySize != key.remaining()) throw new IOException("Tried to store data with key of size ["+key.remaining()+"] when key size was "+keySize+", into data file "+path);
        // start storing process, prepares tempWriteBuffer, writes block header data, returns byteOffset
        int blockOffset = startStoring(hash, dataToStore.remaining());
        // write key and value
        tempWriteBuffer.put(key);
        tempWriteBuffer.put(dataToStore);
        // write temp buffer to file]
        tempWriteBuffer.flip();
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
        tempWriteBuffer.clear();
        tempWriteBuffer.putInt(FILE_FORMAT_VERSION);
        tempWriteBuffer.putInt(keySize);
        tempWriteBuffer.putInt(hashSize);
        tempWriteBuffer.putInt(blockSize);
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
     * @param startBlockOffset the offset for the starting block of the data in the file.
     * @param dataToRead The chosen data that should be read into bufferToReadInto
     * @throws IOException If there was a problem reading from data file
     */
    public synchronized void readData(ByteBuffer bufferToReadInto, int startBlockOffset, DataToRead dataToRead) throws IOException {
        if (!isReadOnly) throw new IOException("Tried to read data from a non-read-only file "+path);
        if (seekableByteChannel == null) throw new IOException("Tried to read from closed data file "+path);
        long offset = (long)startBlockOffset*(long)blockSize+Integer.BYTES;
        switch(dataToRead) {
            case HASH:
                bufferToReadInto.limit(bufferToReadInto.position()+hashSize); // set limit so we only read hash
                break;
            case KEY_VALUE:
                offset += hashSize; // jump over hash
                // we have to assume bufferToReadInto's limit is set correctly or will just fill it up
                break;
            case VALUE:
                offset += hashSize + keySize;
                break;
            // case HASH_KEY_VALUE: // this is fine we just read all the data
        }
        seekableByteChannel.position(offset);
        seekableByteChannel.read(bufferToReadInto);
        // get buffer ready for a reader
        bufferToReadInto.rewind();
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
