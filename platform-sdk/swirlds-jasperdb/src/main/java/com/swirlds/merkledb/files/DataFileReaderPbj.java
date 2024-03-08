/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.pbj.runtime.ProtoParserTools.TAG_FIELD_OFFSET;
import static com.swirlds.merkledb.files.DataFileCommon.FIELD_DATAFILE_ITEMS;

import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import com.swirlds.merkledb.utilities.MerkleDbFileUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
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
 * <p>Protobuf schema:
 *
 * <p><pre>
 * message DataFile {
 *
 *     // File index in its file collection
 *     uint32 index = 1;
 *
 *     // Creation timestamp, seconds
 *     uint64 creationDateSeconds = 2;
 *
 *     // Creation timestamp, nanos
 *     uint32 creationDateNanos = 3;
 *
 *     // Items count
 *     uint64 itemsCount = 4;
 *
 *     // Data item version. May not be needed
 *     uint64 itemVersion = 5;
 *
 *     // Data items
 *     repeated bytes items = 11;
 * }
 * </pre>
 *
 * @param <D> Data item type
 */
// Future work: make it final again after DataFileReaderJdb is dropped
// https://github.com/hashgraph/hedera-services/issues/8344
public class DataFileReaderPbj<D> implements DataFileReader<D> {

    private static final ThreadLocal<ByteBuffer> BUFFER_CACHE = new ThreadLocal<>();
    private static final ThreadLocal<BufferedData> BUFFEREDDATA_CACHE = new ThreadLocal<>();

    protected final MerkleDbConfig dbConfig;

    /** Max number of file channels to use for reading */
    protected final int maxFileChannels;

    /**
     * When a data file reader is created, a single file channel is open to read data from the
     * file. This channel is used by all threads. Number of threads currently reading data is
     * tracked in {@link #fileChannelsInUse}. When the number of threads per opened file channel
     * exceeds this threshold, a new file channel is open, unless there are {@link #maxFileChannels}
     * channels are already opened.
     */
    protected final int threadsPerFileChannel;

    /**
     * A single data file reader may use multiple file channels. Previously, a single file channel
     * was used, and it resulted in unnecessary locking in FileChannelImpl.readInternal(), when
     * the number of threads working with the channel in parallel was high. Now a single file
     * channel is open in the constructor, and additioinal file channels up to {@link #maxFileChannels}
     * are opened as needed
     */
    protected final AtomicReferenceArray<FileChannel> fileChannels;

    /** Number of currently opened file channels */
    protected final AtomicInteger fileChannelsCount = new AtomicInteger(0);
    /** Number of file channels currently in use by all threads working with this data file reader */
    protected final AtomicInteger fileChannelsInUse = new AtomicInteger(0);

    /** Indicates whether this file reader is open */
    private final AtomicBoolean open = new AtomicBoolean(true);
    /** The path to the file on disk */
    // Future work: make it back private
    // https://github.com/hashgraph/hedera-services/issues/8344
    protected final Path path;
    /** The metadata for this file read from the footer */
    // Future work: make it back private
    // https://github.com/hashgraph/hedera-services/issues/8344
    protected final DataFileMetadata metadata;
    /** Serializer for converting raw data to/from data items */
    // Future work: make it back private
    // https://github.com/hashgraph/hedera-services/issues/8344
    protected final DataItemSerializer<D> dataItemSerializer;
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
     * @param dbConfig MerkleDb config
     * @param path the path to the data file
     * @param dataItemSerializer Serializer for converting raw data to/from data items
     */
    public DataFileReaderPbj(
            final MerkleDbConfig dbConfig, final Path path, final DataItemSerializer<D> dataItemSerializer)
            throws IOException {
        this(dbConfig, path, dataItemSerializer, new DataFileMetadata(path));
    }

    /**
     * Open an existing data file, using the provided metadata
     *
     * @param dbConfig MerkleDb config
     * @param path the path to the data file
     * @param dataItemSerializer Serializer for converting raw data to/from data items
     * @param metadata the file's metadata to save loading from file
     */
    public DataFileReaderPbj(
            final MerkleDbConfig dbConfig,
            final Path path,
            final DataItemSerializer<D> dataItemSerializer,
            final DataFileMetadata metadata)
            throws IOException {
        this.dbConfig = dbConfig;
        maxFileChannels = dbConfig.maxFileChannelsPerFileReader();
        threadsPerFileChannel = dbConfig.maxThreadsPerFileChannel();
        fileChannels = new AtomicReferenceArray<>(maxFileChannels);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException(
                    "Tried to open a non existent data file [" + path.toAbsolutePath() + "].");
        }
        this.path = path;
        this.metadata = metadata;
        this.dataItemSerializer = dataItemSerializer;
        openNewFileChannel(0);
    }

    @Override
    public DataFileType getFileType() {
        return DataFileType.PBJ;
    }

    @Override
    public boolean isFileCompleted() {
        return fileCompleted.get();
    }

    @Override
    public void setFileCompleted() {
        try {
            fileSizeBytes.set(fileChannels.get(0).size());
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to update data file reader size", e);
        } finally {
            fileCompleted.set(true);
        }
    }

    @Override
    public int getIndex() {
        return metadata.getIndex();
    }

    @Override
    public DataFileMetadata getMetadata() {
        return metadata;
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public DataFileIterator<D> createIterator() throws IOException {
        return new DataFileIteratorPbj<>(dbConfig, path, metadata, dataItemSerializer);
    }

    @Override
    public D readDataItem(final long dataLocation) throws IOException {
        final long byteOffset = DataFileCommon.byteOffsetFromDataLocation(dataLocation);
        final BufferedData data = read(byteOffset);
        return data != null ? dataItemSerializer.deserialize(data) : null;
    }

    @Override
    public Object readDataItemBytes(final long dataLocation) throws IOException {
        final long byteOffset = DataFileCommon.byteOffsetFromDataLocation(dataLocation);
        return read(byteOffset);
    }

    @Override
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
        return path.equals(that.getPath());
    }

    /** hashCode for use when comparing in collections, based on file path */
    @Override
    public int hashCode() {
        return path.hashCode();
    }

    /** Compares this Data File to another based on creation date and index */
    @Override
    public int compareTo(@NonNull final DataFileReader o) {
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

    // For testing purpose
    int getMaxFileChannels() {
        return maxFileChannels;
    }

    int getThreadsPerFileChannel() {
        return threadsPerFileChannel;
    }

    /**
     * Get if the DataFile is open for reading.
     *
     * @return True if file is open for reading
     */
    @Override
    public boolean isOpen() {
        return open.get();
    }

    @Override
    public void close() throws IOException {
        open.set(false);
        for (int i = 0; i < maxFileChannels; i++) {
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
     * less than {@link #maxFileChannels}. This method is safe to call from multiple threads.
     *
     * @param index Index of the new file channel. If greater or equal to {@link
     *                            #maxFileChannels}, no new channel is opened
     * @throws IOException
     *      If an I/O error occurs
     */
    protected void openNewFileChannel(final int index) throws IOException {
        if (index >= maxFileChannels) {
            return;
        }
        final FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ);
        if (fileChannels.compareAndSet(index, null, fileChannel)) {
            fileChannelsCount.incrementAndGet();
        } else {
            fileChannel.close();
        }
    }

    /**
     * Replaces a closed file channel at a given index in {@link #fileChannels} with a new one.
     * This method is safe to be called from multiple threads. If a channel is closed, and two
     * threads are calling this method to replace it with a new channel, only one of them will
     * proceed, while the channel opened in the other thread will be closed immediately and
     * not used any further.
     *
     * @param index File channel index in {@link #fileChannels}
     * @param closedChannel Closed file channel to replace
     * @throws IOException
     *      If an I/O error occurs
     */
    protected void reopenFileChannel(final int index, final FileChannel closedChannel) throws IOException {
        assert index < maxFileChannels;
        // May be closedChannel or may be already reopened in a different thread
        assert fileChannels.get(index) != null;
        assert !closedChannel.isOpen();
        final FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ);
        if (!fileChannels.compareAndSet(index, closedChannel, fileChannel)) {
            fileChannel.close();
        }
    }

    /**
     * Returns an index of an opened file channel to read data and increments the lease count.
     * Opens a new file channel, if possible, when the lease count per channel is greater than
     * {@link #threadsPerFileChannel}.
     *
     * @return An index of a file channel to read data
     * @throws IOException
     *      If an I/O exception occurs
     */
    protected int leaseFileChannel() throws IOException {
        int count = fileChannelsCount.get();
        final int inUse = fileChannelsInUse.incrementAndGet();
        // Although openNewFileChannel() is thread safe, it makes sense to check the count here.
        // Since the channels are never closed (other than when the data file reader is closed),
        // it's safe to check count against MAX_FILE_CHANNELS
        if ((inUse / count > threadsPerFileChannel) && (count < maxFileChannels)) {
            openNewFileChannel(count);
            count = fileChannelsCount.get();
        }
        return inUse % count;
    }

    /**
     * Decreases the number of opened file channels in use by one.
     */
    protected void releaseFileChannel() {
        fileChannelsInUse.decrementAndGet();
    }

    /**
     * Read bytesToRead bytes of data from the file starting at byteOffsetInFile unless we reach the
     * end of file. If we reach the end of file then returned buffer's limit will be set to the
     * number of bytes read and be less than bytesToRead.
     *
     * @param byteOffsetInFile Offset to start reading at
     * @return ByteBuffer containing read data. This is a reused per thread buffer, so you can use
     *     it till your thread calls read again.
     * @throws IOException if there was a problem reading
     * @throws ClosedChannelException if the file was closed
     */
    private BufferedData read(final long byteOffsetInFile) throws IOException {
        // Buffer size to read data item tag and size. If the whole item is small and
        // fits into this buffer, there is no need to make an extra file read
        final int PRE_READ_BUF_SIZE = 2048;
        ByteBuffer readBB = BUFFER_CACHE.get();
        BufferedData readBuf = BUFFEREDDATA_CACHE.get();
        if (readBuf == null) {
            readBB = ByteBuffer.allocate(PRE_READ_BUF_SIZE);
            BUFFER_CACHE.set(readBB);
            readBuf = BufferedData.wrap(readBB);
            BUFFEREDDATA_CACHE.set(readBuf);
        }
        // Try a few times. It's very unlikely (other than in tests) that a thread is
        // interrupted more than once in short period of time, so 3 retries should be enough
        for (int retries = 3; retries > 0; retries--) {
            final int fcIndex = leaseFileChannel();
            final FileChannel fileChannel = fileChannels.get(fcIndex);
            if (fileChannel == null) {
                // On rare occasions, if we have a race condition with compaction, the file channel
                // may be closed. We need to return null, so that the caller can retry with a new reader
                return null;
            }
            try {
                // Fill the byte buffer from the channel
                readBB.clear();
                readBB.limit(PRE_READ_BUF_SIZE); // No need to read more than that for a header
                MerkleDbFileUtils.completelyRead(fileChannel, readBB, byteOffsetInFile);
                // Then read the tag and size from the read buffer, since it's wrapped over the byte buffer
                readBuf.reset();
                final int tag = readBuf.getVarInt(0, false); // tag
                assert tag
                        == ((FIELD_DATAFILE_ITEMS.number() << TAG_FIELD_OFFSET)
                                | ProtoConstants.WIRE_TYPE_DELIMITED.ordinal());
                final int sizeOfTag = ProtoWriterTools.sizeOfUnsignedVarInt32(tag);
                final int size = readBuf.getVarInt(sizeOfTag, false);
                final int sizeOfSize = ProtoWriterTools.sizeOfUnsignedVarInt32(size);
                // Check if the whole data item is already read in the header
                if (PRE_READ_BUF_SIZE >= sizeOfTag + sizeOfSize + size) {
                    readBuf.position(sizeOfTag + sizeOfSize);
                    readBuf.limit(sizeOfTag + sizeOfSize + size);
                    return readBuf;
                }
                // Otherwise read it separately
                if (readBB.capacity() <= size) {
                    readBB = ByteBuffer.allocate(size);
                    BUFFER_CACHE.set(readBB);
                    readBuf = BufferedData.wrap(readBB);
                    BUFFEREDDATA_CACHE.set(readBuf);
                } else {
                    readBB.position(0);
                    readBB.limit(size);
                }
                final int bytesRead = MerkleDbFileUtils.completelyRead(
                        fileChannel, readBB, byteOffsetInFile + sizeOfTag + sizeOfSize);
                assert bytesRead == size : "Failed to read all data item bytes";
                readBuf.position(0);
                readBuf.limit(bytesRead);
                return readBuf;
            } catch (final ClosedByInterruptException e) {
                // If the thread and the channel are interrupted, propagate it to the callers
                throw e;
            } catch (final ClosedChannelException e) {
                // This exception may be thrown, if the channel was closed, because a different
                // thread reading from the channel was interrupted. Re-create the file channel
                // and retry
                reopenFileChannel(fcIndex, fileChannel);
            } finally {
                releaseFileChannel();
            }
        }
        throw new IOException("Failed to read from file, file channel keeps getting closed");
    }
}
