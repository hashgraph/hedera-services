package com.hedera.services.state.jasperdb.collections;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * An on-heap in memory store of longs, it stores them in AtomicLongArrays and adds arrays as needed.
 * It uses memory from 0 to the highest index used. If your use case starts at a high minimum index then this will
 * waste a load of ram.
 *
 * It is thread safe for concurrent access.
 */
@SuppressWarnings("unused")
public final class LongListHeap implements LongList {
    /** Constant for 1Mb */
    private static final int MB = 1024*1024;
    /** Number of longs we can store in each memory chunk */
    private final int numLongsPerChunk;
    /** Copy on write array of our memory chunks */
    private final CopyOnWriteArrayList<AtomicLongArray> data = new CopyOnWriteArrayList<>();
    /** Atomic long we use for keeping track of the capacity we have available in all current chunks */
    private final AtomicLong maxIndexThatCanBeStored = new AtomicLong(-1);
    /** Atomic long for the number of data items currently stored */
    private final AtomicLong currentMaxIndex = new AtomicLong(-1);

    /**
     * Construct a new OffHeapLongList with the default 8Mb chunk size
     */
    public LongListHeap() {
        this(8);
    }

    /**
     * Construct a new OffHeapLongList with the specified chunk size
     *
     * @param chunkSizeInMb size for each chunk of memory to allocate. Max 16Gb = 16,384Mb
     */
    public LongListHeap(int chunkSizeInMb) {
         numLongsPerChunk = (chunkSizeInMb*MB) / Long.BYTES;
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
        data.get((int) (index / numLongsPerChunk)).set(subIndex, value);
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
        final var chunk = data.get((int) (index / numLongsPerChunk));
        // set
        return chunk.compareAndSet(subIndex,oldValue,newValue);
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
        currentMaxIndex.getAndUpdate(oldMaxIndex -> Math.max(oldMaxIndex,newIndex));
        // expand data if needed
        maxIndexThatCanBeStored.updateAndGet(currentValue -> {
            while (newIndex > currentValue) { // need to expand
                data.add(new AtomicLongArray(numLongsPerChunk));
                currentValue += numLongsPerChunk;
            }
            return currentValue;
        });
    }

}
