/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.merkledb.files;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import com.swirlds.merkledb.utilities.ProtoUtils;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;
import org.apache.commons.lang3.builder.ToStringBuilder;

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
    /**
     * Since {@code com.swirlds.platform.Browser} populates settings, and it is loaded before any
     * application classes that might instantiate a data source, the {@link ConfigurationHolder}
     * holder will have been configured by the time this static initializer runs.
     */
    private static final MerkleDbConfig config = ConfigurationHolder.getConfigData(MerkleDbConfig.class);

    /** Input stream this iterator is reading from */
    private final BufferedInputStream inputStream;
    /** Readable data on top of the input stream */
    private final ReadableSequentialData in;
    /** The file metadata read from the end of file */
    private final DataFileMetadata metadata;
    /** The path to the file we are iterating over */
    private final Path path;
    /** The serializer used for reading data from the file */
    private final DataItemSerializer dataItemSerializer;

    /** Buffer that is reused for reading each data item */
    private BufferedData dataItemBuffer;
    /** Index of current data item this iterator is reading, zero being the first item, -1 being before start */
    private long currentDataItem = -1;
    /** The offset in bytes from start of file to the beginning of the current item. */
    private long currentDataItemFilePosition = 0;
    /** True if this iterator has been closed */
    private boolean closed = false;

    /**
     * Create a new DataFileIterator on an existing file.
     *
     * @param path
     * 		The path to the file to read.
     * @param metadata
     * 		The metadata read from the file.
     * @throws IOException
     * 		if there was a problem creating a new InputStream on the file at path
     */
    public DataFileIterator(
            final Path path, final DataFileMetadata metadata, final DataItemSerializer dataItemSerializer)
            throws IOException {
        this.path = path;
        this.metadata = metadata;
        this.dataItemSerializer = dataItemSerializer;
        /* FUTURE WORK - https://github.com/swirlds/swirlds-platform/issues/3929 */
        this.inputStream = new BufferedInputStream(
                Files.newInputStream(path, StandardOpenOption.READ), config.iteratorInputBufferBytes());
        this.in = new ReadableStreamingData(inputStream);

        // Skip the header
        this.in.skip(metadata.getHeaderSize());
    }

    /**
     * Get the metadata for the file we are iterating over.
     *
     * @return File's metadata
     */
    public DataFileMetadata getMetadata() {
        return metadata;
    }

    /**
     * Close the file reader.
     *
     * @throws IOException
     * 		– if this resource cannot be closed
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            dataItemBuffer = null;
            inputStream.close();
        }
    }

    /**
     * Advance to the next dataItem.
     *
     * @return true if a dataItem was read or false if the end of the file has been reached.
     * @throws IOException
     * 		If there was a problem reading from file.
     */
    public boolean next() throws IOException {
        if (closed) {
            throw new IllegalStateException("Cannot read from a closed iterator");
        }

        // Have we reached the end?
        if (currentDataItem >= metadata.getDataItemCount() - 1) {
            dataItemBuffer = null;
            return false;
        }

        currentDataItemFilePosition = in.position();

        final int dataItemSize = ProtoUtils.getBytesSize(in, DataFileCommon.FIELD_DATAFILE_ITEMS);
        dataItemBuffer = fillBuffer(dataItemSize);

        currentDataItem++;
        return true;
    }

    /**
     * Get the current dataItems data. This is a shared buffer and must NOT be leaked from
     * the call site or modified directly.
     *
     * @return ByteBuffer containing the key and value data. This will return null if the iterator has
     * 		been closed, or if the iterator is in the before-first or after-last states.
     */
    public BufferedData getDataItemData() {
        return dataItemBuffer;
    }

    /**
     * Get the data location for current dataItem.
     *
     * @return file index and dataItem index combined location
     */
    public long getDataItemsDataLocation() {
        return DataFileCommon.dataLocation(metadata.getIndex(), currentDataItemFilePosition);
    }

    /**
     * Get current dataItems key.
     *
     * @return the key for current dataItem
     */
    public long getDataItemsKey() {
        return dataItemSerializer.extractKey(dataItemBuffer);
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
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .append("fileIndex", metadata.getIndex())
                .append("currentDataItemIndex", currentDataItem)
                .append("currentDataItemByteOffset", currentDataItemFilePosition)
                .append("fileName", path.getFileName())
                .append("metadata", metadata)
                .toString();
    }

    /**
     * Equals for use when comparing in collections, based on matching file paths and metadata
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataFileIterator that = (DataFileIterator) o;
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
     * Reads bytesToRead bytes from the current data item
     * @param bytesToRead bytes to read
     * @return ByteBuffer containing requested bytes
     * @throws IOException if request can not be completed
     */
    private BufferedData fillBuffer(int bytesToRead) throws IOException {
        if (bytesToRead <= 0) {
            throw new IOException("Malformed file [" + path + "], data item [" + currentDataItem
                    + "], requested bytes [" + bytesToRead + "]");
        }

        // Create or resize the buffer if necessary
        if (dataItemBuffer == null || dataItemBuffer.capacity() < bytesToRead) {
            resizeBuffer(bytesToRead);
        }

        dataItemBuffer.position(0);
        dataItemBuffer.limit(bytesToRead);
        in.readBytes(dataItemBuffer);

        dataItemBuffer.position(0);
        return dataItemBuffer;
    }

    /**
     * Resizes the dataItemBuffer, or creates it if necessary, such that it is large enough
     * to read the bytes provided.
     *
     * @param bytesToRead
     * 		Number of bytes to be able to fit into the buffer.
     */
    private void resizeBuffer(int bytesToRead) {
        dataItemBuffer = BufferedData.allocate(bytesToRead);
    }
}
