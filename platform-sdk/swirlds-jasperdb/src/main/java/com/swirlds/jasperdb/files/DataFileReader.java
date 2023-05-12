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

package com.swirlds.jasperdb.files;

import com.swirlds.jasperdb.collections.IndexedObject;
import com.swirlds.jasperdb.utilities.JasperDBFileUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The aim for a DataFileReader is to facilitate fast highly concurrent random reading of items from a data file. It is
 * designed to be used concurrently from many threads.
 *
 * @param <D>
 * 		Data item type
 */
@SuppressWarnings({"DuplicatedCode", "NullableProblems"})
public final class DataFileReader<D> implements AutoCloseable, Comparable<DataFileReader<D>>, IndexedObject {
    /** FileChannel's for each thread */
    private static final ThreadLocal<ByteBuffer> BUFFER_CACHE = new ThreadLocal<>();
    /** FileChannel's for each thread */
    private final FileChannel fileChannel;
    /** The path to the file on disk */
    private final Path path;
    /** The metadata for this file read from the footer */
    private final DataFileMetadata metadata;
    /** Serializer for converting raw data to/from data items */
    private final DataItemSerializer<D> dataItemSerializer;
    /**
     * Flag for if this file can be included in merging, it defaults to false and has to be set to true by file writing
     * and merging after updating any indexes. But the file can still be read from during that time.
     */
    private final AtomicBoolean fileAvailableForMerging = new AtomicBoolean(false);
    /** The size of this file in bytes, cached as need it often, and it's constant as file is immutable. */
    private final long fileSizeBytes;

    /**
     * Open an existing data file, reading the metadata from the file
     *
     * @param path
     * 		the path to the data file
     * @param dataItemSerializer
     * 		Serializer for converting raw data to/from data items
     */
    public DataFileReader(final Path path, final DataItemSerializer<D> dataItemSerializer) throws IOException {
        this(path, dataItemSerializer, new DataFileMetadata(path));
    }

    /**
     * Open an existing data file, using the provided metadata
     *
     * @param path
     * 		the path to the data file
     * @param dataItemSerializer
     * 		Serializer for converting raw data to/from data items
     * @param metadata
     * 		the file's metadata to save loading from file
     */
    public DataFileReader(
            final Path path, final DataItemSerializer<D> dataItemSerializer, final DataFileMetadata metadata)
            throws IOException {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException(
                    "Tried to open a non existent data file [" + path.toAbsolutePath() + "].");
        }
        this.path = path;
        this.metadata = metadata;
        this.dataItemSerializer = dataItemSerializer;
        this.fileChannel = FileChannel.open(path, StandardOpenOption.READ);
        this.fileSizeBytes = this.fileChannel.size();
    }

    /**
     * Get fileAvailableForMerging a flag for if this file can be included in merging.
     *
     * @return if true the file can be included in merging, false then it can not be included
     */
    public boolean getFileAvailableForMerging() {
        return fileAvailableForMerging.get();
    }

    /**
     * Set fileAvailableForMerging a flag for if this file can be included in merging.
     *
     * @param newValue
     * 		When true the file can be included in merging, false then it can not be included
     */
    public void setFileAvailableForMerging(boolean newValue) {
        fileAvailableForMerging.set(newValue);
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
    public DataFileMetadata getMetadata() {
        return metadata;
    }

    /**
     * Get the path to this data file
     */
    public Path getPath() {
        return path;
    }

    /**
     * Create an iterator to iterate over the data items in this data file. It opens its own file handle so can be used
     * in a separate thread. It must therefore be closed when you are finished with it.
     *
     * @return new data item iterator
     * @throws IOException
     * 		if there was a problem creating a new DataFileIterator
     */
    public DataFileIterator createIterator() throws IOException {
        return new DataFileIterator(path, metadata, dataItemSerializer);
    }

    /**
     * Read a data item from file at dataLocation.
     *
     * @param dataLocation
     * 		The file index combined with the offset for the starting block of the data in the file.
     * @throws IOException
     * 		If there was a problem reading from data file
     * @throws ClosedChannelException
     * 		if the data file was closed
     */
    public D readDataItem(long dataLocation) throws IOException {
        return readDataItem(dataLocation, true);
    }

    /**
     * Read a data item from file at dataLocation.
     *
     * @param dataLocation
     * 		The file index combined with the offset for the starting block of the data in the file.
     * @param deserialize
     *      flag to avoid deserialization cost
     * @return deserialized data item, always returns null if deserialize == false
     *
     * @throws IOException
     * 		If there was a problem reading from data file
     * @throws ClosedChannelException
     * 		if the data file was closed
     */
    public D readDataItem(final long dataLocation, final boolean deserialize) throws IOException {
        final long byteOffset = DataFileCommon.byteOffsetFromDataLocation(dataLocation);
        final int bytesToRead;
        if (dataItemSerializer.isVariableSize()) {
            // read header to get size
            final ByteBuffer serializedHeader = read(byteOffset, dataItemSerializer.getHeaderSize());
            final DataItemHeader header = dataItemSerializer.deserializeHeader(serializedHeader);
            bytesToRead = header.getSizeBytes();
        } else {
            bytesToRead = dataItemSerializer.getSerializedSize();
        }

        final ByteBuffer data = read(byteOffset, bytesToRead);
        return deserialize ? dataItemSerializer.deserialize(data, metadata.getSerializationVersion()) : null;
    }

    /**
     * Get the size of this file in bytes
     *
     * @return file size in bytes
     */
    public long getSize() {
        return fileSizeBytes;
    }

    /**
     * Equals for use when comparing in collections, based on matching file paths
     */
    @SuppressWarnings("rawtypes")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataFileReader that = (DataFileReader) o;
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
     * Compares this Data File to another based on creation date and index
     */
    @Override
    public int compareTo(final DataFileReader o) {
        Objects.requireNonNull(o);
        if (this == o) {
            return 0;
        }
        final int res = metadata.getCreationDate().compareTo(o.getMetadata().getCreationDate());
        return (res != 0)
                ? res
                : Integer.compare(metadata.getIndex(), o.getMetadata().getIndex());
    }

    /** ToString for debugging */
    @Override
    public String toString() {
        return Integer.toString(metadata.getIndex());
    }

    /**
     * Get if the DataFile is open for reading.
     *
     * @return True if file is open for reading
     */
    public boolean isOpen() {
        return fileChannel != null && fileChannel.isOpen();
    }

    /**
     * Close this data file, it can not be used once closed.
     */
    public void close() throws IOException {
        fileChannel.close();
    }
    // =================================================================================================================

    // Private methods

    /**
     * Read bytesToRead bytes of data from the file starting at byteOffsetInFile unless we reach the end of file. If we
     * reach the end of file then returned buffer's limit will be set to the number of bytes read and be less than
     * bytesToRead.
     *
     * @param byteOffsetInFile
     * 		Offset to start reading at
     * @param bytesToRead
     * 		Number of bytes to read
     * @return ByteBuffer containing read data. This is a reused per thread buffer, so you can use it till your thread
     * 		calls read again.
     * @throws IOException
     * 		if there was a problem reading
     * @throws ClosedChannelException
     * 		if the file was closed
     */
    private ByteBuffer read(final long byteOffsetInFile, final int bytesToRead) throws IOException {
        // get or create cached buffer
        ByteBuffer buffer = BUFFER_CACHE.get();
        if (buffer == null || bytesToRead > buffer.capacity()) {
            buffer = ByteBuffer.allocate(bytesToRead);
            BUFFER_CACHE.set(buffer);
        }
        buffer.position(0);
        buffer.limit(bytesToRead);
        // read data
        JasperDBFileUtils.completelyRead(fileChannel, buffer, byteOffsetInFile);
        buffer.flip();
        return buffer;
    }
}
