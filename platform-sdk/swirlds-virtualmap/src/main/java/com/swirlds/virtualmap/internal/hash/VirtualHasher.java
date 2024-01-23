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

package com.swirlds.virtualmap.internal.hash;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.virtualmap.internal.Path.INVALID_PATH;
import static com.swirlds.virtualmap.internal.Path.ROOT_PATH;

import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashBuilder;
import com.swirlds.common.wiring.tasks.AbstractTask;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.Path;
import com.swirlds.virtualmap.internal.merkle.VirtualInternalNode;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for hashing virtual merkle trees. This class is designed to work both for normal
 * hashing use cases, and also for hashing during reconnect.
 *
 * <p>There should be one {@link VirtualHasher} shared across all copies of a {@link VirtualMap}
 * "family".
 *
 * @param <K>
 * 		The {@link VirtualKey} type
 * @param <V>
 * 		The {@link VirtualValue} type
 */
public final class VirtualHasher<K extends VirtualKey, V extends VirtualValue> {
    /**
     * Use this for all logging, as controlled by the optional data/log4j2.xml file
     */
    private static final Logger logger = LogManager.getLogger(VirtualHasher.class);

    /**
     * Since {@code com.swirlds.platform.Browser} populates settings, and it is loaded before any
     * application classes that might instantiate a data source, the {@link ConfigurationHolder}
     * holder will have been configured by the time this static initializer runs.
     */
    private static final VirtualMapConfig CONFIG = ConfigurationHolder.getConfigData(VirtualMapConfig.class);

    /**
     * The number of threads to use when hashing. Can either be supplied by a system property, or
     * will compute a default based on "percentHashThreads".
     */
    private static final int HASHING_THREAD_COUNT = CONFIG.getNumHashThreads();

    /**
     * This thread-local gets a HashBuilder that can be used for hashing on a per-thread basis.
     */
    private static final ThreadLocal<HashBuilder> HASH_BUILDER_THREAD_LOCAL =
            ThreadLocal.withInitial(() -> new HashBuilder(Cryptography.DEFAULT_DIGEST_TYPE));

    /**
     * A function to look up clean hashes by path during hashing. This function is stored in
     * a class field to avoid passing it as an arg to every hashing task.
     */
    private LongFunction<Hash> hashReader;

    /**
     * A listener to notify about hashing events. This listener is stored in a class field to
     * acoid passing it as an arg to every hashing task.
     */
    private VirtualHashListener<K, V> listener;

    /**
     * An instance of {@link Cryptography} used to hash leaves. This should be a static final
     * field, but it doesn't work very well as platform configs aren't loaded at the time when
     * this class is initialized. It would result in a cryptography instance with default (and
     * possibly wrong) configs be used by the hasher. Instead, this field is initialized in
     * the {@link #hash(LongFunction, Iterator, long, long)} method and used by all hashing
     * tasks.
     */
    private Cryptography cryptography;

    /**
     * Tracks if this virtual hasher has been shut down. If true (indicating that the hasher
     * has been intentionally shut down), then don't log/throw if the rug is pulled from
     * underneath the hashing threads.
     */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private static final ForkJoinPool HASHING_POOL = new ForkJoinPool(HASHING_THREAD_COUNT);

    /**
     * Indicate to the virtual hasher that it has been shut down. This method does not interrupt threads, but
     * it indicates to threads that an interrupt may happen, and that the interrupt should not be treated as
     * an error.
     */
    public void shutdown() {
        shutdown.set(true);
    }

    /**
     * Hash the given dirty leaves and the minimal subset of the tree necessary to produce a single root hash.
     * The root hash is returned.
     *
     * @param hashReader
     * 		Return a {@link Hash} by path. Used when this method needs to look up clean nodes.
     * @param sortedDirtyLeaves
     * 		A stream of dirty leaves sorted in <strong>ASCENDING PATH ORDER</strong>, such that path
     * 		1234 comes before 1235. If null or empty, a null hash result is returned.
     * @param firstLeafPath
     * 		The firstLeafPath of the tree that is being hashed. If &lt; 1, then a null hash result is returned.
     * 		No leaf in {@code sortedDirtyLeaves} may have a path less than {@code firstLeafPath}.
     * @param lastLeafPath
     * 		The lastLeafPath of the tree that is being hashed. If &lt; 1, then a null hash result is returned.
     * 		No leaf in {@code sortedDirtyLeaves} may have a path greater than {@code lastLeafPath}.
     * @return The hash of the root of the tree
     */
    public Hash hash(
            final LongFunction<Hash> hashReader,
            Iterator<VirtualLeafRecord<K, V>> sortedDirtyLeaves,
            final long firstLeafPath,
            final long lastLeafPath) {
        return hash(hashReader, sortedDirtyLeaves, firstLeafPath, lastLeafPath, null);
    }

    class ChunkHashTask extends AbstractTask {

        private final long path;

        private final int height; // 1 for 3-node chunk, 2 for 7-node chunk, and so on

        private ChunkHashTask out;

        // Input hashes. Some hashes may be null, which indicates they should be loaded from disk
        private final Hash[] ins;

        // If not null, the task hashes the leaf. If null, the task processes the input hashes
        private VirtualLeafRecord<K, V> leaf;

        ChunkHashTask(final ForkJoinPool pool, final long path, final int height) {
            super(pool, 1 + (1 << height));
            this.height = height;
            this.path = path;
            this.ins = new Hash[1 << height];
        }

        void setOut(final ChunkHashTask out) {
            this.out = out;
            assert path == 0 || Path.getRank(path) - out.height == Path.getRank(out.path)
                    : "setOut " + path + " " + height + " " + out.path;
            send();
        }

        void setData(final VirtualLeafRecord<K, V> leaf) {
            assert leaf == null || path == leaf.getPath();
            assert leaf == null || height == 1;
            assert leaf != null || out != null;
            if (leaf == null) {
                out.setHash(getIndexInOut(), null);
            } else {
                this.leaf = leaf;
                send(); // left hash dependency
                send(); // right hash dependency
            }
        }

        void setHash(final int index, final Hash hash) {
            assert index >= 0 && index < (1 << height);
            ins[index] = hash;
            send();
        }

        @Override
        public void completeExceptionally(Throwable ex) {
            if (out != null) {
                out.completeExceptionally(ex);
            }
            super.completeExceptionally(ex);
        }

        @Override
        protected boolean exec() {
            try {
                final Hash hash;
                if (leaf != null) {
                    hash = cryptography.digestSync(leaf);
                    listener.onLeafHashed(leaf);
                    listener.onNodeHashed(path, hash);
                } else {
                    int len = 1 << height;
                    long rankPath = path;
                    for (int i = 0; i < height; i++) {
                        rankPath = Path.getLeftChildPath(rankPath);
                    }
                    while (len > 1) {
                        for (int i = 0; i < len / 2; i++) {
                            final long hashedPath = Path.getParentPath(rankPath + i * 2);
                            Hash left = ins[i * 2];
                            Hash right = ins[i * 2 + 1];
                            if ((left == null) && (right == null)) {
                                ins[i] = null;
                            } else {
                                if (left == null) {
                                    left = hashReader.apply(rankPath + i * 2);
                                }
                                if (right == null) {
                                    right = hashReader.apply(rankPath + i * 2 + 1);
                                }
                                ins[i] = hash(hashedPath, left, right);
                                listener.onNodeHashed(hashedPath, ins[i]);
                            }
                        }
                        rankPath = Path.getParentPath(rankPath);
                        len = len >> 1;
                    }
                    hash = ins[0];
                }
                out.setHash(getIndexInOut(), hash);
                return true;
            } catch (final Throwable e) {
                completeExceptionally(e);
                throw e;
            }
        }

        static Hash hash(final long path, final Hash left, final Hash right) {
            final long classId = path == ROOT_PATH ? VirtualRootNode.CLASS_ID : VirtualInternalNode.CLASS_ID;
            final int serId = path == ROOT_PATH
                    ? VirtualRootNode.ClassVersion.CURRENT_VERSION
                    : VirtualInternalNode.SERIALIZATION_VERSION;
            final HashBuilder builder = HASH_BUILDER_THREAD_LOCAL.get();
            builder.reset();
            builder.update(classId);
            builder.update(serId);
            builder.update(left);
            builder.update(right);
            return builder.build();
        }

        private int getIndexInOut() {
            if (path == 0) {
                return 0;
            }
            final long firstInPathInOut = Path.getLeftGrandChildPath(out.path, out.height);
            return (int) (path - firstInPathInOut);
        }
    }

    public Hash hash(
            final LongFunction<Hash> hashReader,
            final Iterator<VirtualLeafRecord<K, V>> sortedDirtyLeaves,
            final long firstLeafPath,
            final long lastLeafPath,
            VirtualHashListener<K, V> listener) {

        // If the first or last leaf path are invalid, then there is nothing to hash.
        if (firstLeafPath < 1 || lastLeafPath < 1) {
            return null;
        }

        if (!sortedDirtyLeaves.hasNext()) {
            return null;
        }

        // We don't want to include null checks everywhere, so let the listener be NoopListener if null
        if (listener == null) {
            listener =
                    new VirtualHashListener<>() {
                        /* noop */
                    };
        }

        this.hashReader = hashReader;
        this.listener = listener;
        this.cryptography = CryptographyHolder.get();
        final Hash NULL_HASH = cryptography.getNullHash();

        final int chunkHeight = CONFIG.virtualHasherChunkHeight();
        int firstLeafRank = Path.getRank(firstLeafPath);
        int lastLeafRank = Path.getRank(lastLeafPath);

        // Let the listener know we have started hashing.
        listener.onHashingStarted();

        final HashMap<Long, ChunkHashTask> map = new HashMap<>();
        ChunkHashTask resultTask = new ChunkHashTask(HASHING_POOL, INVALID_PATH, 1);
        int rootTaskHeight = Math.min(firstLeafRank, chunkHeight);
        ChunkHashTask rootTask = new ChunkHashTask(HASHING_POOL, ROOT_PATH, rootTaskHeight);
        rootTask.setOut(resultTask);
        map.put(ROOT_PATH, rootTask);

        boolean firstLeaf = true;
        final long[] stack = new long[lastLeafRank + 1];
        Arrays.fill(stack, INVALID_PATH);

        final int[] parentRankHeights = new int[256];
        parentRankHeights[0] = 1;
        for (int i = 1; i <= firstLeafRank; i++) {
            parentRankHeights[i] = Math.min((i - 1) % chunkHeight + 1, i);
        }
        if (firstLeafRank != lastLeafRank) {
            parentRankHeights[lastLeafRank] = 1;
        }

        while (sortedDirtyLeaves.hasNext()) {
            VirtualLeafRecord<K, V> leaf = sortedDirtyLeaves.next();
            long curPath = leaf.getPath();
            ChunkHashTask curTask = map.remove(curPath);
            if (curTask == null) {
                curTask = new ChunkHashTask(HASHING_POOL, curPath, 1);
            }
            curTask.setData(leaf);

            while (true) {
                final int curRank = Path.getRank(curPath);
                final int chunkWidth = 1 << parentRankHeights[curRank];
                if (stack[curRank] != INVALID_PATH) {
                    long curStackPath = stack[curRank];
                    final long firstPathInRank = Path.getPathForRankAndIndex(curRank, 0);
                    final long curStackChunkNoInRank = (curStackPath - firstPathInRank) / chunkWidth;
                    final long lastPathInCurStackChunk = firstPathInRank + (curStackChunkNoInRank + 1) * chunkWidth - 1;
                    while (curStackPath < Math.min(curPath, lastPathInCurStackChunk)) {
                        final ChunkHashTask t = map.remove(curStackPath);
                        assert t != null;
                        t.setData(null);
                        curStackPath++;
                    }
                    stack[curRank] = INVALID_PATH;
                }

                if (curTask.out != null) {
                    break;
                }

                final long parentPath = Path.getGrandParentPath(curPath, parentRankHeights[curRank]);
                ChunkHashTask parentTask = map.remove(parentPath);
                if (parentTask == null) {
                    parentTask = new ChunkHashTask(HASHING_POOL, parentPath, parentRankHeights[curRank]);
                }
                curTask.setOut(parentTask);

                final long firstPathInRank = Path.getPathForRankAndIndex(curRank, 0);
                final long chunkNoInRank = (curPath - firstPathInRank) / chunkWidth;
                final long firstSiblingPath = firstPathInRank + chunkNoInRank * chunkWidth;
                final long lastSiblingPath = firstSiblingPath + chunkWidth - 1;
                for (long siblingPath = firstSiblingPath; siblingPath <= lastSiblingPath; siblingPath++) {
                    if (siblingPath == curPath) {
                        continue;
                    }
                    if (siblingPath > lastLeafPath) {
                        parentTask.setHash((int) (siblingPath - firstSiblingPath), NULL_HASH);
                        continue;
                    }
                    ChunkHashTask siblingTask = map.remove(siblingPath);
                    if (siblingTask == null) {
                        siblingTask = new ChunkHashTask(HASHING_POOL, siblingPath, curTask.height);
                    }
                    siblingTask.setOut(parentTask);
                    if ((siblingPath < curPath) && !firstLeaf) {
                        siblingTask.setData(null);
                    } else {
                        map.put(siblingPath, siblingTask);
                    }
                    if ((curPath != lastSiblingPath) && !firstLeaf) {
                        stack[curRank] = curPath + 1;
                    }
                }

                curPath = parentPath;
                curTask = parentTask;
            }
            firstLeaf = false;
        }
        map.forEach((path, task) -> task.setData(null));
        map.clear();

        try {
            rootTask.join();
        } catch (final Exception e) {
            if (shutdown.get()) {
                return null;
            }
            logger.error(EXCEPTION.getMarker(), "Failed to wait for all hashing tasks", e);
            throw e;
        }

        listener.onHashingCompleted();

        this.hashReader = null;
        this.listener = null;

        return resultTask.ins[0];
    }

    public Hash emptyRootHash() {
        final Hash NULL_HASH = CryptographyHolder.get().getNullHash();
        return ChunkHashTask.hash(ROOT_PATH, NULL_HASH, NULL_HASH);
    }
}
