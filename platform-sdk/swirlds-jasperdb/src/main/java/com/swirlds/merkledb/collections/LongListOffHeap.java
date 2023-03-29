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

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNullElse;

import com.swirlds.merkledb.utilities.MerkleDbFileUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link LongList} that stores its contents off-heap via a {@link AtomicReferenceArray} of direct
 * {@link ByteBuffer}s. Each {@link ByteBuffer} is the same size, so the "chunk" containing the
 * value for any given index is easily found using modular arithmetic. Note that <br>
 * to reduce memory consumption one can use {@link LongList#updateValidRange(long, long)}.
 * A call to this method discards memory chunks reserved for the indices that are before the index
 * passed as an argument subtracted by {@link AbstractLongList#reservedBufferLength}. The idea is to
 * keep the amount of memory defined by {@link AbstractLongList#reservedBufferLength} reserved even
 * though it serves indices that are before the minimal index. It may be a good idea because there
 * is a good chance that the indices in this range may be used (e.g. in case of mass deletion from
 * an instance of {@link com.swirlds.merkledb.files.MemoryIndexDiskKeyValueStore})
 *
 * <p>Per the {@link LongList} contract, this class is thread-safe for both concurrent reads and
 * writes.
 */
public final class LongListOffHeap extends AbstractLongList<ByteBuffer> {
    /** Offset of the {@code java.nio.Buffer#address} field. */
    private static final long BYTE_BUFFER_ADDRESS_FIELD_OFFSET;

    static {
        try {
            BYTE_BUFFER_ADDRESS_FIELD_OFFSET = UNSAFE.objectFieldOffset(Buffer.class.getDeclaredField("address"));
        } catch (final NoSuchFieldException | SecurityException | IllegalArgumentException e) {
            throw new InternalError(e);
        }
    }

    private static final Logger logger = LogManager.getLogger(LongListOffHeap.class);
    /**
     * Construct a new OffHeapLongList with the default 8Mb chunk size and 2Mb of reserved buffer
     */
    public LongListOffHeap() {
        this(DEFAULT_NUM_LONGS_PER_CHUNK, DEFAULT_MAX_LONGS_TO_STORE, DEFAULT_RESERVED_BUFFER_LENGTH);
    }

    /**
     * Construct a new LongListOffHeap with the specified chunk size
     *
     * @param numLongsPerChunk size for each chunk of memory to allocate. Max 16Gb = 16,384Mb
     * @param maxLongs the maximum number of longs permissible for this LongList
     * @param reservedBufferLength the number of indices before the minimal index to keep reserved
     */
    LongListOffHeap(final int numLongsPerChunk, final long maxLongs, final long reservedBufferLength) {
        super(numLongsPerChunk, maxLongs, reservedBufferLength);
    }

    /**
     * Construct a new OffHeapLongList with the default 8Mb chunk size and 2Mb of reserved buffer
     */
    public LongListOffHeap(final int reservedBufferLength) {
        this(DEFAULT_NUM_LONGS_PER_CHUNK, DEFAULT_MAX_LONGS_TO_STORE, reservedBufferLength);
    }

    /**
     * Create a {@link LongListOffHeap} from a file that was saved.
     *
     * @throws IOException If there was a problem reading the file
     */
    public LongListOffHeap(final Path file) throws IOException {
        super(file, DEFAULT_RESERVED_BUFFER_LENGTH);
    }

    /** {@inheritDoc} */
    @Override
    protected void readBodyFromFileChannelOnInit(String sourceFileName, FileChannel fileChannel) throws IOException {
        final int totalNumberOfChunks = calculateNumberOfChunks(size());
        final int firstChunkWithDataIndex = toIntExact(minValidIndex.get() / numLongsPerChunk);
        final int minValidIndexInChunk = toIntExact(minValidIndex.get() % numLongsPerChunk);
        // read the first chunk
        final ByteBuffer firstBuffer = createChunk();
        firstBuffer.position(minValidIndexInChunk * Long.BYTES).limit(firstBuffer.capacity());
        MerkleDbFileUtils.completelyRead(fileChannel, firstBuffer);
        chunkList.set(firstChunkWithDataIndex, firstBuffer);
        // read the rest of the data
        for (int i = firstChunkWithDataIndex + 1; i < totalNumberOfChunks; i++) {
            final ByteBuffer directBuffer = createChunk();
            MerkleDbFileUtils.completelyRead(fileChannel, directBuffer);
            directBuffer.position(0);
            chunkList.set(i, directBuffer);
        }
    }

    /**
     * Clean up all the direct buffers reserved for chunks
     */
    @Override
    protected void onClose() {
        for (int i = 0; i < chunkList.length(); i++) {
            final ByteBuffer directBuffer = chunkList.get(i);
            if (directBuffer != null) {
                UNSAFE.invokeCleaner(directBuffer);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void put(ByteBuffer chunk, int subIndex, long value) {
        /* The remaining lines below are equivalent to a chunk.put(subIndex, value) call
        on a heap byte buffer. Since we have instead a direct buffer, we need to, first,
        get its native memory address from the Buffer.address field; and, second, store
        the given long at the appropriate offset from that address. */
        final int subIndexOffset = subIndex * Long.BYTES;
        final long chunkPointer = address(chunk);
        UNSAFE.putLongVolatile(null, chunkPointer + subIndexOffset, value);
    }

    /** {@inheritDoc} */
    @Override
    protected boolean putIfEqual(ByteBuffer chunk, int subIndex, long oldValue, long newValue) {
        /* Below would be equivalent to a compareAndSet(subIndex, oldValue, newValue)
        call on a heap byte buffer, if such a method existed. Since we have instead a
        direct buffer, we need to, first, get its native memory address from the
        Buffer.address field; and, second, compare-and-swap the given long at the
        appropriate offset from that address. */
        final int subIndexBytes = subIndex * Long.BYTES;
        final long chunkPointer = address(chunk);
        return UNSAFE.compareAndSwapLong(null, chunkPointer + subIndexBytes, oldValue, newValue);
    }

    /**
     * Write the long data to file, This it is expected to be in one simple block of raw longs.
     *
     * @param fc The file channel to write to
     * @throws IOException if there was a problem writing longs
     */
    @Override
    protected void writeLongsData(final FileChannel fc) throws IOException {
        final int totalNumOfChunks = calculateNumberOfChunks(size());
        final long currentMinValidIndex = minValidIndex.get();
        final int firstChunkWithDataIndex = toIntExact(currentMinValidIndex / numLongsPerChunk);
        // write data
        final ByteBuffer emptyBuffer = createChunk();
        try {
            for (int i = firstChunkWithDataIndex; i < totalNumOfChunks; i++) {
                final ByteBuffer byteBuffer = chunkList.get(i);
                final ByteBuffer nonNullBuffer = requireNonNullElse(byteBuffer, emptyBuffer);
                final ByteBuffer buf = nonNullBuffer.slice(); // slice so we don't mess with state
                if (i == firstChunkWithDataIndex) {
                    // writing starts from the first valid index in the first valid chunk
                    final int firstValidIndexInChunk = toIntExact(currentMinValidIndex % numLongsPerChunk);
                    buf.position(firstValidIndexInChunk * Long.BYTES);
                } else {
                    buf.position(0);
                }
                if (i == (totalNumOfChunks - 1)) {
                    // last array, so set limit to only the data needed
                    final long bytesWrittenSoFar = (long) memoryChunkSize * (long) i;
                    final long remainingBytes = (size() * Long.BYTES) - bytesWrittenSoFar;
                    buf.limit(toIntExact(remainingBytes));
                } else {
                    buf.limit(buf.capacity());
                }
                MerkleDbFileUtils.completelyWrite(fc, buf);
            }
        } finally {
            // releasing memory allocated
            UNSAFE.invokeCleaner(emptyBuffer);
        }
    }

    /**
     * Lookup a long in a data chunk.
     *
     * @param chunk     The data chunk
     * @param subIndex   The sub index of the long in that chunk
     * @return The stored long value at given index
     */
    @Override
    protected long lookupInChunk(@NonNull final ByteBuffer chunk, final long subIndex) {
        try {
            /* Do a volatile memory read from off-heap memory */
            final long chunkPointer = address(chunk);
            final int subIndexOffset = toIntExact(subIndex * Long.BYTES);
            return UNSAFE.getLongVolatile(null, chunkPointer + subIndexOffset);
        } catch (final IndexOutOfBoundsException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Index out of bounds in lookupInChunk: buf={}, offset={}, subIndex={}",
                    chunk,
                    subIndex,
                    e);
            throw e;
        }
    }

    protected ByteBuffer createChunk() {
        final ByteBuffer directBuffer = ByteBuffer.allocateDirect(memoryChunkSize);
        directBuffer.order(ByteOrder.nativeOrder());
        return directBuffer;
    }

    protected void releaseChunk(@NonNull ByteBuffer newChunk) {
        UNSAFE.invokeCleaner(newChunk);
    }

    /**
     * Looks up a chunk by {@code chunkIndex} and, if the chunk exists,
     * zeros values up to {@code elementsToCleanUp} index.
     *
     * @param chunk            a chunk to clean up,
     * @param entriesToCleanUp number of elements to clean up starting with 0 index
     */
    @Override
    protected void partialChunkCleanup(
            @NonNull final ByteBuffer chunk, final boolean leftSide, final long entriesToCleanUp) {
        final long chunkPointer = address(chunk);
        if (leftSide) {
            // cleans up all values up to newMinValidIndex in the first chunk
            UNSAFE.setMemory(chunkPointer, entriesToCleanUp * Long.BYTES, (byte) 0);
        } else {
            // cleans up all values on the right side of the last chunk
            final long offset = (numLongsPerChunk - entriesToCleanUp) * Long.BYTES;
            UNSAFE.setMemory(chunkPointer + offset, entriesToCleanUp * Long.BYTES, (byte) 0);
        }
    }

    /**
     * Get the address at which the underlying buffer storage begins.
     *
     * @param buffer that wraps the underlying storage.
     * @return the memory address at which the buffer storage begins.
     */
    static long address(final Buffer buffer) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("buffer.isDirect() must be true");
        }
        return UNSAFE.getLong(buffer, BYTE_BUFFER_ADDRESS_FIELD_OFFSET);
    }

    /**
     * Measures the amount of off-heap memory consumption.
     * It doesn't guarantee the exact result, there is a chance it may deviate
     * by a chunk size from the actual amount if this chunk was added or removed while the measurement.
     *
     * @return the amount of off-heap memory (in bytes) consumed by the list
     */
    public long getOffHeapConsumption() {
        int nonEmptyChunkCount = 0;
        final int chunkListSize = chunkList.length();

        for (int i = 0; i < chunkListSize; i++) {
            if (chunkList.get(i) != null) {
                nonEmptyChunkCount++;
            }
        }

        return (long) nonEmptyChunkCount * memoryChunkSize;
    }
}
