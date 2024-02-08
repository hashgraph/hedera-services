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

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.virtualmap.internal.Path.INVALID_PATH;
import static com.swirlds.virtualmap.internal.Path.ROOT_PATH;
import static com.swirlds.virtualmap.internal.Path.getLeftChildPath;
import static com.swirlds.virtualmap.internal.Path.getRightChildPath;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.ConcurrentNodeStatusTracker;
import com.swirlds.virtualmap.internal.RecordAccessor;
import com.swirlds.virtualmap.internal.VirtualStateAccessor;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import com.swirlds.virtualmap.internal.pipeline.VirtualPipeline;
import java.io.IOException;
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
public final class VirtualTeacherTreeView<K extends VirtualKey, V extends VirtualValue>
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

    /** A lock to monitor the size of the expectedResponseQueue for throttling. */
    private final Object expectedResponseQueueSizeLock = new Object();

    /**
     * The maximum size of the expectedResponseQueue. Sending more nodes to the learner is throttled
     * if the size exceeds this number. The queue size will subsequently become lower as we keep
     * receiving responses from the learner, and then the teacher will resume sending nodes to the learner.
     * <p>
     * The value may need tuning. Too large a value would effectively remove the throttle and produce
     * virtually no effect on the existing reconnect behavior because the learner would be able to
     * keep up with the teacher and promptly send responses for every sent node, thus keeping the queue size
     * below the threshold at all times.
     * Too small a value will significantly minimize the amount of I/O (and as a side effect, reduce memory usage)
     * because the teacher would only ever send nodes that the learner has reported it doesn't know them,
     * but at the same time, the small value would introduce too many delays between sending nodes
     * to the learner thus potentially not utilizing the available resources (network, disk) efficiently
     * and hence increasing the overall time the reconnect takes.
     * <p>
     * Valid values: >= 1.
     */
    private static final long MAX_EXPECTED_RESPONSE_QUEUE_SIZE = 1000;

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

        new ThreadConfiguration(threadManager)
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
        throttleOnExpectedResponseQueueSize();
        return handleQueue.remove();
    }

    /** Check if the size of the expectedResponseQueue is reasonably small. */
    private boolean isExpectedResponseQueueSizeSmall() {
        return expectedResponseQueue.size() <= MAX_EXPECTED_RESPONSE_QUEUE_SIZE;
    }

    /**
     * Check the size of the expectedResponseQueue, and if it isn't small enough,
     * then wait until it gets lower.
     * <p>
     * This helps to:
     * <li>1. Avoid overwhelming the learner with new nodes while it hasn't responded about the old ones yet.
     * <li>2. Ensure the teacher gets enough responses about previously sent nodes to avoid sending child nodes
     *    for which the learner has reported/would report that it knows their parents already.
     * <p><p>
     * This should help reduce I/O (both disk and network) and memory pressure on both the teacher
     * and the learner. In the best case scenario, this should improve the reconnect performance overall.
     * In the worst case scenario, the reconnect performance should remain not worse than today, but
     * the teacher would still free up some resources for doing its regular, reconnect-unrelated work.
     */
    private void throttleOnExpectedResponseQueueSize() {
        if (isExpectedResponseQueueSizeSmall()) return;
        try {
            synchronized (expectedResponseQueueSizeLock) {
                while (!isExpectedResponseQueueSizeSmall()) {
                    expectedResponseQueueSizeLock.wait();
                }
            }
        } catch (InterruptedException ignore) {
            // It's okay to ignore. We'll just send one more node to the learner.
            // The next call will block and wait again if the queue size is still large.
        }
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
        synchronized (expectedResponseQueueSizeLock) {
            final Long node = expectedResponseQueue.remove();
            if (isExpectedResponseQueueSizeSmall()) {
                expectedResponseQueueSizeLock.notify();
            }
            return node;
        }
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
        out.writeSerializable(leafRecord, false);
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

    @Override
    public void writeChildHashes(final Long parent, final SerializableDataOutputStream out) throws IOException {
        checkValidInternal(parent, reconnectState);
        if (parent == ROOT_PATH && reconnectState.getLastLeafPath() == INVALID_PATH) {
            // out.writeSerializableList() writes just a single int if the list is empty
            out.writeInt(0);
            return;
        }
        final int size;
        if (parent > ROOT_PATH || (parent == ROOT_PATH && reconnectState.getLastLeafPath() > 1)) {
            size = 2;
        } else if (parent == ROOT_PATH && reconnectState.getLastLeafPath() == 1) {
            size = 1;
        } else {
            throw new MerkleSynchronizationException("Unexpected parent " + parent);
        }
        out.writeInt(size);
        // All same class? true
        out.writeBoolean(true);

        final long leftPath = getLeftChildPath(parent);
        // Is null? false
        out.writeBoolean(false);
        // Class version is written for the first entry only
        out.writeInt(Hash.CLASS_VERSION);
        // Write hash in SelfSerializable format
        if (!records.findAndWriteHash(leftPath, out)) {
            throw new MerkleSynchronizationException("Null hash for path = " + leftPath);
        }

        if (size == 2) {
            final long rightPath = getRightChildPath(parent);
            // Is null? false
            out.writeBoolean(false);
            // Class version is not written
            // Write hash in SelfSerializable format
            if (!records.findAndWriteHash(rightPath, out)) {
                throw new MerkleSynchronizationException("Null hash for path = " + rightPath);
            }
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
            logger.error(EXCEPTION.getMarker(), "interrupted while attempting to close data source");
        } catch (final InterruptedException e) {
            logger.error(EXCEPTION.getMarker(), "Failed to close data source properly", e);
            Thread.currentThread().interrupt();
        }
    }
}
