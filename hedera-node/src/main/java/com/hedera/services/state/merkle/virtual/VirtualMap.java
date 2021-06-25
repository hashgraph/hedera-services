package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.merkle.virtual.persistence.VirtualDataSource;
import com.hedera.services.state.merkle.virtual.persistence.VirtualRecord;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleExternalLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

import static com.hedera.services.state.merkle.virtual.VirtualTreePath.INVALID_PATH;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.ROOT_PATH;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getIndexInRank;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getLeftChildPath;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getParentPath;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getPathForRankAndIndex;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getRank;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getRightChildPath;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.getSiblingPath;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.isFarRight;
import static com.hedera.services.state.merkle.virtual.VirtualTreePath.isLeft;

/**
 * A map-like Merkle node designed for working with huge numbers of key/value pairs stored primarily
 * in off-heap memory mapped files and pulled on-heap only as needed. It buffers changes locally and
 * flushes them to storage on {@link #release()}.
 *
 * <p>A {@code VirtualMap} is created with a {@link VirtualDataSource}. The {@code VirtualDataSource} is
 * used by the map to read/write data to/from disk. This interface has only one practical implementation
 * in this code base, the {@link com.hedera.services.state.merkle.virtual.persistence.mmap.MemMapDataSource},
 * which is backed by memory-mapped files. Several in-memory data source implementations exist for testing
 * purposes. The API of the data source is closely tied to the needs of the {@code VirtualMap}. This was done
 * intentionally to <strong>reduce temporary objects and misalignment of the API to enhance performance.</strong>.
 * The {@code VirtualDataSource} exists as an implementation interface of the {@code VirtualMap} and is unsuited
 * for generic use.</p>
 *
 * <p>The {@code VirtualMap} buffers changes in memory and only flushes them to the {@code VirtualDataSource} on
 * {@link #release()}. The release should happen in a background thread and not as part of {@code handleTransaction}.
 * This map <strong>does not accept null keys</strong> but does accept null values.</p>
 *
 * TODO: Right now the implementation will break if commit is called on a background thread. This needs to be fixed.
 *
 * <p>The {@code VirtualMap} is {@code FastCopyable} and should be used in a similar manner to any other normal
 * Swirlds MerkleNode. The {@code VirtualMap} does have some runtime overhead, such as caches, which require
 * size relative to the number of <strong>dirty leaves</strong> in the map, not relative to the number of values
 * read or the number of items in the backing store. On {@code release} these changes are flushed to disk and
 * the caches are cleared. Thus, a node in the Merkle tree that had a VirtualMap has very little overhead when
 * not in use, and very little overhead after release, and reasonable overhead when in use for reasonable-sized
 * changes.</p>
 *
 * TODO: The implementation needs to be made to work with reconnect. There are serialization methods to attend to.
 * TODO: The implementation needs to be integrated into the normal Merkle commit mechanism
 * TODO: The implementation needs to prove out the integration of hashing with how the Merkle tree normally hashes.
 * TODO: I'm not sure how the datasource can be serialized and restored, or how that works.
 */
@ConstructableIgnored
public final class VirtualMap
        extends AbstractMerkleLeaf
        implements MerkleExternalLeaf {

    /** Used for serialization **/
    private static final long CLASS_ID = 0xb881f3704885e853L;

    /** Used for serialization **/
    private static final int CLASS_VERSION = 1;

    /**
     * This thread group is used by the threads that do the hashing for the VirtualMap.
     * Each VirtualMap "family" (the map and its copies) have a single thread in this
     * thread group.
     */
    private static final ThreadGroup HASHING_GROUP = new ThreadGroup("VirtualMap-Hashers");

    /**
     * A singleton reference to the Cryptography libraries.
     */
    private static final Cryptography CRYPTOGRAPHY = CryptoFactory.getInstance();

    /**
     * Pre-cache the NULL_HASH since we use it so frequently.
     */
    private static final Hash NULL_HASH = CRYPTOGRAPHY.getNullHash();

    /**
     * This data source is used for looking up the values and the virtual tree information.
     * All instances of VirtualTreeMap in the "family" (i.e. that are copies
     * going back to some first progenitor) share the same exact dataSource instance.
     */
    private final VirtualDataSource dataSource;

    /**
     * A local cache that maps from keys to leaves. Normally this map will contain a few
     * tens of items at most. It is only populated with "dirty" leaves, that were either
     * newly added or modified.
     */
    private final Map<VirtualKey, VirtualRecord> dirtyLeaves = new HashMap<>();

    /**
     * A secondary cache that maps from leaf paths to dirty leaves. This map is only used
     * for the case where we need to find the first leaf and modify its position.
     */
    private final LongObjectHashMap<VirtualRecord> dirtyLeavesByPath = new LongObjectHashMap<>();

    /**
     * A map of dirty parent hashes. The map is filled during the process of hashing. This is
     * threadsafe.
     */
    private final MutableLongObjectMap<Future<Hash>> dirtyParentHashes = new LongObjectHashMap<Future<Hash>>().asSynchronized();

    /**
     * An executor service for processing hashing work. A single executor service is
     * shared across all copies of a VirtualMap.
     */
    private final ExecutorService hashingPool;

    /**
     * A weak reference to the previous VirtualMap from which this one was copied, or null.
     * The previous copy is used as a secondary lookup in case of a cache miss on "get" before
     * going to the data source. This lookup will visit each previous copy until it runs out
     * of previous copies before going to the data source. We do not use it on "put", since we
     * will definitely need our own VirtualRecord for put.
     */
    private final WeakReference<VirtualMap> prevRef;

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
     * A future that contains the hash. If the hash is still being computed, then
     * any "get" on this will block until the computation finishes.
     */
    private transient Future<Hash> rootHash;

    /**
     * The path of the very last leaf in the tree. Can be null if there are no leaves.
     * It is pushed to the data source on commit.
     */
    private long lastLeafPath;

    /**
     * The path of the very first leaf in the tree. Can be null if there are no leaves.
     * Its path is pushed to the data source on commit.
     */
    private long firstLeafPath;

    /**
     * Creates a new VirtualTreeMap.
     */
    public VirtualMap(VirtualDataSource ds) {
        this.dataSource = Objects.requireNonNull(ds);
        this.firstLeafPath = ds.getFirstLeafPath();
        this.lastLeafPath = ds.getLastLeafPath();
        this.prevRef = null;
        setImmutable(false);

        final var rh = ds.loadParentHash(ROOT_PATH);
        this.rootHash = rh == null ? null : completedHash(rh);

        this.hashingPool = Executors.newFixedThreadPool(5, (r) -> {
            final var thread = new Thread(HASHING_GROUP, r);
            thread.setDaemon(true);
            return thread;
        });
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
        this.rootHash = source.rootHash;
        this.hashingPool = source.hashingPool;
        this.prevRef = new WeakReference<>(source);
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
        throwIfReleased();
        Objects.requireNonNull(key);

        // Either return the latest dirty value, or get the value from the data source
        var rec = findDirtyRecord((m) -> m.dirtyLeaves.get(key));
        return rec == null ? dataSource.getLeafValue(key) : rec.getValue();
    }

    /**
     * Puts the given value into the map, associated with the given key. The key
     * must be non-null. Cannot be called if the map is immutable.
     *
     * @param key   A non-null key
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

    @Override
    public VirtualMap copy() {
        throwIfImmutable();
        throwIfReleased();

        // Since we know that at the time of copy there can be no more changes to this map,
        // this is a good time to start the background hashing work. Hopefully by the time
        // any code asks for the hash code, or tries to release the copy, the hashing will
        // either be done or have done substantial work.
        recomputeHash();

        // Return the copy.
        return new VirtualMap(this);
    }

    @Override
    protected void onRelease() {
        // NOTE: The copies need to be released IN ORDER or we will end up writing older state
        // over top newer state.
        throwIfReleased();

        // NOTE: It is assumed that this method is being called on a background thread, although
        // it can be called from any thread. The method WILL NOT RETURN until all hashes have
        // been recomputed and all state saved successfully.

        // Write the leaf paths
        this.dataSource.writeFirstLeafPath(firstLeafPath);
        this.dataSource.writeLastLeafPath(lastLeafPath);

        // Write all the dirty leaves
        this.dirtyLeaves.values().stream()
                .filter(VirtualRecord::isDirty)
                .forEach(dataSource::saveLeaf);

        // TODO Delete all leaves and parents that are no longer needed

        // Write all the dirty parent hashes
        dirtyParentHashes.forEachKeyValue((k, v) -> {
            try {
                dataSource.saveParent(k, v.get());
            } catch (InterruptedException | ExecutionException e) {
                // TODO Not sure what to do here!!
                e.printStackTrace();
            }
        });

        // Although this instance should be garbage collected soon, we might as well
        // proactively clear these data structures, just in case the VirtualMap instance
        // is held on to a little longer than expected.
        dirtyLeaves.clear();
        dirtyLeavesByPath.clear();
        dirtyParentHashes.clear();
    }

    @Override
    public Hash getHash() {
        // In case it hasn't been computed yet, recompute it
        recomputeHash();
        final var rh = getRootHash();
        return rh == null ? NULL_HASH : rh; // Really should never be null by this point...
    }

    @Override
    public void setHash(Hash hash) {
        throw new UnsupportedOperationException("Cannot set the hash on this node, it is computed");
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

    /**
     * Finds the VirtualRecord corresponding to the given key, first by looking
     * among the dirty records, and failing that, by looking in the data source.
     * Returns null if no record can be found anywhere.
     */
    private VirtualRecord findRecord(VirtualKey key) {
        assert key != null;

        // Find the dirty record, or if it wasn't dirty, then load it from the data source
        var rec = findDirtyRecord((m) -> m.dirtyLeaves.get(key));
        if (rec == null) {
            rec = dataSource.loadLeaf(key);
            // If we found it as a clean record, then we mark it dirty
            if (rec != null) {
                dirtyLeaves.put(key, rec);
                dirtyLeavesByPath.put(rec.getPath(), rec);
            }
        }

        return rec;
    }

    /**
     * Finds the VirtualRecord corresponding to the given leaf path, first by looking
     * among the dirty records, and failing that, by looking in the data source.
     * Returns null if no record can be found anywhere.
     */
    private VirtualRecord findRecord(long path) {
        // Find the dirty record, or if it wasn't dirty, then load it from the data source
        var rec = findDirtyRecord((m) -> m.dirtyLeavesByPath.get(path));
        if (rec == null) {
            rec = dataSource.loadLeaf(path);
            // If we found it as a clean record, then we mark it dirty
            if (rec != null) {
                dirtyLeaves.put(rec.getKey(), rec);
                dirtyLeavesByPath.put(rec.getPath(), rec);
            }
        }

        return rec;
    }

    /**
     * Finds a dirty record by checking this virtual map, and each previous one in the "copy chain"
     * until it either finds the dirty record, or has exhaustively searched. Since older copies may
     * have been GC'd, we use a WeakReference to point to each previous version. We are given the
     * guarantee that before a previous copy has been GC'd, it has been persisted, so that if we
     * fail to find a dirty record, looking up the value in the data source will give us the
     * latest info.
     *
     * @param supplier A function that takes a VirtualMap and returns a dirty VirtualRecord, if that
     *                 map had one. This indirection allows us to consolidate logic for iteration
     *                 into one place while allowing callers to look up by path or key.
     * @return The VirtualRecord if a dirty one was found, or null.
     */
    private VirtualRecord findDirtyRecord(Function<VirtualMap, VirtualRecord> supplier) {
        var rec = supplier.apply(this);
        if (rec != null) {
            return rec;
        } else {
            var p = prevRef == null ? null : prevRef.get();
            while (p != null && !p.isReleased()) {
                rec = supplier.apply(p);
                if (rec != null) {
                    return rec;
                }
                p = p.prevRef == null ? null : p.prevRef.get();
            }
        }

        return null;
    }

    /**
     * Adds a new leaf with the given key and value. The precondition to calling this
     * method is that the key DOES NOT have a corresponding leaf already either in the
     * dirty records or in the data source.
     *
     * @param key   A non-null key. Previously validated.
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
            dirtyLeaves.put(key, newLeaf);
            dirtyLeavesByPath.put(leafPath, newLeaf);
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
            dirtyLeaves.put(key, newLeaf);
            dirtyLeavesByPath.put(leafPath, newLeaf);
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
                    getPathForRankAndIndex((byte) (getRank(firstLeafPath) + 1), 0) :
                    getPathForRankAndIndex(getRank(firstLeafPath), getIndexInRank(firstLeafPath) + 1);

            // The firstLeafPath points to the old leaf that we want to replace.
            // Get the old leaf. Could be null, if it has not been realized.
            final var oldLeafPath = firstLeafPath;
            final var oldLeaf = findRecord(oldLeafPath);

            // Create a new internal node that is in the position of the old leaf and attach it to the parent
            // on the left side.
            final var newSlotParentPath = firstLeafPath;

            // Put the new item on the right side of the new parent.
            final var leafPath = getRightChildPath(newSlotParentPath);
            final var newLeaf = new VirtualRecord(NULL_HASH, leafPath, key, value);
            newLeaf.makeDirty();
            dirtyLeaves.put(key, newLeaf);
            dirtyLeavesByPath.put(leafPath, newLeaf);
            // Add the leaf nodes to the newSlotParent
            if (oldLeaf != null) {
                dirtyLeavesByPath.remove(oldLeafPath);
                oldLeaf.setPath(getLeftChildPath(newSlotParentPath));
                dirtyLeavesByPath.put(oldLeaf.getPath(), oldLeaf);
            }

            // Save the first and last leaf paths
            firstLeafPath = nextFirstLeafPath;
            lastLeafPath = leafPath;
        }
    }

    /**
     * Gets the root hash. Blocks until the hash has been computed. Returns null if the
     * hashing either failed or if we have not performed any hashing at all.
     *
     * @return The hash, after blocking for the result if needed. May return null.
     */
    private Hash getRootHash() {
        try {
            return rootHash == null ? null : rootHash.get();
        } catch (InterruptedException | ExecutionException e) {
            // TODO Not sure what to do if this fails... Try, try, again?
            e.printStackTrace();
            return null;
        }
    }

    /**
     * If needed, recomputes the hashes of all parent nodes of dirty leaves and updates the
     * rootHash to be a Future with the final result of all that hashing.
     */
    private void recomputeHash() {
        // Get the old hash so we can see whether we need to recompute it at all.
        // Note that this call blocks if there was a previous hash running that
        // hasn't completed yet. This is critical, otherwise we may lose some
        // information about what needs to be rehashed and end up with the wrong
        // hash in the end.
        Hash hash = getRootHash();

        // Only recompute if we have to.
        if (hash == null || !dirtyLeaves.isEmpty() || NULL_HASH.equals(hash)) {

            // First, process all of the leaves. We need to make sure we only handle each
            // leaf once, but we also have to deal with siblings. So we'll have an array
            // of dirty leaves, sorted by path. Thus, two siblings will be side-by-side in
            // the array if they are both dirty. If a sibling is missing, then it was clean and
            // I need to look up its hash from the data source. I create a HashJobData, add it
            // to the hashWork queue, and process the next (non-sibling) leaf.
            final var dirtyLeaves = new ArrayList<>(this.dirtyLeaves.values());
            final var numDirtyLeaves = dirtyLeaves.size();
            dirtyLeaves.sort((a, b) -> Long.compare(b.getPath(), a.getPath())); // reverse the order

            // The hashWork queue will hold hashing work for parents. This is basically my reverse iterator.
            final var hashWork = new LinkedList<HashJobData>();

            // Process the leaves, adding dirty parents to the hashWork queue.
            for (int i=0; i<numDirtyLeaves; i++) {
                // We may have a dirty left leaf followed by a possible dirty right, or a
                // dirty right with a clean left.
                final var leaf = dirtyLeaves.get(i);
                final var leafPath = leaf.getPath();
                final var nextLeaf = i == numDirtyLeaves - 1 ? // Are we on the last iteration?
                        null : // If we're on the last iteration, there is no next.
                        dirtyLeaves.get(i + 1);

                HashJobData data;

                // If the next leaf is the sibling of this leaf, then we don't need to look
                // it up in the data source
                final var siblingPath = getSiblingPath(leafPath);
                if (nextLeaf != null && nextLeaf.getPath() == siblingPath) {
                    i++; // Increment so we skip this leaf on the next iteration
                    data = new HashJobData(
                            getParentPath(leafPath),
                            leaf.getFutureHash(),
                            nextLeaf.getFutureHash());
                } else {
                    // nextLeaf was not the sibling. This might have happened because there were
                    // no more leaves in the list, but a sibling might *now* exist which is a parent
                    // and can be found on the hashJobData. Look there. If we find the sibling there,
                    // then we use that. Otherwise, we need to look it up in the data source.
                    if (nextLeaf == null && !hashWork.isEmpty() && hashWork.getFirst().path == siblingPath) {
                        final var siblingData = hashWork.removeFirst();
                        data = new HashJobData(
                                getParentPath(leafPath),
                                leaf.getFutureHash(),
                                siblingData.hash);
                    } else {
                        // nextLeaf was not the sibling, so we need to look it up. There might be no
                        // sibling, in which case it will be null. A leaf might have a leaf OR a parent
                        // as a sibling, so we need to check both in the case one is null.
                        final var siblingLeaf = dataSource.loadLeaf(siblingPath);
                        if (siblingLeaf != null) {
                            data = new HashJobData(
                                    getParentPath(leafPath),
                                    leaf.getFutureHash(),
                                    siblingLeaf.getFutureHash());
                        } else {
                            final var siblingParent = dataSource.loadParentHash(siblingPath);
                            data = new HashJobData(
                                    getParentPath(leafPath),
                                    leaf.getFutureHash(),
                                    siblingParent == null ? null : completedHash(siblingParent));
                        }
                    }
                }
                data.hash = hashingPool.submit(() -> compute(data.leftHash, data.rightHash));
                dirtyParentHashes.put(data.path, data.hash);
                hashWork.addLast(data);
            }

            // Now we start processing all of the HashJobData that has been setup. For each
            // one, we will create a new Future for creating the hash. Just like with the
            // leaves, we need to look for siblings and make sure we're not processing siblings
            // unnecessarily. Fortunately, just like with the leaves, if there *is* a sibling,
            // it will be the next item in the hashJobData list, since we were careful to
            // processing leaves in reverse order. As we process each parent, we add it to
            // the hashJobData, and keep iterating until we've eventually handled everything.
            this.rootHash = null;
            while (!hashWork.isEmpty()) {
                // FIFO, pull off the first, push on the last.
                final var data = hashWork.removeFirst();
                final var path = data.path;

                if (path == ROOT_PATH) {
                    // Also set this future as the rootHash.
                    assert hashWork.isEmpty();
                    this.rootHash = data.hash;
                } else {
                    HashJobData newData;
                    // We're not at the root yet, so we need to look for a sibling. Fortunately,
                    // if there is a dirty sibling, it will be next on the hashJobData list.
                    // Otherwise, we load it from dataSource. Add the new HashJobData to the
                    // list to be processed next.
                    final var siblingPath = getSiblingPath(path);
                    if (!hashWork.isEmpty() && hashWork.getFirst().path == siblingPath) {
                        // The next node is a sibling, so lets remove it too
                        final var sibling = hashWork.removeFirst();
                        newData = new HashJobData(
                                getParentPath(path),
                                data.hash,
                                sibling.hash);
                    } else {
                        // No dirty sibling, so get a fresh one from the data source
                        final var siblingHash = dataSource.loadParentHash(siblingPath);
                        newData = new HashJobData(
                                getParentPath(path),
                                data.hash,
                                siblingHash == null ? null : completedHash(siblingHash));
                    }

                    newData.hash = hashingPool.submit(() -> compute(newData.leftHash, newData.rightHash));
                    dirtyParentHashes.put(newData.path, newData.hash);
                    hashWork.addLast(newData);
                }
            }

            try {
                // block until it is done hashing. Might actually care to do that here.
                rootHash.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                // TODO oof, now what?
            }
        }
    }

    private Hash compute(Future<Hash> leftHash, Future<Hash> rightHash) {
//                        System.out.println("Hashing (" + getRank(path) + ", " + getBreadcrumbs(path) + ")");
        try {
            if (leftHash == null && rightHash != null) {
                // Since there is only a rightHash, we might as well pass it up and not bother
                // hashing anything at all.
                return rightHash.get();
            } else if (leftHash != null && rightHash == null) {
                // Since there is only a left hash, we can use it as our hash
                return leftHash.get();
            } else if (leftHash != null) {
                // BTW: This branch is hit if right and left hash != null.
                // Since we have both a left and right hash, we need to hash them together.
                assert leftHash.get() != null;
                assert rightHash.get() != null;

                // Hash it.
                final var hash1 = leftHash.get();
                final var hash2 = rightHash.get();
                return CRYPTOGRAPHY.calcRunningHash(hash1, hash2, DigestType.SHA_384);
            } else {
                System.err.println("Both children were null. This shouldn't be possible!");
                return NULL_HASH;
            }
        } catch (Exception e) {
            e.printStackTrace();
            // TODO not sure what to do with exceptions here
            return NULL_HASH;
        }
    }

    private Future<Hash> completedHash(Hash data) {
        final var future = new CompletableFuture<Hash>();
        future.complete(data);
        return future;
    }

    public String getAsciiArt() {
        if (lastLeafPath == INVALID_PATH) {
            return "<Empty>";
        }

        final var nodeWidth = 10; // Let's reserve this many chars for each node to write their name.

        // Use this for storing all the strings we produce as we go along.
        final var strings = new ArrayList<List<String>>(64);
        final var l = new ArrayList<String>(1);
        l.add("( )");
        strings.add(l);

        // Simple depth-first traversal
        print(strings, ROOT_PATH);

        final var buf = new StringBuilder();
        final var numRows = strings.size();
        final var width = (int) (Math.pow(2, numRows-1) * nodeWidth);
        for (int i=0; i<strings.size(); i++) {
            final var list = strings.get(i);
            int x = width/2 - (nodeWidth * (int)(Math.pow(2, i)))/2;
            buf.append(" ".repeat(x));
            x = 0;
            for (var s : list) {
                final var padLeft = ((nodeWidth - s.length()) / 2);
                final var padRight = ((nodeWidth - s.length()) - padLeft);
                buf.append(" ".repeat(padLeft)).append(s).append(" ".repeat(padRight));
                x += nodeWidth;
            }
            buf.append("\n");
        }
        return buf.toString();
    }

    private void print(List<List<String>> strings, long path) {
        // Write this node out
        final var rank = getRank(path);
        final var pnode = Long.compare(path, firstLeafPath) < 0;
        final var dirtyMark = !pnode && dirtyLeavesByPath.containsKey(path) ? "*" : "";
        strings.get(rank).set(getIndexInRank(path), dirtyMark + "(" + (pnode ? "P" : "L") + ", " + (getIndexInRank(path)) + ")" + dirtyMark);

        if (pnode) {
            // Make sure we have another level down to go.
            if (strings.size() <= rank + 1) {
                final var size = (int)Math.pow(2, rank+1);
                final var list = new ArrayList<String>(size);
                for (int i=0; i<size; i++) {
                    list.add("( )");
                }
                strings.add(list);
            }

            print(strings, getLeftChildPath(path));
            print(strings, getRightChildPath(path));
        }
    }

    private static final class HashJobData {
        private final long path;
        private final Future<Hash> leftHash;
        private final Future<Hash> rightHash;
        private transient Future<Hash> hash;

        HashJobData(long path, Future<Hash> leftHash, Future<Hash> rightHash) {
            this.path = path;
            this.leftHash = leftHash;
            this.rightHash = rightHash;
        }
    }

    private static final class HashWorkQueue {
        private HashJobData[] q;
        private int head = -1; // Points to first
        private int tail = -1; // Points to last

        public HashWorkQueue(int initialSize) {
            q = new HashJobData[initialSize];
        }

        public HashJobData getFirst() {
            return q[head];
        }

        public HashJobData getLast() {
            return q[tail];
        }

        public HashJobData get(int index) {
            if (index < head || index > tail) {
                throw new IndexOutOfBoundsException();
            }

            return q[index];
        }

        public void addLast(HashJobData data) {
            head = 0;
            tail += 1;
            if (tail >= q.length) {
                q = Arrays.copyOf(q, q.length * 2);
            }
            q[tail] = data;
        }

        public boolean isEmpty() {
            return head == -1;
        }

        public int size() {
            return head == -1 ? 0 : tail - head + 1;
        }

        public void reset() {
            head = tail = -1;
        }
    }
}
