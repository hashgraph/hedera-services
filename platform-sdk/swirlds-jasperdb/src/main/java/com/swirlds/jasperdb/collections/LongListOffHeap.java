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

package com.swirlds.jasperdb.collections;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.jasperdb.utilities.JasperDBFileUtils;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sun.misc.Unsafe;

/**
 * A {@link LongList} that stores its contents off-heap via a {@link CopyOnWriteArrayList} of direct {@link LongBuffer}s.
 * Each {@link LongBuffer} is the same size, so the "chunk" containing the value for any given index is easily
 * found using modular arithmetic.
 *
 * It is important to note that if indexes are not used sequentially from zero, many (or most) of the chunks in the list
 * may consume RAM without storing any longs. So this data structure is only appropriate for use cases where list
 * indices are filled in roughly ascending order, starting from zero.
 *
 * Per the {@link LongList} contract, this class is thread-safe for both concurrent reads and writes.
 */
public final class LongListOffHeap extends LongList {
    private static final Logger logger = LogManager.getLogger(LongListOffHeap.class);
    /**
     * Offset of the {@code java.nio.Buffer#address} field.
     */
    private static final long BYTE_BUFFER_ADDRESS_FIELD_OFFSET;

    /**
     * Access to sun.misc.Unsafe required for atomic compareAndSwapLong on off-heap memory
     */
    private static final Unsafe UNSAFE;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
            BYTE_BUFFER_ADDRESS_FIELD_OFFSET = UNSAFE.objectFieldOffset(Buffer.class.getDeclaredField("address"));
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Copy on write array of our memory chunks
     */
    private final CopyOnWriteArrayList<ByteBuffer> data = new CopyOnWriteArrayList<>();

    /**
     * Construct a new OffHeapLongList with the default 8Mb chunk size
     */
    public LongListOffHeap() {
        super(DEFAULT_NUM_LONGS_PER_CHUNK, DEFAULT_MAX_LONGS_TO_STORE);
    }

    /**
     * Construct a new LongListOffHeap with the specified chunk size
     *
     * @param numLongsPerChunk
     * 		size for each chunk of memory to allocate. Max 16Gb = 16,384Mb
     * @param maxLongs
     * 		the maximum number of longs permissible for this LongList
     */
    public LongListOffHeap(final int numLongsPerChunk, final long maxLongs) {
        super(numLongsPerChunk, maxLongs);
    }

    /**
     * Create a {@link LongListOffHeap} from a file that was saved.
     *
     * @throws IOException
     * 		If there was a problem reading the file
     */
    public LongListOffHeap(final Path file) throws IOException {
        super(FileChannel.open(file, StandardOpenOption.READ));
        // read data
        int numOfBuffers = (int) Math.ceil((double) size() / (double) numLongsPerChunk);
        // read data
        for (int i = 0; i < numOfBuffers; i++) {
            final ByteBuffer directBuffer = ByteBuffer.allocateDirect(memoryChunkSize);
            directBuffer.order(ByteOrder.nativeOrder());
            JasperDBFileUtils.completelyRead(fileChannel, directBuffer);
            directBuffer.position(0);
            data.add(directBuffer);
        }
        // close file channel as we are done with it
        fileChannel.close();
        fileChannel = null;
    }

    /**
     * Close and clean up resources
     */
    @Override
    public void close() {
        maxIndexThatCanBeStored.set(0);
        size.set(0);
        data.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(final long index, final long value) {
        checkValueAndIndex(value, index);
        expandIfNeeded(index);
        final int dataIndex = (int) (index / numLongsPerChunk);
        final ByteBuffer chunk = data.get(dataIndex);
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
     * {@inheritDoc}
     */
    @Override
    public boolean putIfEqual(final long index, final long oldValue, final long newValue) {
        checkValueAndIndex(newValue, index);
        expandIfNeeded(index);
        final int dataIndex = (int) (index / numLongsPerChunk);
        final ByteBuffer chunk = data.get(dataIndex);
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
     * @param fc
     * 		The file channel to write to
     * @throws IOException
     * 		if there was a problem writing longs
     */
    @Override
    protected void writeLongsData(final FileChannel fc) throws IOException {
        // write data
        final int numOfArrays = data.size();
        for (int i = 0; i < numOfArrays; i++) {
            ByteBuffer buf = data.get(i).slice(); // slice so we don't mess with state
            buf.position(0);
            if (i == (numOfArrays - 1)) {
                // last array, so set limit to only the data needed
                final long bytesWrittenSoFar = (long) memoryChunkSize * (long) i;
                final long remainingBytes = (size() * Long.BYTES) - bytesWrittenSoFar;
                buf.limit((int) remainingBytes);
            } else {
                buf.limit(buf.capacity());
            }
            JasperDBFileUtils.completelyWrite(fc, buf);
        }
    }

    /**
     * Lookup a long in data
     *
     * @param chunkIndex
     * 		the index of the chunk the long is contained in
     * @param subIndex
     * 		The sub index of the long in that chunk
     * @return The stored long value at given index
     */
    protected long lookupInChunk(final long chunkIndex, final long subIndex) {
        try {
            final int subIndexOffset = (int) (subIndex * Long.BYTES);
            final ByteBuffer chunk = data.get((int) chunkIndex);
            /* Do a volatile memory read from off-heap memory */
            final long chunkPointer = address(chunk);
            return UNSAFE.getLongVolatile(null, chunkPointer + subIndexOffset);
        } catch (final IndexOutOfBoundsException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Index out of bounds in lookupInChunk, "
                            + "buf={}, offset={}, chunkIndex={}, chunkIndex={}, subIndex={}",
                    data.get((int) chunkIndex),
                    ((int) subIndex * Long.BYTES),
                    chunkIndex,
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
     * @param newIndex
     * 		the index of the new item we would like to add to storage
     */
    private void expandIfNeeded(final long newIndex) {
        // This is important to be lock free which means two or more threads can be inside the loop at a time. This
        // means two threads can be making the buffer bigger at the same time. The most that can happen in this case is
        // the buffer is bigger than needed. Because the index for inserting the value is fixed, there is no contention
        // over the index only over the size of the buffer.
        while (newIndex > maxIndexThatCanBeStored.get()) {
            // need to expand
            final ByteBuffer directBuffer = ByteBuffer.allocateDirect(memoryChunkSize);
            directBuffer.order(ByteOrder.nativeOrder());
            data.add(directBuffer);
            maxIndexThatCanBeStored.addAndGet(numLongsPerChunk);
        }

        // updates the index to the max of new index and its current value. If two threads are trying to do this at the
        // same time they will both keep trying till each one gets a clean change of setting the value. The largest will
        // always win, which is what matters.
        size.getAndUpdate(oldSize -> newIndex >= oldSize ? (newIndex + 1) : oldSize);
    }

    /**
     * Get the address at which the underlying buffer storage begins.
     *
     * @param buffer
     * 		that wraps the underlying storage.
     * @return the memory address at which the buffer storage begins.
     */
    static long address(final Buffer buffer) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("buffer.isDirect() must be true");
        }
        return UNSAFE.getLong(buffer, BYTE_BUFFER_ADDRESS_FIELD_OFFSET);
    }
}
