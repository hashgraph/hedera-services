package com.hedera.services.state.jasperdb.collections;

/**
 * Simple array list for triplets of longs.
 *
 * IMPORTANT this is not designed to be thread safe.
 *
 * IMPORTANT this is limited to a maximum of 700 million entries which is about 16Gb of RAM.
 *
 * TODO This should be a array of arrays class again so we do not pay the cost of copies on enlargement.
 */
public class ThreeLongsList {
    private long[] data;
    private int capacity;
    private int size = 0;

    /**
     * Construct a new ThreeLongsList with the default size of 4,000 triplets
     */
    public ThreeLongsList() {
        this(4_000);
    }

    /**
     * Construct a new ThreeLongsList with the chosen capacity
     *
     * @param capacity the initial number of triplets that can be stored
     */
    public ThreeLongsList(int capacity) {
        this.capacity = capacity;
        data = new long[capacity*3];
    }

    /**
     * Add a triplet of 3 longs
     *
     * @param l1 first long to add
     * @param l2 second long to add
     * @param l3 third long to add
     */
    public void add(long l1, long l2, long l3) {
        if (size == capacity) {
            if (capacity == Integer.MAX_VALUE) throw new ArrayIndexOutOfBoundsException(
                    "Can not expand ThreeLongsList as hit max array size in Java");
            int newCapacity = Math.min(data.length * 3, Integer.MAX_VALUE);
            long[] newItems = new long[newCapacity];
            System.arraycopy(this.data, 0, newItems, 0, Math.min(size*3, newCapacity));
            data = newItems;
            capacity = newCapacity/3;
        }
        int offset = size*3;
        data[offset] = l1;
        data[offset+1] = l2;
        data[offset+2] = l3;
        size ++;
    }

    /**
     * Get long triplet at given index
     *
     * @param index the index to get
     * @return array of the three longs in triplet
     */
    public long[] get(int index) {
        if (index < 0) throw new ArrayIndexOutOfBoundsException("Index is less than zero");
        if (index >= size) throw new ArrayIndexOutOfBoundsException("Index ["+index+"] is greater than size");
        int offset = index*3;
        return new long[]{data[offset],data[offset+1],data[offset+2]};
    }

    /**
     * Clear contents leaving capacity the same
     */
    public void clear() {
        this.size = 0;
    }

    /**
     * For each to iterate over all data
     *
     * @param handler callback
     */
    public void forEach(ThreeLongFunction handler) {
        for (int i = 0; i < size; i++) {
            int offset = i*3;
            handler.process(data[offset], data[offset+1], data[offset+2]);
        }
    }

    /**
     * Simple interface for a three longs function
     */
    public interface ThreeLongFunction {
        void process(long l1, long l2, long l3);
    }

    /**
     * Get the number of long triplets stored in this list
     */
    public int size() {
        return size;
    }
}
