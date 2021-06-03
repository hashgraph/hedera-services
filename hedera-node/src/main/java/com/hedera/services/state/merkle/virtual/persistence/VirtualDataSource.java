package com.hedera.services.state.merkle.virtual.persistence;

import com.hedera.services.state.merkle.virtual.VirtualKey;
import com.hedera.services.state.merkle.virtual.tree.VirtualTreePath;

import java.io.Closeable;
import java.nio.file.Path;

/**
 * Defines the datasource for persisting information about the virtual tree to disk,
 * or some other persistence engine. Every node in the virtual tree is represented
 * as a {@link VirtualRecord}. The {@code VirtualDataSource} is transactional,
 * having a {@link #commit()} method which must be called to persist the current
 * transaction state.
 */
public interface VirtualDataSource extends Closeable {
    /**
     * Gets the {@link VirtualRecord} for the node associated with the given key.
     * This record is guaranteed to refer to a leaf node.
     *
     * @param key The key. May be null, but shouldn't be.
     * @return The VirtualRecord for that key, or null if there isn't one.
     */
    VirtualRecord getRecord(VirtualKey key);

    /**
     * Gets the {@link VirtualRecord} for the given node Path. If there is no record
     * of this node (perhaps the node was never created or recorded), then
     * null is returned.
     *
     * @param path The path to use for finding the record. May be null, but shouldn't be.
     * @return Gets the virtual record for this path. Returns null if there is no such record.
     */
    VirtualRecord getRecord(VirtualTreePath path);

    /**
     * Deletes the record.
     *
     * @param record A non-null record.
     */
    void deleteRecord(VirtualRecord record);

    /**
     * Deletes the record.
     *
     * @param path A non-null path.
     */
    void deleteRecord(VirtualTreePath path);

    /**
     * Adds or updates the given record.
     *
     * @param record A non-null record.
     */
    void setRecord(VirtualRecord record);

    /**
     * Writes the path for the very last leaf to durable storage. This is needed for
     * an efficient implementation of the virtual tree.
     *
     * @param path The path of the very last leaf node. This can be null, if the tree is empty.
     */
    void writeLastLeafPath(VirtualTreePath path);

    /**
     * Gets the Path of the last leaf node. If there are no leaves, this will return null.
     *
     * @return The path to the last leaf node, or null if there are no leaves.
     */
    VirtualTreePath getLastLeafPath();

    /**
     * Writes the path for the very first leaf to durable storage. This is needed for
     * an efficient implementation of the virtual tree.
     *
     * @param path The path of the very first leaf node. This can be null, if the tree is empty.
     */
    void writeFirstLeafPath(VirtualTreePath path);

    /**
     * Gets the Path of the first leaf node. If there are no leaves, this will return null.
     *
     * @return The path to the first leaf node, or null if there are no leaves.
     */
    VirtualTreePath getFirstLeafPath();

}
