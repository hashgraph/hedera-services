package com.hedera.services.state.jasperdb.collections;

import com.hedera.services.state.jasperdb.files.DataFileCommon;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

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
    /** Atomic long for the number of data items currently stored */
    private final AtomicLong currentMaxIndex = new AtomicLong(-1);

    /**
     * Offset of the {@code java.nio.Buffer#address} field.
     */
    public static final long BYTE_BUFFER_ADDRESS_FIELD_OFFSET;

    private static final Unsafe unsafe;
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
            BYTE_BUFFER_ADDRESS_FIELD_OFFSET = unsafe.objectFieldOffset(Buffer.class.getDeclaredField("address"));
        } catch (Exception ex) { throw new Error(ex); }
    }

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
        expandIfNeeded(index);
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
    public boolean putIfEqual(long index, long oldValue, long newValue) {
        expandIfNeeded(index);
        // get the right buffer
        final int subIndex = (int)(index % numLongsPerChunk);
        final LongBuffer chunk = data.get((int) (index / numLongsPerChunk));
        // get native memory address
        long chunkPointer = address(chunk);
        int subIndexBytes = subIndex * Long.BYTES;
        boundsCheck0(subIndexBytes, Long.BYTES);
        boolean result = unsafe.compareAndSwapLong(null, chunkPointer + subIndexBytes, oldValue, newValue);
        return result;
    }

    private void boundsCheck0(final int index, final int length) {
        final long resultingPosition = index + (long)length;
        if (index < 0 || length < 0 || resultingPosition > memoryChunkSize) {
            throw new IndexOutOfBoundsException("index=" + index + " length=" + length + " capacity=" + memoryChunkSize);
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
        return unsafe.getLong(buffer, BYTE_BUFFER_ADDRESS_FIELD_OFFSET);
    }


    /**
     * Put a value at given index, if the old value matches oldValue
     *
     * @param index the index of where to put value in list
     * @param oldValue only update if the current value matches this
     * @param newValue the value to store at index
     */
    public void putIfEqualSafe(long index, long oldValue, long newValue) {
        expandIfNeeded(index);
        // get the right buffer
        final int subIndex = (int)(index % numLongsPerChunk);
        final LongBuffer chunk = data.get((int) (index / numLongsPerChunk));
        final long currentValue = chunk.get(subIndex);
        if (currentValue == oldValue) {
            chunk.put(subIndex, newValue);
        }
    }

    /**
     * Get the current capacity of this OffHeapLongList, this is the most data that is stored in it
     */
    public long capacity() {
        return maxIndexThatCanBeStored.get()+1;
    }

    /**
     * Get the current size of this OffHeapLongList, this is the most data that is stored in it
     */
    public long size() {
        return currentMaxIndex.get()+1;
    }

    private void expandIfNeeded(long newIndex) {
        currentMaxIndex.getAndUpdate(oldMaxIndex -> Math.max(oldMaxIndex,newIndex));
        // expand data if needed
        maxIndexThatCanBeStored.updateAndGet(currentValue -> {
            while (newIndex > currentValue) { // need to expand
                ByteBuffer directBuffer = ByteBuffer.allocateDirect(memoryChunkSize);
                directBuffer.order(ByteOrder.nativeOrder());
                data.add(directBuffer.asLongBuffer());
                currentValue += numLongsPerChunk;
            }
            return currentValue;
        });
    }

    /**
     * Create a stream over the data in this OffHeapLongList. This is designed for testing and may be inconsistent under
     * current modifications.
     */
    public LongStream stream() {
        return StreamSupport.longStream(new OffHeapLongListSpliterator(this), false);
    }

    /**
     * A Spliterator.OfLong based on long array spliterator.
     */
    static final class OffHeapLongListSpliterator implements Spliterator.OfLong {
        private OffHeapLongList offHeapLongList;
        private long index;        // current index, modified on advance/split
        private final long fence;  // one past last index
        private final int characteristics;
        /**
         * Creates a spliterator covering the given OffHeapLongList
         *
         * @param offHeapLongList the long list
         */
        public OffHeapLongListSpliterator(OffHeapLongList offHeapLongList) {
            this(offHeapLongList,0,offHeapLongList.size());
        }

        /**
         * Creates a spliterator covering the given OffHeapLongList and range
         * @param offHeapLongList the long list
         * @param origin the least index (inclusive) to cover
         * @param fence one past the greatest index to cover
         */
        public OffHeapLongListSpliterator(OffHeapLongList offHeapLongList, long origin, long fence) {
            this.offHeapLongList = offHeapLongList;
            this.index = origin;
            this.fence = fence;
            this.characteristics = Spliterator.SIZED | Spliterator.SUBSIZED;
        }

        @Override
        public OfLong trySplit() {
            long lo = index, mid = (lo + fence) >>> 1;
            return (lo >= mid)
                    ? null
                    : new OffHeapLongListSpliterator(offHeapLongList, lo, index = mid);
        }

        @Override
        public void forEachRemaining(LongConsumer action) {
            OffHeapLongList a; long i, hi; // hoist accesses and checks from loop
            if (action == null)
                throw new NullPointerException();
            if ((a = offHeapLongList).size() >= (hi = fence) &&
                    (i = index) >= 0 && i < (index = hi)) {
                do { action.accept(a.get(i, DataFileCommon.NON_EXISTENT_DATA_LOCATION)); } while (++i < hi);
            }
        }

        @Override
        public boolean tryAdvance(LongConsumer action) {
            if (action == null)
                throw new NullPointerException();
            if (index >= 0 && index < fence) {
                action.accept(offHeapLongList.get(index++,DataFileCommon.NON_EXISTENT_DATA_LOCATION));
                return true;
            }
            return false;
        }

        @Override
        public long estimateSize() { return (fence - index); }

        @Override
        public int characteristics() {
            return characteristics;
        }

        @Override
        public Comparator<? super Long> getComparator() {
            if (hasCharacteristics(Spliterator.SORTED))
                return null;
            throw new IllegalStateException();
        }
    }
}
