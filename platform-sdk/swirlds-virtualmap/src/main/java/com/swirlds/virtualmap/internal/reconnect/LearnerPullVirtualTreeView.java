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
import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.task.ExpectedLesson;
import com.swirlds.common.merkle.synchronization.task.ReconnectNodeCount;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.CustomReconnectRoot;
import com.swirlds.common.merkle.synchronization.views.LearnerTreeView;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.RecordAccessor;
import com.swirlds.virtualmap.internal.VirtualStateAccessor;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
     * Received nodes statistics.
     */
    private ReconnectNodeCount nodeCount;

    /**
     * A {@link RecordAccessor} for getting access to the original records.
     */
    private final RecordAccessor<K, V> originalRecords;

    /**
     * Node traversal order. Defines the order in which node requests will be sent to the teacher.
     */
    private final NodeTraversalOrder traversalOrder;

    /**
     * Indicates if no responses from the teacher have been received yet. The very first response
     * must be for path 0 (root virtual node)
     */
    private boolean firstNodeResponse = true;

    /**
     * True until we have handled our first leaf
     */
    private boolean firstLeaf = true;

    /**
     * Responses from teacher may come in a different order than they are sent by learner. The order
     * is important for hashing, so it's restored using this queue. Once hashing is improved to work
     * with unsorted dirty leaves stream, this code may be cleaned up.
     */
    private final Queue<Long> anticipatedPaths = new ArrayDeque<>();

    /**
     * Related to the queue above. If a response is received out of order, it's temporarily stored
     * in this map.
     */
    private final Map<Long, PullVirtualTreeResponse> responses = new HashMap<>();

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
     */
    public LearnerPullVirtualTreeView(
            final ReconnectConfig reconnectConfig,
            final int viewId,
            final VirtualRootNode<K, V> root,
            final RecordAccessor<K, V> originalRecords,
            final VirtualStateAccessor originalState,
            final VirtualStateAccessor reconnectState,
            final ReconnectNodeRemover<K, V> nodeRemover,
            final NodeTraversalOrder traversalOrder) {
        super(root, originalState, reconnectState);
        this.reconnectConfig = reconnectConfig;
        this.viewId = viewId;
        this.originalRecords = Objects.requireNonNull(originalRecords);
        this.nodeRemover = nodeRemover;
        this.traversalOrder = traversalOrder;
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
        this.nodeCount = learningSynchronizer;

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
        assert traversalOrder != null;
        final LearnerPullVirtualTreeSendTask learnerSendTask = new LearnerPullVirtualTreeSendTask(
                reconnectConfig,
                workGroup,
                viewId,
                out,
                this,
                traversalOrder,
                viewRootResponseReceived,
                viewExpectedResponses);
        learnerSendTask.exec();
    }

    public int getViewId() {
        return viewId;
    }

    @Override
    public boolean usesSharedInputQueue() {
        return true;
    }

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
        traversalOrder.start(firstLeafPath, lastLeafPath, nodeCount);
        firstNodeResponse = false;
    }

    synchronized void responseReceived(final PullVirtualTreeResponse response) {
        responses.put(response.getPath(), response);
        while (!anticipatedPaths.isEmpty()) {
            final long nextExpectedPath = anticipatedPaths.peek();
            final PullVirtualTreeResponse r = responses.get(nextExpectedPath);
            if (r == null) {
                break;
            }
            handleResponse(r);
            anticipatedPaths.remove();
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
            if (firstLeaf) {
                root.prepareForFirstLeaf();
                firstLeaf = false;
            }
            if (!isClean) {
                final VirtualLeafRecord<K, V> leaf = response.getLeafData();
                assert leaf != null;
                assert path == leaf.getPath();
                nodeRemover.newLeafNode(path, leaf.getKey());
                root.handleReconnectLeaf(leaf); // may block if hashing is slower than ingest
            }
        }
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

    synchronized void anticipatePath(final long path) {
        anticipatedPaths.add(path);
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
}
