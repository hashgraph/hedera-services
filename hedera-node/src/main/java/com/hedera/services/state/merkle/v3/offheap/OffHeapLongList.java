package com.hedera.services.state.merkle.v3.offheap;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An off-heap in memory store of longs, it stores them in 8Mb direct buffers and adds buffers as needed.
 *
 * It is thread safe for concurrent access.
 */
public final class OffHeapLongList {
    private static final int MB = 1024*1024;
    private static final int CHUNK_SIZE_MB = 8;
    private static final int MEMORY_CHUNK_SIZE = CHUNK_SIZE_MB*MB;
    private static final long NUM_LONGS_PER_CHUNK = MEMORY_CHUNK_SIZE/Long.BYTES;
    private final List<LongBuffer> data = new CopyOnWriteArrayList<>();
    private final AtomicLong maxIndexThatCanBeStored = new AtomicLong(-1);

    /**
     * Load hash for a node with given index
     *
     * @param index the index to get hash for
     * @param notFoundValue the value to use if not found
     * @return loaded long or -1 if long is not stored
     */
    public long get(long index, long notFoundValue) {
        if (index <= maxIndexThatCanBeStored.get()) {
            int subIndex = (int)(index % NUM_LONGS_PER_CHUNK);
            return data.get((int) (index / NUM_LONGS_PER_CHUNK)).get(subIndex);
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
                data.add(ByteBuffer.allocateDirect(MEMORY_CHUNK_SIZE).asLongBuffer());
                currentValue += NUM_LONGS_PER_CHUNK;
            }
            return currentValue;
        });
        // get the right buffer
        int subIndex = (int)(index % NUM_LONGS_PER_CHUNK);
        data.get((int) (index / NUM_LONGS_PER_CHUNK)).put(subIndex, value);
    }

    /**
     * Get the current size of this OffHeapLongList, this is the most data that is stored in it
     */
    public long size() {
        return maxIndexThatCanBeStored.get()+1;
    }
}
