package com.hedera.services.state.jasperdb.files;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;

/**
 * Iterator interface for iterating over data items in a DataFile. It is designed to be used in a while(iter.next()){...}
 * loop and you can then read the data items info for current item with getDataItemsKey, getDataItemsDataLocation and
 * getDataItemData.
 *
 * It is designed to be used from a single thread.
 */
public interface DataFileIterator extends AutoCloseable {
    /**
     * Close file reader
     *
     * @throws IOException – if this resource cannot be closed
     */
    void close() throws IOException;

    /**
     * move on to next dataItem
     *
     * @return true if a dataItem was read or false if the end of the file has been reached.
     * @throws IOException If there was a problem reading from file.
     */
    boolean next() throws IOException;

    /**
     * Write the complete data for the current item to a DataFileWriter. That includes key, size(optional) and value.
     *
     * @param writer The writer to write out to
     * @return data location the item was written to
     * @throws IOException if there was a problem writing the item
     */
    long writeItemData(DataFileWriter writer) throws IOException;

    /**
     * Get current dataItems data
     *
     * @return ByteBuffer containing the key and value data
     */
    ByteBuffer getDataItemData();

    /**
     * Get the data location for current dataItem
     *
     * @return file index and dataItem index combined location
     */
    long getDataItemsDataLocation();

    /**
     * Get current dataItems key
     *
     * @return the key for current dataItem
     */
    long getDataItemsKey();

    /**
     * Get the creation time and data for the data file we are iterating over
     *
     * @return data file creation date
     */
    Instant getDataFileCreationDate();
}
