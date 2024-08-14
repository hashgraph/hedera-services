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

import static com.swirlds.logging.legacy.LogMarker.RECONNECT;

import com.swirlds.base.time.Time;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.task.TeacherSubtree;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.CustomReconnectRoot;
import com.swirlds.common.merkle.synchronization.views.TeacherPushMerkleTreeView;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.SocketException;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Performs synchronization in the role of the teacher.
 */
public class TeachingSynchronizer {

    private static final String WORK_GROUP_NAME = "teaching-synchronizer";

    private static final Logger logger = LogManager.getLogger(TeachingSynchronizer.class);

    private final StandardWorkGroup workGroup;

    private volatile Throwable firstReconnectException;

    /**
     * Used to get data from the listener.
     */
    private final MerkleDataInputStream inputStream;

    private volatile AsyncInputStream in;

    /**
     * Used to transmit data to the listener.
     */
    private final MerkleDataOutputStream outputStream;

    private volatile AsyncOutputStream out;

    /**
     * <p>
     * Subtrees that require reconnect using a custom view.
     * </p>
     *
     * <p>
     * Although multiple threads may modify this queue, it is still thread safe. This is because only one thread will
     * attempt to read/write this data structure at any time, and when the thread touching the queue changes there is a
     * synchronization point that establishes a happens before relationship.
     * </p>
     */
    private final Queue<TeacherSubtree> subtrees;

    /**
     * Subtree views, by view ID.
     */
    private final Map<Integer, TeacherTreeView<?>> views;

    private final Map<Integer, TeacherSubtree> subtreesInProgress = new ConcurrentHashMap<>();

    private final Map<String, Object> viewMetadata = new ConcurrentHashMap<>();

    protected final ReconnectConfig reconnectConfig;

    private final Time time;

    /*
     * During reconnect, all merkle subtrees get unique IDs, which are used to dispatch
     * messages to correct teacher/learner tree views, when multiple sub-trees are synced
     * in parallel. This generator is used to generate these IDs, and a similar one exists
     * on the learner side.
     */
    private final AtomicInteger viewIdGen = new AtomicInteger(0);

    /**
     * Number of subtrees being synchronized. May not be greater than {@link ReconnectConfig#maxParallelSubtrees()}
     * Once this number reaches zero, synchronization is complete.
     */
    private final AtomicInteger viewsInProgress = new AtomicInteger(0);

    /**
     * Create a new teaching synchronizer.
     *
     * @param configuration   the configuration
     * @param threadManager   responsible for managing thread lifecycles
     * @param in              the input stream
     * @param out             the output stream
     * @param root            the root of the tree
     * @param breakConnection a method that breaks the connection. Used iff an exception is encountered. Prevents
     *                        deadlock if there is a thread stuck on a blocking IO operation that will never finish due
     *                        to a failure.
     * @param reconnectConfig reconnect configuration from platform
     */
    public TeachingSynchronizer(
            @NonNull final Configuration configuration,
            @NonNull final Time time,
            @NonNull final ThreadManager threadManager,
            @NonNull final MerkleDataInputStream in,
            @NonNull final MerkleDataOutputStream out,
            @NonNull final MerkleNode root,
            @Nullable final Runnable breakConnection,
            @NonNull final ReconnectConfig reconnectConfig) {

        this.time = Objects.requireNonNull(time);
        inputStream = Objects.requireNonNull(in, "in must not be null");
        outputStream = Objects.requireNonNull(out, "out must not be null");

        views = new ConcurrentHashMap<>();
        final int viewId = viewIdGen.getAndIncrement();
        final TeacherTreeView<?> view = new TeacherPushMerkleTreeView(configuration, root);
        views.put(viewId, view);
        subtrees = new ConcurrentLinkedQueue<>();
        final TeacherSubtree rootSubtree = new TeacherSubtree(root, viewId, view);
        subtrees.add(rootSubtree);

        this.reconnectConfig = Objects.requireNonNull(reconnectConfig, "reconnectConfig must not be null");

        final Function<Throwable, Boolean> reconnectExceptionListener = e -> {
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof SocketException socketEx) {
                    if (socketEx.getMessage().equalsIgnoreCase("Connection reset by peer")) {
                        // Connection issues during reconnects are expected and recoverable, just
                        // log them as info. All other exceptions should be treated as real errors
                        logger.info(RECONNECT.getMarker(), "Connection reset while sending tree. Aborting");
                        return true;
                    }
                }
                cause = cause.getCause();
            }
            if (firstReconnectException == null) {
                firstReconnectException = e;
            }
            // Let StandardWorkGroup log it as an error using the EXCEPTION marker
            return false;
        };
        // A future improvement might be to reuse threads between subtrees.
        workGroup = createStandardWorkGroup(threadManager, breakConnection, reconnectExceptionListener);
    }

    /**
     * Perform synchronization in the role of the teacher.
     */
    public void synchronize() throws InterruptedException {
        in = new AsyncInputStream(inputStream, workGroup, reconnectConfig);
        in.start();
        out = buildOutputStream(workGroup, in::isAlive, outputStream);
        out.start();

        final boolean rootScheduled = synchronizeNextSubtree(workGroup, in, out);
        assert rootScheduled;

        InterruptedException interruptException = null;
        try {
            workGroup.waitForTermination();
        } catch (final InterruptedException e) { // NOSONAR: Exception is rethrown below after cleanup.
            interruptException = e;
            logger.warn(RECONNECT.getMarker(), "interrupted while waiting for work group termination");
        } finally {
            // If we crash, make sure to clean up any remaining subtrees ...
            for (final TeacherSubtree subtree : subtrees) {
                subtree.close();
            }
            // ... and all synchronized subtrees
            for (final TeacherSubtree toClean : subtreesInProgress.values()) {
                toClean.close();
            }
        }

        if ((interruptException != null) || workGroup.hasExceptions()) {
            in.abort();
            if (interruptException != null) {
                throw interruptException;
            }
            throw new MerkleSynchronizationException("Synchronization failed with exceptions", firstReconnectException);
        }
    }

    private void reconnectRootEncountered(final CustomReconnectRoot<?, ?> root) {
        final int viewId = viewIdGen.getAndIncrement();
        final TeacherTreeView<?> view = root.buildTeacherView(reconnectConfig);
        final TeacherSubtree subtree = new TeacherSubtree(root, viewId, view);
        subtrees.add(subtree);
        views.put(viewId, view);
        if (view.usesSharedInputQueue()) {
            in.setNeedsSharedQueue(viewId);
        }
    }

    private synchronized boolean synchronizeNextSubtree(
            final StandardWorkGroup workGroup, final AsyncInputStream in, final AsyncOutputStream out) {
        if (viewsInProgress.incrementAndGet() > reconnectConfig.maxParallelSubtrees()) {
            // Max number of views is already being synchronized
            viewsInProgress.decrementAndGet();
            return false;
        }

        final TeacherSubtree subtree = subtrees.poll(); // NOSONAR: the subtree is closed later
        if (subtree == null) {
            // Nothing to sync yet
            viewsInProgress.decrementAndGet();
            return false;
        }

        final MerkleNode root = subtree.getRoot();
        final String route = root == null ? "[]" : root.getRoute().toString();
        final TeacherTreeView<?> view = subtree.getView();
        final int viewId = subtree.getViewId();
        logger.info(RECONNECT.getMarker(), "Sending tree rooted with route {}, view={}", route, viewId);
        subtreesInProgress.put(viewId, subtree);
        final Consumer<Integer> completeListener = id -> {
            final TeacherSubtree st = subtreesInProgress.get(id);
            assert st != null : "Unknown subtree: " + id;
            final MerkleNode rt = st.getRoot();
            final String rte = rt == null ? "[]" : rt.getRoute().toString();
            logger.info(RECONNECT.getMarker(), "Finished sending tree with route {}", rte);

            viewsInProgress.decrementAndGet();
            boolean nextViewScheduled = synchronizeNextSubtree(workGroup, in, out);
            while (nextViewScheduled) {
                nextViewScheduled = synchronizeNextSubtree(workGroup, in, out);
            }
        };
        view.startTeacherTasks(
                this, viewId, time, workGroup, in, out, this::reconnectRootEncountered, views, completeListener);
        return true;
    }

    @SuppressWarnings("unchecked")
    public <V> V computeViewMetadata(final String key, final V defaultValue) {
        return (V) viewMetadata.compute(key, (k, v) -> (v != null) ? v : defaultValue);
    }

    protected StandardWorkGroup createStandardWorkGroup(
            ThreadManager threadManager, Runnable breakConnection, Function<Throwable, Boolean> exceptionListener) {
        return new StandardWorkGroup(threadManager, WORK_GROUP_NAME, breakConnection, exceptionListener);
    }

    /**
     * Build the output stream. Exposed to allow unit tests to override implementation to simulate latency.
     */
    public <T extends SelfSerializable> AsyncOutputStream buildOutputStream(
            final StandardWorkGroup workGroup, final Supplier<Boolean> alive, final SerializableDataOutputStream out) {
        return new AsyncOutputStream(out, workGroup, alive, reconnectConfig);
    }
}
