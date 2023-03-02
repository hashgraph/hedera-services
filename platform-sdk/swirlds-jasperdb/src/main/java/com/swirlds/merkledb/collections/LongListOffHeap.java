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

import static com.swirlds.common.utility.Units.MEBIBYTES_TO_BYTES;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static java.util.Objects.requireNonNullElse;

import com.swirlds.merkledb.utilities.MerkleDbFileUtils;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sun.misc.Unsafe;

/**
 * A {@link LongList} that stores its contents off-heap via a {@link AtomicReferenceArray} of direct
 * {@link ByteBuffer}s. Each {@link ByteBuffer} is the same size, so the "chunk" containing the
 * value for any given index is easily found using modular arithmetic. Note that <br>
 * to reduce memory consumption one can use {@link LongListOffHeap#updateMinValidIndex(long)}.
 * A call to this method discards memory chunks reserved for the indices that are before the index
 * passed as an argument subtracted by {@link LongListOffHeap#reservedBufferLength}. The idea is to
 * keep the amount of memory defined by {@link LongListOffHeap#reservedBufferLength} reserved even
 * though it serves indices that are before the minimal index. It may be a good idea because there
 * is a good chance that the indices in this range may be used (e.g. in case of mass deletion from
 * an instance of {@link com.swirlds.merkledb.files.MemoryIndexDiskKeyValueStore})
 *
 * <p>Per the {@link LongList} contract, this class is thread-safe for both concurrent reads and
 * writes.
 */
public final class LongListOffHeap extends LongList {
    private static final Logger logger = LogManager.getLogger(LongListOffHeap.class);
    /** Offset of the {@code java.nio.Buffer#address} field. */
    private static final long BYTE_BUFFER_ADDRESS_FIELD_OFFSET;

    /**
     * A suitable default for the reserved buffer length that the list should have before minimal
     * index in the list
     */
    public static final int DEFAULT_RESERVED_BUFFER_LENGTH = Math.toIntExact(2L * MEBIBYTES_TO_BYTES / Long.BYTES);

    /** Maximum number of chunks allowed. */
    public static final int MAX_NUM_CHUNKS = 2 << 14;

    /** Access to sun.misc.Unsafe required for atomic compareAndSwapLong on off-heap memory */
    private static final Unsafe UNSAFE;

    /**
     * A length of a buffer that is reserved to remain intact after memory optimization that is
     * happening in {@link LongListOffHeap#updateMinValidIndex}
     */
    private final long reservedBufferLength;

    static {
        try {
            final Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
            BYTE_BUFFER_ADDRESS_FIELD_OFFSET = UNSAFE.objectFieldOffset(Buffer.class.getDeclaredField("address"));
        } catch (final NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    /** Atomic reference array of our memory chunks */
    private final AtomicReferenceArray<ByteBuffer> chunkList;

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
    public LongListOffHeap(final int numLongsPerChunk, final long maxLongs, final long reservedBufferLength) {
        super(numLongsPerChunk, maxLongs);
        this.reservedBufferLength = reservedBufferLength;
        final long chunkNum = calculateNumberOfChunks(maxLongs);
        if (chunkNum > MAX_NUM_CHUNKS) {
            throw new IllegalArgumentException(String.format(
                    "The maximum number of memory chunks should not exceed %s. "
                            + "Either increase numLongsPerChunk or decrease maxLongs",
                    MAX_NUM_CHUNKS));
        }
        chunkList = new AtomicReferenceArray<>((int) chunkNum);
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
        super(FileChannel.open(file, StandardOpenOption.READ));
        final int totalNumberOfChunks = calculateNumberOfChunks(size());
        final int firstChunkWithDataIndex = (int) (minValidIndex.get() / numLongsPerChunk);
        chunkList = new AtomicReferenceArray<>(calculateNumberOfChunks(maxLongs));
        reservedBufferLength = DEFAULT_RESERVED_BUFFER_LENGTH;
        // read data
        for (int i = firstChunkWithDataIndex; i < totalNumberOfChunks; i++) {
            final ByteBuffer directBuffer = createChunk();
            MerkleDbFileUtils.completelyRead(fileChannel, directBuffer);
            directBuffer.position(0);
            chunkList.set(i, directBuffer);
        }
        // close file channel as we are done with it
        fileChannel.close();
        fileChannel = null;
    }

    /**
     * @return number of memory chunks that this list may have
     */
    private int calculateNumberOfChunks(final long rightBoundary) {
        return (int) ((rightBoundary - 1) / numLongsPerChunk + 1);
    }

    /**
     * Close and clean up resources
     */
    @Override
    public void close() {
        maxIndexThatCanBeStored.set(0);
        size.set(0);
        for (int i = 0; i < chunkList.length(); i++) {
            final ByteBuffer directBuffer = chunkList.get(i);
            if (directBuffer != null) {
                UNSAFE.invokeCleaner(directBuffer);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void put(final long index, final long value) {
        checkValueAndIndex(value, index);
        final ByteBuffer chunk = createOrGetChunk(index);
        /* The remaining lines below are equivalent to a chunk.put(subIndex, value) call
        on a heap byte buffer. Since we have instead a direct buffer, we need to, first,
        get its native memory address from the Buffer.address field; and, second, store
        the given long at the appropriate offset from that address. */
        final int subIndex = (int) (index % numLongsPerChunk);
        final int subIndexOffset = subIndex * Long.BYTES;
        final long chunkPointer = address(chunk);
        UNSAFE.putLongVolatile(null, chunkPointer + subIndexOffset, value);
    }

    /**
     * After invocation of this method, {@link LongList#get(long)}) calls
     * will return {@link LongList#IMPERMISSIBLE_VALUE} for indices that
     * are before {@code newMinValidIndex}.
     * Also, a call to this method releases memory taken by unused byte buffers.
     *
     * @param newMinValidIndex minimal valid index of the list
     */
    @Override
    public void onUpdateMinValidIndex(final long newMinValidIndex) {
        if (newMinValidIndex < 0) {
            throw new IndexOutOfBoundsException("Min valid index " + newMinValidIndex + " must be non-negative");
        }

        if (newMinValidIndex >= size.get()) {
            logger.warn("New min valid index cannot exceed current list size, returning.");
            return;
        }

        // This is an optimization: we know that a memory block for this index must exist,
        // so let's make sure that it's there
        createOrGetChunk(newMinValidIndex);
        shrinkIfNeeded(newMinValidIndex);
    }

    /** {@inheritDoc} */
    @Override
    public boolean putIfEqual(final long index, final long oldValue, final long newValue) {
        checkValueAndIndex(newValue, index);
        final int dataIndex = (int) (index / numLongsPerChunk);
        final ByteBuffer chunk = chunkList.get(dataIndex);
        if (chunk == null) {
            // quick optimization: we can quit early without creating new memory blocks
            // unnecessarily
            return false;
        }
        /* Below would be equivalent to a compareAndSet(subIndex, oldValue, newValue)
        call on a heap byte buffer, if such a method existed. Since we have instead a
        direct buffer, we need to, first, get its native memory address from the
        Buffer.address field; and, second, compare-and-swap the given long at the
        appropriate offset from that address. */
        final int subIndex = (int) (index % numLongsPerChunk);
        final int subIndexBytes = subIndex * Long.BYTES;
        final long chunkPointer = address(chunk);
        return UNSAFE.compareAndSwapLong(null, chunkPointer + subIndexBytes, oldValue, newValue);
    }

    // =================================================================================================================
    // Protected methods

    /**
     * Write the long data to file, This it is expected to be in one simple block of raw longs.
     *
     * @param fc The file channel to write to
     * @throws IOException if there was a problem writing longs
     */
    @Override
    protected void writeLongsData(final FileChannel fc) throws IOException {
        final int totalNumOfChunks = calculateNumberOfChunks(size());
        final int firstChunkWithDataIndex = (int) minValidIndex.get() / numLongsPerChunk;
        // write data
        final ByteBuffer emptyBuffer = createChunk();
        try {
            for (int i = firstChunkWithDataIndex; i < totalNumOfChunks; i++) {
                final ByteBuffer byteBuffer = chunkList.get(i);
                final ByteBuffer nonNullBuffer = requireNonNullElse(byteBuffer, emptyBuffer);
                final ByteBuffer buf = nonNullBuffer.slice(); // slice so we don't mess with state
                buf.position(0);
                if (i == (totalNumOfChunks - 1)) {
                    // last array, so set limit to only the data needed
                    final long bytesWrittenSoFar = (long) memoryChunkSize * (long) i;
                    final long remainingBytes = (size() * Long.BYTES) - bytesWrittenSoFar;
                    buf.limit((int) remainingBytes);
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
     * Lookup a long in a data chunk with the given chunk ID.
     *
     * @param chunkIndex The index of the chunk the long is contained in
     * @param subIndex   The sub index of the long in that chunk
     * @return The stored long value at given index
     */
    @Override
    protected long lookupInChunk(final long chunkIndex, final long subIndex) {
        final ByteBuffer chunk = chunkList.get((int) chunkIndex);
        return lookupInChunk(chunk, subIndex);
    }

    /**
     * Lookup a long in a data chunk.
     *
     * @param chunk The data chunk
     * @param subIndex The sub index of the long in that chunk
     * @return The stored long value at given index
     */
    private static long lookupInChunk(final ByteBuffer chunk, final long subIndex) {
        if (chunk == null) {
            // a chunk was either removed by the memory optimization or never existed
            return LongList.IMPERMISSIBLE_VALUE;
        }
        try {
            /* Do a volatile memory read from off-heap memory */
            final long chunkPointer = address(chunk);
            final int subIndexOffset = (int) (subIndex * Long.BYTES);
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

    // =================================================================================================================
    // Private helper methods

    /**
     * Expand the available data storage if needed to allow storage of an item at newIndex
     *
     * @param newIndex the index of the new item we would like to add to storage
     */
    private ByteBuffer createOrGetChunk(final long newIndex) {
        size.getAndUpdate(oldSize -> newIndex >= oldSize ? (newIndex + 1) : oldSize);
        final int chunkIndex = (int) (newIndex / numLongsPerChunk);
        final ByteBuffer result = chunkList.get(chunkIndex);
        if (result == null) {
            final ByteBuffer newChunk = createChunk();
            // set new chunk if it's not created yet, if it is - release the chunk immediately
            // and use the one from the list
            final ByteBuffer oldChunk = chunkList.compareAndExchange(chunkIndex, null, newChunk);
            if (oldChunk == null) {
                return newChunk;
            } else {
                UNSAFE.invokeCleaner(newChunk);
                return oldChunk;
            }
        } else {
            return result;
        }
    }

    private ByteBuffer createChunk() {
        final ByteBuffer directBuffer = ByteBuffer.allocateDirect(memoryChunkSize);
        directBuffer.order(ByteOrder.nativeOrder());
        return directBuffer;
    }

    /**
     * Deletes values up to {@code newMinValidIndex}, releases memory chunks reserved for these
     * values. Note that it takes {@code reservedBufferOffset} into account to decide if a chunk has
     * to be released.
     *
     * @param newMinValidIndex new minimal valid index, left boundary of the list
     */
    private void shrinkIfNeeded(final long newMinValidIndex) {
        final int firstValidChunkWithBuffer =
                (int) Math.max((newMinValidIndex - reservedBufferLength) / numLongsPerChunk, 0);
        final int firstChunkIndexToDelete = firstValidChunkWithBuffer - 1;
        for (int i = firstChunkIndexToDelete; i >= 0; i--) {
            final ByteBuffer byteBuffer = chunkList.get(i);
            if (byteBuffer != null) {
                if (chunkList.compareAndSet(i, byteBuffer, null)) {
                    // clean up off-heap memory reserved for the byte buffer
                    UNSAFE.invokeCleaner(byteBuffer);
                }
            }
        }

        // clean up a chunk with data
        final int firstChunkWithData = (int) (newMinValidIndex / numLongsPerChunk);
        final long numberOfElementsToCleanUp = (newMinValidIndex % numLongsPerChunk);
        cleanupChunk(firstChunkWithData, numberOfElementsToCleanUp);

        // clean up chunk(s) reserved for buffer
        for (int i = firstValidChunkWithBuffer; i < firstChunkWithData; i++) {
            cleanupChunk(i, numLongsPerChunk);
        }
    }

    /**
     * Looks up a chunk by {@code chunkIndex} and, if the chunk exists,
     * zeros values up to {@code elementsToCleanUp} index.
     *
     * @param chunkIndex        an index of a chunk to clean up
     * @param elementsToCleanUp number of elements to clean up starting with 0 index
     */
    private void cleanupChunk(final int chunkIndex, final long elementsToCleanUp) {
        if (elementsToCleanUp == 0) {
            // nothing to clean up
            return;
        }
        final ByteBuffer validChunk = chunkList.get(chunkIndex);
        if (validChunk != null) {
            final long chunkPointer = address(validChunk);
            // cleans up all values up to newMinValidIndex in the first chunk
            UNSAFE.setMemory(chunkPointer, elementsToCleanUp * Long.BYTES, (byte) 0);
        }
    }

    /**
     * This method returns a snapshot of the current {@link data}. FOR TEST PURPOSES ONLY. NOT
     * THREAD SAFE
     *
     * @return a copy of data.
     */
    List<ByteBuffer> dataCopy() {
        final ArrayList<ByteBuffer> result = new ArrayList<>();
        for (int i = 0; i < chunkList.length(); i++) {
            result.add(chunkList.get(i));
        }
        return result;
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

        return (long) nonEmptyChunkCount * numLongsPerChunk * Long.BYTES;
    }
}
