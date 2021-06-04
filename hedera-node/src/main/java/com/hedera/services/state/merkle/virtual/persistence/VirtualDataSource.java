package com.hedera.services.state.merkle.virtual.persistence;

import com.hedera.services.state.merkle.virtual.VirtualKey;
import com.hedera.services.state.merkle.virtual.VirtualValue;
import com.hedera.services.state.merkle.virtual.tree.VirtualTreeInternal;
import com.hedera.services.state.merkle.virtual.tree.VirtualTreeLeaf;
import com.hedera.services.state.merkle.virtual.tree.VirtualTreePath;

import java.io.Closeable;
import java.nio.file.Path;

/**
 * Defines the datasource for persisting information about the virtual tree to disk.
 */
public interface VirtualDataSource extends Closeable {
    public VirtualTreeInternal load(long parentPathId);
    public VirtualTreeLeaf load(VirtualKey leafKey);

    public VirtualValue get(VirtualKey leafKey);

    public void save(VirtualTreeInternal parent);
    public void save(VirtualTreeLeaf leaf);

    public void delete(VirtualTreeInternal parent);
    public void delete(VirtualTreeLeaf leaf);

    /**
     * Writes the path for the very last leaf to durable storage. This is needed for
     * an efficient implementation of the virtual tree.
     *
     * @param path The path of the very last leaf node. This can be null, if the tree is empty.
     */
    void writeLastLeafPath(long pathId);

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
    void writeFirstLeafPath(long pathId);

    /**
     * Gets the Path of the first leaf node. If there are no leaves, this will return null.
     *
     * @return The path to the first leaf node, or null if there are no leaves.
     */
    long getFirstLeafPath();

}
