package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.AbstractHashable;
import java.util.Objects;

/**
 * A leaf node in the virtual tree. Every leave node has a single parent, but no children.
 * The parent can change, there are times when the tree is modified such that the node moves
 * around. This leaf node, if instantiated, has a reference to the data source so it can handle
 * saving data and information about itself to the datasource, such as when its path changes,
 * or its hash.
 */
public class VirtualTreeLeaf extends AbstractHashable implements VirtualTreeNode {

    /**
     * The dataSource that saves all the important information about
     * this tree leaf.
     */
    private final VirtualDataSource dataSource;

    /**
     * The path from the root to this node. This path can and will change as
     * the tree is modified (leaves added and removed). Whenever it changes,
     * we have to update the dataSource accordingly.
     */
    private Path path;

    /**
     * The reference to the current parent. Any node attached to a graph
     * will have a non-null parent. The pointer can and will change over
     * time as the tree is modified.
     */
    private VirtualTreeInternal parent;

    /**
     * The VirtualRecord that represents this leaf.
     */
    private final VirtualRecord record;

    /**
     * The data associated with this leaf node. The data should never be null,
     * but can change over time. It should be a fixed 256-byte array.
     */
    private Block data;

    public VirtualTreeLeaf(VirtualDataSource dataSource, VirtualRecord record) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.record = Objects.requireNonNull(record);
    }

    public void setData(Block data) {
        this.data = data;
    }

    public Block getData() {
        return data;
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public void adopt(Path path, VirtualTreeInternal parent) {
        // If the path has changed from what we expected in the record, then update the DB.
        if (!record.getPath().equals(path)) {
            dataSource.writeRecord(path, new VirtualRecord(getHash(), path, record.getKey()));
        }

        this.path = path;
        this.parent = parent;
    }

    @Override
    public VirtualTreeInternal getParent() {
        return parent;
    }
}
