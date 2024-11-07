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

package com.swirlds.virtualmap.internal.reconnect;

import static com.swirlds.virtualmap.internal.Path.ROOT_PATH;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.LearningSynchronizer;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.stats.ReconnectMapStats;
import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.task.ExpectedLesson;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.CustomReconnectRoot;
import com.swirlds.common.merkle.synchronization.views.LearnerTreeView;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.Path;
import com.swirlds.virtualmap.internal.RecordAccessor;
import com.swirlds.virtualmap.internal.VirtualStateAccessor;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An implementation of {@link LearnerTreeView} for the virtual merkle. The learner during reconnect
 * needs access both to the original state and records, and the current reconnect state and records.
 * This implementation uses {@link Long} as the representation of a node and corresponds directly
 * to the path of the node.
 *
 * <p>This implementation is supposed to work with {@link TeacherPullVirtualTreeView} on the
 * teacher side.
 *
 * @param <K>
 * 		The key
 * @param <V>
 * 		The value
 */
public final class LearnerPullVirtualTreeView<K extends VirtualKey, V extends VirtualValue>
        extends VirtualTreeViewBase<K, V> implements LearnerTreeView<Long> {

    private static final Logger logger = LogManager.getLogger(LearnerPullVirtualTreeView.class);

    /**
     * A stashed null hash, which is used for any leaves which are null that we need to send
     * (specifically, leaf 2 for a tree with only a single leaf).
     */
    private static final Hash NULL_HASH = CryptographyHolder.get().getNullHash();

    /**
     * Reconnect configuration.
     */
    private final ReconnectConfig reconnectConfig;

    private final int viewId;

    /**
     * Handles removal of old nodes.
     */
    private final ReconnectNodeRemover<K, V> nodeRemover;

    /**
     * A {@link RecordAccessor} for getting access to the original records.
     */
    private final RecordAccessor<K, V> originalRecords;

    /**
     * Node traversal order. Defines the order in which node requests will be sent to the teacher.
     */
    private final NodeTraversalOrder traversalOrder;

    private final ReconnectMapStats mapStats;

    /**
     * Indicates if no responses from the teacher have been received yet. The very first response
     * must be for path 0 (root virtual node)
     */
    private volatile boolean firstNodeResponse = true;

    /**
     * Responses from teacher may come in a different order than they are sent by learner. The order
     * is important for hashing, so it's restored using this queue. Once hashing is improved to work
     * with unsorted dirty leaves stream, this code may be cleaned up.
     */
    private final Queue<Long> anticipatedLeafPaths = new ConcurrentLinkedDeque<>();

    /**
     * Related to the queue above. If a response is received out of order, it's temporarily stored
     * in this map.
     */
    private final Map<Long, PullVirtualTreeResponse> responses = new ConcurrentHashMap<>();

    /**
     * Create a new {@link LearnerPullVirtualTreeView}.
     *
     * @param root
     * 		The root node of the <strong>reconnect</strong> tree. Cannot be null.
     * @param originalRecords
     * 		A {@link RecordAccessor} for accessing records from the unmodified <strong>original</strong> tree.
     * 		Cannot be null.
     * @param originalState
     * 		A {@link VirtualStateAccessor} for accessing state (first and last paths) from the
     * 		unmodified <strong>original</strong> tree. Cannot be null.
     * @param reconnectState
     * 		A {@link VirtualStateAccessor} for accessing state (first and last paths) from the
     * 		modified <strong>reconnect</strong> tree. We only use first and last leaf path from this state.
     * 		Cannot be null.
     * @param mapStats
     *      A ReconnectMapStats object to collect reconnect metrics
     */
    public LearnerPullVirtualTreeView(
            final ReconnectConfig reconnectConfig,
            final int viewId,
            final VirtualRootNode<K, V> root,
            final RecordAccessor<K, V> originalRecords,
            final VirtualStateAccessor originalState,
            final VirtualStateAccessor reconnectState,
            final ReconnectNodeRemover<K, V> nodeRemover,
            final NodeTraversalOrder traversalOrder,
            @NonNull final ReconnectMapStats mapStats) {
        super(root, originalState, reconnectState);
        this.reconnectConfig = reconnectConfig;
        this.viewId = viewId;
        this.originalRecords = Objects.requireNonNull(originalRecords);
        this.nodeRemover = nodeRemover;
        this.traversalOrder = traversalOrder;
        this.mapStats = mapStats;
    }

    @Override
    public void startLearnerTasks(
            final LearningSynchronizer learningSynchronizer,
            final StandardWorkGroup workGroup,
            final AsyncInputStream in,
            final AsyncOutputStream out,
            final Map<Integer, LearnerTreeView<?>> views,
            final Consumer<CustomReconnectRoot<?, ?>> subtreeListener,
            final AtomicReference<MerkleNode> reconstructedRoot,
            final Consumer<Integer> completeListener) {
        final Map<Integer, CountDownLatch> allRootResponseReceived =
                learningSynchronizer.computeViewMetadata("ROOTRESPONSES", new ConcurrentHashMap<>());
        final CountDownLatch viewRootResponseReceived = new CountDownLatch(1);
        allRootResponseReceived.put(viewId, viewRootResponseReceived);

        final Map<Integer, AtomicLong> allExpectedResponses =
                learningSynchronizer.computeViewMetadata("EXPECTEDRESPONSES", new ConcurrentHashMap<>());
        final AtomicLong viewExpectedResponses = new AtomicLong(0);
        allExpectedResponses.put(viewId, viewExpectedResponses);

        final AtomicBoolean pullLearnerReceiveTasksStarted =
                learningSynchronizer.computeViewMetadata("POOL", new AtomicBoolean(false));
        if (pullLearnerReceiveTasksStarted.compareAndSet(false, true)) {
            // FUTURE WORK: configurable number of tasks
            for (int i = 0; i < 32; i++) {
                final LearnerPullVirtualTreeReceiveTask learnerReceiveTask = new LearnerPullVirtualTreeReceiveTask(
                        reconnectConfig,
                        workGroup,
                        in,
                        views,
                        allExpectedResponses,
                        allRootResponseReceived,
                        completeListener);
                learnerReceiveTask.exec();
            }
        }

        reconstructedRoot.set(root);
        final AtomicBoolean rootRequestSent =
                learningSynchronizer.computeViewMetadata("ROOTREQUESTSENT" + viewId, new AtomicBoolean(false));
        final AtomicBoolean lastPathSent =
                learningSynchronizer.computeViewMetadata("LASTPATHSENT" + viewId, new AtomicBoolean(false));
        // FUTURE WORK: configurable number of tasks
        for (int i = 0; i < 4; i++) {
            final LearnerPullVirtualTreeSendTask learnerSendTask = new LearnerPullVirtualTreeSendTask(
                    reconnectConfig,
                    workGroup,
                    viewId,
                    out,
                    this,
                    viewRootResponseReceived,
                    viewExpectedResponses,
                    rootRequestSent,
                    lastPathSent);
            learnerSendTask.exec();
        }
    }

    public int getViewId() {
        return viewId;
    }

    @Override
    public boolean usesSharedInputQueue() {
        return true;
    }

    /**
     * Determines if a given path refers to a leaf of the tree.
     * @param path a path
     * @return true if leaf, false if internal
     */
    public boolean isLeaf(long path) {
        assert path <= reconnectState.getLastLeafPath();
        return path >= reconnectState.getFirstLeafPath();
    }

    public void setReconnectPaths(final long firstLeafPath, final long lastLeafPath) {
        assert firstNodeResponse : "Root node must be the first node received from the teacher";
        reconnectState.setFirstLeafPath(firstLeafPath);
        reconnectState.setLastLeafPath(lastLeafPath);
        root.prepareReconnectHashing(firstLeafPath, lastLeafPath);
        nodeRemover.setPathInformation(firstLeafPath, lastLeafPath);
        traversalOrder.start(firstLeafPath, lastLeafPath);
        firstNodeResponse = false;
    }

    private final AtomicBoolean lastLeafSent = new AtomicBoolean(false);

    // This method is called concurrently from multiple threads
    long getNextPathToSend() {
        // If the last leaf path request has been sent, don't send anything else
        if (lastLeafSent.get()) {
            return Path.INVALID_PATH;
        }
        final long intPath = traversalOrder.getNextInternalPathToSend();
        if (intPath != Path.INVALID_PATH) {
            assert (intPath < 0) || !isLeaf(intPath);
            return intPath;
        }
        synchronized (this) {
            // If the last leaf path is sent, all subsequent calls to getNextPathToSend()
            // are expected to return INVALID_PATH, so there is no need to check
            // lastLeafPath.get() here again
            final long leafPath = traversalOrder.getNextLeafPathToSend();
            if (leafPath == Path.INVALID_PATH) {
                lastLeafSent.set(true);
            } else {
                assert (leafPath < 0) || isLeaf(leafPath);
                if (leafPath > 0) {
                    anticipatedLeafPaths.add(leafPath);
                }
            }
            return leafPath;
        }
    }

    // This method is called concurrently from multiple threads
    void responseReceived(final PullVirtualTreeResponse response) {
        final long responsePath = response.getPath();
        if ((responsePath == 0) || !isLeaf(responsePath)) {
            handleResponse(response);
        } else {
            responses.put(responsePath, response);
            // Handle responses in the same order as the corresponding requests were sent to the teacher
            while (true) {
                final Long nextExpectedPath = anticipatedLeafPaths.peek();
                if (nextExpectedPath == null) {
                    break;
                }
                final PullVirtualTreeResponse r = responses.remove(nextExpectedPath);
                if (r == null) {
                    break;
                }
                handleResponse(r);
                anticipatedLeafPaths.remove();
            }
        }
    }

    private void handleResponse(final PullVirtualTreeResponse response) {
        assert !firstNodeResponse : "Root node must be the first node received from the teacher";
        final long path = response.getPath();
        if (reconnectState.getLastLeafPath() <= 0) {
            return;
        }
        final boolean isClean = response.isClean();
        final boolean isLeaf = isLeaf(path);
        traversalOrder.nodeReceived(path, isClean);

        if (isLeaf) {
            if (!isClean) {
                final VirtualLeafRecord<K, V> leaf = response.getLeafData();
                assert leaf != null;
                assert path == leaf.getPath();
                nodeRemover.newLeafNode(path, leaf.getKey());
                root.handleReconnectLeaf(leaf); // may block if hashing is slower than ingest
            }
            mapStats.incrementLeafData(1, isClean ? 1 : 0);
        }
    }

    /**
     * Returns the ReconnectMapStats object.
     * @return the ReconnectMapStats object.
     */
    @NonNull
    public ReconnectMapStats getMapStats() {
        return mapStats;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRootOfState() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getOriginalRoot() {
        return ROOT_PATH;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash getNodeHash(final Long originalChild) {
        // The path given is the _ORIGINAL_ child. Each call to this
        // method will be made only for the original state from the original tree.

        // Make sure the path is valid for the original state
        if (originalChild > originalState.getLastLeafPath()) {
            return NULL_HASH;
        }

        final Hash hash = originalRecords.findHash(originalChild);
        // The hash must have been specified by this point. The original tree was hashed before
        // we started running on the learner, so either the hash is in cache or on disk, but it
        // definitely exists at this point. If it is null, something bad happened elsewhere.
        if (hash == null) {
            throw new MerkleSynchronizationException("Node found, but hash was null. path=" + originalChild);
        }
        return hash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void expectLessonFor(
            final Long parent, final int childIndex, final Long original, final boolean nodeAlreadyPresent) {
        throw new UnsupportedOperationException("LearnerPullVirtualTreeView.expectLessonFor()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExpectedLesson<Long> getNextExpectedLesson() {
        throw new UnsupportedOperationException("LearnerPullVirtualTreeView.getNextExpectedLesson()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNextExpectedLesson() {
        throw new UnsupportedOperationException("LearnerPullVirtualTreeView.hasNextExpectedLesson()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long deserializeLeaf(final SerializableDataInputStream in) throws IOException {
        throw new UnsupportedOperationException("LearnerPullVirtualTreeView.deserializeLeaf()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long deserializeInternal(final SerializableDataInputStream in) throws IOException {
        throw new UnsupportedOperationException("LearnerPullVirtualTreeView.deserializeInternal()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        nodeRemover.allNodesReceived();
        root.endLearnerReconnect();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void markForInitialization(final Long node) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void releaseNode(final Long node) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setChild(final Long parent, final int childIndex, final Long child) {
        // No-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long convertMerkleRootToViewType(final MerkleNode node) {
        throw new UnsupportedOperationException("Nested virtual maps not supported");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordHashStats(
            @NonNull final ReconnectMapStats mapStats,
            @NonNull final Long parent,
            final int childIndex,
            final boolean nodeAlreadyPresent) {
        throw new UnsupportedOperationException("The Reconnect Pull Model records the hash stats elsewhere");
    }
}
