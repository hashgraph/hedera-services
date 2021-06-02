package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.crypto.Hash;

import java.io.Closeable;

/**
 * Defines the datasource for managing records and internal node
 * hashes.
 *
 * TODO Updates to the data source need to be buffered, such that
 * they are not flushed to disk unless the transaction completes
 * successfully!
 */
public interface VirtualDataSource<K, V> extends Closeable {

    /**
     * Gets the data from storage using a non-null key as the input.
     * The return value may be null.
     *
     * @param key A non-null key.
     * @return The value, or null if there is not one.
     */
    V getData(K key);

    /**
     * Writes data to disk. This may be asynchronous, but subsequent reads from
     * getData for this key must return the written value.
     *
     * @param key The non-null key.
     * @param data The value to write.
     */
    void writeData(K key, V data);

    void deleteData(K key);

    Path getPathForKey(K key);
    void setPathForKey(K key, Path path);

    /**
     * Gets the Hash of an internal node denoted by the given Path.
     * @param path A non-null path to the internal node
     * @return The Hash. If null, then there is no record of this node in storage (typically an error condition).
     */
    Hash getHash(Path path);

    /**
     * Writes the given hash to storage with the Path as the key. Used for internal nodes.
     * @param path The non-null Path.
     * @param hash The non-null hash associated with the internal node of this Path.
     */
    void writeHash(Path path, Hash hash);

    /**
     * Gets the details for a leaf node.
     * @param path A non-null path to the leaf node
     * @return The record. If null, then there is no record of this node in storage (typically an error condition).
     */
    VirtualRecord<K> getRecord(Path path);

    /**
     * Writes the given record to storage with the Path as the key. Used for leaf nodes.
     * @param path The non-null Path.
     * @param record The non-null record associated with the leaf node of this Path,
     *               or null if it is to be deleted.
     */
    void writeRecord(Path path, VirtualRecord<K> record);

    /**
     * Writes the path for the very last leaf to durable storage. This is needed for
     * an efficient implementation of the virtual tree.
     *
     * @param path The path of the very last leaf node. This can be null, if the tree is empty.
     */
    void writeLastLeafPath(Path path);

    /**
     * Gets the Path of the last leaf node. If there are no leaves, this will return null.
     *
     * @return The path to the last leaf node, or null if there are no leaves.
     */
    Path getLastLeafPath();

    /**
     * Writes the path for the very first leaf to durable storage. This is needed for
     * an efficient implementation of the virtual tree.
     *
     * @param path The path of the very first leaf node. This can be null, if the tree is empty.
     */
    void writeFirstLeafPath(Path path);

    /**
     * Gets the Path of the first leaf node. If there are no leaves, this will return null.
     *
     * @return The path to the first leaf node, or null if there are no leaves.
     */
    Path getFirstLeafPath();
}
