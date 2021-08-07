package com.hedera.services.state.merkle.v3.files;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Abstract base class for DataFileReaders
 */
public abstract class DataFileReader implements AutoCloseable, Comparable<DataFileReader> {
    public enum DataToRead{KEY,VALUE,KEY_VALUE}
    protected final Path path;
    protected final DataFileMetadata metadata;
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
     * Compares this Data File to another based on index
     */
    @Override
    public final int compareTo(@NotNull DataFileReader o) {
        return Integer.compare(metadata.getIndex(), Objects.requireNonNull(o).getMetadata().getIndex());
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
     * Read a data ietm from file at dataLocation. The data returned is defined by DataToRead.
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
     * Get the size of this file in bytes
     *
     * @return file size in bytes
     * @throws IOException If there was a problem reading file size from filesystem
     */
    public final long getSize() throws IOException {
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
