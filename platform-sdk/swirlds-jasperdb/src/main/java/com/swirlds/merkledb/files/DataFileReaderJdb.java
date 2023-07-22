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

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.merkledb.serialize.DataItemHeader;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import com.swirlds.merkledb.utilities.MerkleDbFileUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The aim for a DataFileReader is to facilitate fast highly concurrent random reading of items from
 * a data file. It is designed to be used concurrently from many threads.
 *
 * @param <D> Data item type
 */
@SuppressWarnings({"DuplicatedCode", "NullableProblems"})
// Future work: drop this class after all files are migrated to protobuf format
public class DataFileReaderJdb<D> extends DataFileReaderPbj<D> {

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

    /**
     * Open an existing data file, reading the metadata from the file
     *
     * @param path the path to the data file
     * @param dataItemSerializer Serializer for converting raw data to/from data items
     */
    public DataFileReaderJdb(final Path path, final DataItemSerializer<D> dataItemSerializer) throws IOException {
        this(path, dataItemSerializer, new DataFileMetadataJdb(path));
    }

    /**
     * Open an existing data file, using the provided metadata
     *
     * @param path the path to the data file
     * @param dataItemSerializer Serializer for converting raw data to/from data items
     * @param metadata the file's metadata to save loading from file
     */
    public DataFileReaderJdb(final Path path, final DataItemSerializer<D> dataItemSerializer,
            DataFileMetadataJdb metadata) throws IOException {
        super(path, dataItemSerializer, metadata);
        openNewFileChannel(0);
    }

    @Override
    public DataFileType getFileType() {
        return DataFileType.JDB;
    }

    /**
     * Create an iterator to iterate over the data items in this data file. It opens its own file
     * handle so can be used in a separate thread. It must therefore be closed when you are finished
     * with it.
     *
     * @return new data item iterator
     */
    public DataFileIterator createIterator() throws IOException {
        return new DataFileIteratorJdb(path, metadata, dataItemSerializer);
    }

    /**
     * Read data item bytes from file at dataLocation and deserialize them into the Java object, if
     * requested.
     *
     * @param dataLocation The file index combined with the offset for the starting block of the
     *     data in the file
     * @return Deserialized data item, or {@code null} if deserialization is not requested
     * @throws IOException If there was a problem reading from data file
     * @throws ClosedChannelException if the data file was closed
     */
    public D readDataItem(final long dataLocation) throws IOException {
        long serializationVersion = metadata.getSerializationVersion();
        final ByteBuffer data = (ByteBuffer) readDataItemBytes(dataLocation);
        return dataItemSerializer.deserialize(data, serializationVersion);
    }

    @Override
    public Object readDataItemBytes(final long dataLocation) throws IOException {
        long serializationVersion = metadata.getSerializationVersion();
        final long byteOffset = DataFileCommon.byteOffsetFromDataLocation(dataLocation);
        final int bytesToRead;
        if (dataItemSerializer.isVariableSize()) {
            // read header to get size
            final ByteBuffer serializedHeader = read(byteOffset, dataItemSerializer.getHeaderSize());
            final DataItemHeader header = dataItemSerializer.deserializeHeader(serializedHeader);
            bytesToRead = header.getSizeBytes();
        } else {
            bytesToRead = dataItemSerializer.getSerializedSizeForVersion(serializationVersion);
        }
        return read(byteOffset, bytesToRead);
    }

    /** Close this data file, it can not be used once closed. */
    public void close() throws IOException {
        super.close();
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
     * @param index Index of the new file channel. If greater or equal to {@link
     *                            #MAX_FILE_CHANNELS}, no new channel is opened
     * @throws IOException
     *      If an I/O error occurs
     */
    private void openNewFileChannel(final int index) throws IOException {
        if (index >= MAX_FILE_CHANNELS) {
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
    private void reopenFileChannel(final int index, final FileChannel closedChannel) throws IOException {
        assert index < MAX_FILE_CHANNELS;
        // May be closedChannel or may be already reopened in a different thread
        assert fileChannels.get(index) != null;
        assert !closedChannel.isOpen();
        final FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ);
        if (!fileChannels.compareAndSet(index, closedChannel, fileChannel)) {
            fileChannel.close();
        }
    }

    /**
     * Returns an index of an opened file channel to read data. Opens a new file channel, if
     * possible, when the number of file channels currently in use is much greater than the number
     * of opened file channels.
     *
     * @return An index of a file channel to read data
     * @throws IOException
     *      If an I/O exception occurs
     */
    private int leaseFileChannel() throws IOException {
        int count = fileChannelsCount.get();
        final int inUse = fileChannelsInUse.incrementAndGet();
        // Although openNewFileChannel() is thread safe, it makes sense to check the count here.
        // Since the channels are never closed (other than when the data file reader is closed),
        // it's safe to check count against MAX_FILE_CHANNELS
        if ((inUse / count > THREADS_PER_FILECHANNEL) && (count < MAX_FILE_CHANNELS)) {
            openNewFileChannel(count);
            count = fileChannelsCount.get();
        }
        return inUse % count;
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
        // Try a few times. It's very unlikely (other than in tests) that a thread is
        // interrupted more than once in short period of time, so 2 retries should be enough
        for (int retries = 2; retries > 0; retries--) {
            final int fcIndex = leaseFileChannel();
            final FileChannel fileChannel = fileChannels.get(fcIndex);
            try {
                buffer.position(0);
                buffer.limit(bytesToRead);
                // read data
                MerkleDbFileUtils.completelyRead(fileChannel, buffer, byteOffsetInFile);
                buffer.flip();
                return buffer;
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
        throw new IOException("Failed to read from file, file channels keep getting closed");
    }
}
