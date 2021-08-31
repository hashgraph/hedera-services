package com.hedera.services.state.jasperdb.files;

import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.Exchanger;

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
public final class DataFileIteratorDoubleBuffered implements DataFileIterator {
    private final InputStream inputStream;
    private final DataFileMetadata metadata;
    private final boolean hasVariableSizedData;
    private ByteBuffer dataItemBuffer;
    private int currentValueSizeBytes;
    private int currentDataItemSizeBytes;
    private boolean currentItemsValueHasBeenRead = false;
    private long currentDataItem = -1;
    private long currentDataItemByteOffset = 0;
    private long nextDataItemByteOffset = 0;
    private long key;

    private final Exchanger<Buffer> bufferExchanger = new Exchanger<>();
    private final ThreadGroup readingThreadsGroup = new ThreadGroup("DoubleBufferedInputStream Readers");
    private Buffer currentBuffer = null;


    /**
     * Create a new DataFileIterator on a existing file.
     *
     * @param path The path to the file to read.
     * @param metadata The metadata read from the file.
     */
    public DataFileIteratorDoubleBuffered(Path path, DataFileMetadata metadata) {
        try {
            this.metadata = metadata;
            this.hasVariableSizedData = metadata.hasVariableSizeData();
            if (!this.hasVariableSizedData) {
                this.currentValueSizeBytes = metadata.getDataItemValueSize();
                this.currentDataItemSizeBytes = KEY_SIZE + this.currentValueSizeBytes;
            }
            this.inputStream = Files.newInputStream(path, StandardOpenOption.READ);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // create background thread to read ahead and keep a full buffer read for us to read from
        Thread readingThread = new Thread(readingThreadsGroup, "DoubleBufferedInputStream Reader") {
            public void run() {
                try {
                    // fill first buffer
                    Buffer buffer = new Buffer();
                    buffer.fill(inputStream);
                    boolean bufferIsEndOfFile = buffer.isEndOfFile();
                    while (true) {
                        // swap with reader
                        buffer = bufferExchanger.exchange(buffer);
                        // check if there is more to read
                        if (bufferIsEndOfFile) break;
                        // create a second buffer if we got null back
                        if (buffer == null) buffer = new Buffer();
                        // fill the buffer
                        buffer.fill(inputStream);
                        bufferIsEndOfFile = buffer.isEndOfFile();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        readingThread.setDaemon(true);
        readingThread.start();
    }

    /**
     * Close file reader
     * 
     * @throws IOException – if this resource cannot be closed
     */
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
        // if we are reading a file with variableSized data we need to read the item size first
        if (hasVariableSizedData) {
            currentValueSizeBytes = readInt();
            currentDataItemSizeBytes = SIZE_OF_DATA_ITEM_SIZE_IN_FILE + KEY_SIZE + readInt();
        }
        currentItemsValueHasBeenRead = false;
        // read key
        key = readLong();
        // increment dataItem and current byte position
        currentDataItemByteOffset = nextDataItemByteOffset;
        nextDataItemByteOffset += currentDataItemSizeBytes;
        currentDataItem++;
        return true;
    }

    /**
     * Write the complete data for the current item to a DataFileWriter. That includes key, size(optional) and value.
     * Either this method or writeItemData can be used not both on a single item.
     *
     * @param writer The writer to write out to
     * @return data location the item was written to
     * @throws IOException if there was a problem writing the item
     */
    public long writeItemData(DataFileWriter writer) throws IOException {
        SerializableDataOutputStream out = writer.startStreamingItem(key,currentValueSizeBytes);
        int remainingBytesToRead = currentValueSizeBytes;
        while (remainingBytesToRead > 0) {
            remainingBytesToRead -= getCurrentBuffer().read(currentValueSizeBytes, out);
        }
        return writer.endStreamingItem();
    }

    /**
     * Get current dataItems data. Either this method or writeItemData can be used not both on a single item.
     *
     * @return ByteBuffer containing the key and value data
     */
    public ByteBuffer getDataItemData() {
        if (currentItemsValueHasBeenRead) {
            dataItemBuffer.rewind();
        } else {
            // check dataItem buffer is big enough
            if (dataItemBuffer == null || dataItemBuffer.capacity() < currentDataItemSizeBytes) {
                dataItemBuffer = ByteBuffer.allocate(currentDataItemSizeBytes);
            } else {
                dataItemBuffer.clear();
            }
            // put header back into data buffer so data buffer contains complete item block
            if (hasVariableSizedData) dataItemBuffer.putInt(currentValueSizeBytes);
            dataItemBuffer.putLong(key);
            // read dataItemValue from file
            int remainingBytesToRead = currentValueSizeBytes;
            while (remainingBytesToRead > 0) {
                remainingBytesToRead -= getCurrentBuffer().read(currentValueSizeBytes, dataItemBuffer);
            }
            currentItemsValueHasBeenRead = true;
            dataItemBuffer.flip();
        }
        return dataItemBuffer;
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
        return key;
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
                "metadata=" + metadata +
                ", key=" + key +
                '}';
    }

    // =================================================================================================================
    // Private Methods

    /**
     * Get the current buffer, refilling it if it has all been read.
     *
     * @return the current buffer to read from
     */
    private Buffer getCurrentBuffer() {
        if (currentBuffer == null || currentBuffer.isFullyRead()) {
            try {
                currentBuffer = bufferExchanger.exchange(currentBuffer);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return currentBuffer;
    }

    /**
     * Read an int from the current buffer
     *
     * @return the read int
     * @throws IOException If there was a problem reading, like end of file
     */
    private int readInt() throws IOException {
        final byte b1 = getCurrentBuffer().read();
        final byte b2 = getCurrentBuffer().read();
        final byte b3 = getCurrentBuffer().read();
        final byte b4 = getCurrentBuffer().read();
        return (b1 << 24) + (b2 << 16) + (b3 << 8) + b4;
    }

    /**
     * Read a long from the current buffer
     *
     * @return the read long
     * @throws IOException If there was a problem reading, like end of file
     */
    private long readLong() throws IOException {
        final byte b1 = getCurrentBuffer().read();
        final byte b2 = getCurrentBuffer().read();
        final byte b3 = getCurrentBuffer().read();
        final byte b4 = getCurrentBuffer().read();
        final byte b5 = getCurrentBuffer().read();
        final byte b6 = getCurrentBuffer().read();
        final byte b7 = getCurrentBuffer().read();
        final byte b8 = getCurrentBuffer().read();
        return  ((long)b1 << 56) +
                ((long)(b2 & 255) << 48) +
                ((long)(b3 & 255) << 40) +
                ((long)(b4 & 255) << 32) +
                ((long)(b5 & 255) << 24) +
                ((b6 & 255) << 16) +
                ((b7 & 255) <<  8) +
                (b8 & 255);
    }


    // =================================================================================================================
    // Buffer Class

    /**
     * A Buffer, we have two of these, one being filled by a background thread and the other being read from.
     */
    private static final class Buffer {
        private final byte[] buffer = new byte[1024*1024];
        private int contentSize;
        private int position = 0;

        /**
         * Fill this buffer from a input streams data
         *
         * @param in the stream to read from
         * @throws IOException If there was a problem reading
         */
        private void fill(InputStream in) throws IOException {
            contentSize = in.read(buffer);
            position = 0;
        }

        /**
         * Is this buffer empty because end of file has been reached.
         *
         * @return true if there is no data in this buffer
         */
        private boolean isEndOfFile() { return contentSize < buffer.length; }

        /**
         * Has all data in this buffer been read.
         *
         * @return true if all data has been read using read methods
         */
        private boolean isFullyRead() { return position >= contentSize-1; }

        private byte read() throws EOFException {
            if (isFullyRead()) throw new EOFException("End of buffer reached");
            return buffer[++position];
        }

        /**
         * Read maximumNumBytesToRead bytes from buffer or remaining bytes which ever is less
         *
         * @param maximumNumBytesToRead the maximum number of bytes to read from buffer into ByteBuffer
         * @param bufferToReadInto the bytebuffer to read data into
         * @return the number of bytes read, will be maximumNumBytesToRead if there was enough data available or some
         *          value less than maximumNumBytesToRead
         */
        private int read(int maximumNumBytesToRead, ByteBuffer bufferToReadInto) {
            final int remaining = contentSize - position;
            final int bytesToRead = Math.min(remaining,maximumNumBytesToRead);
            bufferToReadInto.put(buffer,position,bytesToRead);
            position += bytesToRead;
            return bytesToRead;
        }

        /**
         * Read maximumNumBytesToRead bytes from buffer or remaining bytes which ever is less
         *
         * @param maximumNumBytesToRead the maximum number of bytes to read from buffer into ByteBuffer
         * @param outputStreamToReadInto the OutputStream to read data into
         * @return the number of bytes read, will be maximumNumBytesToRead if there was enough data available or some
         *          value less than maximumNumBytesToRead
         * @throws IOException if there was a problem writing into the output stream.
         */
        private int read(int maximumNumBytesToRead, SerializableDataOutputStream outputStreamToReadInto) throws IOException {
            final int remaining = contentSize - position;
            final int bytesToRead = Math.min(remaining,maximumNumBytesToRead);
            outputStreamToReadInto.write(buffer,position,bytesToRead);
            position += bytesToRead;
            return bytesToRead;
        }
    }
}
