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

import static com.swirlds.logging.legacy.LogMarker.RECONNECT;
import static com.swirlds.virtualmap.internal.Path.ROOT_PATH;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.task.ExpectedLesson;
import com.swirlds.common.merkle.synchronization.task.ReconnectNodeCount;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.LearnerTreeView;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.Path;
import com.swirlds.virtualmap.internal.RecordAccessor;
import com.swirlds.virtualmap.internal.VirtualStateAccessor;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An implementation of {@link LearnerTreeView} for the virtual merkle. The learner during reconnect
 * needs access both to the original state and records, and the current reconnect state and records.
 * This implementation uses {@link Long} as the representation of a node and corresponds directly
 * to the path of the node.
 *
 * @param <K>
 * 		The key
 * @param <V>
 * 		The value
 */
public final class LearnerPullVirtualTreeView<K extends VirtualKey, V extends VirtualValue>
        extends VirtualTreeViewBase<K, V> implements VirtualLearnerTreeView {

    private static final Logger logger = LogManager.getLogger(LearnerPullVirtualTreeView.class);

    /**
     * A stashed null hash, which is used for any leaves which are null that we need to send
     * (specifically, leaf 2 for a tree with only a single leaf).
     */
    private static final Hash NULL_HASH = CryptographyHolder.get().getNullHash();

    private final ReconnectConfig reconnectConfig;

    private AsyncInputStream<PullVirtualTreeResponse> in;

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

    private NodeTraversalOrder traversalOrder;

    private final AtomicLong expectedResponses = new AtomicLong(0);

    private boolean firstNode = true;

    /**
     * True until we have handled our first leaf
     */
    private boolean firstLeaf = true;

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
            final VirtualRootNode<K, V> root,
            final RecordAccessor<K, V> originalRecords,
            final VirtualStateAccessor originalState,
            final VirtualStateAccessor reconnectState,
            final ReconnectNodeRemover<K, V> nodeRemover) {
        super(root, originalState, reconnectState);
        this.reconnectConfig = reconnectConfig;
        this.originalRecords = Objects.requireNonNull(originalRecords);
        this.nodeRemover = nodeRemover;
    }

    @Override
    public void setNodeTraveralOrder(final NodeTraversalOrder traversalOrder) {
        this.traversalOrder = traversalOrder;
    }

    @Override
    public void startLearnerTasks(
            final StandardWorkGroup workGroup,
            final MerkleDataInputStream inputStream,
            final MerkleDataOutputStream outputStream,
            final Queue<MerkleNode> rootsToReceive,
            final AtomicReference<Long> reconstructedRoot,
            final ReconnectNodeCount nodeCount) {
        this.nodeCount = nodeCount;

        in = new AsyncInputStream<>(inputStream, workGroup, () -> new PullVirtualTreeResponse(this), reconnectConfig);
        in.start();

        final AtomicBoolean senderIsFinished = new AtomicBoolean();
        final CountDownLatch rootResponseReceived = new CountDownLatch(1);

        final LearnerPullVirtualTreeReceiveTask learnerReceiveTask = new LearnerPullVirtualTreeReceiveTask(
                workGroup, in, this, senderIsFinished, expectedResponses, rootResponseReceived);
        learnerReceiveTask.exec();
        reconstructedRoot.set(0L);
        assert traversalOrder != null;
        final LearnerPullVirtualTreeSendTask learnerSendTask = new LearnerPullVirtualTreeSendTask(
                workGroup, outputStream, this, traversalOrder, senderIsFinished, rootResponseReceived);
        learnerSendTask.exec();
    }

    @Override
    public void abort() {
        in.abort();
    }

    private boolean isLeaf(long path) {
        assert path <= reconnectState.getLastLeafPath();
        return path >= reconnectState.getFirstLeafPath();
    }

    private static int completelyRead(final InputStream in, final byte[] dst) throws IOException {
        int totalBytesRead = 0;
        while (totalBytesRead < dst.length) {
            final int bytesRead = in.read(dst, totalBytesRead, dst.length - totalBytesRead);
            if (bytesRead < 0) {
                // Reached EOF
                break;
            }
            totalBytesRead += bytesRead;
        }
        return totalBytesRead;
    }

    @Override
    public void readNode(final SerializableDataInputStream in, final long path) throws IOException {
        if (path == Path.ROOT_PATH) {
            final long firstLeafPath = in.readLong();
            final long lastLeafPath = in.readLong();
            if (firstNode) {
                reconnectState.setFirstLeafPath(firstLeafPath);
                reconnectState.setLastLeafPath(lastLeafPath);
                root.prepareReconnectHashing(firstLeafPath, lastLeafPath);
                nodeRemover.setPathInformation(firstLeafPath, lastLeafPath);
                traversalOrder.start(firstLeafPath, lastLeafPath, nodeCount);
                firstNode = false;
                if (lastLeafPath <= 0) {
                    return;
                }
            }
        }
        assert !firstNode : "Root node must be the first node received from the teacher";
        final Hash hash = new Hash(DigestType.SHA_384);
        if (completelyRead(in, hash.getValue()) != DigestType.SHA_384.digestLength()) {
            throw new IOException("Failed to read node hash from the teacher");
        }
        final boolean isLeaf = isLeaf(path);
        final boolean isClean = traversalOrder.nodeReceived(path, hash);
        if (isLeaf) {
            if (firstLeaf) {
                root.prepareForFirstLeaf();
                firstLeaf = false;
            }
            final VirtualLeafRecord<K, V> leaf = in.readSerializable(false, VirtualLeafRecord::new);
            if (!isClean) {
                nodeRemover.newLeafNode(path, leaf.getKey());
                root.handleReconnectLeaf(leaf); // may block if hashing is slower than ingest
            }
        }
    }

    @Override
    public void anticipateMesssage() {
        expectedResponses.incrementAndGet();
        in.anticipateMessage();
    }

    @Override
    public void applySendBackpressure() throws InterruptedException {
        final long t = expectedResponses.get();
        if (t > 4096) {
            Thread.sleep(1);
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

        // If the originalChild is null, then it means we're outside the range of valid nodes, and we will
        // return a NULL_HASH.
        if (originalChild == null) {
            return NULL_HASH;
        }

        // Make sure the path is valid for the original state
        checkValidNode(originalChild, originalState);
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
}
