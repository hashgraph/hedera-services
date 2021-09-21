package com.hedera.services.state.jasperdb.files;

import com.hedera.services.state.jasperdb.collections.IndexedObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The aim for a DataFileReader is to facilitate fast highly concurrent random reading of items from a data file. It is
 * designed to be used concurrently from many threads.
 *
 * @param <D> Data item type
 */
@SuppressWarnings({"DuplicatedCode", "NullableProblems"})
public class DataFileReader<D> implements AutoCloseable, Comparable<DataFileReader<D>>, IndexedObject {
    /** SeekableByteChannel's for each thread */
    protected final ThreadLocalSeekableByteChannel seekableByteChannel;
    /** Enum for read operation, you can read the key, value or both. */
    public enum DataToRead{KEY,VALUE,KEY_VALUE}
    /** The path to the file on disk */
    protected final Path path;
    /** The metadata for this file read from the footer */
    protected final DataFileMetadata metadata;
    /** Serializer for converting raw data to/from data items */
    private final DataItemSerializer<D> dataItemSerializer;

    /**
     * Open an existing data file, reading the metadata from the file
     *
     * @param path the path to the data file
     * @param dataItemSerializer Serializer for converting raw data to/from data items
     */
    public DataFileReader(Path path, DataItemSerializer<D> dataItemSerializer) throws IOException {
        this(path, dataItemSerializer, new DataFileMetadata(path));
    }

    /**
     * Open an existing data file, using the provided metadata
     *
     * @param path the path to the data file
     * @param dataItemSerializer Serializer for converting raw data to/from data items
     * @param metadata the file's metadata to save loading from file
     */
    public DataFileReader(Path path, DataItemSerializer<D> dataItemSerializer, DataFileMetadata metadata) throws IOException {
        if (!Files.exists(path))
            throw new IllegalArgumentException("Tried to open a non existent data file [" + path.toAbsolutePath() + "].");
        this.path = path;
        this.metadata = metadata;
        this.dataItemSerializer = dataItemSerializer;
        this.seekableByteChannel = new ThreadLocalSeekableByteChannel(path, dataItemSerializer.isVariableSize());
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
     * @throws IOException if there was a problem creating a new DataFileIterator
     */
    public final DataFileIterator createIterator() throws IOException {
        return new DataFileIterator(path, metadata, dataItemSerializer);
    }

    /**
     * Read a data item from file at dataLocation. The data returned is defined by DataToRead.
     *
     * @param dataLocation The file index combined with the offset for the starting block of the data in the file.
     * @throws IOException If there was a problem reading from data file
     */
    public final D readDataItem(long dataLocation) throws IOException {
        final LocalData localData = this.seekableByteChannel.get();
        long byteOffset = DataFileCommon.byteOffsetFromDataLocation(dataLocation);
        int bytesToRead;
        if (dataItemSerializer.isVariableSize()) {
            // read header to get size
            var header = dataItemSerializer.deserializeHeader(read(byteOffset, dataItemSerializer.getHeaderSize(), localData));
            bytesToRead = header.getSizeBytes();
        } else {
            bytesToRead = dataItemSerializer.getSerializedSize();
        }
        // read
        return dataItemSerializer.deserialize(read(byteOffset,bytesToRead,localData),
                metadata.getSerializationVersion());
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
     * Equals for use when comparing in collections, based on matching file paths
     */
    @SuppressWarnings("rawtypes")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataFileReader that = (DataFileReader) o;
        return path.equals(that.path);
    }

    /**
     * hashCode for use when comparing in collections, based on file path
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

    /**
     * Close this data file, it can not be used once closed.
     */
    public void close() throws IOException {
        seekableByteChannel.close();
    }

    // =================================================================================================================
    // Private methods

    /**
     * Read data from the file starting at byteOffsetInFile till we fill the bufferToFill.remaining() bytes.
     *
     * @param byteOffsetInFile offset to start reading at
     * @param bytesToRead Number of bytes to read
     * @return ByteBuffer containing read data
     * @throws IOException if there was a problem reading
     */
    private ByteBuffer read(long byteOffsetInFile, int bytesToRead, LocalData localData) throws IOException {
        ByteBuffer buffer = localData.getDataByteBuffer(bytesToRead);
        SeekableByteChannel seekableByteChannel = localData.channel;
        seekableByteChannel.position(byteOffsetInFile);
        seekableByteChannel.read(buffer);
        buffer.flip();
        return buffer;
    }

    // =================================================================================================================
    // Inner classes

    /**
     * A special thread local that keeps track of the SeekableByteChannel's it creates so that they can be closed.
     */
    private static class ThreadLocalSeekableByteChannel extends ThreadLocal<LocalData>
            implements AutoCloseable {
        private final Path path;
        private final boolean hasVariableSizedData;
        private final CopyOnWriteArrayList<SeekableByteChannel> allChannels = new CopyOnWriteArrayList<>();

        /**
         * Create a new ThreadLocalSeekableByteChannel
         *
         * @param path the path to the data file so we can open SeekableByteChannel's on it
         */
        protected ThreadLocalSeekableByteChannel(Path path, boolean hasVariableSizedData) {
            this.path = path;
            this.hasVariableSizedData = hasVariableSizedData;
        }

        /**
         * Open a new SeekableByteChannel when a new thread comes along
         */
        @Override
        protected LocalData initialValue() {
            try {
                LocalData localData = new LocalData(path, hasVariableSizedData);
                allChannels.add(localData.channel);
                return localData;
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        /**
         * Closes all channels
         *
         * @throws IOException if this resource cannot be closed
         */
        @Override
        public void close() throws IOException {
            for(var channel: allChannels) channel.close();
        }
    }

    /**
     * Simple struct so we can store multiple items in thread local
     */
    private static class LocalData {
        public final SeekableByteChannel channel;
        public final ByteBuffer intSizeByteBuffer;
        private ByteBuffer dataByteBuffer;

        public LocalData(Path path, boolean hasVariableSizedData) throws IOException {
            this.channel = Files.newByteChannel(path, StandardOpenOption.READ);
            this.intSizeByteBuffer = hasVariableSizedData ? ByteBuffer.allocateDirect(Integer.BYTES) : null;
        }

        public ByteBuffer getDataByteBuffer(int size) {
            if (dataByteBuffer == null || size > dataByteBuffer.capacity()) {
                dataByteBuffer = ByteBuffer.allocate(size);
            }
            dataByteBuffer.position(0);
            dataByteBuffer.limit(size);
            return dataByteBuffer;
        }
    }
}
