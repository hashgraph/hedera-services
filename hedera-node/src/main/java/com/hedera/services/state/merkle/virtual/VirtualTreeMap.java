package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.Archivable;
import com.swirlds.common.FCMElement;
import com.swirlds.common.FCMValue;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleExternalLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A type of Merkle node that is also map-like and is used primarily as storage for
 * EVM contracts. An EVM contract may have a very large data set stored to disk.
 * For performance reasons, we don't want to read the entire data set into memory,
 * hash the entire data set, and write it all back out to disk on each contract
 * invocation. While this approach leads to very fast storage read/write times
 * during contract execution, it adds enormous overhead to contract setup/teardown.
 * <p>
 * Instead, we want to read only the data we need during the contract execution,
 * efficiently compute the hash, and write only the changed data during teardown.
 * In a typical large application, there might be 20mb of storage but only a few
 * kilobytes that get used during contract execution (since reading and writing have
 * substantial gas costs, contracts are not written to attempt reading and writing
 * all memory, they would quickly run out of gas). From a security perspective,
 * we just need to make sure that the read/write overhead is sufficiently less
 * than the gas we charge so that it is not possible to slow down the system
 * (gas is used as a proxy for time, and the gas limit is set to guarantee we
 * get the TPS that we want).
 * <p>
 * To achieve this, we have a "virtual" tree map. It does not implement any of the
 * java.util.* APIs because it is not necessary and would only add complexity and
 * overhead to the implementation. It implements a simple get and put method pair.
 * Access to the storage subsystem (typically, the filesystem) is implemented by
 * the {@link VirtualDataSource}.
 * <p>
 * TODO Everything below here is possibly wrong.
 * Initially the VirtualTreeMap starts with an empty tree, it doesn't even have a root.
 * On the first {@code get}, it defers to the DataSource to see if the entry
 * exists. If it does not, it returns null. If it does, then it reads some information
 * about the {@link VirtualTreeLeaf} that had previously been saved. Specifically, it
 * reads back the data (256 byte array for EVM) and a {@code Path} that defines the
 * path from the root of the tree to the leaf. We then decode this path, and read
 * from the data source the root node's hash from storage, and so on down the path
 * until we get to the leaf node (we actually instantiate both children of each
 * internal node on our way down to the leaf, so that we have all the hashes we need
 * to compute the updated hash later). We then return the data we looked up to the
 * caller.
 * <p>
 * Now that we have a partial tree loaded into memory, if the same {@code get} call
 * is made for the same key, we will simply return the same cached value. The initial
 * implementation tries to keep memory costs to a minimum, so it will actually go back
 * to the data source, read the same info it read previously, discover that it has already
 * loaded each node along the path, and return the value. A future implementation could
 * look into FCHashMap or some other cache for reducing costs. It isn't really necessary
 * since EVM calls are metered and the gas cost is chosen such that it is greater than
 * the effort we expend.
 * <p>
 * When the {@code set} method is called, we go through the same steps as {@code get}
 * so as to have the values ready for us to set, except that if the value is null, we
 * do a little more work to construct the node to save. TODO I really do need a local
 * cache because I want writes to be asynchronous. Time to spend a little more memory...
 * If the tree needs to be rebalanced, then we update the "Paths" saved for each node
 * to accurately reflect the new tree structure.
 * <p>
 * This node is considered a {@link MerkleExternalLeaf}. As such, it is responsible
 * for computing its own Hash. Ideally, this would not be the case, but we would
 * let the platform compute the hashes so they could be done in bulk. The problem with
 * reconnect. We don't have all the data in memory, and we don't want to create the
 * entire tree in memory during reconnect. Instead, we need to be able to send the
 * entire file across during sync. This is trivially done because each DataSource
 * is backed by a file, so we can just send those file bytes across the network
 * and reconstitute them on the other side. This avoids having to know ahead of time
 * the set of keys, or nodes, that exist in the tree.
 *
 */
public class VirtualTreeMap
        extends AbstractMerkleLeaf
        implements Archivable, FCMValue, MerkleExternalLeaf {

    private static final long CLASS_ID = 0xb881f3704885e853L;
    private static final int CLASS_VERSION = 1;

    /**
     * This data source is used for looking up the values and the
     * virtual tree leaf node information. All instances of
     * VirtualTreeMap in the "family" (i.e. that are copies
     * going back to some first progenitor) share the same exact
     * dataSource instance. It is never null.
     */
    private /*final*/ VirtualDataSource dataSource; // cannot be final due to swirlds des

    /**
     * The root node of the inner merkle tree. It always starts off as null,
     * and on the first "set" on the class we end up creating this.
     * TODO We really should reuse it if possible in a fast-copyable sense,
     * but this needs thinking.
     */
    private VirtualTreeInternal root;

    /**
     * A reference to the previous VirtualTreeMap from which this instance
     * was copied. May be null, if there was no previous, and may contain a
     * null reference if it has been garbage collected.
     * TODO not currently used, need to think about it more.
     */
    private WeakReference<VirtualTreeMap> prev;

    /**
     * Creates a new VirtualTreeMap. setDataSource has to be called
     * before this class is usable.
     */
    public VirtualTreeMap() {
        this.root = null;
        setImmutable(false);
    }

    public void setDataSource(VirtualDataSource ds) {
        this.dataSource = ds;
    }

    /**
     * Creates a new VirtualTreeMap backed by the given data source.
     *
     * @param dataSource Not null.
     */
    public VirtualTreeMap(VirtualDataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.root = null;
        setImmutable(false);
    }

    /**
     * Creates a copy based on the given source.
     * @param source Not null.
     */
    private VirtualTreeMap(VirtualTreeMap source) {
        this.dataSource = source.dataSource;
        this.root = null;
        this.setImmutable(false);
        source.setImmutable(true);
        this.prev = new WeakReference<>(source);
    }

    /**
     * Gets the value associated with the given key. The key must not be null, and
     * must be exactly 256-bytes long.
     *
     * @param key The key to use for getting the value. Not null, 256-bytes long.
     * @return The value as a 256-byte array, nor null if there is no such data.
     */
    public Block getValue(Block key) {
        Objects.requireNonNull(key);

        return dataSource.getData(key);
    }

    /**
     * Puts the given value into the map, associated with the given key. The key
     * must be a non-null 256-byte array. Putting a null value will cause the
     * entry to be deleted.
     *
     * @param key A non-null 256-byte array.
     * @param value Either null, or a non-null 256-byte array.
     */
    public void putValue(Block key, Block value) {
        // Validate the key.
        Objects.requireNonNull(key);

        // Get the current record for the key
        final var path = dataSource.getPathForKey(key);
        final var record = path == null ? null : dataSource.getRecord(path);

        // Handle the modification
        if (value == null) {
            delete(record);
        } else if (record != null) {
            update(value, record);
        } else {
            add(key, value);
        }
    }

    @Override
    public FCMElement copy() {
        throwIfImmutable();
        throwIfReleased();
        return new VirtualTreeMap(this);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }

    @Override
    public void archive() {
        // I only want to delegate the "archive" call to the data source if this
        // is the very last merkle tree to exist, not if it has been copied
        if (!isImmutable()) {
            try {
                dataSource.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void serializeAbbreviated(SerializableDataOutputStream serializableDataOutputStream) throws IOException {

    }

    @Override
    public void deserializeAbbreviated(SerializableDataInputStream serializableDataInputStream, Hash hash, int i) throws IOException {

    }

    @Override
    public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) throws IOException {

    }

    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {

    }

    // ----------------------------------------------------------------------------------------------------
    //
    // Implementation of tree functionality, such as adding nodes, walking the tree, realizing nodes,
    // deleting nodes, etc.
    //
    // Definitions:
    //   - Node: Either a VirtualTreeInternal or a VirtualTreeLeaf. Every "parent" node, including the
    //           root node is a VirtualTreeInternal.
    //
    //   - Ghost Node: A "ghost" node is one that exists in the data source but has no associated node instance
    //                 in memory yet.
    //
    //   - Realized Node: A "realize" node is one that was once a ghost, but is no longer. To "realize" is
    //                    the process of reading a ghost from disk and creating an instance of the node
    //                    in Java.
    //
    //   - Path: Each node has a unique path. Since our tree is a binary tree, every internal node has,
    //           conceptually, two children; a left, and a right. In reality, sometimes the node has only
    //           one child, and the second is left null, but this is just an optimization. No parent node
    //           ever has zero children. "Left" nodes are represented with a "0" and "right" nodes are
    //           represented with a "1". The Path is made up of two pieces of information: a "depth", and
    //           a 64-bit number. Using the "depth", you can determine how many bits in the number are used
    //           for that path. Using this system, a tree cannot be more than 64 levels deep (which is
    //           way more than we need). The node on the far left side of any level will have a number of
    //           all zeros. The node on the far right side of any level will have all ones. All other nodes
    //           have some sequence of 0's and 1's that tell us how to walk from the root down to the node,
    //           turning "left" or "right" as we traverse at each level.
    //
    //  Notes:
    //   - Paths on a node change when the tree is modified. Since paths are used for node Ids and stored in
    //     in the data source, when the tree is modified it may be necessary to update the path associated
    //     with a node. This makes it difficult to have asynchronous writes (it requires some cache in the
    //     data source that will know the new value before it is visible in the memory mapped file, for exmample).
    //
    //   - We store in the data source the Path of the very last leaf node on the right. This makes it trivial
    //     to add and remove nodes in constant time.
    //
    //   - We always add nodes from "left to right". When a level if full (the last leaf node path is all 1's),
    //     then we insert an internal node where the left-most leaf node was, move that leaf down a level,
    //     and add the new leaf. A similar technique is used for every time we have to add a child to an
    //     internal node that is already full.
    //
    // ----------------------------------------------------------------------------------------------------

    /**
     * Looks for and returns the VirtualTreeLeaf at this Path. If the leaf was a ghost, it
     * is realized. If the Path does not refer to a leaf node, then null is returned.
     *
     * @param path A path to the leaf. Can be null.
     * @return A VirtualTreeLeaf, if there is one, realizing it if needed. If Path is null, return a null.
     *         If the path doesn't refer to a leaf node (perhaps the path is greater than the last leaf
     *         node or smaller than the first leaf node) then return null.
     */
    private VirtualTreeLeaf findLeaf(Path path) {
        // Quick check for null or root. Always return null in this case
        if (path == null || path.isRoot()) {
            return null;
        }

        // Check for whether there are any leaves at all
        final var lastLeafPath = dataSource.getLastLeafPath();
        if (lastLeafPath == null) {
            return null;
        }

        // Check whether the path is "greater than" the last leaf node.
        // There cannot be any valid leaf after the lastLeafPath
        if (path.isRightOf(lastLeafPath)) {
            return null;
        }

        // Check whether the path is "less than" the first leaf node.
        final var firstLeafPath = dataSource.getFirstLeafPath();
        if (path.isLeftOf(firstLeafPath)) {
            return null;
        }

        // Now we know that we have a valid leaf path, we start walking the tree from the root.
        // If we encounter a ghost, we realize it into an instance. Eventually, we'll find
        // the leaf, and we can return it.

        // If the root node is a ghost, then we realize it first (we know we will need it)
        if (root == null) {
            root = realizeInternalNode(null, Path.ROOT_PATH);
        }

        // The mask will have zeros on the high order bits and ones on the low order bits.
        // Given some path, we will 'and' the mask with the path to get just the low order
        // path elements.
        var mask = 1L;
        var parent = root;
        VirtualTreeInternal node;

        // We start on the first child of root, and iterate until just before the leaf.
        // Basically, we just want to walk down the internal nodes. At the end of this
        // loop, "parent" will point to the parent of the leaf node.
        for (byte i=0; i<path.depth-1; i++) {
            // i represents the child node that we're trying to visit.
            final var decisionMask = 1L << i;
            final var flag = (path.path & decisionMask) == 0;
            node = (VirtualTreeInternal) (flag ? parent.getLeftChild() : parent.getRightChild());

            // If the node is null, we realize it.
            if (node == null) {
                node = realizeInternalNode(parent, new Path((byte)(i + 1), path.path & mask));
            }

            // Adjust the mask to prepare for the next iteration
            mask = (mask << 1) | 0x1L;
            parent = node;
        }

        // Parent is now the parent, and it has already been realized.
        // Get the child and verify that the child is not a ghost.
        var leaf = (VirtualTreeLeaf) (path.isLeft() ? parent.getLeftChild() : parent.getRightChild());
        if (leaf == null) {
            leaf = realizeLeafNode(parent, path);
        }

        return leaf;
    }

    private void delete(VirtualRecord record) {
        // Nothing to do if we're deleting something that doesn't exist!
        if (record != null) {
            // TODO delete the node associated with this record. We gotta realize everything to get hashes right
            // and move everything around as needed.
        }
    }

    /**
     * Update a value.
     *
     * @param value A potentially null value, or a 256-byte array
     * @param record A non-null record related to the leaf node to update.
     */
    private void update(Block value, VirtualRecord record) {
        // Finds the leaf, realizing it if necessary. Because record is not null,
        // we know *FOR SURE* that the leaf and all its parents exist, so we can
        // simply walk the tree looking for it.
        final var leaf = findLeaf(record.getPath());
        save(record.getKey(), value, leaf);
    }

    /**
     * Adds a new leaf with the given key and value. At this point, we know for
     * certain that there is no record in the data source for this key, so
     * we can assume that here.
     *
     * @param key A non-null 256-byte key. Previously validated.
     * @param value Either null, or a non-null 256-byte value.
     */
    private void add(Block key, Block value) {
        // Gotta create the root, if there isn't one.
        if (root == null) {
            root = realizeInternalNode(null, Path.ROOT_PATH);
        }

        // Find the lastLeafPath which will tell me the new path for this new item
        Path leafPath;
        VirtualTreeLeaf newLeaf;
        final var lastLeafPath = dataSource.getLastLeafPath();
        if (lastLeafPath == null) {
            // There are no leaves! So this one will just go right on the root
            leafPath = new Path((byte)1, 0);
            final var rec = new VirtualRecord(null, leafPath, key);
            newLeaf = new VirtualTreeLeaf(dataSource, rec);
            root.setLeftChild(newLeaf);
            dataSource.writeFirstLeafPath(leafPath);
        } else if (lastLeafPath.isLeft()) {
            // If the lastLeafPath is on the left, then this is easy, we just need
            // to add the new leaf to the right of it, on the same parent (same
            // path with a 1 as the MSB)
            final long mask = 1L << (lastLeafPath.depth - 1);
            leafPath = new Path(lastLeafPath.depth, lastLeafPath.path | mask);
            final var rec = new VirtualRecord(null, leafPath, key);
            newLeaf = new VirtualTreeLeaf(dataSource, rec);
            root.setRightChild(newLeaf);
        } else {
            // We have to make some modification to the tree because there is not
            // an open slot. So we need to pick a slot where a leaf currently exists
            // and then swap it out with a parent, move the leaf to the parent as the
            // "left", and then we can put the new leaf on the right. It turns out,
            // the slot is always the firstLeafPath.
            final var firstLeafPath = dataSource.getFirstLeafPath();

            // Now we have to find the next "firstLeafPath". If the current firstLeafPath
            // is all the way on the far right of the graph, then the next firstLeafPath
            // will be the first leaf on the far left of the next level. Otherwise,
            // it is just the sibling to the right.
            final var mask = ~(-1L << firstLeafPath.depth);
            final var firstLeafIsOnFarRight = (firstLeafPath.path & mask) == mask;
            final var nextFirstLeafPath = firstLeafIsOnFarRight ?
                    new Path((byte)(firstLeafPath.depth + 1), 0) :
                    Path.getPathForDepthAndIndex(firstLeafPath.depth, firstLeafPath.getIndex() + 1);

            final var slotPath = firstLeafPath;
            final var oldLeaf = findLeaf(slotPath);
            final var parent = oldLeaf.getParent();
            final var newSlotParent = realizeInternalNode(parent, slotPath);
            if (slotPath.isLeft()) {
                parent.setLeftChild(newSlotParent);
            } else {
                parent.setRightChild(newSlotParent);
            }
            leafPath = slotPath.getRightChildPath();
            final var rec = new VirtualRecord(null, leafPath, key);
            newLeaf = new VirtualTreeLeaf(dataSource, rec);
            newSlotParent.setLeftChild(oldLeaf);
            newSlotParent.setRightChild(newLeaf);

            dataSource.writeFirstLeafPath(nextFirstLeafPath);
        }

        save(key, value, newLeaf);

        // Now record our new lastLeafPath
        dataSource.writeLastLeafPath(leafPath);
    }

    private void save(Block key, Block value, VirtualTreeLeaf leaf) {
        Path leafPath = leaf.getPath();
        leaf.setData(value);
        final var newRecord = new VirtualRecord(null, leafPath, key);
        dataSource.writeRecord(leafPath, newRecord);
        dataSource.writeData(newRecord.getKey(), value);
    }

    /**
     * Either convert a ghost node to a realized one, or create a new one.
     *
     * @param parent The parent, can be null.
     * @param path The path to this node.
     * @return A non-null internal node
     */
    private VirtualTreeInternal realizeInternalNode(VirtualTreeInternal parent, Path path) {
        // It is possible that we haven't recorded the hash, maybe because we have invalidated
        // it and haven't refreshed it, or something. Whatever, we'll survive.
        final var hash = dataSource.getHash(path);
        final var node = new VirtualTreeInternal(dataSource);
        node.setHash(hash);

        // The parent may be null if this is the root node.
        if (parent != null) {
            if(path.isLeft()) {
                parent.setLeftChild(node);
            } else {
                parent.setRightChild(node);
            }
        }

        return node;
    }

    /**
     * Either convert a ghost node to a realized one, or create a new one.
     *
     * @param parent The parent, cannot be null.
     * @param path The path, cannot be null
     * @return A non-null virtual leaf
     */
    private VirtualTreeLeaf realizeLeafNode(VirtualTreeInternal parent, Path path) {
        final var record = dataSource.getRecord(path);
        if (record == null) {
            throw new IllegalStateException("Unexpectedly encountered a null record for a leaf that " +
                    "should have existed.");
        }

        final var leaf = new VirtualTreeLeaf(dataSource, record);
        final var data = dataSource.getData(record.getKey());
        leaf.setData(data);
        leaf.setHash(record.getHash());
        if (path.isLeft()) {
            parent.setLeftChild(leaf);
        } else {
            parent.setRightChild(leaf);
        }
        return leaf;
    }

    public String getAsciiArt() {
        if (root == null) {
            return "<Empty>";
        }

        final var nodeWidth = 8; // Let's reserve this many chars for each node to write their name.
        final var nodeHeight = 1; // And this many lines.

        // Use this for storing all the strings we produce as we go along.
        final var strings = new ArrayList<List<String>>(64);
        final var l = new ArrayList<String>(1);
        l.add("( )");
        strings.add(l);

        // Simple depth-first traversal
        print(strings, root);

        final var buf = new StringBuilder();
        final var numRows = strings.size();
        final var width = (int) (Math.pow(2, numRows-1) * nodeWidth);
        for (int i=0; i<strings.size(); i++) {
            final var list = strings.get(i);
            int x = width/2 - (nodeWidth * (int)(Math.pow(2, i)))/2;
            buf.append(pad(x));
            x = 0;
            for (var s : list) {
                final var padLeft = ((nodeWidth - s.length()) / 2);
                final var padRight = ((nodeWidth - s.length()) - padLeft);
                buf.append(pad(padLeft)).append(s).append(pad(padRight));
                x += nodeWidth;
            }
            buf.append("\n");
        }
        return buf.toString();
    }

    private String pad(int size) {
        final var sb = new StringBuilder();
        for (int i=0; i<size; i++) {
            sb.append(" ");
        }
        return sb.toString();
    }

    private void print(List<List<String>> strings, VirtualTreeNode node) {
        // Write this node out
        final var path = node.getPath();
        final var depth = path.depth;
        final var pnode = node instanceof VirtualTreeInternal;
        strings.get(depth).set(path.getIndex(), "(" + (pnode ? "P" : "L") + ", " + path.path + ")");

        if (pnode) {
            final var parent = (VirtualTreeInternal) node;
            final var left = parent.getLeftChild();
            final var right = parent.getRightChild();
            if (left != null || right != null) {
                // Make sure we have another level down to go.
                if (strings.size() <= depth + 1) {
                    final var size = (int)Math.pow(2, depth+1);
                    final var list = new ArrayList<String>(size);
                    for (int i=0; i<size; i++) {
                        list.add("( )");
                    }
                    strings.add(list);
                }

                if (left != null) {
                    print(strings, left);
                }

                if (right != null) {
                    print(strings, right);
                }
            }
        }
    }

}
