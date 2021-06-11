package com.hedera.services.state.merkle.virtual.persistence;

import com.hedera.services.state.merkle.virtual.VirtualKey;
import com.hedera.services.state.merkle.virtual.VirtualValue;

import java.io.Closeable;

/**
 * Defines the datasource for persisting information about the virtual tree to disk.
 */
public interface VirtualDataSource extends Closeable {
    public byte[] loadParentHash(long parentPath);
    public VirtualRecord loadLeaf(long leafPath);
    public VirtualRecord loadLeaf(VirtualKey leafKey);

    public VirtualValue getLeafValue(VirtualKey leafKey);

    public void saveParent(long parentPath, byte[] hash);
    public void saveLeaf(VirtualRecord leaf);

    public void deleteParent(long parentPath);
    public void deleteLeaf(VirtualRecord leaf);

    /**
     * Writes the path for the very last leaf to durable storage. This is needed for
     * an efficient implementation of the virtual tree.
     *
     * @param path The path of the very last leaf node. This can be null, if the tree is empty.
     */
    void writeLastLeafPath(long path);

    /**
     * Gets the Path of the last leaf node. If there are no leaves, this will return null.
     *
     * @return The path to the last leaf node, or null if there are no leaves.
     */
    long getLastLeafPath();

    /**
     * Writes the path for the very first leaf to durable storage. This is needed for
     * an efficient implementation of the virtual tree.
     *
     * @param path The path of the very first leaf node. This can be null, if the tree is empty.
     */
    void writeFirstLeafPath(long path);

    /**
     * Gets the Path of the first leaf node. If there are no leaves, this will return null.
     *
     * @return The path to the first leaf node, or null if there are no leaves.
     */
    long getFirstLeafPath();

}
