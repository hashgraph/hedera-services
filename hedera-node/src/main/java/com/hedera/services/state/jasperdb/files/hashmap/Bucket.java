package com.hedera.services.state.jasperdb.files.hashmap;

import com.hedera.services.state.jasperdb.files.DataFileOutputStream;
import com.swirlds.virtualmap.VirtualKey;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.hedera.services.state.jasperdb.files.hashmap.HalfDiskHashMap.*;

/**
 * Class for accessing the data in a bucket. This is designed to be used from a single thread.
 * <p>
 * Each bucket has a header containing:
 * - int - Bucket index in map hash index
 * - int - Count of keys stored
 * - long - pointer to next bucket if this one is full. TODO implement this
 * - Entry[] - array of entries
 * <p>
 * Each Entry contains:
 * - KEY_HASHCODE_SIZE(int/long) - key hash code
 * - value - the value of the key/value pair. It is here because it is fixed size
 * - optional int - key serialization version
 * - key data - can be fixed size of entryKeySize or variable size
 */
@SuppressWarnings("unused")
public final class Bucket<K extends VirtualKey> {
    private static final int BUCKET_INDEX_IN_HASH_MAP_SIZE = Integer.BYTES;
    private static final int BUCKET_ENTRY_COUNT_SIZE = Integer.BYTES;
    private static final int BUCKET_ENTRY_COUNT_OFFSET = BUCKET_INDEX_IN_HASH_MAP_SIZE;
    private static final int NEXT_BUCKET_OFFSET = BUCKET_ENTRY_COUNT_OFFSET + BUCKET_ENTRY_COUNT_SIZE;
    private final ByteBuffer bucketBuffer = ByteBuffer.allocate(BUCKET_SIZE);
    private final KeySerializer<K> keySerializer;
    private final int entryValueOffset = KEY_HASHCODE_SIZE;
    private int keySerializationVersion;

    /**
     * Create a new bucket
     *
     * @param keySerializer The serializer responsible for converting keys to/from bytes
     */
    Bucket(KeySerializer<K> keySerializer) {
        this.keySerializer = keySerializer;
    }

    /**
     * Reset for next use
     *
     * @return this bucket for each chaining
     */
    public Bucket<K> clear() {
        setBucketIndex(-1);
        // set 0 for entry count
        this.bucketBuffer.putInt(BUCKET_ENTRY_COUNT_OFFSET, 0);
        bucketBuffer.clear();
        return this;
    }

    /**
     * Set the serialization version to use for keys
     */
    public void setKeySerializationVersion(int keySerializationVersion) {
        this.keySerializationVersion = keySerializationVersion;
    }

    /**
     * Get the index for this bucket
     */
    public int getBucketIndex() {
        return bucketBuffer.getInt(0);
    }

    /**
     * Set the index for this bucket
     */
    public void setBucketIndex(int bucketIndex) {
        this.bucketBuffer.putInt(0, bucketIndex);
    }

    /**
     * Get the number of entries stored in this bucket
     */
    public int getBucketEntryCount() {
        return bucketBuffer.getInt(BUCKET_ENTRY_COUNT_OFFSET);
    }

    /**
     * Add one to the number of entries stored in this bucket
     */
    private void incrementBucketEntryCount() {
        this.bucketBuffer.putInt(BUCKET_ENTRY_COUNT_OFFSET, bucketBuffer.getInt(BUCKET_ENTRY_COUNT_OFFSET) + 1);
    }

    /**
     * Get the location of next bucket, when buckets overflow
     */
    public long getNextBucketLocation() {
        return bucketBuffer.getLong(NEXT_BUCKET_OFFSET);
    }

    /**
     * Set the location of next bucket, when buckets overflow
     * <p>
     * TODO still needs to be implemented over flow into a second bucket
     */
    public void setNextBucketLocation(long bucketLocation) {
        this.bucketBuffer.putLong(NEXT_BUCKET_OFFSET, bucketLocation);
    }

    /**
     * Get the buckets buffer reset ready for reading. Not this should not be changed.
     */
    public ByteBuffer getBucketBuffer() {
        return bucketBuffer.rewind();
    }

    /**
     * Find a value for given key
     *
     * @param keyHashCode   the int hash for the key
     * @param key           the key object
     * @param notFoundValue the long to return if the key is not found
     * @return the stored value for given key or notFoundValue if nothing is stored for the key
     * @throws IOException If there was a problem reading the value from file
     */
    public long findValue(int keyHashCode, K key, long notFoundValue) throws IOException {
        final var found = findEntryOffset(keyHashCode, key);
        if (found.found) {
            // yay! we found it
            return getValue(found.entryOffset);
        } else {
            return notFoundValue;
        }
    }

    /**
     * Put a key/value entry into this bucket.
     *
     * @param key   the entry key
     * @param value the entry value
     */
    public void putValue(int keyHashCode, K key, long value) {
        try {
            // scan over all existing key/value entries and see if there is already one for this key. If there is
            // then update it, otherwise we have at least worked out the entryOffset for the end of existing entries
            // and can use that for appending a new entry if there is room
            final var result = findEntryOffset(keyHashCode, key);
            if (result.found) {
                // yay! we found it, so update value
                setValue(result.entryOffset, value);
                return;
            }
            // so there are no entries that match the key. Check if there is enough space for another entry in this bucket
            final int currentEntryCount = getBucketEntryCount();
            // TODO if we serialized the key to a temp ByteBuffer then we could do a check to see if there is enough
            //  space for this specific key rather than using max size. This might allow a extra one or two entries
            //  in a bucket at the cost of serializing to a temp buffer and writing that buffer into the bucket.
            if (keySerializer.isVariableSize()) {
                DataFileOutputStream dataFileOutputStream = new DataFileOutputStream(keySerializer.getTypicalSerializedSize()); // TODO keep thread local and reuse
                dataFileOutputStream.reset();
                key.serialize(dataFileOutputStream);
                dataFileOutputStream.flush();
                int keySizeBytes = dataFileOutputStream.bytesWritten();
                if ((result.entryOffset + KEY_HASHCODE_SIZE + VALUE_SIZE + keySizeBytes) < BUCKET_SIZE) {
                    // add a new entry
                    bucketBuffer.position(result.entryOffset);
                    bucketBuffer.putInt(keyHashCode);
                    bucketBuffer.putLong(value);
                    dataFileOutputStream.writeTo(bucketBuffer);
                    // increment count
                    incrementBucketEntryCount();
                } else {
                    // bucket is full
                    // TODO expand into another bucket
                    throw new IllegalStateException("Bucket is full, could not store key: " + key);
                }
            } else {

                if ((result.entryOffset + KEY_HASHCODE_SIZE + VALUE_SIZE + keySerializer.getSerializedSize()) < BUCKET_SIZE) {
                    // add a new entry
                    bucketBuffer.position(result.entryOffset);
                    bucketBuffer.putInt(keyHashCode);
                    bucketBuffer.putLong(value);
                    key.serialize(bucketBuffer);
                    // increment count
                    incrementBucketEntryCount();
                } else {
                    // bucket is full
                    // TODO expand into another bucket
                    throw new IllegalStateException("Bucket is full, could not store key: " + key);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Find the offset in bucket for an entry matching the given key, if not found then just return the offset for
     * the end of all entries.
     *
     * @param keyHashCode hash code for the key to search for
     * @param key         the key to search for
     * @return either true and offset for found key entry or false and offset for end of all entries in this bucket
     * @throws IOException If there was a problem reading bucket
     */
    private FindResult findEntryOffset(int keyHashCode, K key) throws IOException {
        int entryCount = getBucketEntryCount();
        int entryOffset = BUCKET_HEADER_SIZE;
        for (int i = 0; i < entryCount; i++) {
            int readHashCode = bucketBuffer.getInt(entryOffset);
            if (readHashCode == keyHashCode) {
                // now check the full key
                bucketBuffer.position(entryOffset + KEY_HASHCODE_SIZE + VALUE_SIZE);
                if (key.equals(bucketBuffer, keySerializationVersion)) {
                    // yay! we found it
                    return new FindResult(entryOffset, true);
                }
            }
            // now read the key size so we can jump
            // TODO ideally we would only read this once and use in key.equals and here
            // TODO also could avoid doing this for last entry
            int keySize = getKeySize(entryOffset);
            // move to next entry
            entryOffset += KEY_HASHCODE_SIZE + VALUE_SIZE + keySize;
        }
        return new FindResult(entryOffset, false);
    }

    /**
     * Read the size of the key for an entry
     *
     * @param entryOffset the offset to start of entry
     * @return the size of the key in bytes
     */
    private int getKeySize(int entryOffset) {
        if (!keySerializer.isVariableSize()) return keySerializer.getSerializedSize();
        bucketBuffer.position(entryOffset + KEY_HASHCODE_SIZE + VALUE_SIZE);
        return keySerializer.deserializeKeySize(bucketBuffer);
    }

    /**
     * Read a key for a given entry
     *
     * @param entryOffset the offset for the entry
     * @return The key deserialized from bucket
     * @throws IOException If there was a problem reading or deserializing the key
     */
    private K getKey(int entryOffset) throws IOException {
        bucketBuffer.position(entryOffset + Integer.BYTES);
        return keySerializer.deserialize(bucketBuffer,keySerializationVersion);
    }

    /**
     * Read a value for a given entry
     *
     * @param entryOffset the offset for the entry
     * @return the value stored in given entry
     */
    private long getValue(int entryOffset) {
        return bucketBuffer.getLong(entryOffset + entryValueOffset);
    }

    /**
     * Read a value for a given entry
     *
     * @param entryOffset the offset for the entry
     * @param value       the value to set for entry
     */
    private void setValue(int entryOffset, long value) {
        bucketBuffer.putLong(entryOffset + entryValueOffset, value);
    }

    /**
     * toString for debugging
     */
    @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
    @Override
    public String toString() {
        final int entryCount = getBucketEntryCount();
        StringBuilder sb = new StringBuilder(
                "Bucket{bucketIndex=" + getBucketIndex() + ", entryCount=" + entryCount + ", nextBucketLocation=" + getNextBucketLocation() + "\n"
        );
        try {
            int entryOffset = BUCKET_HEADER_SIZE;
            for (int i = 0; i < entryCount; i++) {
                final int keySize = getKeySize(entryOffset);
                bucketBuffer.position(entryOffset);
                final int readHash = bucketBuffer.getInt();
                final long value = bucketBuffer.getLong();
                final K key = getKey(entryOffset);
                sb.append("    ENTRY[" + i + "] value= " + value + " keyHashCode=" + readHash + " keyVer=" + keySerializationVersion +
                        " key=" + key + " keySize=" + keySize + "\n");
                entryOffset += KEY_HASHCODE_SIZE + VALUE_SIZE + keySize;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        bucketBuffer.clear();
        sb.append("} RAW DATA = ");
        for (byte b : bucketBuffer.array()) {
            sb.append(String.format("%02X ", b).toUpperCase());
        }
        return sb.toString();
    }
}
