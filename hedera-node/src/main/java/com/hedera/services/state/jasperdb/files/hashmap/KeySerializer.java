package com.hedera.services.state.jasperdb.files.hashmap;

import com.hedera.services.state.jasperdb.files.BaseSerializer;

import java.nio.ByteBuffer;

/**
 * Interface for serializers of hash map keys. This is very similar to a DataItemSerializer but only serializes a key.
 * The key can serialize to fixed number or variable number of bytes.
 *
 * @param <K> the class for a key
 */
public interface KeySerializer<K> extends BaseSerializer<K> {

    /**
     * Deserialize key size from the given byte buffer
     *
     * @param buffer Buffer to read from
     * @return The number of bytes used to store the key, including for storing the key size if needed.
     */
    int deserializeKeySize(ByteBuffer buffer);
}
