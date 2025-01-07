/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.virtualmap.internal.cache;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.virtualmap.internal.cache.VirtualNodeCache.CLASS_ID;
import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.state.MutabilityException;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.constructable.ConstructableClass;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.exceptions.PlatformException;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.futures.StandardFuture;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import com.swirlds.virtualmap.constructable.constructors.VirtualNodeCacheConstructor;
import com.swirlds.virtualmap.datasource.VirtualHashRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafBytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A cache for virtual merkel trees.
 * <p>
 * At genesis, a virtual merkel tree has an empty {@link VirtualNodeCache} and no data on disk. As values
 * are added to the tree, corresponding {@link VirtualLeafBytes}' are added to the cache. When the round
 * completes, a fast-copy of the tree is made, along with a fast-copy of the cache. Any new changes to the
 * modifiable tree are done through the corresponding copy of the cache. The original tree and original
 * cache have <strong>IMMUTABLE</strong> leaf data. The original tree is then submitted to multiple hashing
 * threads. For each internal node that is hashed, a {@link VirtualHashRecord} is created and added
 * to the {@link VirtualNodeCache}.
 * <p>
 * Eventually, there are multiple copies of the cache in memory. It may become necessary to merge two
 * caches together. The {@link #merge()} method is provided to merge a cache with the one-prior cache.
 * These merges are non-destructive, meaning it is OK to continue to query against a cache that has been merged.
 * <p>
 * At some point, the cache should be flushed to disk. This is done by calling the {@link #dirtyLeavesForFlush(long,
 * long)} and {@link #dirtyHashesForFlush(long)} methods and sending them to the code responsible for flushing. The
 * cache itself knows nothing about the data source or how to save data, it simply maintains a record of mutations
 * so that some other code can perform the flushing.
 * <p>
 * A cache is {@link FastCopyable}, so that each copy of the {@link VirtualMap} has a corresponding copy of the
 * {@link VirtualNodeCache}, at the same version. It keeps track of immutability of leaf data and internal
 * data separately, since the set of dirty leaves in a copy is added during {@code handleTransaction}
 * (which uses the most current copy), but the set of dirty internals during the {@code hashing} phase,
 * which is always one-copy removed from the current copy.
 * <p>
 * Caches have pointers to the next and previous caches in the chain of copies. This is necessary to support
 * merging of two caches (we could have added API to explicitly merge two caches together, but our use case
 * only supports merging a cache and its next-of-kin (the next copy in the chain), so we made this the only
 * supported case in the API).
 * <p>
 * The {@link VirtualNodeCache} was designed with our specific performance requirements in mind. We need to
 * maintain a chain of mutations over versions (the so-called map-of-lists approach to a fast-copy data
 * structure), yet we need to be able to get all mutations for a given version quickly, and we need to release
 * memory back to the garbage collector quickly. We also need to maintain the mutations for leaf data, for
 * which leaves occupied a given path, and for the internal node hashes at a given path.
 * <p>
 * To fulfill these design requirements, each "chain" of caches share three different indexes:
 * {@link #keyToDirtyLeafIndex}, {@link #pathToDirtyLeafIndex}, and {@link #pathToDirtyHashIndex}.
 * Each of these is a map from either the leaf key or a path (long) to a custom linked list data structure. Each element
 * in the list is a {@link Mutation} with a reference to the data item (either a {@link VirtualHashRecord}
 * or a {@link VirtualLeafBytes}, depending on the list), and a reference to the next {@link Mutation}
 * in the list. In this way, given a leaf key or path (based on the index), you can get the linked list and
 * walk the links from mutation to mutation. The most recent mutation is first in the list, the oldest mutation
 * is last. There is at most one mutation per cache per entry in one of these indexes. If a leaf value is modified
 * twice in a single cache, only a single mutation exists recording the most recent change. There is no need to
 * keep track of multiple mutations per cache instance for the same leaf or internal node.
 * <p>
 * If there is one non-obvious gotcha that you *MUST* be aware of to use this class, it is that a record
 * (leaf or internal) *MUST NOT BE REUSED ACROSS CACHE INSTANCES*. If I create a leaf record, and put it
 * into {@code cache0}, and then create a copy of {@code cache0} called {@code cache1}, I *MUST NOT* put
 * the same leaf record into {@code cache1} or modify the old leaf record, otherwise I will pollute
 * {@code cache0} with a leaf modified outside of the lifecycle for that cache. Instead, I must make a
 * fast copy of the leaf record and put *that* copy into {@code cache1}.
 */
@ConstructableClass(value = CLASS_ID, constructorType = VirtualNodeCacheConstructor.class)
@SuppressWarnings("rawtypes")
public final class VirtualNodeCache implements FastCopyable, SelfSerializable {

    private static final Logger logger = LogManager.getLogger(VirtualNodeCache.class);

    public static final long CLASS_ID = 0x493743f0ace96d2cL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
        public static final int NO_LEAF_HASHES = 2;
    }

    /**
     * A special {@link VirtualLeafBytes} that represents a deleted leaf. At times, the {@link VirtualMap}
     * will ask the cache for a leaf either by key or path. At such times, if we determine by looking at
     * the mutation that the leaf has been deleted, we will return this singleton instance.
     */
    public static final VirtualLeafBytes DELETED_LEAF_RECORD = new VirtualLeafBytes(-1, Bytes.EMPTY, null, null);

    /**
     * A special {@link Hash} used to indicate that the record associated with a particular
     * path has been deleted. The {@link VirtualMap} has code that asks the cache for the record. If the
     * return value is null, it will create a new record or load from disk, since it thinks {@code null}
     * means that the value doesn't exist. But we need it to know that we have deleted the record so it
     * doesn't go to disk. Hence, we have this special marker.
     */
    public static final Hash DELETED_HASH = new Hash();

    /**
     * Another marker {@link Hash} instance used to store null hashes instead of {@code null}s, which
     * are only used for deleted hashes. Before hashes are returned to callers in {@link #dirtyHashes}
     * or {@link #lookupHashByPath(long)}, this value is converted to {@code null}.
     */
    public static final Hash NULL_HASH = new Hash();

    private static Executor cleaningPool = null;

    /**
     * This method is invoked from a non-static method and uses the provided configuration.
     * Consequently, the cleaning pool will be initialized using the configuration provided
     * by the first instance of VirtualNodeCache class that calls the relevant non-static methods.
     * Subsequent calls will reuse the same pool, regardless of any new configurations provided.
     */
    private static synchronized Executor getCleaningPool(@NonNull final VirtualMapConfig virtualMapConfig) {
        requireNonNull(virtualMapConfig);

        if (cleaningPool == null) {
            cleaningPool = Boolean.getBoolean("syncCleaningPool")
                    ? Runnable::run
                    : new ThreadPoolExecutor(
                            virtualMapConfig.getNumCleanerThreads(),
                            virtualMapConfig.getNumCleanerThreads(),
                            60L,
                            TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(),
                            new ThreadConfiguration(getStaticThreadManager())
                                    .setThreadGroup(new ThreadGroup("virtual-cache-cleaners"))
                                    .setComponent("virtual-map")
                                    .setThreadName("cache-cleaner")
                                    .setExceptionHandler((t, ex) -> logger.error(
                                            EXCEPTION.getMarker(),
                                            "Failed to purge unneeded key/mutationList pairs",
                                            ex))
                                    .buildFactory());
        }
        return cleaningPool;
    }

    /**
     * The fast-copyable version of the cache. This version number is auto-incrementing and set
     * at construction time and cannot be changed, unless the cache is created through deserialization,
     * in which case it is set during deserialization and not changed thereafter.
     */
    private final AtomicLong fastCopyVersion = new AtomicLong(0L);

    // Pointers to the next and previous versions of VirtualNodeCache. When released,
    // the pointers are fixed so that next and prev point to each other instead of this
    // instance. The only time that these can be changed is during merge or release.
    // We only use these references during merging, otherwise we wouldn't even need them...

    /**
     * A reference to the next (older) version in the chain of copies. The reference is null
     * if this is the last copy in the chain.
     */
    private final AtomicReference<VirtualNodeCache> next = new AtomicReference<>();

    /**
     * A reference to the previous (newer) version in the chain of copies. The reference is
     * null if this is the first copy in the chain. This is needed to support merging.
     */
    private final AtomicReference<VirtualNodeCache> prev = new AtomicReference<>();

    /**
     * A shared index of keys (K) to the linked lists that contain the values for that key
     * across different versions. The value is a reference to the
     * first {@link Mutation} in the list.
     * <p>
     * For example, the key "APPLE" might point to a {@link Mutation} that refers to the 3rd
     * copy, where "APPLE" was first modified. We simply follow the {@link Mutation} to that
     * {@link Mutation} and return the associated leaf value.
     * <p>
     * <strong>ONE PER CHAIN OF CACHES</strong>.
     */
    private final Map<Bytes, Mutation<Bytes, VirtualLeafBytes>> keyToDirtyLeafIndex;

    /**
     * A shared index of paths to leaves, via {@link Mutation}s. Works the same as {@link #keyToDirtyLeafIndex}.
     * <p>
     * <strong>ONE PER CHAIN OF CACHES</strong>.
     */
    private final Map<Long, Mutation<Long, Bytes>> pathToDirtyLeafIndex;

    /**
     * A shared index of paths to internals, via {@link Mutation}s. Works the same as {@link #keyToDirtyLeafIndex}.
     * <p>
     * <strong>ONE PER CHAIN OF CACHES</strong>.
     */
    private final Map<Long, Mutation<Long, Hash>> pathToDirtyHashIndex;

    /**
     * Whether this instance is released. A released cache is often the last in the
     * chain, but may be any in the middle of the chain. However, you cannot
     * call {@link #release()} on any cache except <strong>the last</strong> one
     * in the chain. To release an intermediate instance, call {@link #merge()}.
     */
    private final AtomicBoolean released = new AtomicBoolean(false);

    /**
     * Indicates whether this cache has been prepared for flush by calling its {@link
     * #prepareForFlush()} method.
     */
    private final AtomicBoolean preparedForFlush = new AtomicBoolean(false);

    /**
     * Whether the <strong>leaf</strong> indexes in this cache are immutable. We track
     * immutability of leaves and internal nodes separately, because leaves are only
     * modified on the head of the chain (the most recent version).
     */
    private final AtomicBoolean leafIndexesAreImmutable = new AtomicBoolean(false);

    /**
     * Whether the <strong>internal node</strong> indexes in this cache are immutable.
     * It turns out that we do not have an easy way to know when a cache is no longer
     * involved in hashing, unless the {@link VirtualMap} tells it. So we add a method,
     * {@link #seal()}, to the virtual map which is called at the end of hashing to
     * let us know that the cache should be immutable from this point forward. Note that
     * an immutable cache can still be merged and released.
     * <p>
     * Since during the {@code handleTransaction} phase there should be no hashing going on,
     * we start off with this being set to true, just to catch bugs or false assumptions.
     */
    private final AtomicBoolean hashesAreImmutable = new AtomicBoolean(true);

    /**
     * A set of all modifications to leaves that occurred in this version of the cache.
     * Note that this isn't actually a set, we have to sort and filter duplicates later.
     * <p>
     * <strong>ONE PER CACHE INSTANCE</strong>.
     */
    private volatile ConcurrentArray<Mutation<Bytes, VirtualLeafBytes>> dirtyLeaves = new ConcurrentArray<>();

    /**
     * A set of leaf path changes that occurred in this version of the cache. This is separate
     * from dirtyLeaves because dirtyLeaves captures the history of changes to leaves, while
     * this captures the history of which leaves lived at a given path.
     * Note that this isn't actually a set, we have to sort and filter duplicates later.
     * <p>
     * <strong>ONE PER CACHE INSTANCE</strong>.
     */
    private volatile ConcurrentArray<Mutation<Long, Bytes>> dirtyLeafPaths = new ConcurrentArray<>();

    /**
     * A set of all modifications to node hashes that occurred in this version of the cache.
     * We use a list as an optimization, but it requires us to filter out mutations for the
     * same key or path from multiple versions.
     * Note that this isn't actually a set, we have to sort and filter duplicates later.
     * <p>
     * <strong>ONE PER CACHE INSTANCE</strong>.
     */
    private volatile ConcurrentArray<Mutation<Long, Hash>> dirtyHashes = new ConcurrentArray<>();

    /**
     * Estimated size of all leaf records in dirtyLeaves. This size is calculated lazily during
     * the first call to {@link #getEstimatedSize()}. This method may only be called after
     * {@link #leafIndexesAreImmutable} is updated to true.
     */
    private final AtomicInteger estimatedLeavesSizeInBytes = new AtomicInteger(0);

    /**
     * Estimated size of all leaf keys in dirtyLeafPaths. This size is calculated similar to
     * {@link #estimatedLeavesSizeInBytes} above.
     */
    private final AtomicInteger estimatedLeafPathsSizeInBytes = new AtomicInteger(0);

    /**
     * Estimated size of all hashes in dirtyHashes. This size is updated on every hash operation
     * (put, delete).
     */
    private final AtomicInteger estimatedHashesSizeInBytes = new AtomicInteger(0);

    /**
     * Indicates if this virtual cache instance contains mutations from older cache versions
     * as a result of cache merge operation.
     */
    private final AtomicBoolean mergedCopy = new AtomicBoolean(false);

    /**
     * A shared lock that prevents two copies from being merged/released at the same time. For example,
     * one thread might be merging two caches while another thread is releasing the oldest copy. These
     * all happen completely in parallel, but we have some bookkeeping which should be done inside
     * a critical section.
     */
    private final ReentrantLock releaseLock;

    /**
     * Whether this instance is a snapshot. There are certain operations like serialization that
     * are only valid on a snapshot. Hence, we need this flag to validate this condition is met.
     */
    private final AtomicBoolean snapshot = new AtomicBoolean(false);

    /**
     * lastReleased serves as a lock shared by a VirtualNodeCache family.
     * It provides needed synchronization between purge() and snapshot() (see #5838).
     * It didn't have to be atomic, just a reference to mutable long.
     * Purge() may be blocked by snapshot() until it finishes.
     * Snapshot(), however, should not be blocked for long by purge().
     * lastReleased ensures that snapshot() is aware of which version should not be included in
     * the snapshot.
     */
    private final AtomicLong lastReleased;

    /** Platform configuration for VirtualMap */
    @NonNull
    private final VirtualMapConfig virtualMapConfig;

    /**
     * Create a new VirtualNodeCache. The cache will be the first in the chain. It will get a
     * fastCopyVersion of zero, and create the shared data structures.
     *
     * @param virtualMapConfig platform configuration for VirtualMap
     */
    public VirtualNodeCache(final @NonNull VirtualMapConfig virtualMapConfig) {
        this(virtualMapConfig, 0);
    }

    /**
     * Create a new VirtualNodeCache. The cache will be the first in the chain. It will get the
     * specified fastCopyVersion, and create the shared data structures.
     *
     * @param virtualMapConfig platform configuration for VirtualMap
     * @param fastCopyVersion copy version
     */
    public VirtualNodeCache(final @NonNull VirtualMapConfig virtualMapConfig, long fastCopyVersion) {
        this.keyToDirtyLeafIndex = new ConcurrentHashMap<>();
        this.pathToDirtyLeafIndex = new ConcurrentHashMap<>();
        this.pathToDirtyHashIndex = new ConcurrentHashMap<>();
        this.releaseLock = new ReentrantLock();
        this.lastReleased = new AtomicLong(-1L);
        this.fastCopyVersion.set(fastCopyVersion);
        this.virtualMapConfig = requireNonNull(virtualMapConfig);
    }

    /**
     * Create a copy of the cache. The resulting copy will have a reference to the previous cache,
     * and the previous cache will have a reference to the copy. The copy will have a fastCopyVersion
     * that is one greater than the one it copied from. It will share some data structures. Critically,
     * it will modify the {@link #hashesAreImmutable} to be false so that the older copy
     * can be hashed.
     *
     * @param source
     * 		Cannot be null and must be the most recent version!
     */
    @SuppressWarnings("CopyConstructorMissesField")
    private VirtualNodeCache(final VirtualNodeCache source) {
        // Make sure this version is exactly 1 greater than source
        this.fastCopyVersion.set(source.fastCopyVersion.get() + 1);

        // Get a reference to the shared data structures
        this.keyToDirtyLeafIndex = source.keyToDirtyLeafIndex;
        this.pathToDirtyLeafIndex = source.pathToDirtyLeafIndex;
        this.pathToDirtyHashIndex = source.pathToDirtyHashIndex;
        this.releaseLock = source.releaseLock;
        this.lastReleased = source.lastReleased;
        this.virtualMapConfig = source.virtualMapConfig;

        // The source now has immutable leaves and mutable internals
        source.prepareForHashing();

        // Wire up the next & prev references
        this.next.set(source);
        source.prev.set(this);
    }

    /**
     * {@inheritDoc}
     *
     * Only a single thread should call copy at a time, but other threads may {@link #release()} and {@link#merge()}
     * concurrent to this call on other nodes in the chain.
     */
    @SuppressWarnings("unchecked")
    @Override
    public VirtualNodeCache copy() {
        return new VirtualNodeCache(this);
    }

    /**
     * Makes the cache immutable for leaf changes, but mutable for internal node changes.
     * This method call is idempotent.
     */
    public void prepareForHashing() {
        this.leafIndexesAreImmutable.set(true);
        this.hashesAreImmutable.set(false);
        this.dirtyLeaves.seal();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isImmutable() {
        // We use this as the stand-in as it obeys the normal semantics. Technically there is no advantage to
        // having this class implement FastCopyable, other than declaration of intent.
        return this.leafIndexesAreImmutable.get();
    }

    /**
     * {@inheritDoc}
     *
     * May be called on one cache in the chain while another copy is being made. Do not call
     * release on two caches in the chain concurrently. Must only release the very oldest cache in the chain. See
     * {@link #merge()}.
     *
     * @throws IllegalStateException
     * 		if this is not the oldest cache in the chain
     */
    @Override
    public boolean release() {
        throwIfDestroyed();

        // Under normal conditions "seal()" would have been called already, but it is at least possible to
        // release something that hasn't been sealed. So we call "seal()", just to tidy things up.
        seal();

        synchronized (lastReleased) {
            lastReleased.set(fastCopyVersion.get());
        }

        // We lock across all merges and releases across all copies (releaseLock is shared with all copies)
        // to prevent issues with one thread releasing while another thread is merging (as might happen if
        // the archive thread wants to release and another thread wants to merge).
        releaseLock.lock();
        try {
            // We are very strict about this, or the semantics around the cache will break.
            if (next.get() != null) {
                throw new IllegalStateException("Cannot release an intermediate version, must release the oldest");
            }

            this.released.set(true);

            // Fix the next/prev pointer so this entire cache will get dropped. We don't have to clear
            // any of our per-instance stuff, because nobody will be holding a strong reference to the
            // cache anymore. Still, in the off chance that somebody does hold a reference, we might
            // as well be proactive about it.
            wirePrevAndNext();
        } finally {
            releaseLock.unlock();
        }

        // Fire off the cleaning threads to go and clear out data in the indexes that doesn't need
        // to be there anymore.
        getCleaningPool(virtualMapConfig).execute(() -> {
            purge(dirtyLeaves, keyToDirtyLeafIndex, virtualMapConfig);
            purge(dirtyLeafPaths, pathToDirtyLeafIndex, virtualMapConfig);
            purge(dirtyHashes, pathToDirtyHashIndex, virtualMapConfig);

            dirtyLeaves = null;
            dirtyLeafPaths = null;
            dirtyHashes = null;

            estimatedLeavesSizeInBytes.set(0);
            estimatedLeafPathsSizeInBytes.set(0);
            estimatedHashesSizeInBytes.set(0);
        });

        if (logger.isTraceEnabled()) {
            logger.trace("Released {}", fastCopyVersion);
        }

        return true;
    }

    @Override
    public boolean isDestroyed() {
        return this.released.get();
    }

    /**
     * Merges this cache with the one that is just-newer.
     * This cache will be removed from the chain and become available for garbage collection. Both this
     * cache and the one it is being merged into <strong>must</strong> be sealed (full immutable).
     *
     * @throws IllegalStateException
     * 		if there is nothing to merge into, or if both this cache and the one
     * 		it is merging into are not sealed.
     */
    public void merge() {
        releaseLock.lock();
        try {
            // We only permit you to merge a cache if it is no longer being used for hashing.
            final VirtualNodeCache p = prev.get();
            if (p == null) {
                throw new IllegalStateException("Cannot merge with a null cache");
            } else if (!p.hashesAreImmutable.get() || !hashesAreImmutable.get()) {
                throw new IllegalStateException("You can only merge caches that are sealed");
            }

            // Merge my mutations into the previous (newer) cache's arrays.
            // This operation has a high probability of producing override mutations. That is, two mutations
            // for the same key/path but with different versions. Before returning to a caller a stream of
            // dirty leaves or dirty hashes, the stream must be sorted (which we had to do anyway) and
            // deduplicated. But it makes for a _VERY FAST_ merge operation.
            p.dirtyLeaves.merge(dirtyLeaves);
            p.dirtyLeafPaths.merge(dirtyLeafPaths);
            p.dirtyHashes.merge(dirtyHashes);
            p.estimatedLeavesSizeInBytes.addAndGet(estimatedLeavesSizeInBytes.get());
            p.estimatedLeafPathsSizeInBytes.addAndGet(estimatedLeafPathsSizeInBytes.get());
            p.estimatedHashesSizeInBytes.addAndGet(estimatedHashesSizeInBytes.get());
            p.mergedCopy.set(true);

            // Remove this cache from the chain and wire the prev and next caches together.
            // This will allow this cache to be garbage collected.
            wirePrevAndNext();
        } finally {
            releaseLock.unlock();

            if (logger.isTraceEnabled()) {
                logger.trace(
                        "Merged version {}, {} dirty leaves, {} dirty internals",
                        fastCopyVersion,
                        dirtyLeaves.size(),
                        dirtyHashes.seal());
            }
        }
    }

    /**
     * Seals this cache, making it immutable. A sealed cache can still be merged with another sealed
     * cache.
     */
    public void seal() {
        leafIndexesAreImmutable.set(true);
        hashesAreImmutable.set(true);
        dirtyLeaves.seal();
        dirtyHashes.seal();
        dirtyLeafPaths.seal();
    }

    // --------------------------------------------------------------------------------------------
    // API for caching leaves.
    //
    // The mutation APIs can **ONLY** be called on the handleTransaction thread and only on the
    // most recent copy. The query APIs can be called from any thread.
    // --------------------------------------------------------------------------------------------

    public void putLeaf(@NonNull final VirtualLeafBytes leaf) {
        putLeaf(leaf, false);
    }

    /**
     * Puts a leaf into the cache. Called whenever there is a <strong>new</strong> leaf or
     * whenever the <strong>value</strong> of the leaf has changed. Note that the caller is
     * responsible for ensuring that this leaf instance does not exist in any older copies
     * of caches. This is done by making fast-copies of the leaf as needed. This is the caller's
     * responsibility!
     * <p>
     * The caller must <strong>also</strong> call this each time the path of the node changes,
     * since we maintain a path-to-leaf mapping and need to be aware of the new path, even though
     * the value has not necessarily changed. This is necessary so that we record the leaf record
     * as dirty, since we need to include this leaf is the set that are involved in hashing, and
     * since we need to include this leaf in the set that are written to disk (since paths are
     * also written to disk).
     * <p>
     * This method should only be called from the <strong>HANDLE TRANSACTION THREAD</strong>.
     * It is NOT threadsafe!
     *
     * @param leaf
     * 		The leaf to put. Must not be null. Must have the correct key and path.
     * @param newRecord
     *      Indicates if this leaf update is to insert a new entity rather than to update
     *      an existing one
     * @throws NullPointerException
     * 		if the leaf is null
     * @throws MutabilityException
     * 		if the cache is immutable for leaf changes
     */
    public void putLeaf(@NonNull final VirtualLeafBytes leaf, final boolean newRecord) {
        throwIfLeafImmutable();
        requireNonNull(leaf);

        // The key must never be empty. Only DELETED_LEAF_RECORD should have an empty key.
        // The VirtualMap forbids null keys, so we should never see an empty key here.
        final Bytes key = leaf.keyBytes();

        // Update the path index to point to this node at this path
        updatePaths(key, null, null, leaf.path(), pathToDirtyLeafIndex, dirtyLeafPaths);

        // Get the first data element (mutation) in the list based on the key,
        // and then create or update the associated mutation.
        keyToDirtyLeafIndex.compute(key, (k, mutations) -> mutate(leaf, newRecord, mutations));
    }

    /**
     * Records that the given leaf was deleted in the cache. This creates a "delete" {@link Mutation} in the
     * linked list for this leaf. The leaf must have a correct leaf path and key. This call will
     * clear the leaf path for you.
     * <p>
     * This method should only be called from the <strong>HANDLE TRANSACTION THREAD</strong>.
     * It is NOT threadsafe!
     *
     * @param leaf
     * 		the leaf to delete. Must not be null.
     * @throws NullPointerException
     * 		if the leaf argument is null
     * @throws MutabilityException
     * 		if the cache is immutable for leaf changes
     */
    public void deleteLeaf(@NonNull final VirtualLeafBytes leaf) {
        throwIfLeafImmutable();
        requireNonNull(leaf);

        // This leaf is no longer at this leaf path. So clear it.
        clearLeafPath(leaf.path());

        // Find or create the mutation and mark it as deleted
        final Bytes key = leaf.keyBytes();
        assert key != Bytes.EMPTY : "Keys cannot be empty";
        assert key.length() > 0 : "Keys cannot be empty";
        keyToDirtyLeafIndex.compute(key, (k, mutations) -> {
            mutations = mutate(leaf, false, mutations);
            mutations.setDeleted(true);
            assert pathToDirtyLeafIndex.get(leaf.path()).isDeleted() : "It should be deleted too";
            return mutations;
        });
    }

    /**
     * A leaf that used to be at this {@code path} is no longer there. This should really only
     * be called if there is no longer any leaf at this path. This happens when we add leaves
     * and the leaf at firstLeafPath is moved and replaced by an internal node, or when a leaf
     * is deleted and the lastLeafPath is moved or removed.
     *
     * This method should only be called from the <strong>HANDLE TRANSACTION THREAD</strong>.
     * It is NOT threadsafe!
     *
     * @param path
     * 		The path to clear. After this call, there is no longer a leaf at this path.
     * @throws MutabilityException
     * 		if the cache is immutable for leaf changes
     */
    public void clearLeafPath(final long path) {
        throwIfLeafImmutable();
        // Note: this marks the mutations as deleted, in addition to clearing the value of the mutation
        updatePaths(null, null, null, path, pathToDirtyLeafIndex, dirtyLeafPaths);
    }

    /**
     * Looks for a leaf record in this cache instance, and all older ones, based on the given {@code key}.
     * For example, suppose {@code cache2} has a leaf record for the key "A", but we have {@code cache4}.
     * By calling this method on {@code cache4}, it will find the record in {@code cache2}.
     * If the leaf record exists, it is returned. If the leaf was deleted, {@link #DELETED_LEAF_RECORD}
     * is returned. If there is no mutation record at all, null is returned, indicating a cache miss,
     * and that the caller should consult on-disk storage.
     * <p>
     * This method may be called concurrently from multiple threads.
     *
     * @param key
     * 		The key to use to lookup. Cannot be null.
     * @return A {@link VirtualLeafBytes} if there is one in the cache (this instance or a previous
     * 		copy in the chain), or null if there is not one.
     * @throws NullPointerException
     * 		if the key is null
     * @throws com.swirlds.common.exceptions.ReferenceCountException
     * 		if the cache has already been released
     */
    public VirtualLeafBytes lookupLeafByKey(final Bytes key) {
        requireNonNull(key);

        // The only way to be released is to be in a condition where the data source has
        // the data that was once in this cache but was merged and is therefore now released.
        // So we can return null and know the caller can find the data in the data source.
        if (released.get()) {
            return null;
        }

        // Get the newest mutation that is less or equal to this fastCopyVersion. If forModify and
        // the mutation does not exactly equal this fastCopyVersion, then create a mutation.
        final Mutation<Bytes, VirtualLeafBytes> mutation = lookup(keyToDirtyLeafIndex.get(key));

        // Always return null if there is no mutation regardless of forModify
        if (mutation == null) {
            return null;
        }

        // If the mutation was deleted, return our marker instance, regardless of forModify
        if (mutation.isDeleted()) {
            return DELETED_LEAF_RECORD;
        }

        return mutation.value;
    }

    /**
     * Looks for a leaf record in this cache instance, and all older ones, based on the given {@code path}.
     * If the leaf record exists, it is returned. If the leaf was deleted, {@link #DELETED_LEAF_RECORD}
     * is returned. If there is no mutation record at all, null is returned, indicating a cache miss,
     * and that the caller should consult on-disk storage.
     * <p>
     * This method may be called concurrently from multiple threads, but <strong>MUST NOT</strong>
     * be called concurrently for the same path! It is NOT fully threadsafe!
     *
     * @param path
     * 		The path to use to lookup.
     * @return A {@link VirtualLeafBytes} if there is one in the cache (this instance or a previous
     * 		copy in the chain), or null if there is not one.
     * @throws com.swirlds.common.exceptions.ReferenceCountException
     * 		if the cache has already been released
     */
    public VirtualLeafBytes lookupLeafByPath(final long path) {
        // The only way to be released is to be in a condition where the data source has
        // the data that was once in this cache but was merged and is therefore now released.
        // So we can return null and know the caller can find the data in the data source.
        if (released.get()) {
            return null;
        }

        // Get the newest mutation that equals this fastCopyVersion. If forModify and
        // the mutation does not exactly equal this fastCopyVersion, then create a mutation.
        // Note that the mutations in pathToDirtyLeafIndex contain the *path* as the key,
        // and a leaf record *key* as the value. Thus, we look up a mutation first in the
        // pathToDirtyLeafIndex, get the leaf key, and then lookup based on that key.
        final Mutation<Long, Bytes> mutation = lookup(pathToDirtyLeafIndex.get(path));
        // If mutation is null (path is unknown), return null regardless of forModify
        if (mutation == null) {
            return null;
        }

        return mutation.isDeleted() ? DELETED_LEAF_RECORD : lookupLeafByKey(mutation.value);
    }

    /**
     * Returns a stream of dirty leaves from this cache instance to hash this virtual map copy. The stream
     * is sorted by paths.
     *
     * @param firstLeafPath
     * 		The first leaf path to include to the stream
     * @param lastLeafPath
     *      The last leaf path to include to the stream
     * @return
     *      A stream of dirty leaves for hashing
     */
    public Stream<VirtualLeafBytes> dirtyLeavesForHash(final long firstLeafPath, final long lastLeafPath) {
        if (mergedCopy.get()) {
            throw new IllegalStateException("Cannot get dirty leaves for hashing on a merged cache copy");
        }
        // This method is called on a cache copy, which is not a result of merging older
        // copies. There is no need to filter mutations here
        final Stream<VirtualLeafBytes> result = dirtyLeaves(firstLeafPath, lastLeafPath);
        return result.sorted(Comparator.comparingLong(VirtualLeafBytes::path));
    }

    /**
     * Returns a stream of dirty leaves from this cache instance to flush this virtual map copy and all
     * previous copies merged into this one to disk.
     *
     * <p>{@link #prepareForFlush()} must be called before this method.
     *
     * @param firstLeafPath
     * 		The first leaf path to include to the stream
     * @param lastLeafPath
     *      The last leaf path to include to the stream
     * @return
     *      A stream of dirty leaves for flushes
     */
    public Stream<VirtualLeafBytes> dirtyLeavesForFlush(final long firstLeafPath, final long lastLeafPath) {
        if (!preparedForFlush.get()) {
            throw new IllegalStateException("This cache has not been prepared for flush");
        }
        return dirtyLeaves(firstLeafPath, lastLeafPath);
    }

    /**
     * Gets a stream of dirty leaves <strong>from this cache instance</strong>. Deleted leaves are not included
     * in this stream.
     *
     * <p>
     * This method is called for two purposes. First, to get dirty leaves to hash a single virtual map copy. The
     * resulting stream is expected to be sorted. No duplicate entries are expected in this case, as within a
     * single version there may not be duplicates. Second, to get dirty leaves to flush them to disk. In this
     * case, the stream doesn't need to be sorted, but there may be duplicated entries from different versions.
     *
     * <p>
     * This method may be called concurrently from multiple threads (although in practice, this should never happen).
     *
     * @param firstLeafPath
     * 		The first leaf path to receive in the results. It is possible, through merging of multiple rounds,
     * 		for the data to have leaf data that is outside the expected range for the {@link VirtualMap} of
     * 		this cache. We need to provide the leaf boundaries to compensate for this.
     * @param lastLeafPath
     * 		The last leaf path to receive in the results. It is possible, through merging of multiple rounds,
     * 		for the data to have leaf data that is outside the expected range for the {@link VirtualMap} of
     * 		this cache. We need to provide the leaf boundaries to compensate for this.
     * @return A non-null stream of dirty leaves. May be empty. Will not contain duplicate records
     * @throws MutabilityException if called on a cache that still allows dirty leaves to be added
     */
    private Stream<VirtualLeafBytes> dirtyLeaves(final long firstLeafPath, final long lastLeafPath) {
        if (!dirtyLeaves.isImmutable()) {
            throw new MutabilityException("Cannot call on a cache that is still mutable for dirty leaves");
        }
        return dirtyLeaves.stream()
                .filter(mutation -> {
                    final long path = mutation.value.path();
                    return path >= firstLeafPath && path <= lastLeafPath;
                })
                .filter(Mutation::notFiltered)
                .filter(mutation -> !mutation.isDeleted())
                .map(mutation -> mutation.value);
    }

    /**
     * Gets a stream of deleted leaves <strong>from this cache instance</strong>.
     * <p>
     * This method may be called concurrently from multiple threads (although in practice, this should never happen).
     *
     * @return A non-null stream of deleted leaves. May be empty. Will not contain duplicate records.
     * @throws MutabilityException
     * 		if called on a cache that still allows dirty leaves to be added
     */
    public Stream<VirtualLeafBytes> deletedLeaves() {
        if (!dirtyLeaves.isImmutable()) {
            throw new MutabilityException("Cannot call on a cache that is still mutable for dirty leaves");
        }

        final Map<Bytes, VirtualLeafBytes> leaves = new ConcurrentHashMap<>();
        final StandardFuture<Void> result = dirtyLeaves.parallelTraverse(getCleaningPool(virtualMapConfig), element -> {
            if (element.isDeleted()) {
                final Bytes key = element.key;
                final Mutation<Bytes, VirtualLeafBytes> mutation = lookup(keyToDirtyLeafIndex.get(key));
                if (mutation != null && mutation.isDeleted()) {
                    leaves.putIfAbsent(key, element.value);
                }
            }
        });
        try {
            result.getAndRethrow();
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PlatformException("VirtualNodeCache.deletedLeaves() interrupted", ex, EXCEPTION);
        }
        return leaves.values().stream();
    }

    // --------------------------------------------------------------------------------------------
    // API for caching internal nodes.
    //
    // The mutation APIs should **ONLY** be called on the hashing threads, and can only be called
    // until the cache is sealed. The query APIs can be called from any thread.
    // --------------------------------------------------------------------------------------------

    /**
     * For testing purposes only. Equivalent to putHash(node.getPath(), node.getHash())
     * @param node the node to get path and hash from
     */
    public void putHash(final VirtualHashRecord node) {
        requireNonNull(node);
        putHash(node.path(), node.hash());
    }

    /**
     * Stores a {@link Hash} into this version of the cache for a given path. This can be called
     * during {@code handleTransaction}, or during hashing, but must not be called once the
     * instance has been sealed (after hashing).
     * <p>
     * This method may be called concurrently from multiple threads, but <strong>MUST NOT</strong>
     * be called concurrently for the same path! It is NOT fully threadsafe!
     *
     * @param path
     * 		Node path
     * @param hash
     * 		Node hash. Null values are accepted, although observed in tests only. In real scenarios
     * 	    this method is only called by VirtualHasher (via VirtualRootNode), and it never puts
     * 	    null hashes to the cache
     * @throws MutabilityException
     * 		if the instance has been sealed
     */
    public void putHash(final long path, final Hash hash) {
        throwIfInternalsImmutable();
        // If the hash is null, put NULL_HASH instead to avoid mutation to be marked as deleted
        final Hash value = hash != null ? hash : NULL_HASH;
        updatePaths(
                value, estimatedHashesSizeInBytes, Hash::getSerializedLength, path, pathToDirtyHashIndex, dirtyHashes);
    }

    /**
     * Marks the hash at {@code path} as having been deleted. This only happens
     * if the merkle tree shrinks. This is only called during {@code handleTransaction},
     * NOT during hashing and NOT after hashing.
     * <p>
     * This method may be called concurrently from multiple threads, but <strong>MUST NOT</strong>
     * be called concurrently for the same record! It is NOT fully threadsafe!
     *
     * @param path
     * 		The path to the virtual node that is to be marked as deleted in this cache.
     * @throws MutabilityException
     * 		if called when <strong>leaves</strong> are immutable.
     */
    public void deleteHash(final long path) {
        throwIfLeafImmutable();
        updatePaths(
                null, estimatedHashesSizeInBytes, Hash::getSerializedLength, path, pathToDirtyHashIndex, dirtyHashes);
    }

    /**
     * Looks for an internal node record in this cache instance, and all older ones, based on the
     * given {@code path}. If the record exists, it is returned. If the nodes was deleted,
     * {@link #DELETED_LEAF_RECORD} is returned. If there is no mutation record at all, null is returned,
     * indicating a cache miss, and that the caller should consult on-disk storage.
     * <p>
     * This method may be called concurrently from multiple threads, but <strong>MUST NOT</strong>
     * be called concurrently for the same record! It is NOT fully threadsafe!
     *
     * @param path
     * 		The path to use to lookup.
     * @return A {@link Hash} if there is one in the cache (this instance or a previous
     * 		copy in the chain), or null if there is not one.
     * @throws com.swirlds.common.exceptions.ReferenceCountException
     * 		if the cache has already been released
     */
    public Hash lookupHashByPath(final long path) {
        // The only way to be released is to be in a condition where the data source has
        // the data that was once in this cache but was merged and is therefore now released.
        // So we can return null and know the caller can find the data in the data source.
        if (released.get()) {
            return null;
        }

        final Mutation<Long, Hash> mutation = lookup(pathToDirtyHashIndex.get(path));

        // Always return null if there is no mutation regardless of forModify
        if ((mutation == null) || (mutation.value == NULL_HASH)) {
            return null;
        }

        // If the mutation was deleted, return our marker instance
        if (mutation.isDeleted()) {
            return DELETED_HASH;
        }

        return mutation.value;
    }

    /**
     * Gets a stream of dirty hashes <strong>from this cache instance</strong>. Deleted hashes are
     * not included in this stream. Must be called <strong>after</strong> the cache has been sealed.
     *
     * <p>This method may be called concurrently from multiple threads (although in practice, this should
     * never happen).
     *
     * <p>{@link #prepareForFlush()} must be called before this method.
     *
     * @param lastLeafPath
     * 		The last leaf path at and above which no node results should be returned. It is possible,
     * 		through merging of multiple rounds, for the data to have data that is outside the expected range
     * 		for the {@link VirtualMap} of this cache. We need to provide the leaf boundaries to compensate for this.
     * @return A non-null stream of dirty hashes. May be empty. Will not contain duplicate records.
     * @throws MutabilityException if called on a non-sealed cache instance.
     */
    public Stream<VirtualHashRecord> dirtyHashesForFlush(final long lastLeafPath) {
        if (!dirtyHashes.isImmutable()) {
            throw new MutabilityException("Cannot get the dirty internal records for a non-sealed cache.");
        }
        if (!preparedForFlush.get()) {
            throw new IllegalStateException("This cache has not been prepared for flush");
        }
        return dirtyHashes.stream()
                .filter(mutation -> mutation.key <= lastLeafPath)
                .filter(Mutation::notFiltered)
                .map(mutation ->
                        new VirtualHashRecord(mutation.key, mutation.value != NULL_HASH ? mutation.value : null));
    }

    /**
     * This cache copy is selected to flush to disk, either because it is explicitly
     * marked as such, or when cache copy estimated size exceeds flush threshold. Before
     * flush, this method is used to mark redundant mutations as "filtered", so they aren't
     * included to the streams for the data source. It may happen that after filtering the
     * cache copy is no longer needed to flush. In this case, all filtered mutations are
     * removed using {@link #garbageCollect()} method.
     *
     * <p>This method can only be called on sealed caches.
     *
     * @throws MutabilityException if called on a non-sealed cache instance.
     */
    public void prepareForFlush() {
        if (!hashesAreImmutable.get() || !leafIndexesAreImmutable.get()) {
            throw new MutabilityException("Cannot prepare for flushing for a non-sealed cache");
        }
        if (preparedForFlush.getAndSet(true)) {
            throw new IllegalStateException("This cache has been already prepared for flush");
        }
        // Mark obsolete mutations to filter later and update "filtered" counters. These
        // counters will affect the estimated size
        final long version = getFastCopyVersion();
        final long lastReleasedVersion = lastReleased.get();
        //noinspection Convert2MethodRef
        filterMutations(
                dirtyHashes,
                pathToDirtyHashIndex,
                version,
                lastReleasedVersion,
                estimatedHashesSizeInBytes,
                (Hash hash) -> hash.getSerializedLength(),
                virtualMapConfig);
        filterMutations(
                dirtyLeafPaths,
                pathToDirtyLeafIndex,
                version,
                lastReleasedVersion,
                estimatedLeafPathsSizeInBytes,
                (Bytes key) -> (int) key.length(),
                virtualMapConfig);
        //noinspection Convert2MethodRef
        filterMutations(
                dirtyLeaves,
                keyToDirtyLeafIndex,
                version,
                lastReleasedVersion,
                estimatedLeavesSizeInBytes,
                (VirtualLeafBytes rec) -> rec.getSizeInBytes(),
                virtualMapConfig);
    }

    /**
     * If a cache copy's estimated size exceeded flush threshold, but after mutations are
     * filtered the size drops below the threshold, the copy is not flushed to disk, but
     * just removes all filtered mutations using this method.
     *
     * <p>This method can only be called on sealed caches.
     *
     * @throws MutabilityException if called on a non-sealed cache instance.
     */
    public void garbageCollect() {
        if (!hashesAreImmutable.get() || !leafIndexesAreImmutable.get()) {
            throw new MutabilityException("Cannot run garbage collection for a non-sealed cache");
        }
        final Stream<Mutation<Long, Hash>> filteredHashes = dirtyHashes.stream().filter(Mutation::notFiltered);
        dirtyHashes = new ConcurrentArray<>(filteredHashes);
        final Stream<Mutation<Long, Bytes>> filteredLeafPaths =
                dirtyLeafPaths.stream().filter(Mutation::notFiltered);
        dirtyLeafPaths = new ConcurrentArray<>(filteredLeafPaths);
        final Stream<Mutation<Bytes, VirtualLeafBytes>> filteredLeaves =
                dirtyLeaves.stream().filter(Mutation::notFiltered);
        dirtyLeaves = new ConcurrentArray<>(filteredLeaves);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     *
     * This method should be called on a snapshot version of {@link VirtualNodeCache},
     * i.e., the instance return by {@link #snapshot()}
     *
     * @throws IllegalStateException
     * 		If it is called on a non-snapshot instance
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        if (!snapshot.get()) {
            throw new IllegalStateException("Trying to serialize a non-snapshot instance");
        }

        out.writeLong(fastCopyVersion.get());
        serializeKeyToDirtyLeafIndex(keyToDirtyLeafIndex, out);
        serializePathToDirtyLeafIndex(pathToDirtyLeafIndex, out);
        serializePathToDirtyHashIndex(pathToDirtyHashIndex, out);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        this.fastCopyVersion.set(in.readLong());
        deserializeKeyToDirtyLeafIndex(keyToDirtyLeafIndex, in, version);
        deserializePathToDirtyLeafIndex(pathToDirtyLeafIndex, in);
        deserializePathToDirtyHashIndex(pathToDirtyHashIndex, in, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.NO_LEAF_HASHES;
    }

    /**
     * Get fast copy version of the cache.
     *
     * @return Fast copy version
     */
    public long getFastCopyVersion() {
        return fastCopyVersion.get();
    }

    /**
     * Creates a new instance of a {@link VirtualNodeCache}
     * with {@code pathToDirtyHashIndex}, {@code pathToDirtyLeafIndex}, and
     * {@code keyToDirtyLeafIndex}, containing only elements not marked for deletion,
     * and only the latest mutation with version less than or equal to the current
     * version is added to the maps.
     *
     * @return snapshot of the current {@link VirtualNodeCache}
     */
    public VirtualNodeCache snapshot() {
        synchronized (lastReleased) {
            final VirtualNodeCache newSnapshot = new VirtualNodeCache(virtualMapConfig);
            setMapSnapshotAndArray(
                    this.pathToDirtyHashIndex, newSnapshot.pathToDirtyHashIndex, newSnapshot.dirtyHashes);
            setMapSnapshotAndArray(
                    this.pathToDirtyLeafIndex, newSnapshot.pathToDirtyLeafIndex, newSnapshot.dirtyLeafPaths);
            setMapSnapshotAndArray(this.keyToDirtyLeafIndex, newSnapshot.keyToDirtyLeafIndex, newSnapshot.dirtyLeaves);
            newSnapshot.snapshot.set(true);
            newSnapshot.fastCopyVersion.set(this.fastCopyVersion.get());
            newSnapshot.seal();
            return newSnapshot;
        }
    }

    // --------------------------------------------------------------------------------------------
    // Private helper methods.
    //
    // There is a lot of commonality between leaf and internal node caching logic. We try to
    // capture that (and more) here, to keep the rest of the code simple and sane.
    // --------------------------------------------------------------------------------------------

    /**
     * Wires together the {@code prev} and {@code next} caches.
     * <p>
     * Given a chain of caches:
     * <pre>
     *     +----------------+         +----------------+         +----------------+
     *     |    Cache v3    | #-----# |    Cache v2    | #-----# |    Cache v1    |
     *     +----------------+         +----------------+         +----------------+
     * </pre>
     * We should be able to remove any of the above caches. If "Cache v3" is removed, we
     * should see:
     * <pre>
     *     +----------------+         +----------------+
     *     |    Cache v2    | #-----# |    Cache v1    |
     *     +----------------+         +----------------+
     * </pre>
     * If "Cache v2" is removed instead, we should see:
     * <pre>
     *     +----------------+         +----------------+
     *     |    Cache v3    | #-----# |    Cache v1    |
     *     +----------------+         +----------------+
     * </pre>
     * And if "Cache v1" is removed, we should see:
     * <pre>
     *     +----------------+         +----------------+
     *     |    Cache v3    | #-----# |    Cache v2    |
     *     +----------------+         +----------------+
     * </pre>
     * <p>
     * This method IS NOT threadsafe! Control access via locks. It would be bad if a merge
     * and a release were to happen concurrently, or two merges happened concurrently,
     * among neighbors in the chain.
     */
    private void wirePrevAndNext() {
        final VirtualNodeCache n = this.next.get();
        final VirtualNodeCache p = this.prev.get();

        // If "p" is null, this is OK, we just set the "p" of next to null too.
        if (n != null) {
            n.prev.set(p);
        }

        // If "n" is null, that is OK, we just set the "n" of prev to null too.
        if (p != null) {
            p.next.set(n);
        }

        // Clear both of my references. I'm no longer part of the chain.
        this.next.set(null);
        this.prev.set(null);
    }

    /**
     * Updates the mutation for {@code value} at the given {@code path}.
     * <p>
     * This method may be called concurrently from multiple threads, but <strong>MUST NOT</strong>
     * be called concurrently for the same record! It is NOT fully threadsafe!
     *
     * @param value
     * 		The value to store in the mutation. If null, then we interpret this to mean the path was deleted.
     * 		The value is a leaf key, or an internal record.
     * @param path
     * 		The path to update
     * @param index
     * 		The index controlling this path. Could be {@link #pathToDirtyLeafIndex} or
     *        {@link #pathToDirtyHashIndex}.
     * @param dirtyPaths
     * 		The {@link ConcurrentArray} holding references to the dirty paths (leaf or internal).
     * 		Cannot be null.
     * @param <V>
     * 		The type of value stored in the mutation. Either a leaf key or a hash.
     * @throws NullPointerException
     * 		if {@code dirtyPaths} is null.
     */
    private <V> void updatePaths(
            final V value,
            final AtomicInteger estimatedSize,
            final Function<V, Integer> getValueSize,
            final long path,
            final Map<Long, Mutation<Long, V>> index,
            final ConcurrentArray<Mutation<Long, V>> dirtyPaths) {
        index.compute(path, (key, mutation) -> {
            // If there is no mutation or the mutation isn't for this version, then we need to create a new mutation.
            // Note that this code DEPENDS on hashing only a single round at a time. VirtualPipeline
            // enforces this constraint.
            Mutation<Long, V> nextMutation = mutation;
            Mutation<Long, V> previousMutation = null;
            while (nextMutation != null && nextMutation.version > fastCopyVersion.get()) {
                previousMutation = nextMutation;
                nextMutation = nextMutation.next;
            }
            if (nextMutation == null || nextMutation.version != fastCopyVersion.get()) {
                // It must be that there is *NO* mutation in the dirtyPaths for this cache version.
                // I don't have an easy way to assert it programmatically, but by inspection, it must be true.
                // Create a mutation for this version pointing to the next oldest mutation (if any).
                nextMutation = new Mutation<>(nextMutation, path, value, fastCopyVersion.get());
                nextMutation.setDeleted(value == null);
                // Hold a reference to this newest mutation in this cache
                dirtyPaths.add(nextMutation);
                if ((value != null) && (getValueSize != null)) {
                    estimatedSize.addAndGet(getValueSize.apply(value));
                }
            } else {
                assert nextMutation.notFiltered();
                // This mutation already exists in this version. Simply update its value and deleted status.
                if ((nextMutation.value != null) && (getValueSize != null)) {
                    estimatedSize.addAndGet(-getValueSize.apply(nextMutation.value));
                }
                nextMutation.value = value;
                nextMutation.setDeleted(value == null);
                if ((value != null) && (getValueSize != null)) {
                    estimatedSize.addAndGet(getValueSize.apply(value));
                }
            }
            if (previousMutation != null) {
                assert previousMutation.notFiltered();
                previousMutation.next = nextMutation;
            } else {
                mutation = nextMutation;
            }
            return mutation;
        });
    }

    private <K1, V1> Mutation<K1, V1> lookup(Mutation<K1, V1> mutation) {
        return lookup(mutation, fastCopyVersion.get());
    }

    /**
     * Given a mutation list, look up the most recent mutation to this version, but no newer than this
     * cache's version. This method is very fast. Newer mutations are closer to the head of the mutation list,
     * making lookup very fast for the most recent version (O(n)).
     *
     * @param mutation
     * 		The mutation list, can be null.
     * @param <K> The key type held by the mutation. Either a Key or a path.
     * @param <V>>
     * 		The value type held by the mutation. It will be either a Key, leaf record, or a hash.
     * @return null if the mutation could be found, or the mutation.
     */
    private static <K, V> Mutation<K, V> lookup(Mutation<K, V> mutation, final long upToVersion) {
        // Walk the list of values until we find the best match for our version
        for (; ; ) {
            // If mutation is null, then there is nothing else to look for. We're done.
            if (mutation == null) {
                return null;
            }
            // We have found the best match
            if (mutation.version <= upToVersion) {
                return mutation;
            }
            // Look up the next mutation
            mutation = mutation.next;
        }
    }

    /**
     * Record a mutation for the given leaf. If the specified mutation is null, or is of a different version
     * than this cache, then a new mutation will be created and added to the mutation list.
     *
     * @param leaf
     * 		The leaf record. This cannot be null.
     * @param mutation
     * 		The list of mutations for this leaf. This can be null.
     * @return The mutation for this leaf.
     */
    private Mutation<Bytes, VirtualLeafBytes> mutate(
            @NonNull final VirtualLeafBytes leaf,
            final boolean isNew,
            @Nullable Mutation<Bytes, VirtualLeafBytes> mutation) {

        // We only create a new mutation if one of the following is true:
        //  - There is no mutation in the cache (mutation == null)
        //  - There is a mutation but not for this version of the cache
        if (mutation == null || mutation.version != fastCopyVersion.get()) {
            // Only the latest copy can change leaf data, and it cannot ever be merged into while changing,
            // So it should be true that this cache does not have this leaf in dirtyLeaves.

            // Create a new mutation
            final Mutation<Bytes, VirtualLeafBytes> newerMutation =
                    new Mutation<>(mutation, leaf.keyBytes(), leaf, fastCopyVersion.get());
            if (isNew) {
                newerMutation.setNew();
            }
            dirtyLeaves.add(newerMutation);
            // Don't add key size to estimatedSizeInBytes, since the key is a part of the leaf
            mutation = newerMutation;
        } else if (mutation.value != leaf) {
            // A different value (leaf) has arrived, but the mutation already exists for this version.
            // So we can just update the value
            assert mutation.value.keyBytes().equals(leaf.keyBytes());
            mutation.value = leaf;
            mutation.setDeleted(false);
        } else {
            mutation.setDeleted(false);
        }

        return mutation;
    }

    /**
     * Called by one of the purge threads to purge entries from the index that no longer have a referent
     * for the mutation list. This can be called concurrently.
     *
     * BE AWARE: this method is called from the other NON-static method with providing the configuration.
     *
     * @param index
     * 		The index to look through for entries to purge
     * @param <K>
     * 		The key type used in the index
     * @param <V>
     * 		The value type referenced by the mutation list
     */
    private static <K, V> void purge(
            final ConcurrentArray<Mutation<K, V>> array,
            final Map<K, Mutation<K, V>> index,
            @NonNull final VirtualMapConfig virtualMapConfig) {
        array.parallelTraverse(getCleaningPool(virtualMapConfig), element -> {
            if (!element.notFiltered()) {
                return;
            }
            index.compute(element.key, (key, mutation) -> {
                if (mutation == null || element.equals(mutation)) {
                    // Already removed for a more recent mutation
                    return null;
                }
                for (Mutation<K, V> m = mutation; m.next != null; m = m.next) {
                    if (element.equals(m.next)) {
                        m.next = null;
                        break;
                    }
                }
                return mutation;
            });
        });
    }

    /**
     * Node cache contains lists of hash and leaf mutations for every cache version. When caches
     * are merged, the lists are merged, too. To make merges very fast, duplicates aren't removed
     * from the lists on merge. On flush / hash, no duplicates are allowed, so duplicated entries
     * need to be removed.
     *
     * <p>This method iterates over the given list of mutations and marks all obsolete mutations
     * as filtered. Later all marked mutations can be easily removed. A mutation is considered
     * obsolete, if there is a newer mutation for the same key.
     *
     * BE AWARE: this method is called from the other NON-static method with providing the configuration.
     *
     * @param array the list of mutations to process
     * @param index the corresponding index, it's used to look up the newest mutations
     *              for a key
     * @param newestVersion the newest version of all mutations in the array
     * @param lastReleasedVersion the latest flushed version
     * @param <K>
     * 		The key type used in the index
     * @param <V>
     * 		The value type referenced by the mutation list
     */
    private <K, V> void filterMutations(
            final ConcurrentArray<Mutation<K, V>> array,
            final Map<K, Mutation<K, V>> index,
            final long newestVersion,
            final long lastReleasedVersion,
            final AtomicInteger estimatedSize,
            final Function<V, Integer> getValueSize,
            @NonNull final VirtualMapConfig virtualMapConfig) {
        final Consumer<Mutation<K, V>> action = mutation -> {
            // local variable is required because mutation.next can be changed by another thread to null
            // see https://github.com/hashgraph/hedera-services/issues/7046 for the context
            Mutation<K, V> nextMutation = mutation.next;
            mutation.next = null;
            if (nextMutation != null) {
                assert nextMutation.notFiltered();
                // There may be older mutations being purged in parallel, they should not contribute
                // to the "filtered" counter
                if (nextMutation.notFiltered() && (nextMutation.version > lastReleasedVersion)) {
                    nextMutation.setFiltered();
                    if (nextMutation.value != null) {
                        estimatedSize.addAndGet(-getValueSize.apply(nextMutation.value));
                    }
                }
                if (nextMutation.isNew()) {
                    // nextMutation is to put a new element into a virtual map. The element doesn't
                    // exist in the data source. If this mutation is filtered, there must be a newer
                    // mutation for the same key. If that newer mutation has the "deleted" flag, the
                    // element should never be flushed to disk
                    final Mutation<K, V> latestMutation = index.get(mutation.key);
                    assert latestMutation != null;
                    final Mutation<K, V> latestMutationUpToVersion = lookup(latestMutation, newestVersion);
                    assert latestMutationUpToVersion != null;
                    assert latestMutationUpToVersion.notFiltered();
                    if (latestMutationUpToVersion.isDeleted()) {
                        if (latestMutationUpToVersion.notFiltered()) {
                            latestMutationUpToVersion.setFiltered();
                            if (latestMutationUpToVersion.value != null) {
                                estimatedSize.addAndGet(-getValueSize.apply(latestMutationUpToVersion.value));
                            }
                        }
                        // If the latest mutation up to newestVersion is "deleted", and there are no
                        // newer mutations, the whole entry for the key can be removed from the index.
                        // It's safe to do so here, as there are no references to copies older than
                        // newestVersion and there are no mutations in versions newer than newestVersion
                        index.compute(mutation.key, (k, v) -> {
                            assert v != null;
                            if (v == latestMutationUpToVersion) {
                                return null;
                            }
                            Mutation<K, V> m = v;
                            while (m.next != latestMutationUpToVersion) {
                                m = m.next;
                            }
                            assert m.notFiltered();
                            assert m.version > newestVersion;
                            m.next = null;
                            return v;
                        });
                    } else {
                        // Propagate the "new" flag to the newer mutation
                        latestMutationUpToVersion.setNew();
                    }
                }
            }
        };
        try {
            array.parallelTraverse(getCleaningPool(virtualMapConfig), action).getAndRethrow();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /**
     * Copies the mutations from {@code src} into {@code dst}
     * with the following constraints:
     * <ul>
     *     <li>Only one mutation per key is copied</li>
     *     <li>Only the latest mutation with version less than or equal to the
     *     {@code fastCopyVersion} is added</li>
     *     <li>Null mutations are not copied</li>
     * </ul>
     *
     * @param src Map that contains the original mutations
     * @param dst Map that acts as the destination of mutations
     * @param <K> Key type
     * @param <L> Value type
     */
    private <K, L> void setMapSnapshotAndArray(
            final Map<K, Mutation<K, L>> src,
            final Map<K, Mutation<K, L>> dst,
            final ConcurrentArray<Mutation<K, L>> array) {
        final long accepted = fastCopyVersion.get();
        final long rejected = lastReleased.get();
        for (final Map.Entry<K, Mutation<K, L>> entry : src.entrySet()) {
            Mutation<K, L> mutation = entry.getValue();

            while (mutation != null && mutation.version > accepted) {
                mutation = mutation.next;
            }

            if (mutation == null || mutation.version <= rejected) {
                continue;
            }

            dst.put(entry.getKey(), mutation);
            array.add(mutation);
            // Estimated size is not updated, which is hopefully fine
        }
    }

    /**
     * Serialize the {@link #pathToDirtyHashIndex}.
     *
     * @param map
     * 		The index map to serialize. Cannot be null.
     * @param out
     * 		The output stream. Cannot be null.
     * @throws IOException
     * 		If something fails.
     */
    private void serializePathToDirtyHashIndex(
            final Map<Long, Mutation<Long, Hash>> map, final SerializableDataOutputStream out) throws IOException {
        assert snapshot.get() : "Only snapshots can be serialized";
        out.writeInt(map.size());
        for (final Map.Entry<Long, Mutation<Long, Hash>> entry : map.entrySet()) {
            out.writeLong(entry.getKey());
            final Mutation<Long, Hash> mutation = entry.getValue();
            assert mutation != null : "Mutations cannot be null in a snapshot";
            assert mutation.version <= this.fastCopyVersion.get()
                    : "Trying to serialize pathToDirtyInternalIndex with a version ahead";
            out.writeLong(mutation.version);
            out.writeByte(mutation.getFlags());
            if (!mutation.isDeleted()) {
                out.writeSerializable(mutation.value, true);
            }
        }
    }

    /**
     * Deserialize the {@link #pathToDirtyHashIndex}.
     *
     * @param map
     * 		The index. Cannot be null.
     * @param in
     * 		The input stream. Cannot be null.
     * @throws IOException
     * 		In case of trouble.
     */
    private void deserializePathToDirtyHashIndex(
            final Map<Long, Mutation<Long, Hash>> map, final SerializableDataInputStream in, final int version)
            throws IOException {
        final int sizeOfMap = in.readInt();
        for (int index = 0; index < sizeOfMap; index++) {
            final long key = in.readLong();
            final long mutationVersion = in.readLong();
            final byte flags = in.readByte();
            final boolean isNew = Mutation.getFlag(flags, Mutation.FLAG_BIT_NEW);
            final boolean isDeleted = Mutation.getFlag(flags, Mutation.FLAG_BIT_DELETED);
            Hash hash = null;
            if (!isDeleted) {
                if (version == ClassVersion.ORIGINAL) {
                    // skip path
                    in.readLong();
                }
                hash = in.readSerializable();
            }
            final Mutation<Long, Hash> mutation = new Mutation<>(null, key, hash, mutationVersion);
            if (isNew) {
                mutation.setNew();
            }
            mutation.setDeleted(isDeleted);
            map.put(key, mutation);
            dirtyHashes.add(mutation);
        }
    }

    /**
     * Serialize the {@link #pathToDirtyLeafIndex}.
     *
     * @param map
     * 		The index map to serialize. Cannot be null.
     * @param out
     * 		The output stream. Cannot be null.
     * @throws IOException
     * 		If something fails.
     */
    private void serializePathToDirtyLeafIndex(
            final Map<Long, Mutation<Long, Bytes>> map, final SerializableDataOutputStream out) throws IOException {
        assert snapshot.get() : "Only snapshots can be serialized";
        out.writeInt(map.size());
        for (final Map.Entry<Long, Mutation<Long, Bytes>> entry : map.entrySet()) {
            // Write path
            out.writeLong(entry.getKey());
            final Mutation<Long, Bytes> mutation = entry.getValue();
            assert mutation != null : "Mutations cannot be null in a snapshot";
            assert mutation.version <= this.fastCopyVersion.get()
                    : "Trying to serialize pathToDirtyLeafIndex with a version ahead";

            // Write key
            if (mutation.value == null) {
                // Use -1 as a null value marker. 0 is a valid value, some values
                // (which are actually keys in this case) may have length == 0
                out.writeInt(-1);
            } else {
                out.writeInt(Math.toIntExact(mutation.value.length()));
                mutation.value.writeTo(out);
            }
            out.writeLong(mutation.version);
            out.writeBoolean(mutation.isDeleted());
        }
    }

    /**
     * Deserialize the {@link #pathToDirtyLeafIndex}.
     *
     * @param map
     * 		The index. Cannot be null.
     * @param in
     * 		The input stream. Cannot be null.
     * @throws IOException
     * 		In case of trouble.
     */
    private void deserializePathToDirtyLeafIndex(
            final Map<Long, Mutation<Long, Bytes>> map, final SerializableDataInputStream in) throws IOException {
        final int sizeOfMap = in.readInt();
        for (int index = 0; index < sizeOfMap; index++) {
            // Read path
            final Long path = in.readLong();
            // Read key
            final int keyLen = in.readInt();
            final Bytes key;
            if (keyLen < 0) {
                key = null;
            } else {
                key = keyLen == 0 ? Bytes.EMPTY : Bytes.wrap(in.readNBytes(keyLen));
            }
            final long mutationVersion = in.readLong();
            final boolean deleted = in.readBoolean();

            final Mutation<Long, Bytes> mutation = new Mutation<>(null, path, key, mutationVersion);
            mutation.setDeleted(deleted);
            map.put(path, mutation);
            dirtyLeafPaths.add(mutation);
        }
    }

    /**
     * Serialize the {@link #keyToDirtyLeafIndex}.
     *
     * @param map
     * 		The index map to serialize. Cannot be null.
     * @param out
     * 		The output stream. Cannot be null.
     * @throws IOException
     * 		If something fails.
     */
    private void serializeKeyToDirtyLeafIndex(
            final Map<Bytes, Mutation<Bytes, VirtualLeafBytes>> map, final SerializableDataOutputStream out)
            throws IOException {
        assert snapshot.get() : "Only snapshots can be serialized";
        out.writeInt(map.size());
        for (final Map.Entry<Bytes, Mutation<Bytes, VirtualLeafBytes>> entry : map.entrySet()) {
            final Mutation<Bytes, VirtualLeafBytes> mutation = entry.getValue();
            assert mutation != null : "Mutations cannot be null in a snapshot";
            assert mutation.version <= this.fastCopyVersion.get()
                    : "Trying to serialize keyToDirtyLeafIndex with a version ahead";

            final VirtualLeafBytes leaf = mutation.value;
            out.writeLong(leaf.path());
            out.writeInt(Math.toIntExact(leaf.keyBytes().length()));
            leaf.keyBytes().writeTo(out);
            final Bytes value = leaf.valueBytes();
            if (value == null) {
                out.writeInt(0);
            } else {
                out.writeInt(Math.toIntExact(value.length()));
                value.writeTo(out);
            }
            out.writeLong(mutation.version);
            out.writeBoolean(mutation.isDeleted());
        }
    }

    /**
     * Deserialize the {@link #keyToDirtyLeafIndex}.
     *
     * @param map
     * 		The index. Cannot be null.
     * @param in
     * 		The input stream. Cannot be null.
     * @throws IOException
     * 		In case of trouble.
     */
    private void deserializeKeyToDirtyLeafIndex(
            final Map<Bytes, Mutation<Bytes, VirtualLeafBytes>> map,
            final SerializableDataInputStream in,
            final int version)
            throws IOException {
        final int sizeOfMap = in.readInt();
        for (int index = 0; index < sizeOfMap; index++) {
            final long path = in.readLong();
            final int keyLen = in.readInt();
            final Bytes key = Bytes.wrap(in.readNBytes(keyLen));
            final int valueLen = in.readInt();
            final Bytes value = valueLen == 0 ? null : Bytes.wrap(in.readNBytes(valueLen));
            final VirtualLeafBytes leafRecord = new VirtualLeafBytes(path, key, value);
            final long mutationVersion = in.readLong();
            final boolean deleted = in.readBoolean();
            final Mutation<Bytes, VirtualLeafBytes> mutation = new Mutation<>(null, key, leafRecord, mutationVersion);
            mutation.setDeleted(deleted);
            map.put(key, mutation);
            dirtyLeaves.add(mutation);
        }
    }

    /**
     * Get estimated size of this cache copy. The size includes all leaf records in dirtyLeaves,
     * all keys in dirtyLeafPaths, and all hashes in dirtyHashes.
     *
     * <p>This method may only be called when this cache copy instance is immutable, to make sure
     * no further leaf records are updated.
     */
    public int getEstimatedSize() {
        if (estimatedLeavesSizeInBytes.get() == 0) {
            assert leafIndexesAreImmutable.get();
            // In rare cases this method may be called on a released cache instance
            if (dirtyLeaves != null) {
                //noinspection Convert2MethodRef
                estimatedLeavesSizeInBytes.set(estimateSize(dirtyLeaves, record -> record.getSizeInBytes()));
            }
        }
        if (estimatedLeafPathsSizeInBytes.get() == 0) {
            assert leafIndexesAreImmutable.get();
            // In rare cases this method may be called on a released cache instance
            if (dirtyLeafPaths != null) {
                estimatedLeafPathsSizeInBytes.set(estimateSize(dirtyLeafPaths, key -> (int) key.length()));
            }
        }
        return estimatedLeavesSizeInBytes.get()
                + estimatedLeafPathsSizeInBytes.get()
                + estimatedHashesSizeInBytes.get();
    }

    private <K, V> int estimateSize(final ConcurrentArray<Mutation<K, V>> array, final Function<V, Integer> getSize) {
        int size = 0;
        for (int i = 0; i < array.size(); i++) {
            final Mutation<?, V> mutation = array.get(i);
            // Filtered mutations don't contribute to estimated size
            if (mutation.notFiltered() && (mutation.value != null)) {
                size += getSize.apply(mutation.value);
            }
        }
        return size;
    }

    /**
     * Helper method that throws a MutabilityException if the leaf is immutable.
     */
    private void throwIfLeafImmutable() {
        if (leafIndexesAreImmutable.get()) {
            throw new MutabilityException("This operation is not permitted on immutable leaves");
        }
    }

    /**
     * Helper method that throws MutabilityException if the internal is immutable
     */
    private void throwIfInternalsImmutable() {
        if (hashesAreImmutable.get()) {
            throw new MutabilityException("This operation is not permitted on immutable internals");
        }
    }

    /**
     * A mutation. Mutations are linked together within the mutation list. Each mutation
     * has a pointer to the next oldest mutation in the list.
     * @param <K> The key type of data held by the mutation.
     * @param <V> The type of data held by the mutation.
     */
    private static final class Mutation<K, V> {
        private volatile Mutation<K, V> next;
        private final long version; // The version of the cache that owns this mutation
        private final K key;
        private volatile V value;
        private volatile byte flags = 0;

        // A bit in the flags field, which indicates whether this mutation is for a deleted op
        private static final int FLAG_BIT_DELETED = 0;
        // A bit in the flags field, which indicates whether this mutation should not be included
        // into resulting stream of dirty hashes / leaves
        private static final int FLAG_BIT_FILTERED = 1;
        // A bit in the flags field, which indicates whether this mutation is to insert a new
        // entry to the map. It's only used for dirtyLeaves mutations
        private static final int FLAG_BIT_NEW = 2;

        Mutation(Mutation<K, V> next, K key, V value, long version) {
            this.next = next;
            this.key = key;
            this.value = value;
            this.version = version;
        }

        byte getFlags() {
            return flags;
        }

        static boolean getFlag(final byte flags, final int bit) {
            return ((0xFF & flags) & (1 << bit)) != 0;
        }

        @SuppressWarnings("NonAtomicOperationOnVolatileField")
        void setFlag(int bit, boolean value) {
            if (value) {
                flags |= (1 << bit);
            } else {
                flags &= ~(1 << bit);
            }
        }

        boolean isDeleted() {
            return getFlag(flags, FLAG_BIT_DELETED);
        }

        void setDeleted(final boolean deleted) {
            setFlag(FLAG_BIT_DELETED, deleted);
        }

        boolean notFiltered() {
            return !getFlag(flags, FLAG_BIT_FILTERED);
        }

        void setFiltered() {
            setFlag(FLAG_BIT_FILTERED, true);
        }

        boolean isNew() {
            return getFlag(flags, FLAG_BIT_NEW);
        }

        void setNew() {
            setFlag(FLAG_BIT_NEW, true);
        }
    }

    /**
     * Given some cache, print out the contents of all the data structures and mark specially the set of mutations
     * that apply to this cache.
     *
     * @return A string representation of all the data structures of this cache.
     */
    @SuppressWarnings("rawtypes")
    public String toDebugString() {
        //noinspection StringBufferReplaceableByString
        final StringBuilder builder = new StringBuilder();
        builder.append("VirtualNodeCache ").append(this).append("\n");
        builder.append("===================================\n");
        builder.append(toDebugStringChain()).append("\n");
        //noinspection unchecked
        builder.append(toDebugStringIndex("keyToDirtyLeafIndex", (Map<Object, Mutation>) (Object) keyToDirtyLeafIndex))
                .append("\n");
        //noinspection unchecked
        builder.append(toDebugStringIndex(
                        "pathToDirtyLeafIndex", (Map<Object, Mutation>) (Object) pathToDirtyLeafIndex))
                .append("\n");
        //noinspection unchecked
        builder.append(toDebugStringIndex(
                        "pathToDirtyHashIndex", (Map<Object, Mutation>) (Object) pathToDirtyHashIndex))
                .append("\n");
        //noinspection unchecked
        builder.append(toDebugStringArray("dirtyLeaves", (ConcurrentArray<Mutation>) (Object) dirtyLeaves));
        //noinspection unchecked
        builder.append(toDebugStringArray("dirtyLeafPaths", (ConcurrentArray<Mutation>) (Object) dirtyLeafPaths));
        //noinspection unchecked
        builder.append(toDebugStringArray("dirtyHashes", (ConcurrentArray<Mutation>) (Object) dirtyHashes));
        return builder.toString();
    }

    private String toDebugStringChain() {
        final StringBuilder builder = new StringBuilder();
        builder.append("Copies:\n");
        builder.append("\t");

        VirtualNodeCache firstCache = this;
        VirtualNodeCache prevCache;
        while ((prevCache = firstCache.prev.get()) != null) {
            firstCache = prevCache;
        }

        while (firstCache != null) {
            builder.append("[")
                    .append(firstCache.fastCopyVersion.get())
                    .append(firstCache == this ? "*" : "")
                    .append("]->");
            firstCache = firstCache.next.get();
        }

        return builder.toString();
    }

    private String toDebugStringIndex(
            final String indexName, @SuppressWarnings("rawtypes") final Map<Object, Mutation> index) {
        final StringBuilder builder = new StringBuilder();
        builder.append(indexName).append(":\n");

        index.forEach((key, mutation) -> {
            builder.append("\t").append(key).append(":==> ");
            while (mutation != null) {
                builder.append("[")
                        .append(mutation.key)
                        .append(",")
                        .append(mutation.value)
                        .append(",")
                        .append(mutation.isDeleted() ? "D," : "")
                        .append("V")
                        .append(mutation.version)
                        .append(mutation.version == this.fastCopyVersion.get() ? "*" : "")
                        .append("]->");
                mutation = mutation.next;
            }
            builder.append("\n");
        });

        return builder.toString();
    }

    private String toDebugStringArray(
            final String name, @SuppressWarnings("rawtypes") final ConcurrentArray<Mutation> arr) {
        final StringBuilder builder = new StringBuilder();
        builder.append(name).append(":\n");

        final int size = arr.size();
        for (int i = 0; i < size; i++) {
            final var mutation = arr.get(i);
            builder.append("\t")
                    .append(mutation.key)
                    .append(",")
                    .append(mutation.value)
                    .append(",")
                    .append(mutation.isDeleted() ? "D," : "")
                    .append("V")
                    .append(mutation.version)
                    .append(mutation.version == this.fastCopyVersion.get() ? "*" : "")
                    .append("]\n");
        }

        return builder.toString();
    }
}
