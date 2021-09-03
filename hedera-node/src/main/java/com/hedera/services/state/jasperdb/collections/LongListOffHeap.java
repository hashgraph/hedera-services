package com.hedera.services.state.jasperdb.collections;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * An off-heap in memory store of longs, it stores them in memoryChunkSize direct buffers and adds buffers as needed.
 * It uses memory from 0 to the highest index used. If your use case starts at a high minimum index then this will
 * waste a load of ram.
 *
 * This has to use sun.misc.Unsafe to be able to do atomic compare and swap on off heap memory.
 *
 * It is thread safe for concurrent access.
 */
public final class LongListOffHeap implements LongList {
    /** Constant for 1Mb */
    private static final int MB = 1024*1024;
    /** Offset of the {@code java.nio.Buffer#address} field. */
    private static final long BYTE_BUFFER_ADDRESS_FIELD_OFFSET;
    /** Access to sun misc unsafe API for use for atomic compare and swap on long memory */
    private static final Unsafe UNSAFE;
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
            BYTE_BUFFER_ADDRESS_FIELD_OFFSET = UNSAFE.objectFieldOffset(Buffer.class.getDeclaredField("address"));
        } catch (Exception ex) { throw new Error(ex); }
    }

    /** Size in bytes for each memory chunk to allocate */
    private final int memoryChunkSize;
    /** Number of longs we can store in each memory chunk */
    private final long numLongsPerChunk;
    /** Copy on write array of our memory chunks */
    private final CopyOnWriteArrayList<LongBuffer> data = new CopyOnWriteArrayList<>();
    /** Atomic long we use for keeping track of the capacity we have available in all current chunks */
    private final AtomicLong maxIndexThatCanBeStored = new AtomicLong(-1);
    /** Atomic long for the number of data items currently stored */
    private final AtomicLong currentMaxIndex = new AtomicLong(-1);

    /**
     * Construct a new OffHeapLongList with the default 8Mb chunk size
     */
    public LongListOffHeap() {
        this(8);
    }

    /**
     * Construct a new OffHeapLongList with the specified chunk size
     *
     * @param chunkSizeInMb size for each chunk of memory to allocate.
     */
    public LongListOffHeap(int chunkSizeInMb) {
         this.memoryChunkSize = chunkSizeInMb*MB;
         numLongsPerChunk = memoryChunkSize / Long.BYTES;
    }

    /**
     * Load hash for a node with given index
     *
     * @param index the index to get hash for
     * @param notFoundValue the value to use if not found
     * @return loaded long or -1 if long is not stored
     */
    @Override
    public long get(long index, long notFoundValue) {
        if (index <= maxIndexThatCanBeStored.get()) {
            int subIndex = (int)(index % numLongsPerChunk);
            return data.get((int) (index / numLongsPerChunk)).get(subIndex);
        } else {
            return notFoundValue;
        }
    }

    /**
     * Put a value at given index
     *
     * @param index the index of where to put value in list
     * @param value the value to store at index
     */
    @Override
    public void put(long index, long value) {
        expandIfNeeded(index);
        // get the right buffer
        final int subIndex = (int)(index % numLongsPerChunk);
        final LongBuffer chunk = data.get((int) (index / numLongsPerChunk));
//        data.get((int) (index / numLongsPerChunk)).put(subIndex, value);
        // get native memory address
        final long chunkPointer = address(chunk);
        final int subIndexBytes = subIndex * Long.BYTES;
        boundsCheck(subIndexBytes);
        UNSAFE.putLongVolatile(null, chunkPointer + subIndexBytes,value);
    }

    /**
     * Put a value at given index, if the old value matches oldValue
     *
     * @param index the index of where to put value in list
     * @param oldValue only update if the current value matches this
     * @param newValue the value to store at index
     */
    @Override
    public boolean putIfEqual(long index, long oldValue, long newValue) {
        expandIfNeeded(index);
        // get the right buffer
        final int subIndex = (int)(index % numLongsPerChunk);
        final LongBuffer chunk = data.get((int) (index / numLongsPerChunk));
        // get native memory address
        final long chunkPointer = address(chunk);
        final int subIndexBytes = subIndex * Long.BYTES;
        boundsCheck(subIndexBytes);
        return UNSAFE.compareAndSwapLong(null, chunkPointer + subIndexBytes, oldValue, newValue);
    }

    /**
     * Get the current capacity of this OffHeapLongList, this is the most data that is stored in it
     */
    @Override
    public long capacity() {
        return maxIndexThatCanBeStored.get()+1;
    }

    /**
     * Get the current size of this OffHeapLongList, this is the most data that is stored in it
     */
    @Override
    public long size() {
        return currentMaxIndex.get()+1;
    }

    // =================================================================================================================
    // Private helper methods

    /**
     * Expand the available data storage if needed to allow storage of a item at newIndex
     *
     * @param newIndex the index of the new item we would like to add to storage
     */
    private void expandIfNeeded(long newIndex) {
        // updates the index to the max of new index and its current value. If two threads are trying to do this at the
        // same time they will both keep trying till each one gets a clean chance of setting the value. The largest will
        // always win, which is what matters.
        //noinspection ManualMinMaxCalculation
        currentMaxIndex.getAndUpdate(oldMaxIndex -> newIndex > oldMaxIndex ? newIndex : oldMaxIndex);

        // This is important to be lock free which means two or more threads can be inside the loop at a time. This
        // means two threads can be making the buffer bigger at the same time. The most that can happen in this case is
        // the buffer is bigger than needed. Because the index for inserting the value is fixed, there is no contention
        // over the index only over the size of the buffer.
        while (newIndex > maxIndexThatCanBeStored.get()) {
            // need to expand
            ByteBuffer directBuffer = ByteBuffer.allocateDirect(memoryChunkSize);
            directBuffer.order(ByteOrder.nativeOrder());
            data.add(directBuffer.asLongBuffer());
            maxIndexThatCanBeStored.addAndGet(numLongsPerChunk);
        }
    }

    /**
     * Check if the given range is within the memory bounds of a memory chunk
     *
     * @param index the offset index from start of memory chunk
     */
    private void boundsCheck(final long index) {
        final long resultingPosition = index + (long) Long.BYTES;
        if (index < 0 || resultingPosition > memoryChunkSize) {
            throw new IndexOutOfBoundsException("index=" + index + " length=" + (long) Long.BYTES + " capacity=" + memoryChunkSize);
        }
    }

    /**
     * Get the address at which the underlying buffer storage begins.
     *
     * @param buffer that wraps the underlying storage.
     * @return the memory address at which the buffer storage begins.
     */
    public static long address(final Buffer buffer) {
        if (!buffer.isDirect())
            throw new IllegalArgumentException("buffer.isDirect() must be true");
        return UNSAFE.getLong(buffer, BYTE_BUFFER_ADDRESS_FIELD_OFFSET);
    }


}
