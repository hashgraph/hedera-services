package com.hedera.services.state.merkle.v2.persistance;

import com.swirlds.fcmap.VKey;

import java.io.IOException;

/**
 * A simple subset of Map that takes a key K and returns longs. It can also throw IOExceptions on operations.
 *
 * @param <K> the type for key
 */
public interface LongIndex<K extends VKey> {

    /**
     * Put a key into map
     *
     * @param key a non-null key
     * @param value a value that can be null
     * @throws IOException if there was a problem storing the key/value
     */
    void put(K key, long value) throws IOException;

    /**
     * Get a value from map
     *
     * @param key a non-null key
     * @return the found value or null if one was not stored
     * @throws IOException if there was a problem get the key/value
     */
    Long get(K key) throws IOException;

    /**
     * Remove a key/value from map
     *
     * @param key a non-null key
     * @return the old value if there was one or null
     * @throws IOException if there was a problem removing the key/value
     */
    Long remove(K key) throws IOException;

    /**
     * Close this index
     *
     * @throws IOException if there was a problem closing
     */
    void close() throws IOException;
}
