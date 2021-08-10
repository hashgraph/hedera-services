package com.hedera.services.state.merkle.v3.offheap;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An off-heap in memory store of longs, it stores them in memoryChunkSize direct buffers and adds buffers as needed.
 * It uses memory from 0 to the highest index used. If your use case starts at a high minimum index then this will
 * waste a load of ram.
 *
 * It is thread safe for concurrent access.
 */
public final class OffHeapLongList {
    /** Constant for 1Mb */
    private static final int MB = 1024*1024;
    /** Size in bytes for each memory chunk to allocate */
    private final int memoryChunkSize;
    /** Number of longs we can store in each memory chunk */
    private final long numLongsPerChunk;
    /** Copy on write array of our memory chunks */
    private final CopyOnWriteArrayList<LongBuffer> data = new CopyOnWriteArrayList<>();
    /** Atomic long we use for keeping track of the capacity we have available in all current chunks */
    private final AtomicLong maxIndexThatCanBeStored = new AtomicLong(-1);

    /**
     * Construct a new OffHeapLongList with the default 8Mb chunk size
     */
    public OffHeapLongList() {
        this(8);
    }

    /**
     * Construct a new OffHeapLongList with the specified chunk size
     *
     * @param chunkSizeInMb size for each chunk of memory to allocate.
     */
    public OffHeapLongList(int chunkSizeInMb) {
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
    public void put(long index, long value) {
        // expand data if needed
        maxIndexThatCanBeStored.updateAndGet(currentValue -> {
            while (index > currentValue) { // need to expand
                data.add(ByteBuffer.allocateDirect(memoryChunkSize).asLongBuffer());
                currentValue += numLongsPerChunk;
            }
            return currentValue;
        });
        // get the right buffer
        final int subIndex = (int)(index % numLongsPerChunk);
        data.get((int) (index / numLongsPerChunk)).put(subIndex, value);
    }

    /**
     * Put a value at given index, if the old value matches oldValue
     *
     * @param index the index of where to put value in list
     * @param oldValue only update if the current value matches this
     * @param newValue the value to store at index
     */
    public void putIfEqual(long index, long oldValue, long newValue) {
        // expand data if needed
        maxIndexThatCanBeStored.updateAndGet(currentValue -> {
            while (index > currentValue) { // need to expand
                data.add(ByteBuffer.allocateDirect(memoryChunkSize).asLongBuffer());
                currentValue += numLongsPerChunk;
            }
            return currentValue;
        });
        // get the right buffer
        final int subIndex = (int)(index % numLongsPerChunk);
        final LongBuffer chunk = data.get((int) (index / numLongsPerChunk));
        if (chunk.get(subIndex) == oldValue) chunk.put(subIndex, newValue);
    }

    /**
     * Get the current size of this OffHeapLongList, this is the most data that is stored in it
     */
    public long size() {
        return maxIndexThatCanBeStored.get()+1;
    }
}
