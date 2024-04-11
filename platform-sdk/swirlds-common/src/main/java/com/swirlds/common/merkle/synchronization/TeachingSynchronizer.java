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
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.SocketException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Performs synchronization in the role of the teacher.
 */
public class TeachingSynchronizer {

    private static final String WORK_GROUP_NAME = "teaching-synchronizer";

    private static final Logger logger = LogManager.getLogger(TeachingSynchronizer.class);

    /**
     * Used to get data from the listener.
     */
    private final MerkleDataInputStream inputStream;

    /**
     * Used to transmit data to the listener.
     */
    private final MerkleDataOutputStream outputStream;

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

    private final Runnable breakConnection;

    /**
     * Responsible for creating and managing threads used by this object.
     */
    private final ThreadManager threadManager;

    protected final ReconnectConfig reconnectConfig;

    private final Time time;

    /*
     * During reconnect, all merkle sub-trees get unique IDs, which are used to dispatch
     * messages to correct teacher/learner tree views, when multiple sub-trees are synced
     * in parallel. This generator is used to generate these IDs, and a similar one exists
     * on the learner side.
     */
    private final AtomicInteger viewIdGen = new AtomicInteger(0);

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
        this.threadManager = Objects.requireNonNull(threadManager, "threadManager must not be null");
        inputStream = Objects.requireNonNull(in, "in must not be null");
        outputStream = Objects.requireNonNull(out, "out must not be null");

        subtrees = new LinkedList<>();
        subtrees.add(new TeacherSubtree(configuration, root));

        this.breakConnection = breakConnection;
        this.reconnectConfig = Objects.requireNonNull(reconnectConfig, "reconnectConfig must not be null");
    }

    /**
     * Perform synchronization in the role of the teacher.
     */
    public void synchronize() throws InterruptedException {
        try {
            while (!subtrees.isEmpty()) {
                final Set<TeacherSubtree> toSync = new HashSet<>();
                int count = 0;
                while (!subtrees.isEmpty() && (count < reconnectConfig.maxParallelSubtrees())) {
                    toSync.add(subtrees.remove());
                    count++;
                }
                sendTreesInParallel(toSync);
            }
        } finally {
            // If we crash, make sure to clean up any remaining subtrees.
            for (final TeacherSubtree subtree : subtrees) {
                subtree.close();
            }
        }
    }

    private void sendTreesInParallel(final Set<TeacherSubtree> toSend) throws InterruptedException {
        final AtomicReference<Throwable> firstReconnectException = new AtomicReference<>();
        final Function<Throwable, Boolean> reconnectExceptionListener = ex -> {
            Throwable cause = ex;
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
            firstReconnectException.compareAndSet(null, ex);
            // Let StandardWorkGroup log it as an error using the EXCEPTION marker
            return false;
        };
        // A future improvement might be to reuse threads between subtrees.
        final StandardWorkGroup workGroup =
                new StandardWorkGroup(threadManager, WORK_GROUP_NAME, breakConnection, reconnectExceptionListener);

        final AsyncInputStream in = new AsyncInputStream(inputStream, workGroup, reconnectConfig);
        final AsyncOutputStream out = buildOutputStream(workGroup, outputStream);

        final AtomicInteger running = new AtomicInteger(toSend.size());
        for (final TeacherSubtree subtree : toSend) {
            final MerkleNode root = subtree.getRoot();
            final String route = root == null ? "[]" : root.getRoute().toString();
            final TeacherTreeView<?> view = subtree.getView();
            view.waitUntilReady();
            logger.info(RECONNECT.getMarker(), "Sending tree rooted with route {}", route);
            final int viewId = viewIdGen.getAndIncrement();
            view.startTeacherTasks(this, viewId, time, workGroup, in, out, subtrees, success -> {
                if (success) {
                    logger.info(RECONNECT.getMarker(), "Finished sending tree with route {}", route);
                } else {
                    logger.error(RECONNECT.getMarker(), "Failed to send tree with route {}", route);
                }
                if (running.decrementAndGet() == 0) {
                    in.close();
                    out.close();
                }
            });
        }

        in.start();
        out.start();

        workGroup.waitForTermination();

        // Tree views can only be closed after async streams are finished. If a view is closed first,
        // requests to serialize/deserialize a node issued on the async threads may fail
        toSend.forEach(TeacherSubtree::close);

        if (workGroup.hasExceptions()) {
            in.abort();
            throw new MerkleSynchronizationException(
                    "Synchronization failed with exceptions", firstReconnectException.get());
        }
    }

    /**
     * Build the output stream. Exposed to allow unit tests to override implementation to simulate latency.
     */
    public <T extends SelfSerializable> AsyncOutputStream buildOutputStream(
            final StandardWorkGroup workGroup, final SerializableDataOutputStream out) {
        return new AsyncOutputStream(out, workGroup, reconnectConfig);
    }
}
