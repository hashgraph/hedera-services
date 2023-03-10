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
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *  A direct on disk implementation of LongList. This implementation creates a temporary file to store the data.
 *  If the user provides a file, it will be used to create a copy of the in the temporary file. If the file is absent,
 *  it will take the name of the file provided by the user and create a temporary file with this name.
 * <p>
 *  Unlike the "snapshot" file, the temporary files doesn't contain the header, only the body.
 *
 */
public class LongListDisk extends LongList<Long> {

    private static final String STORE_POSTFIX = "longListDisk";
    private static final String DEFAULT_FILE_NAME = "LongListDisk.ll";
    /** A temp byte buffer for reading and writing longs */
    private final ThreadLocal<ByteBuffer> tempLongBufferThreadLocal;

    /** This file channel is to work with the temporary file. The field is effectively immutable, however it can't be
     * declared final because in some cases it has to be initialized in {@link LongListDisk#readBodyFromFileChannelOnInit}
     */
    private FileChannel currentFileChannel;

    /** A temp byte buffer for transferring data between file channels */
    private final ThreadLocal<ByteBuffer> transferBufferThreadLocal;

    /**
     * Offsets of the chunks that are free to be used. The offsets are relative to the start of the file.
     */
    private final Deque<Long> freeChunks;

    /**
     * A list of all the byte buffers that are used to read and write data from the file. This is used to ensure that
     * the buffers are properly disposed of when the list is closed.
     */
    private final List<ByteBuffer> bufferRegistry;

    {
        transferBufferThreadLocal = createThreadLocalByteBuffer(memoryChunkSize);
        tempLongBufferThreadLocal = createThreadLocalByteBuffer(Long.BYTES);
        bufferRegistry = new CopyOnWriteArrayList<>();
        freeChunks = new ConcurrentLinkedDeque<>();
        try {
            if (currentFileChannel == null) {
                currentFileChannel = FileChannel.open(
                        createTempFile(DEFAULT_FILE_NAME),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public LongListDisk() {
        super(DEFAULT_NUM_LONGS_PER_CHUNK, DEFAULT_MAX_LONGS_TO_STORE, DEFAULT_RESERVED_BUFFER_LENGTH);
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

    public LongListDisk(final Path file, final long reservedBufferLength) throws IOException {
        super(file, reservedBufferLength);
    }

    public LongListDisk(int numLongsPerChunk, long maxLongs, long reservedBufferLength) {
        super(numLongsPerChunk, maxLongs, reservedBufferLength);
    }

    /**
     * @param bufferSize the size of the buffer
     * @return a ThreadLocal instance initialized with ByteBuffer instance of the given size.
     */
    private ThreadLocal<ByteBuffer> createThreadLocalByteBuffer(int bufferSize) {
        return ThreadLocal.withInitial(() -> {
            ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder());
            bufferRegistry.add(buffer);
            return buffer;
        });
    }

    @Override
    protected void readBodyFromFileChannelOnInit(String sourceFileName, FileChannel fileChannel) throws IOException {
        Path tempFile = createTempFile(sourceFileName);
        // create temporary file for writing
        @SuppressWarnings("resource")
        RandomAccessFile rf = new RandomAccessFile(tempFile.toFile(), "rw");
        // ensure that the amount of disk space is enough
        // two additional chunks are required to accommodate "compressed" first and last chunks in the original file
        rf.setLength(fileChannel.size() + (long) 2 * memoryChunkSize);
        currentFileChannel = rf.getChannel();

        final int totalNumberOfChunks = calculateNumberOfChunks(size());
        final int firstChunkWithDataIndex = (int) (minValidIndex.get() / numLongsPerChunk);
        final int minValidIndexInChunk = (int) (minValidIndex.get() % numLongsPerChunk);

        // copy the first chunk
        // can't use tempLongBufferThreadLocal here because it's not initialized yet
        ByteBuffer transferBuffer = ByteBuffer.allocateDirect(memoryChunkSize);
        transferBuffer.position(minValidIndexInChunk * Long.BYTES).limit(transferBuffer.capacity());
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
            chunkList.set(i, (long) i * memoryChunkSize);
        }
    }

    private static Path createTempFile(String sourceFileName) throws IOException {
        return TemporaryFileBuilder.buildTemporaryDirectory(STORE_POSTFIX).resolve(sourceFileName);
    }

    @Override
    protected void onEmptyOrAbsentSourceFile(Path path) throws IOException {
        Path tempFile = createTempFile(path.toFile().getName());
        currentFileChannel = FileChannel.open(
                tempFile, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
    }

    /**
     * Stores a long at the given index.
     *
     * @param index the index to use
     * @param value the long to store
     * @throws IndexOutOfBoundsException if the index is negative or beyond the max capacity of the list
     * @throws IllegalArgumentException if the value is zero
     */
    @Override
    public synchronized void put(final long index, final long value) {
        checkValueAndIndex(value, index);
        try {
            final ByteBuffer buf = tempLongBufferThreadLocal.get();
            final long offset = createOrGetChunk(index) + calculateOffsetInChunk(index);
            // write new value to file
            buf.putLong(0, value);
            buf.position(0);
            MerkleDbFileUtils.completelyWrite(currentFileChannel, buf, offset);
            // update size
            size.getAndUpdate(oldSize -> index >= oldSize ? (index + 1) : oldSize);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Stores a long at the given index, on the condition that the current long therein has a given
     * value.
     *
     * @param index the index to use
     * @param oldValue the value that must currently obtain at the index
     * @param newValue the new value to store
     * @return whether the newValue was set
     * @throws IndexOutOfBoundsException if the index is negative or beyond the max capacity of the list
     * @throws IllegalArgumentException if old value is zero (which could never be true)
     */
    @Override
    public synchronized boolean putIfEqual(final long index, final long oldValue, final long newValue) {
        checkValueAndIndex(newValue, index);

        try {
            final ByteBuffer buf = tempLongBufferThreadLocal.get();
            buf.position(0);
            final long offset = createOrGetChunk(index) + calculateOffsetInChunk(index);
            MerkleDbFileUtils.completelyRead(currentFileChannel, buf, offset);
            final long filesOldValue = buf.getLong(0);
            if (filesOldValue == oldValue) {
                // write new value to file
                buf.putLong(0, newValue);
                buf.position(0);
                MerkleDbFileUtils.completelyWrite(currentFileChannel, buf, offset);
                // update size
                size.getAndUpdate(oldSize -> index >= oldSize ? (index + 1) : oldSize);
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
     * Write all longs in this LongList into a file.
     * <p>
     * <b> It is not guaranteed what version of data will be written if the LongList is changed via put methods
     * while this LongList is being written to a file. If you need consistency while calling put concurrently then
     * use a BufferedLongListWrapper. </b>
     * <p>
     * <b> It is not guaranteed what version of data will be written if the LongList is changed
     * via put methods while this LongList is being written to a file. If you need consistency while
     * calling put concurrently then use a BufferedLongListWrapper. </b>
     *
     * @param newFile The file to write into, it should not exist but its parent directory should
     *     exist and be writable.
     * @throws IOException If there was a problem creating or writing to the file.
     */
    @Override
    public void writeToFile(final Path newFile) throws IOException {
        // finish writing to current file
        currentFileChannel.force(true);
        // if new file is provided then copy to it
        try (final FileChannel fc = FileChannel.open(newFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            // write header
            writeHeader(fc);
            // write data
            writeLongsData(fc);
            fc.force(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeLongsData(final FileChannel fc) throws IOException {
        final int totalNumOfChunks = calculateNumberOfChunks(size());
        final long currentMinValidIndex = minValidIndex.get();
        final int firstChunkWithDataIndex = (int) currentMinValidIndex / numLongsPerChunk;
        final ByteBuffer transferBuffer = transferBufferThreadLocal.get();
        for (int i = firstChunkWithDataIndex; i < totalNumOfChunks; i++) {
            final Long chunkOffset;
            if (i == firstChunkWithDataIndex) {
                // writing starts from the first valid index in the first valid chunk
                final int firstValidIndexInChunk = (int) currentMinValidIndex % numLongsPerChunk;
                transferBuffer.position(firstValidIndexInChunk * Long.BYTES);
                chunkOffset = chunkList.get(i) + calculateOffsetInChunk(currentMinValidIndex);
            } else {
                transferBuffer.position(0);
                chunkOffset = chunkList.get(i);
            }
            if (i == (totalNumOfChunks - 1)) {
                // last array, so set limit to only the data needed
                final long bytesWrittenSoFar = (long) memoryChunkSize * (long) i;
                final long remainingBytes = (size() * Long.BYTES) - bytesWrittenSoFar;
                transferBuffer.limit((int) remainingBytes);
            } else {
                transferBuffer.limit(transferBuffer.capacity());
            }
            int currentPosition = transferBuffer.position();
            MerkleDbFileUtils.completelyRead(currentFileChannel, transferBuffer, chunkOffset);
            transferBuffer.position(currentPosition);
            MerkleDbFileUtils.completelyWrite(fc, transferBuffer);
        }
    }

    /**
     * Lookup a long in data
     *
     * @param chunkIndex the index of the chunk the long is contained in
     * @param subIndex   The sub index of the long in that chunk
     * @return The stored long value at given index
     */
    @Override
    protected long lookupInChunk(final long chunkIndex, final long subIndex) {
        try {
            final long listIndex = (chunkIndex * numLongsPerChunk) + subIndex;
            if (listIndex < minValidIndex.get()) {
                return IMPERMISSIBLE_VALUE;
            }
            final ByteBuffer buf = tempLongBufferThreadLocal.get();
            final long offset = createOrGetChunk(listIndex) + calculateOffsetInChunk(listIndex);
            buf.clear();
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
        transferBufferThreadLocal.remove();
        tempLongBufferThreadLocal.remove();
        bufferRegistry.forEach(UNSAFE::invokeCleaner);
    }

    @Override
    protected void fullChunkCleanup(Long chunk) {
        // if it's the last chunk, don't do any cleanup as it may be claimed by another threads
        if (chunk / numLongsPerChunk == getCurrentMax() / numLongsPerChunk) {
            return;
        }

        final ByteBuffer transferBuffer = transferBufferThreadLocal.get();
        transferBuffer.clear();
        try {
            currentFileChannel.write(transferBuffer, chunk);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        freeChunks.add(chunk);
    }

    @Override
    protected void partialChunkCleanup(int chunkIndex, long entriesToCleanUp) {
        final ByteBuffer transferBuffer = transferBufferThreadLocal.get();
        transferBuffer.clear();
        transferBuffer.limit((int) (entriesToCleanUp * Long.BYTES));
        try {
            currentFileChannel.write(transferBuffer, chunkList.get(chunkIndex));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected Long createChunk() {
        Long chunkOffset = freeChunks.poll();

        if (chunkOffset == null) {
            try {
                return currentFileChannel.size();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            return chunkOffset;
        }
    }

    // exposed for test purposes only - do not use
    FileChannel getCurrentFileChannel() {
        return currentFileChannel;
    }
}
