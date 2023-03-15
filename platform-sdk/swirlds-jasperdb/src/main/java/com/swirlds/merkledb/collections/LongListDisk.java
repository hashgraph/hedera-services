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

package com.swirlds.merkledb.collections;

import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.merkledb.utilities.MerkleDbFileUtils;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 *  A direct on disk implementation of LongList. This implementation creates a temporary file to store the data.
 *  If the user provides a file, it will be used to create a copy of the in the temporary file. If the file is absent,
 *  it will take the name of the file provided by the user and create a temporary file with this name.
 * <p>
 *  Unlike the "snapshot" file, the temporary files doesn't contain the header, only the body.
 *
 */
public class LongListDisk extends AbstractLongList<Long> {

    private static final String STORE_POSTFIX = "longListDisk";
    private static final String DEFAULT_FILE_NAME = "LongListDisk.ll";
    /** A temp byte buffer for reading and writing longs */
    private static final ThreadLocal<ByteBuffer> TEMP_LONG_BUFFER_THREAD_LOCAL;

    /** This file channel is to work with the temporary file. The field is effectively immutable, however it can't be
     * declared final because in some cases it has to be initialized in {@link LongListDisk#readBodyFromFileChannelOnInit}
     */
    private FileChannel currentFileChannel;

    /** A temp byte buffer for transferring data between file channels */
    private static final ThreadLocal<ByteBuffer> TRANSFER_BUFFER_THREAD_LOCAL;

    /**
     * Offsets of the chunks that are free to be used. The offsets are relative to the start of the file.
     */
    private final Deque<Long> freeChunks;

    static {
        TRANSFER_BUFFER_THREAD_LOCAL = new ThreadLocal<>();
        // it's initialized as 8 bytes (Long.BYTES) but likely it's going to be resized later
        TEMP_LONG_BUFFER_THREAD_LOCAL =
                ThreadLocal.withInitial(() -> ByteBuffer.allocate(Long.BYTES).order(ByteOrder.nativeOrder()));
    }

    public LongListDisk() {
        this(DEFAULT_NUM_LONGS_PER_CHUNK, DEFAULT_MAX_LONGS_TO_STORE, DEFAULT_RESERVED_BUFFER_LENGTH);
    }

    LongListDisk(final int numLongsPerChunk, final long maxLongs, final long reservedBufferLength) {
        super(numLongsPerChunk, maxLongs, reservedBufferLength);
        try {
            currentFileChannel = FileChannel.open(
                    createTempFile(DEFAULT_FILE_NAME),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        freeChunks = new ConcurrentLinkedDeque<>();
        fillBufferWithZeroes(initOrGetTransferBuffer());
    }

    /**
     * Create a {@link LongListDisk} on a file, if the file doesn't exist it will be created.
     *
     * @param file The file to read and write to
     * @throws IOException If there was a problem reading the file
     */
    public LongListDisk(final Path file) throws IOException {
        this(file, DEFAULT_RESERVED_BUFFER_LENGTH);
    }

    LongListDisk(final Path file, final long reservedBufferLength) throws IOException {
        super(file, reservedBufferLength);
        freeChunks = new ConcurrentLinkedDeque<>();
    }

    @Override
    protected void readBodyFromFileChannelOnInit(final String sourceFileName, final FileChannel fileChannel)
            throws IOException {
        Path tempFile = createTempFile(sourceFileName);
        // create temporary file for writing
        // the warning is suppressed because the file is not supposed to be closed
        // as this implementation uses a file channel from it.
        @SuppressWarnings("resource")
        final RandomAccessFile rf = new RandomAccessFile(tempFile.toFile(), "rw");
        // ensure that the amount of disk space is enough
        // two additional chunks are required to accommodate "compressed" first and last chunks in the original file
        rf.setLength(fileChannel.size() + (long) 2 * memoryChunkSize);
        currentFileChannel = rf.getChannel();

        final int totalNumberOfChunks = calculateNumberOfChunks(size());
        final int firstChunkWithDataIndex = (int) (minValidIndex.get() / numLongsPerChunk);
        final int minValidIndexInChunk = (int) (minValidIndex.get() % numLongsPerChunk);

        // copy the first chunk
        final ByteBuffer transferBuffer = initOrGetTransferBuffer();
        // we need to make sure that the chunk is written in full.
        // If a value is absent, the list element will have IMPERMISSIBLE_VALUE
        fillBufferWithZeroes(transferBuffer);
        transferBuffer.position(minValidIndexInChunk * Long.BYTES);
        MerkleDbFileUtils.completelyRead(fileChannel, transferBuffer);
        transferBuffer.flip();
        // writing the full chunk, all values before minValidIndexInChunk are zeroes
        MerkleDbFileUtils.completelyWrite(currentFileChannel, transferBuffer, 0);
        chunkList.set(firstChunkWithDataIndex, 0L);

        // copy everything except for the first chunk and the last chunk
        MerkleDbFileUtils.completelyTransferFrom(
                currentFileChannel, fileChannel, memoryChunkSize, (long) (totalNumberOfChunks - 2) * memoryChunkSize);

        // copy the last chunk
        transferBuffer.clear();
        MerkleDbFileUtils.completelyRead(fileChannel, transferBuffer);
        transferBuffer.flip();
        MerkleDbFileUtils.completelyWrite(
                currentFileChannel, transferBuffer, (long) (totalNumberOfChunks - 1) * memoryChunkSize);

        for (int i = firstChunkWithDataIndex + 1; i < totalNumberOfChunks; i++) {
            chunkList.set(i, (long) (i - firstChunkWithDataIndex) * memoryChunkSize);
        }
    }

    private static void fillBufferWithZeroes(ByteBuffer transferBuffer) {
        Arrays.fill(transferBuffer.array(), (byte) IMPERMISSIBLE_VALUE);
        transferBuffer.clear();
    }

    private ByteBuffer initOrGetTransferBuffer() {
        ByteBuffer buffer = TRANSFER_BUFFER_THREAD_LOCAL.get();
        if (buffer == null) {
            buffer = ByteBuffer.allocate(memoryChunkSize).order(ByteOrder.nativeOrder());
            TRANSFER_BUFFER_THREAD_LOCAL.set(buffer);
        } else {
            // clean up the buffer
            buffer.clear();
        }
        return buffer;
    }

    static Path createTempFile(final String sourceFileName) throws IOException {
        return TemporaryFileBuilder.buildTemporaryDirectory(STORE_POSTFIX).resolve(sourceFileName);
    }

    /**
     * Initializes the file channel to a temporary file.
     * @param path the path to the source file
     */
    @Override
    protected void onEmptyOrAbsentSourceFile(final Path path) throws IOException {
        Path tempFile = createTempFile(path.toFile().getName());
        currentFileChannel = FileChannel.open(
                tempFile, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
    }

    /** {@inheritDoc} */
    @Override
    protected synchronized void put(final Long chunk, final int subIndex, final long value) {
        try {
            final ByteBuffer buf = TEMP_LONG_BUFFER_THREAD_LOCAL.get();
            final long offset = chunk + (long) subIndex * Long.BYTES;
            // write new value to file
            buf.putLong(0, value);
            buf.position(0);
            MerkleDbFileUtils.completelyWrite(currentFileChannel, buf, offset);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected synchronized boolean putIfEqual(
            final Long chunk, final int subIndex, final long oldValue, long newValue) {
        final ByteBuffer buf = TEMP_LONG_BUFFER_THREAD_LOCAL.get();
        buf.position(0);
        try {
            final long offset = chunk + (long) subIndex * Long.BYTES;
            MerkleDbFileUtils.completelyRead(currentFileChannel, buf, offset);
            final long filesOldValue = buf.getLong(0);
            if (filesOldValue == oldValue) {
                // write new value to file
                buf.putLong(0, newValue);
                buf.position(0);
                MerkleDbFileUtils.completelyWrite(currentFileChannel, buf, offset);
                return true;
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return false;
    }

    /**
     * Calculate the offset in the chunk for the given index.
     * @param index the index to use
     * @return the offset in the chunk for the given index
     */
    private long calculateOffsetInChunk(long index) {
        return (index % numLongsPerChunk) * Long.BYTES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeLongsData(final FileChannel fc) throws IOException {
        final ByteBuffer transferBuffer = initOrGetTransferBuffer();
        final int totalNumOfChunks = calculateNumberOfChunks(size());
        final long currentMinValidIndex = minValidIndex.get();
        final int firstChunkWithDataIndex = (int) currentMinValidIndex / numLongsPerChunk;

        // The following logic sequentially processes chunks. This kind of processing allows to get rid of
        // non-contiguous memory allocation and gaps that may be present in the current file.
        // MerkleDbFileUtils.completelyTransferFrom would work faster, it wouldn't allow
        // the required rearrangement of data.
        for (int i = firstChunkWithDataIndex; i < totalNumOfChunks; i++) {
            Long currentChunkStartOffset = chunkList.get(i);
            // if the chunk is null, we write zeroes to the file. If not, we write the data from the chunk
            if (currentChunkStartOffset != null) {
                final long chunkOffset;
                if (i == firstChunkWithDataIndex) {
                    // writing starts from the first valid index in the first valid chunk
                    final int firstValidIndexInChunk = (int) currentMinValidIndex % numLongsPerChunk;
                    transferBuffer.position(firstValidIndexInChunk * Long.BYTES);
                    chunkOffset = currentChunkStartOffset + calculateOffsetInChunk(currentMinValidIndex);
                } else {
                    // writing the whole chunk
                    transferBuffer.position(0);
                    chunkOffset = currentChunkStartOffset;
                }
                if (i == (totalNumOfChunks - 1)) {
                    // the last array, so set limit to only the data needed
                    final long bytesWrittenSoFar = (long) memoryChunkSize * (long) i;
                    final long remainingBytes = (size() * Long.BYTES) - bytesWrittenSoFar;
                    transferBuffer.limit((int) remainingBytes);
                } else {
                    transferBuffer.limit(transferBuffer.capacity());
                }
                int currentPosition = transferBuffer.position();
                MerkleDbFileUtils.completelyRead(currentFileChannel, transferBuffer, chunkOffset);
                transferBuffer.position(currentPosition);
            } else {
                fillBufferWithZeroes(transferBuffer);
            }

            MerkleDbFileUtils.completelyWrite(fc, transferBuffer);
        }
    }

    /**
     * Lookup a long in data
     *
     * @param chunkOffset the index of the chunk the long is contained in
     * @param subIndex   The sub index of the long in that chunk
     * @return The stored long value at given index
     */
    @Override
    protected long lookupInChunk(final Long chunkOffset, final long subIndex) {
        try {
            final ByteBuffer buf = TEMP_LONG_BUFFER_THREAD_LOCAL.get();
            // if there is nothing to read the buffer will have the default value
            buf.putLong(0, IMPERMISSIBLE_VALUE);
            buf.clear();
            final long offset = chunkOffset + subIndex * Long.BYTES;
            MerkleDbFileUtils.completelyRead(currentFileChannel, buf, offset);
            return buf.getLong(0);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Closes the open file
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void onClose() throws IOException {
        // flush
        if (currentFileChannel.isOpen()) {
            currentFileChannel.force(false);
        }
        // now close
        currentFileChannel.close();
        freeChunks.clear();
    }

    @Override
    protected void releaseChunk(final Long chunk) {
        // if it's the last chunk, don't do any cleanup as it may be claimed by another threads
        if (chunk / numLongsPerChunk == getCurrentMax() / numLongsPerChunk) {
            return;
        }

        final ByteBuffer transferBuffer = initOrGetTransferBuffer();
        transferBuffer.clear();
        try {
            currentFileChannel.write(transferBuffer, chunk);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        freeChunks.add(chunk);
    }

    @Override
    protected void partialChunkCleanup(final int chunkIndex, final long entriesToCleanUp) {
        Long position = chunkList.get(chunkIndex);
        if (position == null) {
            // nothing to clean up
            return;
        }
        final ByteBuffer transferBuffer = initOrGetTransferBuffer();
        transferBuffer.limit((int) (entriesToCleanUp * Long.BYTES));
        try {
            currentFileChannel.write(transferBuffer, position);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected Long createChunk() {
        Long chunkOffset = freeChunks.poll();
        if (chunkOffset == null) {
            long maxOffset = -1;
            for (int i = 0; i < chunkList.length(); i++) {
                Long currentOffset = chunkList.get(i);
                maxOffset = Math.max(maxOffset, currentOffset == null ? -1 : currentOffset);
            }
            return maxOffset == -1 ? 0 : maxOffset + memoryChunkSize;
        } else {
            return chunkOffset;
        }
    }

    // exposed for test purposes only - DO NOT USE IN PROD CODE
    FileChannel getCurrentFileChannel() {
        return currentFileChannel;
    }

    // exposed for test purposes only - DO NOT USE IN PROD CODE
    void resetTransferBuffer() {
        TRANSFER_BUFFER_THREAD_LOCAL.remove();
    }
}
