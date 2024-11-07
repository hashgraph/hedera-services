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
import static com.swirlds.virtualmap.internal.Path.ROOT_PATH;

import com.swirlds.base.time.Time;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.synchronization.TeachingSynchronizer;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.CustomReconnectRoot;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.RecordAccessor;
import com.swirlds.virtualmap.internal.VirtualStateAccessor;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import com.swirlds.virtualmap.internal.pipeline.VirtualPipeline;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An implementation of {@link TeacherTreeView} designed for virtual merkle trees.
 *
 * <p>This learner tree view creates two tasks running in the provided work group. One task
 * is responsible for sending requests to the teacher, the other one receives responses. Once
 * both tasks are completed, the corresponding virtual map is fully synchronized with the
 * teacher.
 *
 * <p>This implementation is supposed to work with {@link LearnerPullVirtualTreeView} on the
 * learner side.
 *
 * @param <K>
 * 		The key
 * @param <V>
 * 		The value
 */
public class TeacherPullVirtualTreeView<K extends VirtualKey, V extends VirtualValue> extends VirtualTreeViewBase<K, V>
        implements TeacherTreeView<Long> {

    private static final Logger logger = LogManager.getLogger(TeacherPullVirtualTreeView.class);

    private final ReconnectConfig reconnectConfig;

    /**
     * The {@link RecordAccessor} used for accessing the original map state.
     */
    private volatile RecordAccessor<K, V> records;

    /**
     * This latch counts down when the view is fully initialized and ready for use.
     */
    private final CountDownLatch ready = new CountDownLatch(1);

    /**
     * Create a new {@link TeacherPullVirtualTreeView}.
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
    public TeacherPullVirtualTreeView(
            final ThreadManager threadManager,
            final ReconnectConfig reconnectConfig,
            final VirtualRootNode<K, V> root,
            final VirtualStateAccessor state,
            final VirtualPipeline pipeline) {
        // There is no distinction between originalState and reconnectState in this implementation
        super(root, state, state);
        this.reconnectConfig = reconnectConfig;
        prepareReady(threadManager, pipeline);
    }

    public void prepareReady(final ThreadManager threadManager, final VirtualPipeline pipeline) {
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

    @Override
    public void startTeacherTasks(
            final TeachingSynchronizer teachingSynchronizer,
            final int viewId,
            final Time time,
            final StandardWorkGroup workGroup,
            final AsyncInputStream in,
            final AsyncOutputStream out,
            final Consumer<CustomReconnectRoot<?, ?>> subtreeListener,
            final Map<Integer, TeacherTreeView<?>> views,
            final Consumer<Integer> completeListener) {
        final AtomicInteger teacherTasksRunning =
                teachingSynchronizer.computeViewMetadata("TasksRunning", new AtomicInteger(0));
        final Set<Integer> viewsInProgress =
                teachingSynchronizer.computeViewMetadata("ViewsInProgress", ConcurrentHashMap.newKeySet());
        viewsInProgress.add(viewId);
        final AtomicBoolean pullTeacherTasksStarted =
                teachingSynchronizer.computeViewMetadata("POOL", new AtomicBoolean(false));
        if (pullTeacherTasksStarted.compareAndSet(false, true)) {
            // FUTURE work: pool size config
            for (int i = 0; i < 32; i++) {
                teacherTasksRunning.incrementAndGet();
                final TeacherPullVirtualTreeReceiveTask teacherReceiveTask = new TeacherPullVirtualTreeReceiveTask(
                        reconnectConfig,
                        workGroup,
                        in,
                        out,
                        views,
                        completeListener,
                        teacherTasksRunning,
                        viewsInProgress);
                teacherReceiveTask.exec();
            }
        }
    }

    @Override
    public boolean usesSharedInputQueue() {
        return true;
    }

    public boolean isLeaf(final long path) {
        return (path >= reconnectState.getFirstLeafPath())
                && (path <= reconnectState.getLastLeafPath())
                && (reconnectState.getFirstLeafPath() > 0);
    }

    /**
     * Read the virtual node hash identified by a given path.
     *
     * @param path the virtual path
     * @return the virtual node hash
     */
    public Hash loadHash(final long path) {
        if (closed.get()) {
            throw new MerkleSynchronizationException("View is closed");
        }
        return records.findHash(path);
    }

    public VirtualLeafRecord<K, V> loadLeaf(final long path) {
        if (closed.get()) {
            throw new MerkleSynchronizationException("View is closed");
        }
        return records.findLeafRecord(path, false);
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
        throw new UnsupportedOperationException("TeacherPullVirtualTreeView.addToHandleQueue()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getNextNodeToHandle() {
        throw new UnsupportedOperationException("TeacherPullVirtualTreeView.getNextNodeToHandle()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean areThereNodesToHandle() {
        throw new UnsupportedOperationException("TeacherPullVirtualTreeView.areThereNodesToHandle()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getChildAndPrepareForQueryResponse(final Long parent, final int childIndex) {
        throw new UnsupportedOperationException("TeacherPullVirtualTreeView.getChildAndPrepareForQueryResponse()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getNodeForNextResponse() {
        throw new UnsupportedOperationException("TeacherPullVirtualTreeView.getNodeForNextResponse()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isResponseExpected() {
        throw new UnsupportedOperationException("TeacherPullVirtualTreeView.isResponseExpected()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerResponseForNode(final Long node, final boolean learnerHasNode) {
        throw new UnsupportedOperationException("TeacherPullVirtualTreeView.registerResponseForNode()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasLearnerConfirmedFor(final Long node) {
        throw new UnsupportedOperationException("TeacherPullVirtualTreeView.hasLearnerConfirmedFor()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serializeLeaf(final SerializableDataOutputStream out, final Long leaf) throws IOException {
        throw new UnsupportedOperationException("TeacherPullVirtualTreeView.serializeLeaf()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serializeInternal(final SerializableDataOutputStream out, final Long internal) throws IOException {
        throw new UnsupportedOperationException("TeacherPullVirtualTreeView.serializeInternal()");
    }

    @Override
    public void writeChildHashes(final Long parent, final SerializableDataOutputStream out) throws IOException {
        throw new UnsupportedOperationException("TeacherPullVirtualTreeView.writeChildHashes()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCustomReconnectRoot(final Long node) {
        return node == ROOT_PATH;
    }

    public final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        closed.set(true);
        try {
            try {
                waitUntilReady();
            } finally {
                // If the current thread is interrupted, waitUntilReady() above throws an interrupted
                // exception. This is why the data source is closed in the "finally" block
                records.getDataSource().close();
            }
        } catch (final IOException e) {
            logger.error(EXCEPTION.getMarker(), "interrupted while attempting to close data source");
        } catch (final InterruptedException e) {
            logger.error(EXCEPTION.getMarker(), "Failed to close data source properly", e);
            Thread.currentThread().interrupt();
        }
    }
}
