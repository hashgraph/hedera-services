/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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
import static com.swirlds.common.merkle.utility.MerkleConstants.MERKLE_DIGEST_TYPE;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.futures.StandardFuture;
import com.swirlds.common.threading.manager.ThreadManager;
import java.util.Iterator;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is responsible for hashing a merkle tree.
 */
public class MerkleHashBuilder {
    private static final Logger logger = LogManager.getLogger(MerkleHashBuilder.class);

    private final ForkJoinPool threadPool;

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

        this.threadPool = new ForkJoinPool(cpuThreadCount);
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
        hashSubtree(iterator);
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
            threadPool.submit(new NodeTask(root,
                    () -> result.set(root.getHash()),
                    result::cancelWithException));
            return result;
        }
    }

    /**
     * The root of a merkle tree.
     *
     * @param it
     * 		An iterator that walks through the tree.
     */
    private void hashSubtree(final Iterator<MerkleNode> it) {
        while (it.hasNext()) {
            final MerkleNode node = it.next();

            if (node.getHash() != null) {
                continue;
            }

            merkleCryptography.digestSync(node, MERKLE_DIGEST_TYPE);
        }
    }

    class NodeTask extends ForkJoinTask<Void> {
        MerkleNode node;
        Runnable onComplete;
        Consumer<Throwable> onError;

        NodeTask(final MerkleNode node, final Runnable onComplete, final Consumer<Throwable> onError) {
            this.node = node;
            this.onComplete = onComplete;
            this.onError = onError;
        }

        @Override
        public Void getRawResult() {
            return null;
        }

        @Override
        protected void setRawResult(Void value) {
        }

        @Override
        public boolean exec() {
            try {
                if (node == null || node.getHash() != null) {
                    onComplete.run();
                } else if (node.isLeaf()) {
                    complete();
                } else {
                    MerkleInternal internal = node.asInternal();
                    int nChildren = internal.getNumberOfChildren();
                    if (nChildren == 0) {
                        complete();
                    } else {
                        final AtomicInteger subTasks = new AtomicInteger(nChildren);
                        for (int childIndex = 0; childIndex < nChildren; childIndex++) {
                            MerkleNode child = internal.getChild(childIndex);
                            new NodeTask(child, () -> {
                                if (subTasks.decrementAndGet() == 0) complete();
                            }, onError).fork();
                        }
                    }
                }
            } catch (Throwable t) {
                onError.accept(t);
                return false;
            }
            return true;
        }

        void complete() {
            merkleCryptography.digestSync(node, MERKLE_DIGEST_TYPE);
            onComplete.run();
        }
    }

}
