package com.hedera.services.state.jasperdb.files;

import com.hedera.services.state.jasperdb.collections.IndexedObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Abstract base class for DataFileReaders. The aim for a DataFileReader is to facilitate fast highly concurrent random
 * reading of items from a data file. This is base class so that we can create a range of different DataFileReader
 * implementations to experiment with different IO libraries and threading strategies to find the fastest way to read
 * items from data files with many concurrent threads.
 */
@SuppressWarnings("DuplicatedCode")
public abstract class DataFileReader implements AutoCloseable, Comparable<DataFileReader>, IndexedObject {
    /** Enum for read operation, you can read the key, value or both. */
    public enum DataToRead{KEY,VALUE,KEY_VALUE}
    /** The path to the file on disk */
    protected final Path path;
    /** The metadata for this file read from the footer */
    protected final DataFileMetadata metadata;
    /** True if this file contains variable sized data rather than fixed size */
    protected final boolean hasVariableSizedData;


    /**
     * Open an existing data file, reading the metadata from the file
     *
     * @param path the path to the data file
     */
    public DataFileReader(Path path) throws IOException {
        if (!Files.exists(path))
            throw new IllegalArgumentException("Tried to open a non existent data file [" + path.toAbsolutePath() + "].");
        this.path = path;
        // load files metadata
        this.metadata = new DataFileMetadata(path);
        this.hasVariableSizedData = this.metadata.hasVariableSizeData();
    }

    /**
     * Open an existing data file, using the provided metadata
     *
     * @param path the path to the data file
     * @param metadata the file's metadata to save loading from file
     */
    public DataFileReader(Path path, DataFileMetadata metadata) throws IOException {
        if (!Files.exists(path))
            throw new IllegalArgumentException("Tried to open a non existent data file [" + path.toAbsolutePath() + "].");
        this.path = path;
        this.metadata = metadata;
        this.hasVariableSizedData = this.metadata.hasVariableSizeData();
    }

    /**
     * Get file index, the index is an ordered integer identifying the file in a set of files
     *
     * @return this file's index
     */
    @Override
    public int getIndex() {
        return metadata.getIndex();
    }

    /**
     * Get the files metadata
     */
    public final DataFileMetadata getMetadata() {
        return metadata;
    }

    /**
     * Get the path to this data file
     */
    public final Path getPath() {
        return path;
    }

    /**
     * Create an iterator to iterate over the data items in this data file. It opens its own file handle so can be used
     * in a separate thread. It must therefore be closed when you are finished with it.
     *
     * @return new data item iterator
     */
    public final DataFileIterator createIterator() {
        return new DataFileIterator(path, metadata);
    }

    /**
     * Read a data item from file at dataLocation. The data returned is defined by DataToRead.
     *
     * @param bufferToReadInto a byte buffer big enough to contain the read data, that has been rewound. If it is too
     *                         small we read just bufferToReadInto.remaining() bytes.
     * @param dataLocation     the file index combined with the offset for the starting block of the data in the file.
     * @param dataToRead       The chosen data that should be read into bufferToReadInto
     * @throws IOException If there was a problem reading from data file
     */
    public final void readData(ByteBuffer bufferToReadInto, long dataLocation,
                               DataFileReaderAsynchronous.DataToRead dataToRead) throws IOException {
        long byteOffset = DataFileCommon.byteOffsetFromDataLocation(dataLocation);
        int bytesToRead;
        if (dataToRead == DataToRead.KEY) {
            if (hasVariableSizedData) byteOffset += Integer.BYTES; // jump over the data value size
            bytesToRead = DataFileCommon.KEY_SIZE;
        } else {
            if (hasVariableSizedData) {
                bytesToRead = readInt(byteOffset);
                byteOffset += Integer.BYTES; // jump over the data value size
            } else {
                bytesToRead = metadata.getDataItemValueSize();
            }
            if (dataToRead == DataToRead.VALUE) {
                // jump over key
                byteOffset += DataFileCommon.KEY_SIZE;
            } else { // reading KEY and VALUE
                bytesToRead += DataFileCommon.KEY_SIZE;
            }
        }
        // check the buffer we were given is big enough
        if (bufferToReadInto.remaining() > bytesToRead) {
            // set limit, so we only read the data for this item
            bufferToReadInto.limit(bufferToReadInto.position() + bytesToRead);
        }
        // read data
        read(byteOffset,bufferToReadInto);
        // get buffer ready for a reader
        bufferToReadInto.rewind();
    }

    /**
     * Read a data item from file at dataLocation. The data returned is defined by DataToRead.
     *
     * @param dataLocation     the file index combined with the offset for the starting block of the data in the file.
     * @param dataToRead       The chosen data that should be read into bufferToReadInto
     * @return ByteBuffer containing the data read or null if not found
     * @throws IOException If there was a problem reading from data file
     */
    public final ByteBuffer readData(long dataLocation,
                               DataFileReaderAsynchronous.DataToRead dataToRead) throws IOException {
        long byteOffset = DataFileCommon.byteOffsetFromDataLocation(dataLocation);
        int bytesToRead;
        if (dataToRead == DataToRead.KEY) {
            if (hasVariableSizedData) byteOffset += Integer.BYTES; // jump over the data value size
            bytesToRead = DataFileCommon.KEY_SIZE;
        } else {
            if (hasVariableSizedData) {
                bytesToRead = readInt(byteOffset);
                byteOffset += Integer.BYTES; // jump over the data value size
            } else {
                bytesToRead = metadata.getDataItemValueSize();
            }
            if (dataToRead == DataToRead.VALUE) {
                // jump over key
                byteOffset += DataFileCommon.KEY_SIZE;
            } else { // reading KEY and VALUE
                bytesToRead += DataFileCommon.KEY_SIZE;
            }
        }
        // create buffer
        ByteBuffer bufferToReadInto = ByteBuffer.allocate(bytesToRead);
        // read data
        read(byteOffset,bufferToReadInto);
        // get buffer ready for a reader and return
        return bufferToReadInto.flip();
    }

    /**
     * Get the size of this file in bytes
     *
     * @return file size in bytes
     * @throws IOException If there was a problem reading file size from filesystem
     */
    public long getSize() throws IOException {
        return Files.size(path);
    }

    /**
     * Equals for use when comparing in collections
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataFileReader that = (DataFileReader) o;
        return path.equals(that.path);
    }

    /**
     * hashCode for use when comparing in collections
     */
    @Override
    public int hashCode() {
        return path.hashCode();
    }

    /**
     * Compares this Data File to another based on index
     */
    @Override
    public final int compareTo(DataFileReader o) {
        return Integer.compare(metadata.getIndex(), Objects.requireNonNull(o).getMetadata().getIndex());
    }

    /** ToString for debugging */
    @Override
    public String toString() {
        return Integer.toString(metadata.getIndex());
    }

    // =================================================================================================================
    // Abstract methods

    /**
     * Read data from the file starting at byteOffsetInFile till we fill the bufferToFill.remaining() bytes.
     *
     * @param byteOffsetInFile offset to start reading at
     * @param bufferToFill byte buffer to fill with read data
     * @throws IOException if there was a problem reading
     */
    protected abstract void read(long byteOffsetInFile, ByteBuffer bufferToFill) throws IOException;

    /**
     * Read an integer from the file starting at byteOffsetInFile.
     *
     * NOTE:This is only called if hasVariableSizedData is true
     *
     * @param byteOffsetInFile offset to start reading at
     * @return the read integer
     * @throws IOException if there was a problem reading
     */
    protected abstract int readInt(long byteOffsetInFile) throws IOException;

    /**
     * Close this data file, it can not be used once closed.
     */
    @Override
    public abstract void close() throws IOException;
}
