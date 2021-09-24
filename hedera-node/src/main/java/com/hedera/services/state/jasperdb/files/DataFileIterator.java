package com.hedera.services.state.jasperdb.files;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;

/**
 * Iterator class for iterating over data items in a DataFile. It is designed to be used in a while(iter.next()){...}
 * loop and you can then read the data items info for current item with getDataItemsKey, getDataItemsDataLocation and
 * getDataItemData.
 *
 * It is designed to be used from a single thread.
 *
 * @see DataFileWriter for definition of file structure
 */
@SuppressWarnings("rawtypes")
public final class DataFileIterator implements AutoCloseable {
    /** Input stream this iterator is reading from */
    private final DataInputStream inputStream;
    /** The file metadata read from the end of file */
    private final DataFileMetadata metadata;
    /** The path to the file we are iterating over */
    private final Path path;
    /** Buffer that is reused for reading each data item */
    private ByteBuffer dataItemBuffer;
    /** Header for the current data item */
    private DataItemHeader currentDataItemHeader;
    /** Index of current data item this iterator is reading, zero being the first item, -1 being before start */
    private long currentDataItem = -1;
    /**
     * The offset in bytes from start of file to the beginning of the current item. This is before the key in
     * non-variable sized data or before the size in variable sized data files.
     */
    private long currentDataItemByteOffset = 0;
    /** Buffer containing the current item's data. This is null if not read yet */
    private ByteBuffer currentDataItemsData;

    private final DataItemSerializer dataItemSerializer;

    /**
     * Create a new DataFileIterator on a existing file.
     *
     * @param path The path to the file to read.
     * @param metadata The metadata read from the file.
     * @throws IOException if there was a problem creating a new InputStream on the file at path
     */
    public DataFileIterator(Path path, DataFileMetadata metadata, DataItemSerializer dataItemSerializer) throws IOException {
        this.path = path;
        this.metadata = metadata;
        this.dataItemSerializer = dataItemSerializer;
        this.inputStream = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path, StandardOpenOption.READ),
                1024*1024)); // 1Mb buffer TODO perf profile the size to use
    }

    /**
     * Get the metadata for the file we are iterating over
     *
     * @return File's metadata
     */
    public DataFileMetadata getMetadata() {
        return metadata;
    }

    /**
     * Close file reader
     * 
     * @throws IOException – if this resource cannot be closed
     */
    @Override
    public void close() throws IOException {
        inputStream.close();
    }

    /**
     * move on to next dataItem
     *
     * @return true if a dataItem was read or false if the end of the file has been reached.
     * @throws IOException If there was a problem reading from file.
     */
    public boolean next() throws IOException {
        if (currentDataItem >= metadata.getDataItemCount() -1) return false; // we have reached the end
        // move the current byte position on, to include the last item
        if (currentDataItem >= 0) {
            currentDataItemByteOffset += currentDataItemHeader.getSizeBytes();
            // check if the data was ever read, if not then skip over it
            if (currentDataItemsData == null) inputStream.skipBytes(currentDataItemHeader.getSizeBytes());
        }
        // read data item header
        final int headerSize = dataItemSerializer.getHeaderSize();
        inputStream.mark(headerSize*2); // read ahead key in stream, then rewind back after reading header
        final ByteBuffer dataBuffer = getResetDataItemBuffer(headerSize);
        int bytesRead = inputStream.read(dataBuffer.array(),0,headerSize);
        assert bytesRead == headerSize;
        currentDataItemHeader = dataItemSerializer.deserializeHeader(dataBuffer);
        inputStream.reset(); // reset the stream back to mark
        // clear currentDataItemsData
        currentDataItemsData = null;
        // increment dataItem
        currentDataItem++;
        return true;
    }

    /**
     * Get current dataItems data
     *
     * @return ByteBuffer containing the key and value data
     */
    public ByteBuffer getDataItemData() throws IOException {
        // lazy read if needed
        if (currentDataItemsData == null) {
            // check dataItem buffer is big enough
            currentDataItemsData = getResetDataItemBuffer(currentDataItemHeader.getSizeBytes());
            // read dataItem from file
            int readBytes = inputStream.read(currentDataItemsData.array(),0,currentDataItemHeader.getSizeBytes());
            if (readBytes < currentDataItemHeader.getSizeBytes()) {
                throw new EOFException("Was trying to read a data item ["+
                        currentDataItem+"] but ran out of data in the file ["+path+"].");
            }
        }
        return currentDataItemsData;
    }

    /**
     * Get the data location for current dataItem
     *
     * @return file index and dataItem index combined location
     */
    public long getDataItemsDataLocation() {
        return DataFileCommon.dataLocation(metadata.getIndex(), currentDataItemByteOffset);
    }

    /**
     * Get current dataItems key
     *
     * @return the key for current dataItem
     */
    public long getDataItemsKey() {
        return currentDataItemHeader.getKey();
    }

    /**
     * Get the creation time and data for the data file we are iterating over
     *
     * @return data file creation date
     */
    public Instant getDataFileCreationDate() {
        return metadata.getCreationDate();
    }

    /** toString for debugging */
    @Override
    public String toString() {
        return "DataFileIterator{" +
                "fileIndex=" + metadata.getIndex() +
                ", currentDataItemHeader=" + currentDataItemHeader +
                ", metadata=" + metadata +
                '}';
    }

    /**
     * Equals for use when comparing in collections, based on matching file paths and metadata
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataFileIterator that = (DataFileIterator) o;
        return path.equals(that.path) && metadata.equals(that.metadata);
    }

    /**
     * hashCode for use when comparing in collections, based on file path and metadata
     */
    @Override
    public int hashCode() {
        return Objects.hash(path, metadata);
    }

    // =================================================================================================================
    // Private methods

    /**
     * Get the dataItemBuffer sized big enough for capacityNeeded
     *
     * @param capacityNeeded The number of bytes we need to have of capacity before limit in dataItemBuffer
     * @return dataItemBuffer, created and sized as needed. With position at 0 and limit set for capacityNeeded
     */
    private ByteBuffer getResetDataItemBuffer(int capacityNeeded) {
        if (dataItemBuffer == null || dataItemBuffer.capacity() < capacityNeeded) {
            dataItemBuffer = ByteBuffer.allocate(capacityNeeded);
        } else {
            dataItemBuffer.position(0);
        }
        dataItemBuffer.limit(capacityNeeded);
        return dataItemBuffer;
    }
}
