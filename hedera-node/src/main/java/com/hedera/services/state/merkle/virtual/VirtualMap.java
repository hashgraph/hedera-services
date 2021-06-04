package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.merkle.virtual.persistence.VirtualDataSource;
import com.hedera.services.state.merkle.virtual.persistence.VirtualRecord;
import com.swirlds.common.Archivable;
import com.swirlds.common.FCMElement;
import com.swirlds.common.FCMValue;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleExternalLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.hedera.services.state.merkle.virtual.VirtualTreePath.INVALID_PATH;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.ROOT_PATH;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getIndexInRank;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getLeftChildPath;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getParentPath;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getPathForRankAndIndex;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getRank;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getRightChildPath;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.isFarRight;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.isLeft;

/**
 * A type of Merkle node that is also map-like and is designed for working with
 * data stored primarily off-heap, and pulled on-heap only as needed. It buffers
 * changes locally and flushes them to the storage on {@link #commit()}.
 *
 * <p>To achieve this, we have a "virtual" map. It does not implement any of the
 * java.util.* APIs because it is not necessary and would only add complexity and
 * overhead to the implementation. It implements a simple get and put method pair.
 * Access to the storage subsystem (typically, the filesystem) is implemented by
 * the {@link VirtualDataSource}. Because MerkleNodes must all implement
 * SerializableDet, and since SerializableDet implementations must have a no-arg
 * constructor, we cannot be certain that a VirtualMap always has a VirtualDataSource.
 * However, without one, it will not function, so please make sure the VirtualMap
 * is configured with a functioning VirtualDataSource before using it.</p>
 *
 * <p>This map <strong>does not accept null keys</strong> but does accept null values.</p>
 */
@ConstructableIgnored
public final class VirtualMap
        extends AbstractMerkleLeaf
        implements Archivable, FCMValue, MerkleExternalLeaf {

    private static final long CLASS_ID = 0xb881f3704885e853L;
    private static final int CLASS_VERSION = 1;

    /**
     * Pre-cache the NULL_HASH since we use it so frequently.
     */
    private static final Hash NULL_HASH = CryptoFactory.getInstance().getNullHash();

    /**
     * This data source is used for looking up the values and the virtual tree leaf node
     * information. All instances of VirtualTreeMap in the "family" (i.e. that are copies
     * going back to some first progenitor) share the same exact dataSource instance.
     */
    private final VirtualDataSource dataSource;

    /**
     * A local cache that maps from keys to leaves. Normally this map will contain a few
     * tens of items at most.
     */
    private final Map<VirtualKey, VirtualRecord> cache = new HashMap<>();
    private final Map<Long, VirtualRecord> cache2 = new HashMap<>();

    /**
     * Keeps track of all tree nodes that were deleted. A leaf node that was deleted represents
     * a result of deleting a key in the main API. A parent node that was deleted represents a node
     * that was removed as a consequence of shrinking the binary tree.
     *
     * // TODO A deleted node might be re-added as a consequence of some sequence of delete and add.
     * // TODO So we need to remove things from deleted nodes if they are re-added later.
     */
//    private final Set<VirtualTreeInternal> deletedInternalNodes = new HashSet<>();
//    private final Set<VirtualTreeLeaf> deletedLeafNodes = new HashSet<>();

    /**
     * The path of the very last leaf in the tree. Can be null if there are no leaves.
     * It is pushed to the data source on commit.
     */
    private long lastLeafPath;

    /**
     * The path of the very first leaf in the tree. Can e null if there are no leaves.
     * It is pushed to the data source on commit;
     */
    private long firstLeafPath;

    /**
     * Creates a new VirtualTreeMap.
     */
    public VirtualMap(VirtualDataSource ds) {
        this.dataSource = Objects.requireNonNull(ds);
        this.firstLeafPath = ds.getFirstLeafPath();
        this.lastLeafPath = ds.getLastLeafPath();
        setImmutable(false);
    }

    /**
     * Creates a copy based on the given source.
     *
     * @param source Not null.
     */
    private VirtualMap(VirtualMap source) {
        this.dataSource = source.dataSource;
        this.firstLeafPath = source.firstLeafPath;
        this.lastLeafPath = source.lastLeafPath;
        this.setImmutable(false);
        source.setImmutable(true);
    }

    /**
     * Gets the value associated with the given key. The key must not be null.
     *
     * @param key The key to use for getting the value. Must not be null.
     * @return The value, or null if there is no such data.
     */
    public VirtualValue getValue(VirtualKey key) {
        Objects.requireNonNull(key);

        // Check the cache and return the value if it was in there.
        var rec = cache.get(key);
        if (rec != null) {
            return rec.getValue();
        }

        return dataSource.getLeafValue(key);
    }

    /**
     * Puts the given value into the map, associated with the given key. The key
     * must be non-null. Cannot be called if the map is immutable.
     *
     * @param key A non-null key
     * @param value The value. May be null.
     */
    public void putValue(VirtualKey key, VirtualValue value) {
        throwIfImmutable();
        Objects.requireNonNull(key);
        final var rec = findRecord(key);
        if (rec != null) {
            rec.setValue(value);
        } else {
            add(key, value);
        }
    }

    /**
     * Deletes the entry in the map for the given key.
     *
     * @param key A non-null key
     */
    public void deleteValue(VirtualKey key) {
        // Validate the key.
        Objects.requireNonNull(key);

        // TODO not sure yet how delete works with the cache.

        // Get the current record for the key
//        final var record = dataSource.getRecord(key);
//        if (record != null) {
//            // TODO delete the node associated with this record. We gotta realize everything to get hashes right
//            // and move everything around as needed.
//        }
    }

    /**
     * Commits all changes buffered in this virtual map to the {@link VirtualDataSource}.
     */
    public void commit() {
        // Write the leaf paths
        this.dataSource.writeFirstLeafPath(firstLeafPath);
        this.dataSource.writeLastLeafPath(lastLeafPath);

        this.cache.values().stream()
                .filter(VirtualRecord::isDirty)
                .forEach(dataSource::saveLeaf);

        // TODO handle updating hashes, updating parents, deleting things, etc.
    }

    @Override
    public FCMElement copy() {
        throwIfImmutable();
        throwIfReleased();
        return new VirtualMap(this);
    }

    @Override
    public Hash getHash() {
        // Realize the root node, if it doesn't already exist
        // TODO compute this
        return NULL_HASH;
//        final var r = root == null ? realizeRootNode() : root;
//        return r.hash(); // recomputes if needed
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
        // TODO?
    }

    @Override
    public void deserializeAbbreviated(SerializableDataInputStream serializableDataInputStream, Hash hash, int i) throws IOException {
        // TODO?
    }

    @Override
    public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) throws IOException {
        // TODO?
    }

    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {
        // TODO?
    }

    // ----------------------------------------------------------------------------------------------------
    //
    // TODO Review this documentation for accuracy
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
    //           represented with a "1". The Path is made up of two pieces of information: a "rank", and
    //           a 64-bit number. Using the "rank", you can determine how many bits in the number are used
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
    //     data source that will know the new value before it is visible in the memory mapped file, for example).
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
     */
    private VirtualRecord findRecord(VirtualKey key) {
        assert key != null;

        var rec = cache.get(key);
        if (rec == null) {
            rec = dataSource.loadLeaf(key);
        }

        if (rec != null) {
            cache.put(key, rec);
            cache2.put(rec.getPath(), rec);
        }
        return rec;
    }

    private VirtualRecord findRecord(long path) {
        var rec = cache2.get(path);
        if (rec == null) {
            rec = dataSource.loadLeaf(path);
        }

        if (rec != null) {
            cache.put(rec.getKey(), rec);
            cache2.put(path, rec);
        }
        return rec;
    }

//    private VirtualTreeInternal realizeAllInternal(long path) {
//        final var parentRef = new AtomicReference<VirtualTreeInternal>();
//        if (root == null) {
//            root = realizeRootNode();
//        }
//        root.walk(path, new VirtualVisitor() {
//            @Override
//            public void visitParent(VirtualTreeInternal parent) {
//                parentRef.set(parent);
//            }
//
//            @Override
//            public void visitUncreated(long uncreated) {
//                // If the uncreated tree path matches the prefix of our path, then we create
//                // the node. This brings all the parents into reality.
//                if (isParentOf(uncreated, path) || uncreated == path) {
//                    realizeInternalNode(parentRef.get(), uncreated);
//                }
//            }
//        });
//        return parentRef.get();
//    }

    /**
     * Adds a new leaf with the given key and value. At this point, we know for
     * certain that there is no record in the data source for this key, so
     * we can assume that here.
     *
     * @param key A non-null key. Previously validated.
     * @param value The value to add. May be null.
     */
    private void add(VirtualKey key, VirtualValue value) {
        // We're going to imagine what happens to the leaf and the tree without
        // actually bringing into existence any nodes. Virtual Virtual!!

        // Find the lastLeafPath which will tell me the new path for this new item
        if (lastLeafPath == INVALID_PATH) {
            // There are no leaves! So this one will just go right on the root
            final var leafPath = getLeftChildPath(ROOT_PATH);
            final var newLeaf = new VirtualRecord(NULL_HASH, leafPath, key, value);
            newLeaf.makeDirty();
            // Save state.
            this.firstLeafPath = leafPath;
            this.lastLeafPath = leafPath;
            cache.put(key, newLeaf);
        } else if (isLeft(lastLeafPath)) {
            // If the lastLeafPath is on the left, then this is easy, we just need
            // to add the new leaf to the right of it, on the same parent
            final var parentPath = getParentPath(lastLeafPath);
            assert parentPath != INVALID_PATH; // Cannot happen because lastLeafPath always points to a leaf in the tree
            final var leafPath = getRightChildPath(parentPath);
            final var newLeaf = new VirtualRecord(NULL_HASH, leafPath, key, value);
            newLeaf.makeDirty();
            // Save state.
            lastLeafPath = leafPath;
            cache.put(key, newLeaf);
        } else {
            // We have to make some modification to the tree because there is not
            // an open slot. So we need to pick a slot where a leaf currently exists
            // and then swap it out with a parent, move the leaf to the parent as the
            // "left", and then we can put the new leaf on the right. It turns out,
            // the slot is always the firstLeafPath. If the current firstLeafPath
            // is all the way on the far right of the graph, then the next firstLeafPath
            // will be the first leaf on the far left of the next level. Otherwise,
            // it is just the sibling to the right.
            final var nextFirstLeafPath = isFarRight(firstLeafPath) ?
                    getPathForRankAndIndex((byte)(getRank(firstLeafPath) + 1), 0) :
                    getPathForRankAndIndex(getRank(firstLeafPath), getIndexInRank(firstLeafPath) + 1);

            // The firstLeafPath points to the old leaf that we want to replace.
            final var parentPath = getParentPath(firstLeafPath);

            // Get the old leaf. Could be null, if it has not been realized.
            final var oldLeafPath = isLeft(firstLeafPath) ? getLeftChildPath(parentPath) : getRightChildPath(parentPath);
            final var oldLeaf = findRecord(oldLeafPath);

            // Create a new internal node that is in the position of the old leaf and attach it to the parent
            // on the left side.
            final var newSlotParentPath = firstLeafPath;

            // Put the new item on the right side of the new parent.
            final var leafPath = getRightChildPath(newSlotParentPath);
            final var newLeaf = new VirtualRecord(NULL_HASH, leafPath, key, value);
            newLeaf.makeDirty();
            cache.put(key, newLeaf);
            cache2.put(leafPath, newLeaf);
            // Add the leaf nodes to the newSlotParent
            if (oldLeaf != null) {
                oldLeaf.setPath(getLeftChildPath(newSlotParentPath));
            }

            // Save the first and last leaf paths
            firstLeafPath = nextFirstLeafPath;
            lastLeafPath = leafPath;
        }
    }

    /**
     * Either convert a root ghost node to a realized one, or create a new one.
     *
     * @return A non-null internal root node
     */
//    private VirtualTreeInternal realizeRootNode() {
//        var node = dataSource.load(VirtualTreePath.ROOT_PATH);
//        if (node == null) {
//            node = new VirtualTreeInternal(NULL_HASH, VirtualTreePath.ROOT_PATH);
//            node.makeDirty();
//        }
//        return node;
//    }

    /**
     * Either convert a ghost node to a realized one, or create a new one.
     *
     * @param parent The parent, cannot be null.
     * @param path The path to this node.
     * @return A non-null internal node
     */
//    private VirtualTreeInternal realizeInternalNode(VirtualTreeInternal parent, long path) {
//        Objects.requireNonNull(parent);
//        final var n = dataSource.load(path);
//        final var node = n == null ? new VirtualTreeInternal(NULL_HASH, path) : n;
//        if(isLeft(path)) {
//            parent.setLeftChild(node);
//        } else {
//            parent.setRightChild(node);
//        }
//        return node;
//    }

//    public String getAsciiArt() {
//        if (root == null) {
//            return "<Empty>";
//        }
//
//        final var nodeWidth = 10; // Let's reserve this many chars for each node to write their name.
//
//        // Use this for storing all the strings we produce as we go along.
//        final var strings = new ArrayList<List<String>>(64);
//        final var l = new ArrayList<String>(1);
//        l.add("( )");
//        strings.add(l);
//
//        // Simple depth-first traversal
//        print(strings, root);
//
//        final var buf = new StringBuilder();
//        final var numRows = strings.size();
//        final var width = (int) (Math.pow(2, numRows-1) * nodeWidth);
//        for (int i=0; i<strings.size(); i++) {
//            final var list = strings.get(i);
//            int x = width/2 - (nodeWidth * (int)(Math.pow(2, i)))/2;
//            buf.append(" ".repeat(x));
//            x = 0;
//            for (var s : list) {
//                final var padLeft = ((nodeWidth - s.length()) / 2);
//                final var padRight = ((nodeWidth - s.length()) - padLeft);
//                buf.append(" ".repeat(padLeft)).append(s).append(" ".repeat(padRight));
//                x += nodeWidth;
//            }
//            buf.append("\n");
//        }
//        return buf.toString();
//    }
//
//    private void print(List<List<String>> strings, VirtualTreeNode node) {
//        // Write this node out
//        final var path = node.getPath();
//        final var rank = getRank(path);
//        final var pnode = node instanceof VirtualTreeInternal;
//        final var dirtyMark = node.isDirty() ? "*" : "";
//        strings.get(rank).set(getIndexInRank(path), dirtyMark + "(" + (pnode ? "P" : "L") + ", " + getBreadcrumbs(path) + ")" + dirtyMark);
//
//        if (pnode) {
//            final var parent = (VirtualTreeInternal) node;
//            final var left = parent.getLeftChild();
//            final var right = parent.getRightChild();
//            if (left != null || right != null) {
//                // Make sure we have another level down to go.
//                if (strings.size() <= rank + 1) {
//                    final var size = (int)Math.pow(2, rank+1);
//                    final var list = new ArrayList<String>(size);
//                    for (int i=0; i<size; i++) {
//                        list.add("( )");
//                    }
//                    strings.add(list);
//                }
//
//                if (left != null) {
//                    print(strings, left);
//                }
//
//                if (right != null) {
//                    print(strings, right);
//                }
//            }
//        }
//    }

//    /**
//     * Walks the tree starting from this node taking the most direct route to the
//     * target node. Calls the visitor for each step downward.
//     *
//     * @param target The path of the node we're trying to walk towards.
//     * @param visitor The visitor. Cannot be null.
//     */
//    public final void walk(long target, VirtualVisitor visitor) {
//        // Maybe I was the target! In that case, visit me and quit.
//        if (getPath() == target) {
//            visitor.visitParent(this);
//            return;
//        }
//
//        // I wasn't the target and I'm not the parent of the target so quit.
//        if (!isParentOf(getPath(), target)) {
//            return;
//        }
//
//        var node = this;
//        while (node != null) {
//            final var path = node.getPath();
//            // Visit the node
//            visitor.visitParent(node);
//
//            // If we've found the target, then we're done
//            if (path == target) {
//                break;
//            }
//
//            // We didn't find the target yet and `node` is a parent node,
//            // so we need to go down either the left or right branch.
//            VirtualTreeNode nextNode;
//            final var leftPath = getLeftChildPath(path);
//            if (isParentOf(leftPath, target) || leftPath == target) {
//                if (node.leftChild == null) {
//                    visitor.visitUncreated(leftPath);
//                }
//                nextNode = node.leftChild;
//            } else {
//                final var rightPath = getRightChildPath(path);
//                if (isParentOf(rightPath, target) || rightPath == target) {
//                    if (node.rightChild == null) {
//                        visitor.visitUncreated(rightPath);
//                    }
//                    nextNode = node.rightChild;
//                } else {
//                    // Neither left or right, we're at a dead end.
//                    break;
//                }
//            }
//
//            if (nextNode instanceof VirtualTreeLeaf) {
//                // We found the leaf. Visit and quit.
//                visitor.visitLeaf((VirtualTreeLeaf) nextNode);
//                break;
//            } else {
//                // iterate
//                node = (VirtualTreeInternal) nextNode;
//            }
//        }
//    }
//
//    /**
//     * Walks the tree starting from this node using pre-order traversal, invoking the visitor
//     * for each node visited. Only the dirty nodes are visited.
//     *
//     * TODO how does this work with deleted nodes? If we delete a node, we somehow need to
//     * visit it too. Maybe this is the wrong way to do it.
//     *
//     * @param visitor The visitor. Cannot be null.
//     */
//    public final void walkDirty(VirtualVisitor visitor) {
//        // We need a stack to keep track of which node to process next
//        final var deque = new ArrayDeque<VirtualTreeNode>(64);
//        deque.push(this);
//        while (!deque.isEmpty()) {
//            // In pre-order traversal, this node is visited first.
//            final var node = deque.pop();
//            final var pnode = node instanceof VirtualTreeInternal;
//            if (pnode) {
//                final var parent = (VirtualTreeInternal) node;
//                visitor.visitParent(parent);
//
//                // Push the right first, and then the left, so that we process
//                // the left branch first as we pop off the stack.
//                final var right = parent.rightChild;
//                if (right != null) {
//                    deque.push(right);
//                }
//
//                final var left = parent.leftChild;
//                if (left != null) {
//                    deque.push(left);
//                }
//            } else {
//                visitor.visitLeaf((VirtualTreeLeaf) node);
//            }
//        }
//    }
}
