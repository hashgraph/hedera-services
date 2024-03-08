/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.files.hashmap;

import static com.hedera.pbj.runtime.ProtoParserTools.TAG_FIELD_OFFSET;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.merkledb.files.hashmap.HalfDiskHashMap.INVALID_VALUE;

import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.virtualmap.VirtualKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 */
public final class ParsedBucket<K extends VirtualKey> extends Bucket<K> {

    private static final Logger logger = LogManager.getLogger(ParsedBucket.class);

    /** Bucket index */
    private int bucketIndex = 0;

    /** List of bucket entries in this bucket */
    private final List<BucketEntry> entries = new ArrayList<>(64);

    /**
     * Create a new bucket with the default size.
     *
     * @param keySerializer The serializer responsible for converting keys to/from bytes
     */
    ParsedBucket(final KeySerializer<K> keySerializer) {
        this(keySerializer, null);
    }

    /**
     * Create a new bucket with the default size.
     *
     * @param keySerializer The serializer responsible for converting keys to/from bytes
     */
    ParsedBucket(final KeySerializer<K> keySerializer, final ReusableBucketPool<K> bucketPool) {
        super(keySerializer, bucketPool);
    }

    /**
     * Reset for next use
     */
    public void clear() {
        bucketIndex = 0;
        if (entries != null) {
            entries.clear();
        }
    }

    /** Get the index for this bucket */
    public int getBucketIndex() {
        return bucketIndex;
    }

    /** Set the index for this bucket */
    public void setBucketIndex(int index) {
        bucketIndex = index;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Get the number of entries stored in this bucket */
    public int getBucketEntryCount() {
        return entries.size();
    }

    /** Get the size of this bucket in bytes, including header */
    public int sizeInBytes() {
        int size = 0;
        // Include bucket index even if it has default value (zero)
        size += ProtoWriterTools.sizeOfTag(FIELD_BUCKET_INDEX, ProtoConstants.WIRE_TYPE_FIXED_32_BIT) + Integer.BYTES;
        for (final BucketEntry entry : entries) {
            size += ProtoWriterTools.sizeOfDelimited(FIELD_BUCKET_ENTRIES, entry.sizeInBytes());
        }
        return size;
    }

    /**
     * Find a value for given key
     *
     * @param keyHashCode the int hash for the key
     * @param key the key object
     * @param notFoundValue the long to return if the key is not found
     * @return the stored value for given key or notFoundValue if nothing is stored for the key
     * @throws IOException If there was a problem reading the value from file
     */
    public long findValue(final int keyHashCode, final K key, final long notFoundValue) throws IOException {
        final int entryIndex = findEntryIndex(keyHashCode, key);
        if (entryIndex >= 0) {
            // yay! we found it
            return entries.get(entryIndex).getValue();
        } else {
            return notFoundValue;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putValue(final K key, final long oldValue, final long value) {
        final boolean needCheckOldValue = oldValue != INVALID_VALUE;
        final int keyHashCode = key.hashCode();
        try {
            final int entryIndex = findEntryIndex(keyHashCode, key);
            if (value == INVALID_VALUE) {
                if (entryIndex >= 0) { // if found
                    final BucketEntry entry = entries.get(entryIndex);
                    if (needCheckOldValue && (oldValue != entry.getValue())) {
                        return;
                    }
                    entries.remove(entryIndex);
                } else {
                    // entry not found, nothing to delete
                }
                return;
            }
            if (entryIndex >= 0) {
                // yay! we found it, so update value
                final BucketEntry entry = entries.get(entryIndex);
                if (needCheckOldValue && (oldValue != entry.getValue())) {
                    return;
                }
                entry.setValue(value);
            } else {
                if (needCheckOldValue) {
                    return;
                }
                final BucketEntry newEntry = new BucketEntry(keyHashCode, value, key);
                entries.add(newEntry);
                checkLargestBucket(entries.size());
            }
        } catch (IOException e) {
            logger.error(EXCEPTION.getMarker(), "Failed putting key={} value={} in a bucket", key, value, e);
            throw new UncheckedIOException(e);
        }
    }

    public void readFrom(final ReadableSequentialData in) {
        // defaults
        bucketIndex = 0;
        entries.clear();

        int entriesCount = 0;
        while (in.hasRemaining()) {
            final int tag = in.readVarInt(false);
            final int fieldNum = tag >> TAG_FIELD_OFFSET;
            if (fieldNum == FIELD_BUCKET_INDEX.number()) {
                bucketIndex = in.readInt();
            } else if (fieldNum == FIELD_BUCKET_ENTRIES.number()) {
                final int entryBytesSize = in.readVarInt(false);
                final long oldLimit = in.limit();
                in.limit(in.position() + entryBytesSize);
                entries.add(new BucketEntry(in));
                in.limit(oldLimit);
                entriesCount++;
            } else {
                throw new IllegalArgumentException("Unknown bucket field: " + fieldNum);
            }
        }

        checkLargestBucket(entriesCount);
    }

    void readFrom(final ByteBuffer buffer) throws IOException {
        bucketIndex = buffer.getInt();
        buffer.getInt(); // skip the size
        final int entriesCount = buffer.getInt();
        for (int i = 0; i < entriesCount; i++) {
            entries.add(new BucketEntry(buffer));
        }
    }

    public void writeTo(final WritableSequentialData out) {
        // Bucket index is not optional, write the value even if default (zero)
        ProtoWriterTools.writeTag(out, FIELD_BUCKET_INDEX);
        out.writeInt(bucketIndex);
        for (final BucketEntry entry : entries) {
            ProtoWriterTools.writeTag(out, FIELD_BUCKET_ENTRIES);
            out.writeVarInt(entry.sizeInBytes(), false);
            entry.writeTo(out);
        }
    }

    void writeTo(final ByteBuffer buffer) throws IOException {
        final int initialPos = buffer.position();
        buffer.putInt(bucketIndex);
        buffer.putInt(0); // size, will be updated later
        buffer.putInt(entries.size());
        for (final BucketEntry entry : entries) {
            buffer.putInt(entry.getHashCode());
            buffer.putLong(entry.getValue());
            keySerializer.serialize(entry.getKey(), buffer);
        }
        final int finalPos = buffer.position();
        final int serializedSize = finalPos - initialPos;
        buffer.putInt(initialPos + Integer.BYTES, serializedSize);
    }

    // =================================================================================================================
    // Private API

    private int findEntryIndex(final int keyHashCode, final K key) throws IOException {
        final int entryCount = entries.size();
        for (int index = 0; index < entryCount; index++) {
            final BucketEntry entry = entries.get(index);
            if (keyHashCode == entry.getHashCode()) {
                if (entry.getKey().equals(key)) {
                    return index;
                }
            }
        }
        return -1;
    }

    /** toString for debugging */
    @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
    @Override
    public String toString() {
        final int entryCount = getBucketEntryCount();
        final int size = sizeInBytes();
        final StringBuilder sb = new StringBuilder("ParsedBucket{bucketIndex=" + getBucketIndex() + ", entryCount="
                + entryCount + ", size=" + size + "\n");
        for (int i = 0; i < entryCount; i++) {
            final BucketEntry entry = entries.get(i);
            final int hashCode = entry.getHashCode();
            final long value = entry.getValue();
            final K key = entry.getKey();
            sb.append("    ENTRY[" + i + "] value= " + value + " keyHashCode=" + hashCode + " key=" + key + "\n");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * A single entry in a bucket, which contains key hash code, value (usually, path), and
     * full serialized key. A bucket may contain multiple such entries.
     *
     * <p>This class would be a record, if it was immutable. However, when a value is updated
     * in a bucket, and a bucket entry already exists for the same key, instead of creating
     * a new entry, we just update the value in the existing entry.
     */
    public class BucketEntry {

        /** Key hash code */
        private final int hashCode;
        /** Long value. May be updated */
        private long value;
        /** Key */
        private final K key;

        /** Creates new bucket entry from hash code, value, and serialized key bytes */
        public BucketEntry(final int hashCode, final long value, @NonNull final K key) {
            this.hashCode = hashCode;
            this.value = value;
            this.key = key;
        }

        /** Creates new bucket entry by reading its fields from the given protobuf buffer */
        public BucketEntry(final ReadableSequentialData entryData) {
            // defaults
            int hashCode = 0;
            long value = 0;
            K key = null;

            // read fields
            while (entryData.hasRemaining()) {
                final int tag = entryData.readVarInt(false);
                final int fieldNum = tag >> TAG_FIELD_OFFSET;
                if (fieldNum == FIELD_BUCKETENTRY_HASHCODE.number()) {
                    hashCode = entryData.readInt();
                } else if (fieldNum == FIELD_BUCKETENTRY_VALUE.number()) {
                    value = entryData.readLong();
                } else if (fieldNum == FIELD_BUCKETENTRY_KEYBYTES.number()) {
                    final int bytesSize = entryData.readVarInt(false);
                    long oldLimit = entryData.limit();
                    entryData.limit(entryData.position() + bytesSize);
                    key = keySerializer.deserialize(entryData);
                    entryData.limit(oldLimit);
                } else {
                    throw new IllegalArgumentException("Unknown bucket entry field: " + fieldNum);
                }
            }

            // check required fields
            if (key == null) {
                throw new IllegalArgumentException("Null key for bucket entry");
            }

            this.hashCode = hashCode;
            this.value = value;
            this.key = key;
        }

        /** Creates new bucket entry by reading its fields from the given binary buffer */
        public BucketEntry(final ByteBuffer buffer) throws IOException {
            hashCode = buffer.getInt();
            value = buffer.getLong();
            // This is going to be somewhat slow. A possible workaround is to re-introduce
            // KeySerializer.deserializeKeySize() method and use it here to read raw key bytes
            // as a byte buffer. In this case, either entry key is not null, or key bytes as
            // BufferedData is not null, or key bytes as ByteBuffer is not null
            key = keySerializer.deserialize(buffer, 0);
        }

        public int getHashCode() {
            return hashCode;
        }

        public long getValue() {
            return value;
        }

        public void setValue(long value) {
            this.value = value;
        }

        public K getKey() {
            return key;
        }

        public int sizeInBytes() {
            int size = 0;
            size += ProtoWriterTools.sizeOfTag(FIELD_BUCKETENTRY_HASHCODE, ProtoConstants.WIRE_TYPE_FIXED_32_BIT)
                    + Integer.BYTES;
            if (value != 0) {
                size += ProtoWriterTools.sizeOfTag(FIELD_BUCKETENTRY_VALUE, ProtoConstants.WIRE_TYPE_FIXED_64_BIT)
                        + Long.BYTES;
            }
            size += ProtoWriterTools.sizeOfDelimited(FIELD_BUCKETENTRY_KEYBYTES, keySerializer.getSerializedSize(key));
            return size;
        }

        public void writeTo(final WritableSequentialData out) {
            ProtoWriterTools.writeTag(out, FIELD_BUCKETENTRY_HASHCODE);
            out.writeInt(hashCode);
            if (value != 0) {
                ProtoWriterTools.writeTag(out, FIELD_BUCKETENTRY_VALUE);
                out.writeLong(value);
            }
            ProtoWriterTools.writeDelimited(
                    out,
                    FIELD_BUCKETENTRY_KEYBYTES,
                    keySerializer.getSerializedSize(key),
                    o -> keySerializer.serialize(key, o));
        }
    }
}
