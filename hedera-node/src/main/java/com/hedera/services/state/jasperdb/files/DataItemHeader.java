package com.hedera.services.state.jasperdb.files;

/**
 * Each data item needs a header containing at least a numeric key. The key can be any size from byte to long. The size
 * can be stored for variable size data or this can be constructed with a fixed size.
 */
public class DataItemHeader {
    /** the size of bytes for the data item, this includes the data item header. */
    private final int sizeBytes;
    /** the key for data item, the key may be smaller than long up to size of long */
    private final long key;

    public DataItemHeader(int sizeBytes, long key) {
        this.sizeBytes = sizeBytes;
        this.key = key;
    }

    /**
     * Get the size of bytes for the data item, this includes the data item header.
     */
    public int getSizeBytes() {
        return sizeBytes;
    }

    /**
     * Get the key for data item, the key may be smaller than long up to size of long
     */
    public long getKey() {
        return key;
    }

    @Override
    public String toString() {
        return "DataItemHeader{" +
                "size=" + sizeBytes +
                ", key=" + key +
                '}';
    }
}
