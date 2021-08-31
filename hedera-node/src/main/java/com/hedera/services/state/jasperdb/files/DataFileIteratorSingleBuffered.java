package com.hedera.services.state.jasperdb.files;

import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import static com.hedera.services.state.jasperdb.files.DataFileCommon.KEY_SIZE;
import static com.hedera.services.state.jasperdb.files.DataFileCommon.SIZE_OF_DATA_ITEM_SIZE_IN_FILE;

/**
 * Iterator class for iterating over data items in a DataFile. It is designed to be used in a while(iter.next()){...}
 * loop and you can then read the data items info for current item with getDataItemsKey, getDataItemsDataLocation and
 * getDataItemData.
 * 
 * It is designed to be used from a single thread.
 */
@SuppressWarnings("jol")
public final class DataFileIteratorSingleBuffered implements DataFileIterator {
    private final DataInputStream inputStream;
    private final DataFileMetadata metadata;
    private final boolean hasVariableSizedData;
    private final Path path;
    private ByteBuffer dataItemBuffer;
    private int currentDataItemSizeBytes;
    private long currentDataItem = -1;
    private long currentDataItemByteOffset = 0;
    private long nextDataItemByteOffset = 0;
    private long key;

    /**
     * Create a new DataFileIterator on a existing file.
     *
     * @param path The path to the file to read.
     * @param metadata The metadata read from the file.
     */
    public DataFileIteratorSingleBuffered(Path path, DataFileMetadata metadata) {
        try {
            this.path = path;
            this.metadata = metadata;
            this.hasVariableSizedData = metadata.hasVariableSizeData();
            if (!this.hasVariableSizedData) this.currentDataItemSizeBytes = KEY_SIZE + metadata.getDataItemValueSize();
            this.inputStream = new DataInputStream(new BufferedInputStream(Files.newInputStream(path, StandardOpenOption.READ),
                    1024*1024)); // 1Mb buffer TODO perf profile the size to use
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
    @Override
    public boolean next() throws IOException {
        if (currentDataItem >= metadata.getDataItemCount() -1) return false; // we have reached the end
        // if we are reading a file with variableSized data we need to read the item size first
        if (hasVariableSizedData) {
            // read ahead 12 bytes in stream, then rewind back
            inputStream.mark(Integer.BYTES + Long.BYTES);
            // read data value size
            currentDataItemSizeBytes = Integer.BYTES + KEY_SIZE + inputStream.readInt();
            // read key
            key = inputStream.readLong();
            // reset the stream back to mark
            inputStream.reset();
        } else {
            // read ahead 8 bytes in stream, then rewind back
            inputStream.mark(Long.BYTES);
            // read key
            key = inputStream.readLong();
            // reset the stream back to mark
            inputStream.reset();
        }
        // check dataItem buffer is big enough
        if (dataItemBuffer == null || dataItemBuffer.capacity() < currentDataItemSizeBytes) {
            dataItemBuffer = ByteBuffer.allocate(currentDataItemSizeBytes);
        } else {
            dataItemBuffer.position(0);
        }
        // read dataItem from file
        // TODO We can avoid reading the data at this point and read it later only if it is needed. The merging
        // code may determine that we don't need to read this data at all. It would be better to skip the bytes
        // than to read them if we don't need them.
        int readBytes = inputStream.read(dataItemBuffer.array(),0,currentDataItemSizeBytes);
        if (readBytes < currentDataItemSizeBytes) throw new EOFException("Was trying to read a data item ["+currentDataItem+"] but ran out of data in the file ["+path+"].");
        // increment dataItem and current byte position
        currentDataItemByteOffset = nextDataItemByteOffset;
        nextDataItemByteOffset += currentDataItemSizeBytes;
        currentDataItem++;
        return true;
    }


    /**
     * Write the complete data for the current item to a DataFileWriter. That includes key, size(optional) and value.
     *
     * @param writer The writer to write out to
     * @return data location the item was written to
     * @throws IOException if there was a problem writing the item
     */
    @Override
    public long writeItemData(DataFileWriter writer) throws IOException {
        SerializableDataOutputStream out = writer.startStreamingItem(key,currentDataItemSizeBytes);
        if (hasVariableSizedData) {
            out.write(
                    dataItemBuffer.array(),
                    KEY_SIZE + SIZE_OF_DATA_ITEM_SIZE_IN_FILE,
                    currentDataItemSizeBytes - KEY_SIZE - SIZE_OF_DATA_ITEM_SIZE_IN_FILE);
        } else {
            out.write(
                    dataItemBuffer.array(),
                    KEY_SIZE,
                    currentDataItemSizeBytes - KEY_SIZE);
        }
        return writer.endStreamingItem();
    }

    /**
     * Get current dataItems data
     *
     * @return ByteBuffer containing the key and value data
     */
    @Override
    public ByteBuffer getDataItemData() {
        // setup bytebuffer read for reader
        dataItemBuffer.position(0);
        dataItemBuffer.limit(currentDataItemSizeBytes);
        return dataItemBuffer;
    }

    /**
     * Get the data location for current dataItem
     *
     * @return file index and dataItem index combined location
     */
    @Override
    public long getDataItemsDataLocation() {
        return DataFileCommon.dataLocation(metadata.getIndex(), currentDataItemByteOffset);
    }

    /**
     * Get current dataItems key
     *
     * @return the key for current dataItem
     */
    @Override
    public long getDataItemsKey() {
        return key;
    }

    /**
     * Get the creation time and data for the data file we are iterating over
     *
     * @return data file creation date
     */
    @Override
    public Instant getDataFileCreationDate() {
        return metadata.getCreationDate();
    }

    /** toString for debugging */
    @Override
    public String toString() {
        return "DataFileIterator{" +
                "metadata=" + metadata +
                ", key=" + key +
                '}';
    }
}
