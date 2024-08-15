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
import com.swirlds.common.merkle.synchronization.stats.ReconnectMapMetrics;
import com.swirlds.common.merkle.synchronization.stats.ReconnectMapStats;
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
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Performs synchronization in the role of the learner.
 */
public class LearningSynchronizer implements ReconnectNodeCount {

    private static final String WORK_GROUP_NAME = "learning-synchronizer";

    private static final Logger logger = LogManager.getLogger(LearningSynchronizer.class);

    private final StandardWorkGroup workGroup;

    private final AtomicReference<Throwable> firstReconnectException = new AtomicReference<>();

    /**
     * Used to get data from the teacher.
     */
    private final MerkleDataInputStream inputStream;

    private volatile AsyncInputStream in;

    /**
     * Used to transmit data to the teacher.
     */
    private final MerkleDataOutputStream outputStream;

    private volatile AsyncOutputStream out;

    private final Queue<MerkleNode> rootsToReceive;
    private volatile boolean processingNullStartingRoot;
    // All root/custom tree views, by view ID
    private final Map<Integer, LearnerTreeView<?>> views;
    private final Deque<LearnerTreeView<?>> viewsToInitialize;
    /**
     * The root of the merkle tree that resulted from the synchronization operation.
     */
    private final AtomicReference<MerkleNode> newRoot = new AtomicReference<>();

    private final List<AtomicReference<MerkleNode>> reconstructedRoots;

    private int leafNodesReceived;
    private int internalNodesReceived;
    private int redundantLeafNodes;
    private int redundantInternalNodes;

    private long synchronizationTimeMilliseconds;
    private long hashTimeMilliseconds;
    private long initializationTimeMilliseconds;

    protected final ReconnectConfig reconnectConfig;

    /*
     * During reconnect, all merkle sub-trees get unique IDs, which are used to dispatch
     * messages to correct teacher/learner tree views, when multiple sub-trees are synced
     * in parallel. This generator is used to generate these IDs, and a similar one exists
     * on the teacher side.
     */
    private final AtomicInteger viewIdGen = new AtomicInteger(0);

    // Not volatile/atomic, because only used in receiveNextSubtree(), which is synchronized
    private int nextViewId = 0;

    /**
     * Number of subtrees currently being synchronized. Once this number reaches zero, synchronization
     * is complete.
     */
    private final AtomicInteger viewsInProgress = new AtomicInteger(0);

    private final Map<String, Object> viewMetadata = new ConcurrentHashMap<>();

    private final ReconnectMapStats mapStats;

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
     * @param metrics         a Metrics instance for ReconnectMapStats
     */
    public LearningSynchronizer(
            @NonNull final ThreadManager threadManager,
            @NonNull final MerkleDataInputStream in,
            @NonNull final MerkleDataOutputStream out,
            final MerkleNode root,
            @NonNull final Runnable breakConnection,
            @NonNull final ReconnectConfig reconnectConfig,
            @NonNull final Metrics metrics) {
        inputStream = Objects.requireNonNull(in, "inputStream is null");
        outputStream = Objects.requireNonNull(out, "outputStream is null");
        this.reconnectConfig = Objects.requireNonNull(reconnectConfig, "reconnectConfig is null");
        this.mapStats = new ReconnectMapMetrics(metrics, null, null);

        views = new ConcurrentHashMap<>();
        final int viewId = viewIdGen.getAndIncrement();
        views.put(viewId, nodeTreeView(viewId, root));
        viewsToInitialize = new ConcurrentLinkedDeque<>();
        rootsToReceive = new ConcurrentLinkedQueue<>();
        if (root == null) {
            processingNullStartingRoot = true;
        } else {
            processingNullStartingRoot = false;
            rootsToReceive.add(root);
        }

        final Function<Throwable, Boolean> reconnectExceptionListener = ex -> {
            firstReconnectException.compareAndSet(null, ex);
            return false;
        };
        workGroup = createStandardWorkGroup(threadManager, breakConnection, reconnectExceptionListener);
        reconstructedRoots = Collections.synchronizedList(new ArrayList<>());
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
                new MerkleTreeVisualizer(newRoot.get())
                        .setDepth(5)
                        .setUseHashes(false)
                        .setUseMnemonics(false)
                        .render());
        try {
            if (newRoot.get() != null) {
                newRoot.get().release();
            }
        } catch (final Exception ex) {
            // The tree may be in a partially constructed state. We don't expect exceptions, but they
            // may be more likely to appear during this operation than at other times.
            logger.error(EXCEPTION.getMarker(), "exception thrown while releasing tree", ex);
        }
    }

    /**
     * Checks if synchronization is still in progress. It's true, when the input stream is still
     * alive (so new messages may be received) and there are tree views to sync. This check is
     * used to terminate the output stream.
     */
    private boolean inProgress() {
        return in.isAlive() && !views.isEmpty();
    }

    /**
     * Receive the tree from the teacher.
     */
    private void receiveTree() throws InterruptedException {
        logger.info(RECONNECT.getMarker(), "synchronizing tree");
        final long start = System.currentTimeMillis();

        in = new AsyncInputStream(inputStream, workGroup, reconnectConfig);
        out = buildOutputStream(workGroup, this::inProgress, outputStream);

        final boolean rootScheduled = receiveNextSubtree();
        assert rootScheduled;

        in.start();
        out.start();

        InterruptedException interruptException = null;
        try {
            workGroup.waitForTermination();
        } catch (final InterruptedException e) { // NOSONAR: Exception is rethrown below after cleanup.
            interruptException = e;
            logger.warn(RECONNECT.getMarker(), "interrupted while waiting for work group termination");
        }

        if ((interruptException != null) || workGroup.hasExceptions()) {
            // Depending on where the failure occurred, there may be deserialized objects still sitting in
            // the async input stream's queue that haven't been attached to any tree.
            in.abort();

            for (final AtomicReference<MerkleNode> root : reconstructedRoots) {
                final MerkleNode merkleRoot = root.get();
                if ((merkleRoot != null) && (merkleRoot.getReservationCount() == 0)) {
                    // If the root has a reference count of 0 then it is not underneath any other tree,
                    // and this thread holds the implicit reference to the root.
                    // This is the last chance to release that tree in this scenario.
                    logger.warn(RECONNECT.getMarker(), "deleting partially constructed subtree");
                    merkleRoot.release();
                }
            }
            // newRoot has been released above. To avoid releasing it again in abort(), set it to null
            newRoot.set(null);
            if (interruptException != null) {
                throw interruptException;
            }
            throw new MerkleSynchronizationException(
                    "Synchronization failed with exceptions", firstReconnectException.get());
        }

        // If this is the first received root, set it as the root for this learning synchronizer
        newRoot.compareAndSet(null, reconstructedRoots.get(0).get());

        synchronizationTimeMilliseconds = System.currentTimeMillis() - start;
        logger.info(RECONNECT.getMarker(), "synchronization complete");
    }

    private LearnerTreeView<?> nodeTreeView(final int viewId, final MerkleNode root) {
        if (root == null || !root.hasCustomReconnectView()) {
            return new LearnerPushMerkleTreeView(viewId, root, mapStats);
        } else {
            assert root instanceof CustomReconnectRoot;
            return ((CustomReconnectRoot<?, ?>) root).buildLearnerView(viewId, reconnectConfig, this, mapStats);
        }
    }

    private void newSubtreeEncountered(final CustomReconnectRoot<?, ?> root) {
        final int viewId = viewIdGen.getAndIncrement();
        final LearnerTreeView<?> view = nodeTreeView(viewId, root);
        views.put(viewId, view);
        rootsToReceive.add(root);
        if (view.usesSharedInputQueue()) {
            in.setNeedsSharedQueue(viewId);
        }
    }

    private synchronized boolean receiveNextSubtree() {
        if (viewsInProgress.incrementAndGet() > reconnectConfig.maxParallelSubtrees()) {
            // Max number of views is already being synchronized
            viewsInProgress.decrementAndGet();
            return false;
        }

        final MerkleNode root;
        if (processingNullStartingRoot) {
            assert rootsToReceive.isEmpty();
            root = null;
            processingNullStartingRoot = false;
        } else {
            if (rootsToReceive.isEmpty()) {
                viewsInProgress.decrementAndGet();
                return false;
            }
            root = rootsToReceive.poll();
        }
        final String route = root == null ? "[]" : root.getRoute().toString();

        final int viewId = nextViewId++;
        final LearnerTreeView<?> view = views.get(viewId);
        if (view == null) {
            throw new MerkleSynchronizationException("Cannot schedule next subtree, unknown view: " + viewId);
        }

        logger.info(RECONNECT.getMarker(), "Receiving tree rooted with route={}, viewId={}", route, viewId);

        final AtomicReference<MerkleNode> reconstructedRoot = new AtomicReference<>();
        reconstructedRoots.add(reconstructedRoot);
        viewsToInitialize.addFirst(view);
        final Consumer<Integer> completeListener = id -> {
            viewsInProgress.decrementAndGet();
            boolean nextViewScheduled = receiveNextSubtree();
            while (nextViewScheduled) {
                nextViewScheduled = receiveNextSubtree();
            }
            // Close the view and remove it from the list of views to sync. If this was the last
            // view to sync, it will cause the async output stream to terminate
            views.remove(id).close();
        };
        view.startLearnerTasks(
                this, workGroup, in, out, views, this::newSubtreeEncountered, reconstructedRoot, completeListener);
        return true;
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
            MerkleCryptoFactory.getInstance().digestTreeAsync(newRoot.get()).get();
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
        System.err.println(new SynchronizationCompletePayload("Finished synchronization")
                .setTimeInSeconds(synchronizationTimeMilliseconds * MILLISECONDS_TO_SECONDS)
                .setHashTimeInSeconds(hashTimeMilliseconds * MILLISECONDS_TO_SECONDS)
                .setInitializationTimeInSeconds(initializationTimeMilliseconds * MILLISECONDS_TO_SECONDS)
                .setTotalNodes(leafNodesReceived + internalNodesReceived)
                .setLeafNodes(leafNodesReceived)
                .setRedundantLeafNodes(redundantLeafNodes)
                .setInternalNodes(internalNodesReceived)
                .setRedundantInternalNodes(redundantInternalNodes)
                .toString());
        logger.info(RECONNECT.getMarker(), () -> mapStats.format());
    }

    /**
     * Get the root of the resulting tree. May return an incomplete tree if called before synchronization is finished.
     */
    public MerkleNode getRoot() {
        return newRoot.get();
    }

    @SuppressWarnings("unchecked")
    public <V> V computeViewMetadata(final String key, final V defaultValue) {
        return (V) viewMetadata.compute(key, (k, v) -> (v != null) ? v : defaultValue);
    }

    protected StandardWorkGroup createStandardWorkGroup(
            ThreadManager threadManager,
            Runnable breakConnection,
            Function<Throwable, Boolean> reconnectExceptionListener) {
        return new StandardWorkGroup(threadManager, WORK_GROUP_NAME, breakConnection, reconnectExceptionListener);
    }

    /**
     * Build the output stream. Exposed to allow unit tests to override implementation to simulate latency.
     */
    public <T extends SelfSerializable> AsyncOutputStream buildOutputStream(
            final StandardWorkGroup workGroup, final Supplier<Boolean> alive, final SerializableDataOutputStream out) {
        return new AsyncOutputStream(out, workGroup, alive, reconnectConfig);
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
