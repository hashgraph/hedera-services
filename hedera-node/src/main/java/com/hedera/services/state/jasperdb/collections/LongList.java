package com.hedera.services.state.jasperdb.collections;

import com.hedera.services.state.jasperdb.files.DataFileCommon;

import java.util.Comparator;
import java.util.Spliterator;
import java.util.function.LongConsumer;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

/**
 * Interface for a simple list of longs. This is intentionally not java util list compatible because keeping the
 * functionality very simple we can implement a bunch of optimizations to allow cheep high concurrency without locks.
 *
 * The aim here is to have a concurrent expandable array with very fast lookups and fast concurrent appends.
 *
 * Some others have tried similar but different ideas
 * https://philosopherdeveloper.com/posts/how-to-build-a-thread-safe-lock-free-resizable-array.html
 */
public interface LongList {

    /**
     * Get the long with given index
     *
     * @param index         the index to get long for
     * @param notFoundValue the value to use if not found
     * @return loaded long or -1 if long is not stored
     */
    long get(long index, long notFoundValue);

    /**
     * Put a value at given index
     *
     * @param index the index of where to put value in list
     * @param value the value to store at index
     */
    void put(long index, long value);

    /**
     * Put a value at given index, if the old value matches oldValue
     *
     * @param index    the index of where to put value in list
     * @param oldValue only update if the current value matches this
     * @param newValue the value to store at index
     */
    boolean putIfEqual(long index, long oldValue, long newValue);

    /**
     * Get the current capacity of this OffHeapLongList, this is the most data that is stored in it
     */
    long capacity();

    /**
     * Get the current size of this OffHeapLongList, this is the most data that is stored in it
     */
    long size();

    /**
     * Create a stream over the data in this OffHeapLongList. This is designed for testing and may be inconsistent under
     * current modifications.
     */
    default LongStream stream()  {
        return StreamSupport.longStream(new LongListSpliterator(this), false);
    }

    // =================================================================================================================
    // LongListSpliterator Class used for stream() method

    /**
     * A Spliterator.OfLong based on long array spliterator.
     */
    final class LongListSpliterator implements Spliterator.OfLong {
        private final LongList longList;
        private final long fence;  // one past last index
        private final int characteristics;
        private long index;        // current index, modified on advance/split

        /**
         * Creates a spliterator covering the given OffHeapLongList
         *
         * @param longList the long list
         */
        public LongListSpliterator(LongList longList) {
            this(longList,0, longList.size());
        }

        /**
         * Creates a spliterator covering the given OffHeapLongList and range
         * @param longList the long list
         * @param origin the least index (inclusive) to cover
         * @param fence one past the greatest index to cover
         */
        public LongListSpliterator(LongList longList, long origin, long fence) {
            this.longList = longList;
            this.index = origin;
            this.fence = fence;
            this.characteristics = Spliterator.SIZED | Spliterator.SUBSIZED;
        }

        @Override
        public OfLong trySplit() {
            long lo = index, mid = (lo + fence) >>> 1;
            return (lo >= mid)
                    ? null
                    : new LongListSpliterator(longList, lo, index = mid);
        }

        @Override
        public void forEachRemaining(LongConsumer action) {
            LongList a; long i, hi; // hoist accesses and checks from loop
            if (action == null)
                throw new NullPointerException();
            if ((a = longList).size() >= (hi = fence) &&
                    (i = index) >= 0 && i < (index = hi)) {
                do { action.accept(a.get(i, DataFileCommon.NON_EXISTENT_DATA_LOCATION)); } while (++i < hi);
            }
        }

        @Override
        public boolean tryAdvance(LongConsumer action) {
            if (action == null)
                throw new NullPointerException();
            if (index >= 0 && index < fence) {
                action.accept(longList.get(index++,DataFileCommon.NON_EXISTENT_DATA_LOCATION));
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
