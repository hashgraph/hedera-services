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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.virtualmap.internal.Path.INVALID_PATH;
import static com.swirlds.virtualmap.internal.Path.ROOT_PATH;
import static com.swirlds.virtualmap.internal.Path.getIndexInRank;
import static com.swirlds.virtualmap.internal.Path.getParentPath;
import static com.swirlds.virtualmap.internal.Path.getRank;
import static com.swirlds.virtualmap.internal.Path.getSiblingPath;

import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashBuilder;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
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
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for hashing virtual merkle trees. This class is designed to work both for normal
 * hashing use cases, and also for hashing during reconnect.
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
    private static final VirtualMapConfig config = ConfigurationHolder.getConfigData(VirtualMapConfig.class);

    /**
     * The number of threads to use when hashing. Can either be supplied by a system property, or
     * will compute a default based on "percentHashThreads".
     */
    private static final int HASHING_THREAD_COUNT = config.getNumHashThreads();

    /**
     * This thread-local gets a HashBuilder that can be used for hashing on a per-thread basis.
     */
    private static final ThreadLocal<HashBuilder> HASH_BUILDER_THREAD_LOCAL =
            ThreadLocal.withInitial(() -> new HashBuilder(Cryptography.DEFAULT_DIGEST_TYPE));

    /**
     * Tracks if this virtual hasher has been shut down. If true (indicating that the hasher
     * has been intentionally shut down), then don't log/throw if the rug is pulled from
     * underneath the hashing threads.
     */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private static final Cryptography CRYPTO = CryptographyHolder.get();

    private static final Hash NULL_HASH = CRYPTO.getNullHash();

    private static final ForkJoinPool pool = new ForkJoinPool(HASHING_THREAD_COUNT);

    /**
     * Create a new {@link VirtualHasher}. There should be one {@link VirtualHasher} shared across all copies
     * of a {@link VirtualMap} "family".
     */
    public VirtualHasher() {
    }

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

    static class BaseTask extends ForkJoinTask<Void> {

        protected final AtomicInteger count;

        public BaseTask(int n) {
            this.count = new AtomicInteger(n);
        }

        @Override
        public Void getRawResult() {
            return null;
        }

        @Override
        protected void setRawResult(Void value) {
            // not used
        }

        @Override
        protected boolean exec() {
            return true;
        }

        void push() {
            if (count.decrementAndGet() == 0) {
                forkIt();
            }
        }

        void push(final int p) {
            if (count.updateAndGet(value -> value - p) == 0) {
                forkIt();
            }
        }

        private void forkIt() {
            if (Thread.currentThread() instanceof ForkJoinWorkerThread) {
                fork();
            } else {
                pool.execute(this);
            }
        }
    }

    class ChunkHashTask extends BaseTask {

        private final long path;

        private final int height; // 1 for 3-node chunk, 2 for 7-node chunk, and so on

        private ChunkHashTask out;

        private final Hash[] ins;

        private VirtualLeafRecord<K, V> leaf;

        // isLeaf is just an optimization: if the task is known to be used as a leaf task,
        // there is no need to allocate an array of hashes that will always be empty
        ChunkHashTask(long path, int height, boolean isLeaf) {
            super(1 + (1 << height));
            this.height = height;
            this.path = path;
            this.ins = isLeaf ? null : new Hash[1 << height];
        }

        void setOut(final ChunkHashTask out) {
            this.out = out;
            assert path == 0 || Path.getRank(path) - out.height == Path.getRank(out.path) :
                    "setOut " + path + " " + height + " " + out.path;
            push();
        }

        void setLeaf(final VirtualLeafRecord<K, V> leaf) {
            assert leaf == null || path == leaf.getPath();
            assert leaf == null || height == 1;
            assert leaf != null || out != null;
            if (leaf == null) {
                out.setIn(getIndexInOut(), null);
            } else {
                this.leaf = leaf;
                push(1 << height);
            }
        }

        void setIn(final int index, final Hash hash) {
            assert index >= 0 && index < (1 << height);
            ins[index] = hash;
            push();
        }

        @Override
        protected boolean exec() {
            assert count.get() == 0;
            final Hash hash;
            if (leaf != null) {
                hash = CRYPTO.digestSync(leaf);
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
            out.setIn(getIndexInOut(), hash);
            return true;
        }

        static Hash hash(final long path, final Hash left, final Hash right) {
            final long classId = path == ROOT_PATH
                    ? VirtualRootNode.CLASS_ID
                    : VirtualInternalNode.CLASS_ID;
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
            long outPath = out.path;
            for (int i = 0; i < out.height; i++) {
                outPath = Path.getLeftChildPath(outPath);
            }
            return (int) (path - outPath);
        }
    }

    private LongFunction<Hash> hashReader;

    private VirtualHashListener<K, V> listener;

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
            listener = new VirtualHashListener<>() { /* noop */
            };
        }

        this.hashReader = hashReader;
        this.listener = listener;

        final int chunkHeight = 5; // TODO: make it configurable
        int firstLeafRank = Path.getRank(firstLeafPath);
        int lastLeafRank = Path.getRank(lastLeafPath);

        // Let the listener know we have started hashing.
        listener.onHashingStarted();

        final HashMap<Long, ChunkHashTask> map = new HashMap<>();
        ChunkHashTask resultTask = new ChunkHashTask(INVALID_PATH, 1, false);
        int rootTaskHeight = Math.min(firstLeafRank, chunkHeight);
        ChunkHashTask rootTask = new ChunkHashTask(ROOT_PATH, rootTaskHeight, false);
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
                curTask = new ChunkHashTask(curPath, 1, true);
            }
            curTask.setLeaf(leaf);

            boolean isLeaf = true;
            while (true) {
                final int curRank = getRank(curPath);
                final int chunkWidth = 1 << parentRankHeights[curRank];
                if (stack[curRank] != INVALID_PATH) {
                    long curStackPath = stack[curRank];
                    long firstPathInRank = Path.getPathForRankAndIndex(curRank, 0);
                    final long curStackChunkNoInRank = (curStackPath - firstPathInRank) / chunkWidth;
                    final long lastPathInCurStackChunk = firstPathInRank + (curStackChunkNoInRank + 1) * chunkWidth - 1;
                    while (curStackPath < Math.min(curPath, lastPathInCurStackChunk)) {
                        final ChunkHashTask t = map.remove(curStackPath);
                        assert t != null;
                        t.setLeaf(null);
                        curStackPath++;
                    }
                    stack[curRank] = INVALID_PATH;
                }

                if (curTask.out != null) {
                    break;
                }

                final int parentRank = curRank - parentRankHeights[curRank];
                long parentPath = curPath;
                for (int i = 0; i < curRank - parentRank; i++) {
                    parentPath = Path.getParentPath(parentPath);
                }
                ChunkHashTask parentTask = map.remove(parentPath);
                if (parentTask == null) {
                    parentTask = new ChunkHashTask(parentPath, parentRankHeights[curRank], false);
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
                        parentTask.setIn((int) (siblingPath - firstSiblingPath), NULL_HASH);
                        continue;
                    }
                    ChunkHashTask siblingTask = map.remove(siblingPath);
                    if (siblingTask == null) {
                        siblingTask = new ChunkHashTask(siblingPath, curTask.height,
                                isLeaf && (!firstLeaf || siblingPath > curPath));
                    }
                    siblingTask.setOut(parentTask);
                    if ((siblingPath < curPath) && !firstLeaf) {
                        siblingTask.setLeaf(null);
                    } else {
                        map.put(siblingPath, siblingTask);
                    }
                    if ((curPath != lastSiblingPath) && !firstLeaf) {
                        stack[curRank] = curPath + 1;
                    }
                }

                curPath = parentPath;
                curTask = parentTask;
                isLeaf = false;
            }
            firstLeaf = false;
        }
        map.forEach((path, task) -> task.setLeaf(null));
        map.clear();

        rootTask.join();

        listener.onHashingCompleted();

        this.hashReader = null;
        this.listener = null;

        return resultTask.ins[0];
    }

    public Hash emptyRootHash() {
        return ChunkHashTask.hash(ROOT_PATH, NULL_HASH, NULL_HASH);
    }

}
