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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.exceptions.MerkleSerializationException;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.Path;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A task running on the teacher side, which is responsible for processing requests from the
 * learner. For every request, a response is sent to the provided async output stream. Async
 * streams serialize objects to the underlying output streams in a separate thread. This is
 * where the provided hash from the learner is compared with the corresponding hash on the
 * teacher.
 */
public class TeacherPullVirtualTreeReceiveTask {

    private static final Logger logger = LogManager.getLogger(TeacherPullVirtualTreeReceiveTask.class);

    private static final String NAME = "reconnect-teacher-receiver";

    private final StandardWorkGroup workGroup;
    private final AsyncInputStream in;
    private final AsyncOutputStream out;
    private final Map<Integer, TeacherTreeView<?>> views;

    private final Consumer<Integer> completeListener;

    private final AtomicInteger tasksRunning;
    private final Set<Integer> viewsInProgress;

    /**
     * Create new thread that will send data lessons and queries for a subtree.
     *
     * @param reconnectConfig       the configuration for reconnect
     * @param workGroup             the work group managing the reconnect
     * @param in                    the input stream
     * @param out                   the output stream
     */
    public TeacherPullVirtualTreeReceiveTask(
            @NonNull final ReconnectConfig reconnectConfig,
            final StandardWorkGroup workGroup,
            final AsyncInputStream in,
            final AsyncOutputStream out,
            final Map<Integer, TeacherTreeView<?>> views,
            final Consumer<Integer> completeListener,
            final AtomicInteger tasksRunning,
            final Set<Integer> viewsInProgress) {
        this.workGroup = workGroup;
        this.in = in;
        this.out = out;
        this.views = views;
        this.completeListener = completeListener;
        this.tasksRunning = tasksRunning;
        this.viewsInProgress = viewsInProgress;

        // TODO: rate limiting
    }

    /**
     * Start the thread that sends lessons and queries to the learner.
     */
    void exec() {
        workGroup.execute(NAME, this::run);
    }

    /**
     * This thread is responsible for sending lessons (and nested queries) to the learner.
     */
    private void run() {
        try {
            long requestCounter = 0;
            final long start = System.currentTimeMillis();
            // This task may receive a request from the learner for a view, which isn't ready to teach
            // yet. To solve it, just call view.waitUntilReady(), but it may impact performance if
            // called for every received message. A workaround is to track what views this task has
            // already checked to be ready
            final Set<Integer> viewsCheckedReady = new HashSet<>();
            while (!Thread.currentThread().isInterrupted()) {
                final PullVirtualTreeRequest request = in.readAnticipatedMessage(PullVirtualTreeRequest::new);
                if (request == null) {
                    if (!in.isAlive()) {
                        break;
                    }
                    Thread.sleep(0, 1);
                    continue;
                }
                requestCounter++;
                final int viewId = request.getViewId();
                final TeacherPullVirtualTreeView<?, ?> view = (TeacherPullVirtualTreeView<?, ?>) views.get(viewId);
                if (request.getPath() == Path.INVALID_PATH) {
                    logger.info(
                            RECONNECT.getMarker(),
                            "Teaching is complete for view={} as requested by the learner",
                            viewId);
                    // Acknowledge the final request
                    out.sendAsync(viewId, new PullVirtualTreeResponse(view, Path.INVALID_PATH, true, -1, -1, null));
                    completeListener.accept(viewId);
                    viewsInProgress.remove(viewId);
                    continue;
                }
                if (!viewsCheckedReady.contains(viewId)) {
                    view.waitUntilReady();
                    viewsCheckedReady.add(viewId);
                }
                final long path = request.getPath();
                final Hash learnerHash = request.getHash();
                assert learnerHash != null;
                final Hash teacherHash = view.loadHash(path);
                // The only valid scenario, when teacherHash may be null, is the empty tree
                if ((teacherHash == null) && (path != 0)) {
                    throw new MerkleSerializationException(
                            "Cannot load node hash (bad request from learner?), view=" + viewId + " path=" + path);
                }
                final boolean isClean = (teacherHash == null) || teacherHash.equals(learnerHash);
                final VirtualLeafRecord<?, ?> leafData = (!isClean && view.isLeaf(path)) ? view.loadLeaf(path) : null;
                final long firstLeafPath = view.getReconnectState().getFirstLeafPath();
                final long lastLeafPath = view.getReconnectState().getLastLeafPath();
                final PullVirtualTreeResponse response =
                        new PullVirtualTreeResponse(view, path, isClean, firstLeafPath, lastLeafPath, leafData);
                out.sendAsync(viewId, response);
            }
            if (tasksRunning.decrementAndGet() == 0) {
                // Check if all views have been fully synchronized
                if (!viewsInProgress.isEmpty()) {
                    throw new MerkleSynchronizationException(
                            "Teacher receiving tasks are complete, but some views aren't synchronized still");
                }
            }
            final long end = System.currentTimeMillis();
            final double requestRate = (end == start) ? 0.0 : (double) requestCounter / (end - start);
            logger.info(
                    RECONNECT.getMarker(),
                    "Teacher task: duration={}ms, requests={}, rate={}",
                    end - start,
                    requestCounter,
                    requestRate);
        } catch (final InterruptedException ex) {
            logger.warn(RECONNECT.getMarker(), "Teacher task is interrupted");
            Thread.currentThread().interrupt();
        } catch (final Exception ex) {
            workGroup.handleError(ex);
        }
    }
}
