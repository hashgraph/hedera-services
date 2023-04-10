/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.internal.merkle;

import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT;
import static com.swirlds.logging.LogMarker.VIRTUAL_MERKLE_STATS;
import static com.swirlds.virtualmap.internal.Path.FIRST_LEFT_PATH;
import static com.swirlds.virtualmap.internal.Path.INVALID_PATH;
import static com.swirlds.virtualmap.internal.Path.ROOT_PATH;
import static com.swirlds.virtualmap.internal.Path.getIndexInRank;
import static com.swirlds.virtualmap.internal.Path.getLeftChildPath;
import static com.swirlds.virtualmap.internal.Path.getParentPath;
import static com.swirlds.virtualmap.internal.Path.getPathForRankAndIndex;
import static com.swirlds.virtualmap.internal.Path.getRank;
import static com.swirlds.virtualmap.internal.Path.getRightChildPath;
import static com.swirlds.virtualmap.internal.Path.getSiblingPath;
import static com.swirlds.virtualmap.internal.Path.isFarRight;
import static com.swirlds.virtualmap.internal.Path.isLeft;
import static com.swirlds.virtualmap.internal.merkle.VirtualMapState.MAX_LABEL_LENGTH;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.ExternalSelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.IllegalChildIndexException;
import com.swirlds.common.merkle.impl.PartialBinaryMerkleInternal;
import com.swirlds.common.merkle.impl.internal.AbstractMerkleInternal;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.CustomReconnectRoot;
import com.swirlds.common.merkle.synchronization.views.LearnerTreeView;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;
import com.swirlds.common.merkle.utility.DebugIterationEndpoint;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapSettings;
import com.swirlds.virtualmap.VirtualMapSettingsFactory;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.datasource.VirtualRecord;
import com.swirlds.virtualmap.internal.RecordAccessor;
import com.swirlds.virtualmap.internal.VirtualStateAccessor;
import com.swirlds.virtualmap.internal.cache.VirtualNodeCache;
import com.swirlds.virtualmap.internal.hash.VirtualHashListener;
import com.swirlds.virtualmap.internal.hash.VirtualHasher;
import com.swirlds.virtualmap.internal.pipeline.VirtualPipeline;
import com.swirlds.virtualmap.internal.pipeline.VirtualRoot;
import com.swirlds.virtualmap.internal.reconnect.ConcurrentBlockingIterator;
import com.swirlds.virtualmap.internal.reconnect.ReconnectHashListener;
import com.swirlds.virtualmap.internal.reconnect.ReconnectState;
import com.swirlds.virtualmap.internal.reconnect.VirtualLearnerTreeView;
import com.swirlds.virtualmap.internal.reconnect.VirtualTeacherTreeView;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An {@link AbstractMerkleInternal} that represents the root node of a virtual tree. This root node always
 * exists, even if no leaves exist.
 *
 * <hr>
 * <p><strong>Hashing</strong></p>
 * <p>
 * This class implements a multithreaded 'leaf-first' hashing algorithm that visits only those nodes required
 * for hashing and visits them at most exactly once. The set of leaves modified during the round (so-called "dirty"
 * leaves) and sorts them such that the first leaf in the list is the leaf with the largest path, and the last leaf
 * has the lowest path. (A path in our system is an incrementing number where root is 0, root's left child is 1,
 * root's right child is 2, root's left-most grand child is 3, and so forth, rank by rank from left to right). This
 * algorithm is <strong>not</strong> depth-first or breadth-first and requires a path-to-node lookup map to work
 * if the nodes do not already have a reference to the parent. Since our leaves are "realized" on demand, most of
 * them will not have references to their parent. However, as the graph builds up over time, many of the parent
 * nodes in the lower ranks (those closer to root) will have strong references to their parents, obviating the
 * need to look up those nodes in the map.
 * <p>
 * This class is responsible for hashing itself. This class has its own thread pool and
 * worker threads which it uses for implementing the algorithm. If the entire in-memory Merkle tree is modified
 * to use the same algorithm, then this code can be moved or eliminated.
 *
 * @param <K>
 * 		The key
 * @param <V>
 * 		The value
 */
@DebugIterationEndpoint
public final class VirtualRootNode<K extends VirtualKey<? super K>, V extends VirtualValue>
        extends PartialBinaryMerkleInternal
        implements CustomReconnectRoot<Long, Long>, ExternalSelfSerializable, VirtualRoot, MerkleInternal {

    private static final String NO_NULL_KEYS_ALLOWED_MESSAGE = "Null keys are not allowed";

    /**
     * Used for serialization.
     */
    public static final long CLASS_ID = 0x4a7d82719a5e1af5L;

    /**
     * This version number should be used to handle compatibility issues that may arise from any future changes
     */
    public static class ClassVersion {
        public static final int VERSION_1_ORIGINAL = 1;

        public static final int CURRENT_VERSION = VERSION_1_ORIGINAL;
    }

    /**
     * Use this for all logging, as controlled by the optional data/log4j2.xml file
     */
    private static final Logger logger = LogManager.getLogger(VirtualRootNode.class);

    /**
     * The number of elements to have in the buffer used during reconnect on a learner when passing
     * leaves to the hashing system. The size of this variable will depend on the incoming rate
     * of leaves vs. the speed of hashing.
     */
    private static final int MAX_RECONNECT_HASHING_BUFFER_SIZE = 10_000_000;

    /**
     * The number of seconds to wait for the hashing buffer during learner-reconnect before we
     * cancel the reconnect with an exception. If we cannot make space after this many seconds,
     * then it means a single round of hashing has exceeded this time threshold.
     */
    private static final int MAX_RECONNECT_HASHING_BUFFER_TIMEOUT = 60;

    /**
     * Placeholder (since this is such a hotspot) to hold the results from {@link VirtualMapSettingsFactory#get()}
     * rather than calling that method more than once during the lifecycle of a {@link VirtualRootNode} instance.
     */
    private final VirtualMapSettings settings = VirtualMapSettingsFactory.get();

    /**
     * The maximum size() we have reached, where we have (already) recorded a warning message about how little
     * space is left before this {@link VirtualRootNode} hits the size limit.  We retain this information
     * because if we later delete some nodes and then add some more, we don't want to trigger a duplicate warning.
     */
    private long maxSizeReachedTriggeringWarning = 0;

    /**
     * A {@link VirtualDataSourceBuilder} used for creating instances of {@link VirtualDataSource}.
     * The data source used by this instance is created from this builder. The builder is needed
     * during reconnect to create a new data source based on a snapshot directory, or in
     * various other scenarios.
     */
    private VirtualDataSourceBuilder<K, V> dataSourceBuilder;

    /**
     * Provides access to the {@link VirtualDataSource} for tree data.
     * All instances of {@link VirtualRootNode} in the "family" (i.e. that are copies
     * going back to some first progenitor) share the exact same dataSource instance.
     */
    private VirtualDataSource<K, V> dataSource;

    /**
     * A cache for {@link VirtualRecord}s. This cache is very specific for this use case. The elements
     * in the cache are those nodes that were modified by this root node, or any copy of this node, that have
     * not yet been written to disk. This cache is used for two purposes. First, we avoid writing to
     * disk until the round is completed and hashed as both a performance enhancement and, more critically,
     * to avoid having to make the filesystem fast-copyable. Second, since modifications are not written
     * to disk, we must cache them here to return correct and consistent results to callers of the map-like APIs.
     * <p>
     * Deleted leaves are represented with records that have the "deleted" flag set.
     * <p>
     * Since this is fast-copyable and shared across all copies of a {@link VirtualRootNode}, it contains the changed
     * leaves over history. Since we always flush from oldest to newest, we know for certain that
     * anything here is at least as new as, or newer than, what is on disk. So we check it first whenever
     * we need a leaf. This allows us to keep the disk simple and not fast-copyable.
     */
    private VirtualNodeCache<K, V> cache;

    /**
     * An interface through which the {@link VirtualRootNode} can access persistent virtual map state, such
     * as the first leaf path, last leaf path, name, and other state. We do not access the {@link VirtualMapState}
     * directly because the {@link VirtualMapState} instance may change over time, so we use this interface
     * instead and allow the {@link VirtualMap} to provide indirection onto the current state data.
     */
    private VirtualStateAccessor state;

    /**
     * An interface through which the {@link VirtualRootNode} can access record data from the cache and the
     * data source. By encapsulating this logic in a RecordAccessor, we make it convenient to access records
     * using a combination of different caches, states, and data sources, which becomes important for reconnect
     * and other uses. This should never be null except for a brief window during initialization / reconnect /
     * serialization.
     */
    private RecordAccessor<K, V> records;

    /**
     * The hasher is responsible for hashing data in a virtual merkle tree.
     */
    private final VirtualHasher<K, V> hasher;

    /**
     * The {@link VirtualPipeline}, shared across all copies of a given {@link VirtualRootNode}, maintains the
     * lifecycle of the nodes, making sure they are merged or flushed or hashed in order and according to the
     * defined lifecycle rules. This class makes calls to the pipeline, and the pipeline calls back methods
     * defined in this class.
     */
    private VirtualPipeline pipeline;

    /**
     * If true, then this copy of {@link VirtualRootNode} should eventually be flushed to disk. A heuristic is
     * used to determine which copy is flushed.
     */
    private boolean shouldBeFlushed;

    /**
     * This latch is used to implement {@link #waitUntilFlushed()}.
     */
    private final CountDownLatch flushLatch = new CountDownLatch(1);

    /**
     * True if this copy has been hashed, false if it has not yet been hashed.
     */
    private final AtomicBoolean hashed = new AtomicBoolean(false);

    /**
     * Specifies whether this current copy has been flushed. This will only be true if {@link #shouldBeFlushed}
     * is true, and it has been flushed.
     */
    private final AtomicBoolean flushed = new AtomicBoolean(false);

    /**
     * Specifies whether this current copy hsa been merged. This will only be true if {@link #shouldBeFlushed}
     * is false, and it has been merged.
     */
    private final AtomicBoolean merged = new AtomicBoolean(false);

    private final AtomicBoolean detached = new AtomicBoolean(false);

    /**
     * Created at the beginning of reconnect as a <strong>learner</strong>, this iterator allows
     * for other threads to feed its leaf records to be used during hashing.
     */
    private ConcurrentBlockingIterator<VirtualLeafRecord<K, V>> reconnectIterator = null;

    /**
     * A {@link java.util.concurrent.Future} that will contain the final hash result of the
     * reconnect hashing process.
     */
    private CompletableFuture<Hash> reconnectHashingFuture;

    /**
     * Set to true once the reconnect hashing thread has been started.
     */
    private AtomicBoolean reconnectHashingStarted;

    /**
     * The {@link RecordAccessor} for the state, cache, and data source needed during reconnect.
     */
    private RecordAccessor<K, V> reconnectRecords;

    private VirtualStateAccessor fullyReconnectedState;

    private VirtualLearnerTreeView<K, V> learnerTreeView;

    private final long fastCopyVersion;

    private VirtualMapStatistics statistics;

    /**
     * Required by the {@link com.swirlds.common.constructable.RuntimeConstructable} contract.
     * This can <strong>only</strong> be called as part of serialization, not for normal use.
     */
    public VirtualRootNode() {
        this(null, false); // FUTURE WORK https://github.com/swirlds/swirlds-platform/issues/4188
    }

    /**
     * Create a new {@link VirtualMap} using the provided data source.
     *
     * @param dataSourceBuilder
     * 		The data source builder. Must not be null.
     */
    public VirtualRootNode(final VirtualDataSourceBuilder<K, V> dataSourceBuilder) {
        this(dataSourceBuilder, true);
    }

    /**
     * Create an instance.
     *
     * @param dataSourceBuilder
     * 		The datasource builder.
     * @param enforce
     * 		Whether to enforce the data source and state being non-null.
     */
    private VirtualRootNode(final VirtualDataSourceBuilder<K, V> dataSourceBuilder, final boolean enforce) {
        this.fastCopyVersion = 0;
        this.cache = new VirtualNodeCache<>();
        this.hasher = new VirtualHasher<>();
        this.shouldBeFlushed = false;
        this.dataSourceBuilder = enforce ? Objects.requireNonNull(dataSourceBuilder) : dataSourceBuilder;
    }

    /**
     * Create a copy of the given source.
     *
     * @param source
     * 		must not be null.
     */
    private VirtualRootNode(VirtualRootNode<K, V> source) {
        super(source);
        this.fastCopyVersion = source.fastCopyVersion + 1;
        this.dataSourceBuilder = source.dataSourceBuilder;
        this.dataSource = source.dataSource;
        this.cache = source.cache.copy();
        this.hasher = source.hasher;
        this.reconnectHashingFuture = null;
        this.reconnectHashingStarted = null;
        this.reconnectIterator = null;
        this.reconnectRecords = null;
        this.fullyReconnectedState = null;
        this.learnerTreeView = null;
        this.maxSizeReachedTriggeringWarning = source.maxSizeReachedTriggeringWarning;
        this.pipeline = source.pipeline;

        if (this.pipeline.isTerminated()) {
            throw new IllegalStateException("A fast-copy was made of a VirtualRootNode with a terminated pipeline!");
        }

        // These three will be set in postInit. This is very unfortunate, but stems from the
        // way serialization / deserialization are implemented which requires partially constructed objects.
        this.state = null;
        this.shouldBeFlushed = false;
        this.records = null;

        this.statistics = source.statistics;
    }

    /**
     * Sets the {@link VirtualStateAccessor}. This is called during copy, and also during reconnect.
     *
     * @param state
     * 		The accessor. Cannot be null.
     */
    @SuppressWarnings("ClassEscapesDefinedScope")
    public void postInit(final VirtualStateAccessor state) {
        // We're reconnecting, state doesn't match cache or dataSource, gotta bail.
        if (learnerTreeView != null) {
            fullyReconnectedState = state;
            return;
        }

        this.state = Objects.requireNonNull(state);
        this.shouldBeFlushed = fastCopyVersion != 0 && fastCopyVersion % settings.getFlushInterval() == 0;
        if (this.dataSourceBuilder != null && this.dataSource == null) {
            this.dataSource = this.dataSourceBuilder.build(state.getLabel(), true);
        }
        this.records = new RecordAccessorImpl<>(this.state, this.cache, this.dataSource);

        if (statistics == null) {
            // Only create statistics instance if we don't yet have statistics. During a reconnect operation.
            // it is necessary to use the statistics object from the previous instance of the state.
            this.statistics = new VirtualMapStatistics(state.getLabel());
        }

        // At this point in time the copy knows if it should be flushed or merged, and so it is safe
        // to register with the pipeline.
        if (pipeline == null) {
            pipeline = new VirtualPipeline();
        }
        pipeline.registerCopy(this);
    }

    /**
     * Gets the {@link VirtualStateAccessor} containing state for this copy of {@link VirtualRootNode}.
     *
     * @return The {@link VirtualStateAccessor}. Will not be null unless called during serialization before
     * 		serialization completes.
     */
    @SuppressWarnings("ClassEscapesDefinedScope")
    public VirtualStateAccessor getState() {
        return state;
    }

    /**
     * Gets the {@link VirtualDataSource}.
     *
     * @return The data source. Will not be null.
     */
    public VirtualDataSource<K, V> getDataSource() {
        return dataSource;
    }

    @SuppressWarnings("ClassEscapesDefinedScope")
    public VirtualNodeCache<K, V> getCache() {
        return cache;
    }

    @SuppressWarnings("ClassEscapesDefinedScope")
    public RecordAccessor<K, V> getRecords() {
        return records;
    }

    // Exposed for tests only.
    public VirtualPipeline getPipeline() {
        return pipeline;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRegisteredToPipeline(final VirtualPipeline pipeline) {
        return pipeline == this.pipeline;
    }

    /*
     * Implementation of MerkleInternal and associated APIs
     **/

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.CURRENT_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends MerkleNode> T getChild(final int index) {
        if (isDestroyed()
                || dataSource == null
                || learnerTreeView != null
                || state.getFirstLeafPath() == INVALID_PATH
                || index > 1) {
            return null;
        }

        final long path = index + 1L;
        final T node;
        if (path < state.getFirstLeafPath()) {
            VirtualInternalRecord internalRecord = records.findInternalRecord(path);
            if (internalRecord == null) {
                internalRecord = new VirtualInternalRecord(path);
            }
            //noinspection unchecked
            node = (T) (new VirtualInternalNode<>(this, internalRecord));
        } else if (path <= state.getLastLeafPath()) {
            final VirtualLeafRecord<K, V> leafRecord = records.findLeafRecord(path, false);
            if (leafRecord == null) {
                throw new IllegalStateException("Invalid null record for child index " + index + " (path = "
                        + path + "). First leaf path = " + state.getFirstLeafPath() + ", last leaf path = "
                        + state.getLastLeafPath() + ".");
            }
            //noinspection unchecked
            node = (T) (new VirtualLeafNode<>(leafRecord));
        } else {
            // The index is out of bounds. Maybe we have a root node with one leaf and somebody has asked
            // for the second leaf, in which case it would be null.
            return null;
        }

        final MerkleRoute route = this.getRoute().extendRoute(index);
        node.setRoute(route);
        return node;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualRootNode<K, V> copy() {
        throwIfImmutable();
        throwIfDestroyed();

        // After creating the copy, mark it as immutable and then register it with the pipeline.
        // We're careful about this ordering because the pipeline runs background threads, and
        // it is crucial that the copy is made and this instance is marked immutable *before*
        // those background threads run. Otherwise, they may try to hash this copy before it
        // has a chance to "seal" the cache, and we will get exceptions.
        final VirtualRootNode<K, V> copy = new VirtualRootNode<>(this);
        setImmutable(true);

        if (isHashed()) {
            // Special case: after a "reconnect", the mutable copy will already be hashed
            // at this point in time.
            cache.seal();
        }

        statistics.recordFlushBacklogSize(pipeline.getFlushBacklogSize());
        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void destroyNode() {
        if (pipeline != null) {
            pipeline.destroyCopy();
        }
    }

    /**
     * We always have *potentially* two children.
     *
     * {@inheritDoc}
     */
    @Override
    public int getNumberOfChildren() {
        return 2;
    }

    /**
     * This is never called for a {@link VirtualRootNode}.
     *
     * {@inheritDoc}
     */
    @Override
    protected void setChildInternal(final int index, final MerkleNode child) {
        throw new UnsupportedOperationException("You cannot set the child of a VirtualRootNode directly with this API");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void allocateSpaceForChild(final int index) {
        // No-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void checkChildIndexIsValid(final int index) {
        if (index < 0 || index > 1) {
            throw new IllegalChildIndexException(0, 1, index);
        }
    }

    /*
     * Implementation of Map-like methods
     **/

    /*
     * Gets the number of elements in this map.
     *
     * @return The number of key/value pairs in the map.
     */
    public long size() {
        return state.size();
    }

    /*
     * Gets whether this map is empty.
     *
     * @return True if the map is empty
     */
    public boolean isEmpty() {
        final long lastLeafPath = state.getLastLeafPath();
        return lastLeafPath == INVALID_PATH;
    }

    /**
     * Checks whether a leaf for the given key exists.
     *
     * @param key
     * 		The key. Cannot be null.
     * @return True if there is a leaf corresponding to this key.
     */
    public boolean containsKey(final K key) {
        Objects.requireNonNull(key, NO_NULL_KEYS_ALLOWED_MESSAGE);
        final long path = records.findKey(key);
        return path != INVALID_PATH;
    }

    /**
     * Gets the value associated with the given key such that any changes to the
     * value will be used in calculating hashes and eventually saved to disk. If the
     * value is actually never modified, some work will be wasted computing hashes
     * and saving data that has not actually changed.
     *
     * @param key
     * 		The key. This must not be null.
     * @return The value. The value may be null.
     */
    public V getForModify(final K key) {
        throwIfImmutable();
        Objects.requireNonNull(key, NO_NULL_KEYS_ALLOWED_MESSAGE);
        final VirtualLeafRecord<K, V> rec = records.findLeafRecord(key, true);
        return rec == null ? null : rec.getValue();
    }

    /**
     * Gets the value associated with the given key. The returned value *WILL BE* immutable.
     * To modify the value, use call {@link #getForModify(VirtualKey)}.
     *
     * @param key
     * 		The key. This must not be null.
     * @return The value. The value may be null, or will be read only.
     */
    public V get(final K key) {
        Objects.requireNonNull(key, NO_NULL_KEYS_ALLOWED_MESSAGE);
        final VirtualLeafRecord<K, V> rec = records.findLeafRecord(key, false);
        final V value = rec == null ? null : rec.getValue();
        //noinspection unchecked
        return value == null ? null : (V) value.asReadOnly();
    }

    /**
     * Puts the key/value pair into the map. The key must not be null, but the value
     * may be null. The previous value, if it existed, is returned. If the entry was already in the map,
     * the value is replaced. If the mapping was not in the map, then a new entry is made.
     *
     * @param key
     * 		the key, cannot be null.
     * @param value
     * 		the value, may be null.
     */
    public void put(final K key, final V value) {
        throwIfImmutable();
        Objects.requireNonNull(key, NO_NULL_KEYS_ALLOWED_MESSAGE);

        final long path = records.findKey(key);
        if (path == INVALID_PATH) {
            // The key is not stored. So add a new entry and return.
            add(key, value);
            return;
        }

        final VirtualLeafRecord<K, V> rec = new VirtualLeafRecord<>(path, null, key, value);
        markDirty(rec);
        super.setHash(null);
    }

    /**
     * Replace the given key with the given value. Only has an effect if the key already exists
     * in the map. Returns the value on success. Throws an IllegalStateException if the key doesn't
     * exist in the map.
     *
     * @param key
     * 		The key. Cannot be null.
     * @param value
     * 		The value. May be null.
     * @return the previous value associated with {@code key}, or {@code null} if there was no mapping for {@code key}.
     * 		(A {@code null} return can also indicate that the map previously associated {@code null} with {@code key}.)
     * @throws IllegalStateException
     * 		if an attempt is made to replace a value that didn't already exist
     */
    public V replace(final K key, final V value) {
        throwIfImmutable();
        Objects.requireNonNull(key, NO_NULL_KEYS_ALLOWED_MESSAGE);

        // Attempt to replace the existing leaf
        final boolean success = replaceImpl(key, value);
        if (success) {
            return value;
        }

        // We failed to find an existing leaf (dirty or clean). So throw an ISE.
        throw new IllegalStateException("Can not replace value that is not in the map");
    }

    /**
     * Removes the key/value pair denoted by the given key from the map. Has no effect
     * if the key didn't exist.
     *
     * @param key
     * 		The key to remove. Cannot be null.
     * @return The removed value. May return null if there was no value to remove or if the value was null.
     */
    public V remove(final K key) {
        throwIfImmutable();
        Objects.requireNonNull(key);

        // Verify whether the current leaf exists. If not, we can just return null.
        VirtualLeafRecord<K, V> leafToDelete = records.findLeafRecord(key, true);
        if (leafToDelete == null) {
            return null;
        }

        // Mark the leaf as being deleted.
        cache.deleteLeaf(leafToDelete);

        // We're going to need these
        final long lastLeafPath = state.getLastLeafPath();
        final long firstLeafPath = state.getFirstLeafPath();
        final long leafToDeletePath = leafToDelete.getPath();

        // If the leaf was not the last leaf, then move the last leaf to take this spot
        if (leafToDeletePath != lastLeafPath) {
            final VirtualLeafRecord<K, V> lastLeaf = records.findLeafRecord(lastLeafPath, true);
            assert lastLeaf != null;
            cache.clearLeafPath(lastLeafPath);
            lastLeaf.setPath(leafToDeletePath);
            markDirty(lastLeaf);
            // NOTE: at this point, if leafToDelete was in the cache at some "path" index, it isn't anymore!
            // The lastLeaf has taken its place in the path index.
        }

        // If the parent of the last leaf is root, then we can simply do some bookkeeping.
        // Otherwise, we replace the parent of the last leaf with the sibling of the last leaf,
        // and mark it dirty. This covers all cases.
        final long lastLeafParent = getParentPath(lastLeafPath);
        if (lastLeafParent == ROOT_PATH) {
            if (firstLeafPath == lastLeafPath) {
                // We just removed the very last leaf, so set these paths to be invalid
                state.setFirstLeafPath(INVALID_PATH);
                state.setLastLeafPath(INVALID_PATH);
            } else {
                // We removed the second to last leaf, so the first & last leaf paths are now the same.
                state.setLastLeafPath(FIRST_LEFT_PATH);
            }
        } else {
            final long lastLeafSibling = getSiblingPath(lastLeafPath);
            final VirtualLeafRecord<K, V> sibling = records.findLeafRecord(lastLeafSibling, true);
            assert sibling != null;
            cache.clearLeafPath(lastLeafSibling);
            cache.deleteInternal(lastLeafParent);
            sibling.setPath(lastLeafParent);
            markDirty(sibling);

            // Update the first & last leaf paths
            state.setFirstLeafPath(lastLeafParent); // replaced by the sibling, it is now first
            state.setLastLeafPath(lastLeafSibling - 1); // One left of the last leaf sibling
        }
        if (statistics != null) {
            statistics.setSize(state.size());
        }

        // Get the value and return it (as read only).
        final V value = leafToDelete.getValue();
        //noinspection unchecked
        return value == null ? null : (V) value.asReadOnly();
    }

    /*
     * Shutdown implementation
     **/

    /**
     * {@inheritDoc}
     */
    @Override
    public void onShutdown(final boolean immediately) {

        if (immediately) {
            // If immediate shutdown is required then let the hasher know it is being stopped. If shutdown
            // is not immediate, the hasher will eventually stop once it finishes all of its work.
            hasher.shutdown();
        }

        // We can now try to shut down the data source. If this doesn't shut things down, then there
        // isn't much we can do aside from logging the fact. The node may well die before too long.
        if (dataSource != null) {
            try {
                dataSource.close();
            } catch (final IOException e) {
                logger.error(
                        EXCEPTION.getMarker(), "Could not close the dataSource after all copies were destroyed", e);
            }
        }
    }

    /*
     * Merge implementation
     **/

    /**
     * {@inheritDoc}
     */
    @Override
    public void merge() {
        final long start = System.currentTimeMillis();
        if (!(isDestroyed() || isDetached())) {
            throw new IllegalStateException("merge is legal only after this node is destroyed or detached");
        }
        if (!isImmutable()) {
            throw new IllegalStateException("merge is only allowed on immutable copies");
        }
        if (!isHashed()) {
            throw new IllegalStateException("copy must be hashed before it is merged");
        }
        if (merged.get()) {
            throw new IllegalStateException("this copy has already been merged");
        }
        if (flushed.get()) {
            throw new IllegalStateException("a flushed copy can not be merged");
        }
        cache.merge();
        merged.set(true);

        final long end = System.currentTimeMillis();
        if (statistics != null) {
            statistics.recordMergeLatency(end - (double) start);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isMerged() {
        return merged.get();
    }

    /*
     * Flush implementation
     **/

    /**
     * If called, this copy of the map will eventually be flushed.
     */
    public void enableFlush() {
        this.shouldBeFlushed = true;
    }

    /**
     * {@inheritDoc}
     */
    public boolean shouldBeFlushed() {
        return shouldBeFlushed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFlushed() {
        return flushed.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitUntilFlushed() throws InterruptedException {
        if (!flushLatch.await(1, MINUTES)) {
            // Unless the platform has enacted a freeze, if it takes
            // more than a minute to become flushed then something is
            // terribly wrong.
            // Write debug information for the pipeline to the log.

            pipeline.logDebugInfo();
            flushLatch.await();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flush() {
        if (!isImmutable()) {
            throw new IllegalStateException("mutable copies can not be flushed");
        }
        if (flushed.get()) {
            throw new IllegalStateException("This map has already been flushed");
        }
        if (merged.get()) {
            throw new IllegalStateException("a merged copy can not be flushed");
        }

        final long start = System.currentTimeMillis();
        flush(cache, state, dataSource);
        cache.release();
        final long end = System.currentTimeMillis();
        flushed.set(true);
        flushLatch.countDown();
        if (statistics != null) {
            statistics.recordFlush(end - (double) start);
        }
        logger.debug(VIRTUAL_MERKLE_STATS.getMarker(), "Flushed in {} ms", end - start);
    }

    private void flush(
            VirtualNodeCache<K, V> cacheToFlush, VirtualStateAccessor stateToUse, VirtualDataSource<K, V> ds) {
        try {
            // Get the leaves that were changed and sort them by path so that lower paths come first
            final Stream<VirtualLeafRecord<K, V>> sortedDirtyLeaves =
                    cacheToFlush.dirtyLeaves(stateToUse.getFirstLeafPath(), stateToUse.getLastLeafPath());

            // Get the deleted leaves
            final Stream<VirtualLeafRecord<K, V>> deletedLeaves = cacheToFlush.deletedLeaves();

            // Save the dirty internals
            final Stream<VirtualInternalRecord> sortedDirtyInternals =
                    cacheToFlush.dirtyInternals(stateToUse.getFirstLeafPath());

            ds.saveRecords(
                    stateToUse.getFirstLeafPath(),
                    stateToUse.getLastLeafPath(),
                    sortedDirtyInternals,
                    sortedDirtyLeaves,
                    deletedLeaves);

        } catch (final ClosedByInterruptException ex) {
            logger.info(
                    TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(),
                    "flush interrupted - this is probably not an error " + "if this happens shortly after a reconnect");
            Thread.currentThread().interrupt();
        } catch (final IOException ex) {
            logger.error(EXCEPTION.getMarker(), "Error while flushing VirtualMap", ex);
            throw new UncheckedIOException(ex);
        }
    }

    /*
     * Serialization implementation
     **/

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out, final Path outputDirectory) throws IOException {

        final RecordAccessor<K, V> detachedRecords = pipeline.detachCopy(this, outputDirectory);
        assert detachedRecords.getDataSource() == null : "No data source should be created.";

        out.writeNormalisedString(state.getLabel());
        out.writeSerializable(dataSourceBuilder, true);
        out.writeSerializable(detachedRecords.getCache(), true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final Path inputDirectory, final int version)
            throws IOException {
        final String label = in.readNormalisedString(MAX_LABEL_LENGTH);
        dataSourceBuilder = in.readSerializable();
        dataSource = dataSourceBuilder.restore(label, inputDirectory);
        cache = in.readSerializable();
    }

    /*
     * hashing implementation
     **/

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSelfHashing() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash getHash() {
        if (super.getHash() == null) {
            pipeline.hashCopy(this);
        }
        return super.getHash();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHash(final Hash hash) {
        throw new UnsupportedOperationException("data type is self hashing");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invalidateHash() {
        throw new UnsupportedOperationException("this node is self hashing");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isHashed() {
        return hashed.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void computeHash() {
        if (hashed.get()) {
            return;
        }

        // Make sure the cache is immutable for leaf changes but mutable for internal node changes
        cache.prepareForHashing();

        // Compute the root hash of the virtual tree
        final VirtualHashListener<K, V> hashListener = new VirtualHashListener<>() {
            @Override
            public void onInternalHashed(VirtualInternalRecord internal) {
                cache.putInternal(internal);
            }
        };
        Hash virtualHash = hasher.hash(
                path -> records.findLeafRecord(path, false),
                records::findInternalRecord,
                cache.dirtyLeaves(state.getFirstLeafPath(), state.getLastLeafPath())
                        .iterator(),
                state.getFirstLeafPath(),
                state.getLastLeafPath(),
                hashListener);

        if (virtualHash == null) {
            final VirtualInternalRecord rootRecord = state.size() == 0 ? null : records.findInternalRecord(0);
            if (rootRecord != null) {
                virtualHash = rootRecord.getHash();
            } else {
                virtualHash = hasher.emptyRootHash();
            }
        }

        super.setHash(virtualHash);

        // There are no remaining changes to be made to the cache, so we can seal it.
        cache.seal();

        hashed.set(true);
    }

    /*
     * Detach implementation
     **/

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T detach(final Path destination) {
        if (isDestroyed()) {
            throw new IllegalStateException("detach is illegal on already destroyed copies");
        }
        if (!isImmutable()) {
            throw new IllegalStateException("detach is only allowed on immutable copies");
        }
        if (!isHashed()) {
            throw new IllegalStateException("copy must be hashed before it is detached");
        }

        // The pipeline is paused while this runs, so I can go ahead and call snapshot on the data
        // source, and also snapshot the cache. I will create a new "RecordAccessor" for the detached
        // record state.
        final T snapshot;
        if (destination == null) {
            //noinspection unchecked
            snapshot = (T) new RecordAccessorImpl<>(state, cache.snapshot(), dataSourceBuilder.copy(dataSource, false));
        } else {
            dataSourceBuilder.snapshot(destination, dataSource);
            //noinspection unchecked
            snapshot = (T) new RecordAccessorImpl<>(state, cache.snapshot(), null);
        }

        detached.set(true);
        return snapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDetached() {
        return detached.get();
    }

    /*
     * Reconnect Implementation
     **/

    /**
     * {@inheritDoc}
     */
    @Override
    public TeacherTreeView<Long> buildTeacherView() {
        return new VirtualTeacherTreeView<>(getStaticThreadManager(), this, state, pipeline);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public void setupWithOriginalNode(final MerkleNode originalNode) {
        assert originalNode instanceof VirtualRootNode : "The original node was not a VirtualRootNode!";

        // NOTE: If we're reconnecting, then the old tree is toast. We hold onto the originalMap to
        // restart from that position again in the future if needed, but we're never going to use
        // the old map again. We need the data source builder from the old map so, we can create
        // new data sources in this new map with all the right settings.
        //noinspection unchecked
        final VirtualRootNode<K, V> originalMap = (VirtualRootNode<K, V>) originalNode;
        this.dataSourceBuilder = originalMap.dataSourceBuilder;

        // shutdown background compaction on original data source as it is no longer needed to be running as all data
        // in that data source is only there as a starting point for reconnect now. So compacting it further is not
        // helpful and will just burn resources.
        originalMap.dataSource.stopBackgroundCompaction();

        // Take a snapshot, and use the snapshot database as my data source
        this.dataSource = dataSourceBuilder.copy(originalMap.dataSource, true);

        // The old map's cache is going to become immutable, but that's OK, because the old map
        // will NEVER be updated again.
        assert originalMap.isHashed() : "The system should have made sure this was hashed by this point!";
        final VirtualNodeCache<K, V> snapshotCache = originalMap.cache.snapshot();
        flush(snapshotCache, originalMap.state, this.dataSource);

        // Set up the VirtualHasher which we will use during reconnect.
        // Initial timeout is intentionally very long, timeout is reduced once we receive the first leaf in the tree.
        reconnectIterator =
                new ConcurrentBlockingIterator<>(MAX_RECONNECT_HASHING_BUFFER_SIZE, Integer.MAX_VALUE, MILLISECONDS);
        reconnectHashingFuture = new CompletableFuture<>();
        reconnectHashingStarted = new AtomicBoolean(false);

        final VirtualStateAccessor reconnectState = new ReconnectState(-1, -1);
        reconnectRecords = new RecordAccessorImpl<>(reconnectState, snapshotCache, dataSource);

        // During reconnect we want to look up state from the original records
        learnerTreeView = new VirtualLearnerTreeView<>(
                this, originalMap.records, dataSource.buildKeySet(), originalMap.getState(), reconnectState);

        // Current statistics can only be registered when the node boots, requiring statistics
        // objects to be passed from version to version of the state.
        dataSource.copyStatisticsFrom(((VirtualRootNode<K, V>) originalNode).dataSource);
        statistics = originalMap.statistics;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setupWithNoData() {
        // No-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LearnerTreeView<Long> buildLearnerView() {
        return learnerTreeView;
    }

    /**
     * Pass all statistics to the registry.
     *
     * @param metrics
     * 		reference to the metrics system
     */
    public void registerMetrics(final Metrics metrics) {
        statistics.registerMetrics(metrics);
        dataSource.registerMetrics(metrics);
    }

    /**
     * This method is passed all leaf nodes that are deserialized during a reconnect operation.
     *
     * @param leafRecord
     * 		describes a leaf
     */
    public void handleReconnectLeaf(final VirtualLeafRecord<K, V> leafRecord) {
        try {
            final boolean success = reconnectIterator.supply(leafRecord, MAX_RECONNECT_HASHING_BUFFER_TIMEOUT, SECONDS);
            if (!success) {
                throw new MerkleSynchronizationException(
                        "Timed out waiting to supply a new leaf to the hashing iterator buffer");
            }
        } catch (final MerkleSynchronizationException e) {
            throw e;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MerkleSynchronizationException(
                    "Interrupted while waiting to supply a new leaf to the hashing iterator buffer", e);
        } catch (final Exception e) {
            throw new MerkleSynchronizationException("Failed to handle a leaf during reconnect on the learner", e);
        }
    }

    public void prepareForFirstLeaf() {
        reconnectIterator.setMaxWaitTime(MAX_RECONNECT_HASHING_BUFFER_TIMEOUT, SECONDS);
    }

    public void prepareReconnectHashing(final long firstLeafPath, final long lastLeafPath) {
        // The hash listener will be responsible for flushing stuff to the reconnect data source
        final ReconnectHashListener<K, V> hashListener = new ReconnectHashListener<>(
                firstLeafPath, lastLeafPath, reconnectRecords.getDataSource(), learnerTreeView.getNodeRemover());

        // This background thread will be responsible for hashing the tree and sending the
        // data to the hash listener to flush.
        getStaticThreadManager()
                .newThreadConfiguration()
                .setComponent("virtualmap")
                .setThreadName("hasher")
                .setRunnable(() -> reconnectHashingFuture.complete(hasher.hash(
                        path -> reconnectRecords.findLeafRecord(path, false),
                        reconnectRecords::findInternalRecord,
                        reconnectIterator,
                        firstLeafPath,
                        lastLeafPath,
                        hashListener)))
                .setExceptionHandler((thread, exception) -> {
                    // Shut down the iterator. This will cause reconnect to terminate.
                    reconnectIterator.close();
                    final var message = "VirtualMap@" + getRoute() + " failed to hash during reconnect";
                    logger.error(EXCEPTION.getMarker(), message, exception);
                    reconnectHashingFuture.completeExceptionally(
                            new MerkleSynchronizationException(message, exception));
                })
                .build()
                .start();

        reconnectHashingStarted.set(true);
    }

    public void endLearnerReconnect() {
        try {
            reconnectIterator.close();
            if (reconnectHashingStarted.get()) {
                // Only block on future if the hashing thread is known to have been started.
                super.setHash(reconnectHashingFuture.get());
            } else {
                logger.warn(RECONNECT.getMarker(), "virtual map hashing thread was never started");
            }
            learnerTreeView = null;
            postInit(fullyReconnectedState);
            // Start up data source compaction now
            dataSource.startBackgroundCompaction();
        } catch (ExecutionException e) {
            final var message = "VirtualMap@" + getRoute() + " failed to get hash during learner reconnect";
            throw new MerkleSynchronizationException(message, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            final var message = "VirtualMap@" + getRoute() + " interrupted while ending learner reconnect";
            throw new MerkleSynchronizationException(message, e);
        }
    }

    /**
     * Loads the leaf, sibling and sibling of parents on the path to root into OS and not in java heap
     * The OS cache helps in fast retrieval of values without costing us java heap
     * @param key key to the leaf node
     */
    public void warm(final K key) {

        // Warm the leaf node
        final VirtualLeafRecord<K, V> leafRecord = records.findLeafRecord(key, false);

        if (leafRecord != null) {
            final long leafPath = leafRecord.getPath();
            // Warm the sibling of the leaf
            records.findLeafRecord(getSiblingPath(leafPath), false);
            // Warm internal nodes (sibling on path to parent)
            warmInternalNodesForLeaf(leafPath);
        }
    }

    /**
     * @param leafPath path to the leaf record
     *   When the value in a leaf node is changed all the parent nodes up to the root need to be rehashed.
     * 	 When navigating from leaf->root, for every parent the sibling needs to be read from disk
     *   Hence we know all internal nodes that need to be read from disk to calculate the rootHash
     *
     * 	We can read those internal nodes from disk and the OS page cache will cache them for us
     *  We do not need to do anything with the read data we can drop it
     */
    public void warmInternalNodesForLeaf(final long leafPath) {

        long siblingPath;
        long path = leafPath;

        while (path > 0) {
            path = getParentPath(path);
            siblingPath = getSiblingPath(path);

            if (siblingPath != INVALID_PATH) {
                records.warmInternalRecord(siblingPath);
            }
        }
    }

    ////////////////////////

    /**
     * Adds a new leaf with the given key and value. The precondition to calling this
     * method is that the key DOES NOT have a corresponding leaf already either in the
     * cached leaves or in the data source.
     *
     * @param key
     * 		A non-null key. Previously validated.
     * @param value
     * 		The value to add. May be null.
     */
    private void add(final K key, final V value) {
        // We're going to imagine what happens to the leaf and the tree without
        // actually bringing into existence any nodes. Virtual Virtual!! SUPER LAZY FTW!!

        // We will compute the new leaf path below, and ultimately set it on the leaf.
        long leafPath;

        // Confirm that adding one more entry is not too much for this VirtualMap to hold.
        final long currentSize = size();
        final long maximumAllowedSize = settings.getMaximumVirtualMapSize();
        if (currentSize >= maximumAllowedSize) {
            throw new IllegalStateException("Virtual Map has no more space");
        }

        final long remainingCapacity = maximumAllowedSize - currentSize;
        if ((currentSize > maxSizeReachedTriggeringWarning)
                && (remainingCapacity <= settings.getVirtualMapWarningThreshold())
                && (remainingCapacity % settings.getVirtualMapWarningInterval() == 0)) {
            maxSizeReachedTriggeringWarning = currentSize;
            logger.warn(
                    VIRTUAL_MERKLE_STATS.getMarker(),
                    "Virtual Map only has room for {} additional entries",
                    remainingCapacity);
        }
        if (remainingCapacity == 1) {
            logger.warn(VIRTUAL_MERKLE_STATS.getMarker(), "Virtual Map is now full!");
        }

        // Find the lastLeafPath which will tell me the new path for this new item
        final long lastLeafPath = state.getLastLeafPath();
        if (lastLeafPath == INVALID_PATH) {
            // There are no leaves! So this one will just go left on the root
            leafPath = getLeftChildPath(ROOT_PATH);
            state.setLastLeafPath(leafPath);
            state.setFirstLeafPath(leafPath);
        } else if (isLeft(lastLeafPath)) {
            // The only time that lastLeafPath is a left node is if the parent is root.
            // In all other cases, it will be a right node. So we can just add this
            // to root.
            leafPath = getRightChildPath(ROOT_PATH);
            state.setLastLeafPath(leafPath);
        } else {
            // We have to make some modification to the tree because there is not
            // an open position on root. So we need to pick a node where a leaf currently exists
            // and then swap it out with a parent, move the leaf to the parent as the
            // "left", and then we can put the new leaf on the right. It turns out,
            // the slot is always the firstLeafPath. If the current firstLeafPath
            // is all the way on the far right of the graph, then the next firstLeafPath
            // will be the first leaf on the far left of the next rank. Otherwise,
            // it is just the sibling to the right.
            final long firstLeafPath = state.getFirstLeafPath();
            final long nextFirstLeafPath = isFarRight(firstLeafPath)
                    ? getPathForRankAndIndex((byte) (getRank(firstLeafPath) + 1), 0)
                    : getPathForRankAndIndex(getRank(firstLeafPath), getIndexInRank(firstLeafPath) + 1);

            // The firstLeafPath points to the old leaf that we want to replace.
            // Get the old leaf.
            final VirtualLeafRecord<K, V> oldLeaf = records.findLeafRecord(firstLeafPath, true);
            Objects.requireNonNull(oldLeaf);
            cache.clearLeafPath(firstLeafPath);
            oldLeaf.setPath(getLeftChildPath(firstLeafPath));
            markDirty(oldLeaf);

            // Create a new internal node that is in the position of the old leaf and attach it to the parent
            // on the left side. Put the new item on the right side of the new parent.
            leafPath = getRightChildPath(firstLeafPath);

            // Save the first and last leaf paths
            state.setLastLeafPath(leafPath);
            state.setFirstLeafPath(nextFirstLeafPath);
        }
        if (statistics != null) {
            statistics.setSize(state.size());
        }

        final VirtualLeafRecord<K, V> newLeaf = new VirtualLeafRecord<>(leafPath, null, key, value);
        markDirty(newLeaf);
        super.setHash(null); // Make sure VirtualMap has an invalid hash, so it will be recomputed later
    }

    /**
     * An internal helper method that replaces the value for the given key and returns true,
     * or returns false if the key was not found in the map.
     *
     * @param key
     * 		The key
     * @param value
     * 		The value
     * @return true if the key was found in the map, false otherwise.
     */
    private boolean replaceImpl(K key, V value) {
        final VirtualLeafRecord<K, V> rec = records.findLeafRecord(key, true);
        if (rec != null) {
            rec.setValue(value);
            super.setHash(null);
            return true;
        }

        return false;
    }

    /*
     * Private Implementation Details
     **/

    private void markDirty(VirtualLeafRecord<K, V> leaf) {
        // Keep track of this as a dirty leaf (even though we don't *really* know if the value
        // will change, the contract of the API is that the caller expects to change it, which
        // is good enough for us).
        cache.putLeaf(leaf);
    }
}
