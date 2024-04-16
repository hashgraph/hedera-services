/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.synchronization;

import static com.swirlds.base.units.UnitConstants.MILLISECONDS_TO_SECONDS;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.RECONNECT;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.task.ReconnectNodeCount;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.CustomReconnectRoot;
import com.swirlds.common.merkle.synchronization.views.LearnerPushMerkleTreeView;
import com.swirlds.common.merkle.synchronization.views.LearnerTreeView;
import com.swirlds.common.merkle.utility.MerkleTreeVisualizer;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.logging.legacy.payload.SynchronizationCompletePayload;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Performs synchronization in the role of the learner.
 */
public class LearningSynchronizer implements ReconnectNodeCount {

    private static final String WORK_GROUP_NAME = "learning-synchronizer";

    private static final Logger logger = LogManager.getLogger(LearningSynchronizer.class);

    /**
     * Used to get data from the teacher.
     */
    private final MerkleDataInputStream inputStream;

    /**
     * Used to transmit data to the teacher.
     */
    private final MerkleDataOutputStream outputStream;

    private final Queue<MerkleNode> rootsToReceive;
    private final Deque<LearnerTreeView<?>> viewsToInitialize;
    private final Runnable breakConnection;
    /**
     * The root of the merkle tree that resulted from the synchronization operation.
     */
    private MerkleNode newRoot;

    private int leafNodesReceived;
    private int internalNodesReceived;
    private int redundantLeafNodes;
    private int redundantInternalNodes;

    private long synchronizationTimeMilliseconds;
    private long hashTimeMilliseconds;
    private long initializationTimeMilliseconds;

    protected final ReconnectConfig reconnectConfig;

    /**
     * Responsible for creating and managing threads used by this object.
     */
    private final ThreadManager threadManager;

    /*
     * During reconnect, all merkle sub-trees get unique IDs, which are used to dispatch
     * messages to correct teacher/learner tree views, when multiple sub-trees are synced
     * in parallel. This generator is used to generate these IDs, and a similar one exists
     * on the teacher side.
     */
    private final AtomicInteger viewIdGen = new AtomicInteger(0);

    /**
     * Create a new learning synchronizer.
     *
     * @param threadManager   responsible for managing thread lifecycles
     * @param in              the input stream
     * @param out             the output stream
     * @param root            the root of the tree
     * @param breakConnection a method that breaks the connection. Used iff an exception is encountered. Prevents
     *                        deadlock if there is a thread stuck on a blocking IO operation that will never finish due
     *                        to a failure.
     * @param reconnectConfig the configuration for the reconnect
     */
    public LearningSynchronizer(
            @NonNull final ThreadManager threadManager,
            @NonNull final MerkleDataInputStream in,
            @NonNull final MerkleDataOutputStream out,
            @NonNull final MerkleNode root,
            @NonNull final Runnable breakConnection,
            @NonNull final ReconnectConfig reconnectConfig) {

        this.threadManager = Objects.requireNonNull(threadManager, "threadManager is null");

        inputStream = Objects.requireNonNull(in, "inputStream is null");
        outputStream = Objects.requireNonNull(out, "outputStream is null");
        this.reconnectConfig = Objects.requireNonNull(reconnectConfig, "reconnectConfig is null");

        rootsToReceive = new LinkedList<>();
        viewsToInitialize = new LinkedList<>();
        rootsToReceive.add(root);

        this.breakConnection = breakConnection;
    }

    /**
     * Perform synchronization in the role of the learner.
     */
    public void synchronize() throws InterruptedException {
        try {
            logger.info(RECONNECT.getMarker(), "learner calls receiveTree()");
            receiveTree();
            logger.info(RECONNECT.getMarker(), "learner calls initialize()");
            initialize();
            logger.info(RECONNECT.getMarker(), "learner calls hash()");
            hash();
            logger.info(RECONNECT.getMarker(), "learner calls logStatistics()");
            logStatistics();
            logger.info(RECONNECT.getMarker(), "learner is done synchronizing");
        } catch (final InterruptedException ex) {
            logger.warn(RECONNECT.getMarker(), "synchronization interrupted");
            Thread.currentThread().interrupt();
            abort();
            throw ex;
        } catch (final Exception ex) {
            abort();
            throw new MerkleSynchronizationException(ex);
        }
    }

    /**
     * Attempt to free any and all resources that were acquired during the reconnect attempt.
     */
    private void abort() {
        logger.warn(
                RECONNECT.getMarker(),
                "Deleting partially constructed tree:\n{}",
                new MerkleTreeVisualizer(newRoot)
                        .setDepth(5)
                        .setUseHashes(false)
                        .setUseMnemonics(false)
                        .render());
        try {
            if (newRoot != null) {
                newRoot.release();
            }
        } catch (final Exception ex) {
            // The tree may be in a partially constructed state. We don't expect exceptions, but they
            // may be more likely to appear during this operation than at other times.
            logger.error(EXCEPTION.getMarker(), "exception thrown while releasing tree", ex);
        }
    }

    /**
     * Receive the tree from the teacher.
     */
    private void receiveTree() throws InterruptedException {
        logger.info(RECONNECT.getMarker(), "synchronizing tree");
        final long start = System.currentTimeMillis();

        while (!rootsToReceive.isEmpty()) {
            final List<MerkleNode> toReceive = new ArrayList<>();
            int count = 0;
            while (!rootsToReceive.isEmpty() && (count < reconnectConfig.maxParallelSubtrees())) {
                toReceive.add(rootsToReceive.remove());
                count++;
            }
            final List<MerkleNode> receivedRoots = receiveTreesInParallel(toReceive);
            if (newRoot == null) {
                // The first tree synchronized will contain the root of the tree as a whole
                assert receivedRoots.size() == 1;
                newRoot = receivedRoots.get(0);
            }
        }

        synchronizationTimeMilliseconds = System.currentTimeMillis() - start;
        logger.info(RECONNECT.getMarker(), "synchronization complete");
    }

    private List<MerkleNode> receiveTreesInParallel(final List<MerkleNode> toReceive) throws InterruptedException {
        final AtomicReference<Throwable> firstReconnectException = new AtomicReference<>();
        final Function<Throwable, Boolean> reconnectExceptionListener = ex -> {
            firstReconnectException.compareAndSet(null, ex);
            return false;
        };
        final StandardWorkGroup workGroup =
                new StandardWorkGroup(threadManager, WORK_GROUP_NAME, breakConnection, reconnectExceptionListener);

        final List<AtomicReference<MerkleNode>> reconstructedRoots = new ArrayList<>(toReceive.size());

        final AsyncInputStream in = new AsyncInputStream(inputStream, workGroup, reconnectConfig);
        final AsyncOutputStream out = buildOutputStream(workGroup, outputStream);
        // Async output can be started right away. Its internal queues for every view are initialized
        // in sendAsync(). Async input is different, views are explicitly registered in it using
        // registerView() method. This is why it is started below, after all tasks are created
        out.start();

        final AtomicInteger runningCount = new AtomicInteger(toReceive.size());
        for (final MerkleNode root : toReceive) {
            final String route = root == null ? "[]" : root.getRoute().toString();
            final LearnerTreeView<?> view;
            if (root == null || !root.hasCustomReconnectView()) {
                view = new LearnerPushMerkleTreeView(root);
            } else {
                assert root instanceof CustomReconnectRoot;
                view = ((CustomReconnectRoot<?, ?>) root).buildLearnerView(reconnectConfig);
            }

            logger.info(RECONNECT.getMarker(), "Receiving tree rooted with route {}", route);

            final AtomicReference<MerkleNode> reconstructedRoot = new AtomicReference<>();
            reconstructedRoots.add(reconstructedRoot);
            final int viewId = viewIdGen.getAndIncrement();
            view.startLearnerTasks(this, workGroup, viewId, in, out, rootsToReceive, reconstructedRoot, success -> {
                if (success) {
                    viewsToInitialize.addFirst(view);
                    logger.info(RECONNECT.getMarker(), "Finished receiving tree with route {}", route);
                } else {
                    logger.error(RECONNECT.getMarker(), "Failed to receive tree with route {}", route);
                }
                if (runningCount.decrementAndGet() == 0) {
                    in.close();
                    out.close();
                }
            });
        }

        // All views have registered themselves in async input. It can now accept messages from the
        // underlying input stream and put them to the right view's queues. It's time to start it
        in.start();

        InterruptedException interruptException = null;
        try {
            workGroup.waitForTermination();
        } catch (final InterruptedException e) { // NOSONAR: Exception is rethrown below after cleanup.
            interruptException = e;
            logger.warn(RECONNECT.getMarker(), "interrupted while waiting for work group termination");
        }

        if (interruptException != null || workGroup.hasExceptions()) {
            // Depending on where the failure occurred, there may be deserialized objects still sitting in
            // the async input stream's queue that haven't been attached to any tree.
            in.abort();

            for (final AtomicReference<MerkleNode> root : reconstructedRoots) {
                final MerkleNode merkleRoot = root.get();
                if (merkleRoot.getReservationCount() == 0) {
                    // If the root has a reference count of 0 then it is not underneath any other tree,
                    // and this thread holds the implicit reference to the root.
                    // This is the last chance to release that tree in this scenario.
                    logger.warn(RECONNECT.getMarker(), "deleting partially constructed subtree");
                    merkleRoot.release();
                }
            }
            if (interruptException != null) {
                throw interruptException;
            }
            throw new MerkleSynchronizationException(
                    "Synchronization failed with exceptions", firstReconnectException.get());
        }

        return reconstructedRoots.stream().map(AtomicReference::get).toList();
    }

    /**
     * Initialize the tree.
     */
    private void initialize() {
        logger.info(RECONNECT.getMarker(), "initializing tree");
        final long start = System.currentTimeMillis();

        while (!viewsToInitialize.isEmpty()) {
            viewsToInitialize.removeFirst().initialize();
        }

        initializationTimeMilliseconds = System.currentTimeMillis() - start;
        logger.info(RECONNECT.getMarker(), "initialization complete");
    }

    /**
     * Hash the tree.
     */
    private void hash() throws InterruptedException {
        logger.info(RECONNECT.getMarker(), "hashing tree");
        final long start = System.currentTimeMillis();

        try {
            MerkleCryptoFactory.getInstance().digestTreeAsync(newRoot).get();
        } catch (ExecutionException e) {
            logger.error(EXCEPTION.getMarker(), "exception while computing hash of reconstructed tree", e);
            return;
        }

        hashTimeMilliseconds = System.currentTimeMillis() - start;
        logger.info(RECONNECT.getMarker(), "hashing complete");
    }

    /**
     * Log information about the synchronization.
     */
    private void logStatistics() {
        logger.info(RECONNECT.getMarker(), () -> new SynchronizationCompletePayload("Finished synchronization")
                .setTimeInSeconds(synchronizationTimeMilliseconds * MILLISECONDS_TO_SECONDS)
                .setHashTimeInSeconds(hashTimeMilliseconds * MILLISECONDS_TO_SECONDS)
                .setInitializationTimeInSeconds(initializationTimeMilliseconds * MILLISECONDS_TO_SECONDS)
                .setTotalNodes(leafNodesReceived + internalNodesReceived)
                .setLeafNodes(leafNodesReceived)
                .setRedundantLeafNodes(redundantLeafNodes)
                .setInternalNodes(internalNodesReceived)
                .setRedundantInternalNodes(redundantInternalNodes)
                .toString());
    }

    /**
     * Get the root of the resulting tree. May return an incomplete tree if called before synchronization is finished.
     */
    public MerkleNode getRoot() {
        return newRoot;
    }

    /**
     * Build the output stream. Exposed to allow unit tests to override implementation to simulate latency.
     */
    public <T extends SelfSerializable> AsyncOutputStream buildOutputStream(
            final StandardWorkGroup workGroup, final SerializableDataOutputStream out) {
        return new AsyncOutputStream(out, workGroup, reconnectConfig);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementLeafCount() {
        leafNodesReceived++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementRedundantLeafCount() {
        redundantLeafNodes++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementInternalCount() {
        internalNodesReceived++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementRedundantInternalCount() {
        redundantInternalNodes++;
    }
}
