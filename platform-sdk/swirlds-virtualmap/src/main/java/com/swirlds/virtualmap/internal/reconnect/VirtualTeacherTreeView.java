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

package com.swirlds.virtualmap.internal.reconnect;

import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.virtualmap.internal.Path.INVALID_PATH;
import static com.swirlds.virtualmap.internal.Path.ROOT_PATH;
import static com.swirlds.virtualmap.internal.Path.getLeftChildPath;
import static com.swirlds.virtualmap.internal.Path.getRightChildPath;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.datasource.VirtualRecord;
import com.swirlds.virtualmap.internal.ConcurrentNodeStatusTracker;
import com.swirlds.virtualmap.internal.RecordAccessor;
import com.swirlds.virtualmap.internal.VirtualStateAccessor;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import com.swirlds.virtualmap.internal.pipeline.VirtualPipeline;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An implementation of {@link TeacherTreeView} designed for virtual merkle trees.
 *
 * @param <K>
 * 		The key
 * @param <V>
 * 		The value
 */
public final class VirtualTeacherTreeView<K extends VirtualKey<? super K>, V extends VirtualValue>
        extends VirtualTreeViewBase<K, V> implements TeacherTreeView<Long> {

    private static final Logger logger = LogManager.getLogger(VirtualTeacherTreeView.class);

    /**
     * A queue of the nodes (by path) that we are about to handle. Note that ConcurrentBitSetQueue
     * cleans up after itself in "chunks", such that we don't end up consuming a ton of memory.
     */
    private final ConcurrentBitSetQueue handleQueue = new ConcurrentBitSetQueue();

    /**
     * A queue of the nodes (by path) that we expect responses for.
     */
    private final ConcurrentBitSetQueue expectedResponseQueue = new ConcurrentBitSetQueue();

    /**
     * Keeps track of responses from learner about known, unknown, and not known nodes.
     */
    private final ConcurrentNodeStatusTracker nodeStatusTracker = new ConcurrentNodeStatusTracker(Long.MAX_VALUE);

    /**
     * The {@link RecordAccessor} used for accessing the original map state.
     */
    private RecordAccessor<K, V> records;

    /**
     * This latch counts down when the view is fully initialized and ready for use.
     */
    private final CountDownLatch ready = new CountDownLatch(1);

    /**
     * Create a new {@link VirtualTeacherTreeView}.
     *
     * @param threadManager
     * 		responsible for creating and managing threads
     * @param root
     * 		The root node on the teacher side of the saved state that we are going to reconnect.
     * @param state
     * 		The state of the virtual tree that we are synchronizing.
     * @param pipeline
     * 		The pipeline managing the virtual map.
     */
    public VirtualTeacherTreeView(
            final ThreadManager threadManager,
            final VirtualRootNode<K, V> root,
            final VirtualStateAccessor state,
            final VirtualPipeline pipeline) {

        // There is no distinction between originalState and reconnectState in this implementation
        super(root, state, state);

        threadManager
                .newThreadConfiguration()
                .setRunnable(() -> {
                    records = pipeline.detachCopy(root);
                    ready.countDown();
                })
                .setComponent("virtualmap")
                .setThreadName("detacher")
                .build()
                .start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitUntilReady() throws InterruptedException {
        ready.await();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getRoot() {
        return ROOT_PATH;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addToHandleQueue(final Long node) {
        checkValidNode(node, reconnectState);
        handleQueue.add(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getNextNodeToHandle() {
        return handleQueue.remove();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean areThereNodesToHandle() {
        return !handleQueue.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getChildAndPrepareForQueryResponse(final Long parent, final int childIndex) {
        final long child = getChild(parent, childIndex);
        expectedResponseQueue.add(child);
        return child;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getNodeForNextResponse() {
        return expectedResponseQueue.remove();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isResponseExpected() {
        return !expectedResponseQueue.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerResponseForNode(final Long node, final boolean learnerHasNode) {
        final ConcurrentNodeStatusTracker.Status status = learnerHasNode
                ? ConcurrentNodeStatusTracker.Status.KNOWN
                : ConcurrentNodeStatusTracker.Status.NOT_KNOWN;
        nodeStatusTracker.set(node, status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasLearnerConfirmedFor(final Long node) {
        return nodeStatusTracker.getStatus(node) == ConcurrentNodeStatusTracker.Status.KNOWN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serializeLeaf(final SerializableDataOutputStream out, final Long leaf) throws IOException {
        checkValidLeaf(leaf, reconnectState);
        final VirtualLeafRecord<K, V> leafRecord = records.findLeafRecord(leaf, false);
        assert leafRecord != null : "Unexpected null leaf record at path=" + leaf;
        out.writeSerializable(leafRecord, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serializeInternal(final SerializableDataOutputStream out, final Long internal) throws IOException {
        checkValidInternal(internal, reconnectState);
        // We don't need to really serialize anything here, except the learner needs a long. So we'll send a long.
        out.writeLong(internal); // <--- works for VIRTUAL_MAP or any virtual internal node
        if (internal == ROOT_PATH) {
            out.writeLong(reconnectState.getFirstLeafPath());
            out.writeLong(reconnectState.getLastLeafPath());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Hash> getChildHashes(final Long parent) {
        checkValidInternal(parent, reconnectState);
        if (parent > ROOT_PATH || (parent == ROOT_PATH && reconnectState.getLastLeafPath() > 1)) {
            final long leftPath = getLeftChildPath(parent);
            final long rightPath = getRightChildPath(parent);
            final VirtualRecord leftLeaf = records.findRecord(leftPath);
            final VirtualRecord rightLeaf = records.findRecord(rightPath);
            final Hash leftHash = leftLeaf == null ? null : leftLeaf.getHash();
            final Hash rightHash = rightLeaf == null ? null : rightLeaf.getHash();
            if (leftHash == null && rightHash == null) {
                throw new MerkleSynchronizationException("Both children had null hashes at paths " + leftPath + " and "
                        + rightPath + " for parent " + parent);
            }
            return Arrays.asList(leftHash, rightHash);
        } else if (parent == ROOT_PATH && reconnectState.getLastLeafPath() == 1) {
            final VirtualLeafRecord<K, V> leaf = records.findLeafRecord(1, false);
            final Hash leafHash = leaf == null ? null : leaf.getHash();
            if (leafHash == null) {
                throw new MerkleSynchronizationException("Null leaf hash for path = 1");
            }
            return List.of(leafHash);
        } else if (parent == ROOT_PATH && reconnectState.getLastLeafPath() == INVALID_PATH) {
            return Collections.emptyList();
        } else {
            throw new MerkleSynchronizationException("Unexpected parent " + parent);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCustomReconnectRoot(final Long node) {
        return node == ROOT_PATH;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        try {
            waitUntilReady();
            records.getDataSource().close();
        } catch (final IOException e) {
            logger.error(RECONNECT.getMarker(), "interrupted while attempting to close data source");
        } catch (final InterruptedException e) {
            logger.error(RECONNECT.getMarker(), "Failed to close data source properly", e);
            Thread.currentThread().interrupt();
        }
    }
}
