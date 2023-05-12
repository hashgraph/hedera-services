/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.hash;

import static com.swirlds.common.crypto.engine.CryptoEngine.THREAD_COMPONENT_NAME;
import static com.swirlds.common.merkle.iterators.MerkleIterationOrder.POST_ORDERED_DEPTH_FIRST_RANDOM;
import static com.swirlds.common.merkle.utility.MerkleConstants.MERKLE_DIGEST_TYPE;
import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.merkle.iterators.MerkleIterator;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.futures.StandardFuture;
import com.swirlds.common.threading.manager.ThreadManager;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is responsible for hashing a merkle tree.
 */
public class MerkleHashBuilder {
    private static final Logger logger = LogManager.getLogger(MerkleHashBuilder.class);

    private final Executor threadPool;

    private final int cpuThreadCount;

    private final MerkleCryptography merkleCryptography;

    private final Cryptography cryptography;

    /**
     * Construct an object which calculates the hash of a merkle tree.
     *
     * @param threadManager
     * 		responsible for managing thread lifecycles
     * @param cryptography
     * 		the {@link Cryptography} implementation to use
     * @param cpuThreadCount
     * 		the number of threads to be used for computing hash
     */
    public MerkleHashBuilder(
            final ThreadManager threadManager,
            final MerkleCryptography merkleCryptography,
            final Cryptography cryptography,
            final int cpuThreadCount) {
        this.merkleCryptography = merkleCryptography;
        this.cryptography = cryptography;
        this.cpuThreadCount = cpuThreadCount;

        final ThreadFactory threadFactory = new ThreadConfiguration(threadManager)
                .setDaemon(true)
                .setComponent(THREAD_COMPONENT_NAME)
                .setThreadName("merkle hash")
                .setPriority(Thread.NORM_PRIORITY)
                .setExceptionHandler((t, ex) -> {
                    logger.error(EXCEPTION.getMarker(), "Uncaught exception in MerkleHashBuilder thread pool", ex);
                })
                .buildFactory();

        this.threadPool = Executors.newFixedThreadPool(cpuThreadCount, threadFactory);
    }

    /**
     * Only return nodes that require a hash.
     */
    private static boolean filter(MerkleNode node) {
        if (node == null) {
            return false;
        }

        if (node.isSelfHashing()) {
            return true;
        }

        return node.getHash() == null;
    }

    /**
     * Don't bother with subtrees that have already been hashed.
     */
    private static boolean descendantFilter(final MerkleNode child) {
        if (child.isSelfHashing()) {
            return false;
        }

        return child.getHash() == null;
    }

    /**
     * Compute the hash of the merkle tree synchronously on the caller's thread.
     *
     * @param root
     * 		the root of the tree to hash
     * @return The hash of the tree.
     */
    public Hash digestTreeSync(MerkleNode root) {
        if (root == null) {
            return cryptography.getNullHash(MERKLE_DIGEST_TYPE);
        }

        final Iterator<MerkleNode> iterator = root.treeIterator()
                .setFilter(MerkleHashBuilder::filter)
                .setDescendantFilter(MerkleHashBuilder::descendantFilter);
        hashSubtree(iterator, null);
        return root.getHash();
    }

    /**
     * Compute the hash of the merkle tree on multiple worker threads.
     *
     * @param root
     * 		the root of the tree to hash
     * @return a Future which encapsulates the hash of the merkle tree
     */
    public Future<Hash> digestTreeAsync(MerkleNode root) {
        if (root == null) {
            return new StandardFuture<>(cryptography.getNullHash(MERKLE_DIGEST_TYPE));
        } else if (root.getHash() != null) {
            return new StandardFuture<>(root.getHash());
        } else {
            final FutureMerkleHash result = new FutureMerkleHash();
            AtomicInteger activeThreadCount = new AtomicInteger(cpuThreadCount);
            for (int threadIndex = 0; threadIndex < cpuThreadCount; threadIndex++) {
                threadPool.execute(createHashingRunnable(threadIndex, activeThreadCount, result, root));
            }
            return result;
        }
    }

    /**
     * Create a thread that will attempt to hash the tree starting at the root.
     *
     * @param threadId
     * 		Used to generate a randomized iteration order
     */
    private Runnable createHashingRunnable(
            final int threadId, AtomicInteger activeThreadCount, final FutureMerkleHash result, final MerkleNode root) {

        return () -> {
            final MerkleIterator<MerkleNode> it = root.treeIterator()
                    .setFilter(MerkleHashBuilder::filter)
                    .setDescendantFilter(MerkleHashBuilder::descendantFilter);
            if (threadId > 0) {
                // One thread can iterate in-order, all others will use random order.
                it.setOrder(POST_ORDERED_DEPTH_FIRST_RANDOM);
            }

            try {
                hashSubtree(it, activeThreadCount);

                // The last thread to terminate is responsible for setting the future
                int remainingActiveThreads = activeThreadCount.getAndDecrement() - 1;
                if (remainingActiveThreads == 0) {
                    result.set(root.getHash());
                }
            } catch (final Throwable t) {
                result.cancelWithException(t);
            }
        };
    }

    /**
     * The root of a merkle tree.
     *
     * @param it
     * 		An iterator that walks through the tree.
     * @param activeThreadCount
     * 		If single threaded then this is null, otherwise contains the number of threads
     * 		that are actively hashing. Once the active thread count dips below the maximum,
     * 		this means that one thread has either finished or exploded.
     */
    private void hashSubtree(final Iterator<MerkleNode> it, final AtomicInteger activeThreadCount) {
        while (it.hasNext()) {
            final MerkleNode node = it.next();
            // Potential optimization: if this node is currently locked, do not wait here. Skip it and continue.
            // This would require a lock object that support the "try lock" paradigm.
            synchronized (node) {
                if (activeThreadCount != null && activeThreadCount.get() != cpuThreadCount) {
                    break;
                }

                if (node.getHash() != null) {
                    continue;
                }

                if (node.isLeaf()) {
                    merkleCryptography.digestSync(node.asLeaf(), MERKLE_DIGEST_TYPE);
                } else {
                    merkleCryptography.digestSync(node.asInternal(), MERKLE_DIGEST_TYPE);
                }
            }
        }
    }
}
