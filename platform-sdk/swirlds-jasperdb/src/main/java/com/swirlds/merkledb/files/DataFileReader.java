/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.swirlds.merkledb.collections.IndexedObject;
import com.swirlds.merkledb.serialize.DataItemHeader;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import com.swirlds.merkledb.utilities.MerkleDbFileUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The aim for a DataFileReader is to facilitate fast highly concurrent random reading of items from
 * a data file. It is designed to be used concurrently from many threads.
 *
 * @param <D> Data item type
 */
@SuppressWarnings({"DuplicatedCode", "NullableProblems"})
public final class DataFileReader<D> implements AutoCloseable, Comparable<DataFileReader<D>>, IndexedObject {
    /** FileChannel's for each thread */
    private static final ThreadLocal<ByteBuffer> BUFFER_CACHE = new ThreadLocal<>();
    /** Max number of file channels to use for reading */
    private static final int MAX_FILE_CHANNELS = 8;
    /**
     * When a data file reader is created, a single file channel is open to read data from the
     * file. This channel is used by all threads. Number of threads currently reading data is
     * tracked in {@link #fileChannelsInUse}. When the number of threads per opened file channel
     * exceeds this threshold, a new file channel is open, unless there are {@link #MAX_FILE_CHANNELS}
     * channels are already opened.
     */
    private static final int THREADS_PER_FILECHANNEL = 8;
    /**
     * A single data file reader may use multiple file channels. Previously, a single file channel
     * was used, and it resulted in unnecessary locking in FileChannelImpl.readInternal(), when
     * the number of threads working with the channel in parallel was high. Now a single file
     * channel is open in the constructor, and additioinal file channels up to {@link #MAX_FILE_CHANNELS}
     * are opened as needed
     */
    private final AtomicReferenceArray<FileChannel> fileChannels = new AtomicReferenceArray<>(MAX_FILE_CHANNELS);
    /** Number of currently opened file channels */
    private final AtomicInteger fileChannelsCount = new AtomicInteger(0);
    /** Number of file channels currently in use by all threads working with this data file reader */
    private final AtomicInteger fileChannelsInUse = new AtomicInteger(0);
    /** The path to the file on disk */
    private final Path path;
    /** The metadata for this file read from the footer */
    private final DataFileMetadata metadata;
    /** Serializer for converting raw data to/from data items */
    private final DataItemSerializer<D> dataItemSerializer;
    /** A flag for if the underlying file is fully written and ready to be compacted. */
    private final AtomicBoolean fileCompleted = new AtomicBoolean(false);
    /**
     * The size of this file in bytes, cached as need it often. This size is updated in {@link
     * #setFileCompleted()}, which is called for existing files right after the reader is created,
     * and for newly created files right after they are fully written and available to compact.
     */
    private final AtomicLong fileSizeBytes = new AtomicLong(0);
    /**
     * Open an existing data file, reading the metadata from the file
     *
     * @param path the path to the data file
     * @param dataItemSerializer Serializer for converting raw data to/from data items
     */
    public DataFileReader(final Path path, final DataItemSerializer<D> dataItemSerializer) throws IOException {
        this(path, dataItemSerializer, new DataFileMetadata(path));
    }

    /**
     * Open an existing data file, using the provided metadata
     *
     * @param path the path to the data file
     * @param dataItemSerializer Serializer for converting raw data to/from data items
     * @param metadata the file's metadata to save loading from file
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
        openNewFileChannel(0);
    }

    /**
     * Returns if this file is completed and ready to be compacted.
     *
     * @return if true the file is completed (read only and ready to compact)
     */
    public boolean isFileCompleted() {
        return fileCompleted.get();
    }

    /**
     * Marks the reader as completed, so it can be included into future compactions. If the reader
     * is created for an existing file, it's usually marked as completed immediately. If the reader
     * is created for a new file, which is still being written in a different thread, it's marked as
     * completed right after the file is fully written and the writer is closed.
     */
    public void setFileCompleted() {
        try {
            fileSizeBytes.set(fileChannels.get(0).size());
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to update data file reader size", e);
        } finally {
            fileCompleted.set(true);
        }
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

    /** Get the files metadata */
    public DataFileMetadata getMetadata() {
        return metadata;
    }

    /** Get the path to this data file */
    public Path getPath() {
        return path;
    }

    /**
     * Create an iterator to iterate over the data items in this data file. It opens its own file
     * handle so can be used in a separate thread. It must therefore be closed when you are finished
     * with it.
     *
     * @return new data item iterator
     * @throws IOException if there was a problem creating a new DataFileIterator
     */
    public DataFileIterator createIterator() throws IOException {
        return new DataFileIterator(path, metadata, dataItemSerializer);
    }

    /**
     * Read a data item from file at dataLocation.
     *
     * @param dataLocation The file index combined with the offset for the starting block of the
     *     data in the file
     * @throws IOException If there was a problem reading from data file
     * @throws ClosedChannelException if the data file was closed
     */
    public D readDataItem(final long dataLocation) throws IOException {
        return readDataItem(dataLocation, true);
    }

    /**
     * Read data item bytes from file at dataLocation and deserialize them into the Java object, if
     * requested.
     *
     * @param dataLocation The file index combined with the offset for the starting block of the
     *     data in the file
     * @param deserialize A flag to avoid deserialization cost
     * @return Deserialized data item, or {@code null} if deserialization is not requested
     * @throws IOException If there was a problem reading from data file
     * @throws ClosedChannelException if the data file was closed
     */
    public D readDataItem(final long dataLocation, final boolean deserialize) throws IOException {
        final ByteBuffer data = readDataItemBytes(dataLocation);
        return deserialize ? dataItemSerializer.deserialize(data, metadata.getSerializationVersion()) : null;
    }

    public ByteBuffer readDataItemBytes(final long dataLocation) throws IOException {
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
        return read(byteOffset, bytesToRead);
    }

    /**
     * Get the size of this file in bytes. This method should only be called for files available to
     * merging (compaction), i.e. after they are fully written.
     *
     * @return file size in bytes
     */
    public long getSize() {
        return fileSizeBytes.get();
    }

    /** Equals for use when comparing in collections, based on matching file paths */
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

    /** hashCode for use when comparing in collections, based on file path */
    @Override
    public int hashCode() {
        return path.hashCode();
    }

    /** Compares this Data File to another based on creation date and index */
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
        final FileChannel fileChannel = fileChannels.get(0);
        return fileChannel != null && fileChannel.isOpen();
    }

    /** Close this data file, it can not be used once closed. */
    public void close() throws IOException {
        for (int i = 0; i < MAX_FILE_CHANNELS; i++) {
            final FileChannel fileChannel = fileChannels.getAndSet(i, null);
            if (fileChannel != null) {
                fileChannel.close();
            }
        }
    }

    // =================================================================================================================
    // Private methods

    /**
     * Opens a new file channel for reading the file, if the total number of channels opened is
     * less than {@link #MAX_FILE_CHANNELS}. This method is safe to call from multiple threads.
     *
     * @param newFileChannelIndex Index of the new file channel. If greater or equal to {@link}
     *                            #MAX_FILE_CHANNELS}, no new channel is opened
     * @throws IOException
     *      If an I/O error occurs
     */
    private void openNewFileChannel(final int newFileChannelIndex) throws IOException {
        if (newFileChannelIndex >= MAX_FILE_CHANNELS) {
            return;
        }
        final FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ);
        if (fileChannels.compareAndSet(newFileChannelIndex, null, fileChannel)) {
            fileChannelsCount.incrementAndGet();
        } else {
            fileChannel.close();
        }
    }

    /**
     * Returns one of the opened file channels for reading data. Opens a new file channel, if
     * possible, when the number of file channels currently in use is much greater than the number
     * of opened file channels.
     *
     * @return A file channel to read data
     * @throws IOException
     *      If an I/O exception occurs
     */
    private FileChannel getFileChannel() throws IOException {
        int count = fileChannelsCount.get();
        final int inUse = fileChannelsInUse.incrementAndGet();
        // Although openNewFileChannel() is thread safe, it makes sense to check the count here.
        // Since the channels are never closed (other than when the data file reader is closed),
        // it's safe to check count against MAX_FC
        if ((inUse / count > THREADS_PER_FILECHANNEL) && (count < MAX_FILE_CHANNELS)) {
            openNewFileChannel(count);
            count = fileChannelsCount.get();
        }
        return fileChannels.get(inUse % count);
    }

    /**
     * Decreases the number of opened file channels in use by one.
     */
    private void releaseFileChannel() {
        fileChannelsInUse.decrementAndGet();
    }

    /**
     * Read bytesToRead bytes of data from the file starting at byteOffsetInFile unless we reach the
     * end of file. If we reach the end of file then returned buffer's limit will be set to the
     * number of bytes read and be less than bytesToRead.
     *
     * @param byteOffsetInFile Offset to start reading at
     * @param bytesToRead Number of bytes to read
     * @return ByteBuffer containing read data. This is a reused per thread buffer, so you can use
     *     it till your thread calls read again.
     * @throws IOException if there was a problem reading
     * @throws ClosedChannelException if the file was closed
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
        final FileChannel fileChannel = getFileChannel();
        try {
            // read data
            MerkleDbFileUtils.completelyRead(fileChannel, buffer, byteOffsetInFile);
        } finally {
            releaseFileChannel();
        }
        buffer.flip();
        return buffer;
    }
}
