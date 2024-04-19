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
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.swirlds.base.time.Time;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.exceptions.MerkleSerializationException;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.common.utility.throttle.RateLimiter;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.Path;
import edu.umd.cs.findbugs.annotations.NonNull;
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
    private final int viewId;
    private final AsyncInputStream in;
    private final AsyncOutputStream out;
    private final TeacherPullVirtualTreeView<?, ?> view;

    private final RateLimiter rateLimiter;
    private final int sleepNanos;

    private final Consumer<Boolean> completeListener;

    /**
     * Create new thread that will send data lessons and queries for a subtree.
     *
     * @param time                  the wall clock time
     * @param reconnectConfig       the configuration for reconnect
     * @param workGroup             the work group managing the reconnect
     * @param in                    the input stream
     * @param out                   the output stream
     * @param view                  an object that interfaces with the subtree
     */
    public TeacherPullVirtualTreeReceiveTask(
            final int viewId,
            @NonNull final Time time,
            @NonNull final ReconnectConfig reconnectConfig,
            final StandardWorkGroup workGroup,
            final AsyncInputStream in,
            final AsyncOutputStream out,
            final TeacherPullVirtualTreeView<?, ?> view,
            final Consumer<Boolean> completeListener) {
        this.workGroup = workGroup;
        this.viewId = viewId;
        this.in = in;
        this.out = out;
        this.view = view;
        this.completeListener = completeListener;

        final int maxRate = reconnectConfig.teacherMaxNodesPerSecond();
        if (maxRate > 0) {
            rateLimiter = new RateLimiter(time, maxRate);
            sleepNanos = (int) reconnectConfig.teacherRateLimiterSleep().toNanos();
        } else {
            rateLimiter = null;
            sleepNanos = -1;
        }
    }

    /**
     * Start the thread that sends lessons and queries to the learner.
     */
    void exec() {
        workGroup.execute(NAME, this::run);
    }

    /**
     * Enforce the rate limit.
     *
     * @throws InterruptedException if the thread is interrupted while sleeping
     */
    private void rateLimit() throws InterruptedException {
        if (rateLimiter != null) {
            while (!rateLimiter.requestAndTrigger()) {
                NANOSECONDS.sleep(sleepNanos);
            }
        }
    }

    /**
     * This thread is responsible for sending lessons (and nested queries) to the learner.
     */
    private void run() {
        boolean success = false;
        try {
            in.anticipateMessage(); // anticipate root node request
            while (true) {
                rateLimit();
                final PullVirtualTreeRequest request = in.readAnticipatedMessage(viewId);
                if (request.getPath() == Path.INVALID_PATH) {
                    logger.info(RECONNECT.getMarker(), "Teacher receiver is complete as requested by the learner");
                    break;
                }
                final long path = request.getPath();
                final Hash learnerHash = request.getHash();
                assert learnerHash != null;
                final Hash teacherHash = view.loadHash(path);
                // The only valid scenario, when teacherHash may be null, is the empty tree
                if ((teacherHash == null) && (path != 0)) {
                    throw new MerkleSerializationException(
                            "Cannot load node hash (bad request from learner?), path = " + path);
                }
                final boolean isClean = (teacherHash == null) || teacherHash.equals(learnerHash);
                final VirtualLeafRecord<?, ?> leafData = (!isClean && view.isLeaf(path)) ? view.loadLeaf(path) : null;
                final long firstLeafPath = view.reconnectState.getFirstLeafPath();
                final long lastLeafPath = view.reconnectState.getLastLeafPath();
                final PullVirtualTreeResponse response = new PullVirtualTreeResponse(
                        view, path, isClean, firstLeafPath, lastLeafPath, leafData);
                // All real work is done in the async output thread. This call just registers a response
                // and returns immediately
                out.sendAsync(viewId, response);
                in.anticipateMessage();
            }
            success = true;
        } catch (final InterruptedException ex) {
            logger.warn(RECONNECT.getMarker(), "Teacher's receiving task is interrupted");
            Thread.currentThread().interrupt();
        } catch (final Exception ex) {
            throw new MerkleSynchronizationException("Exception in the teacher's receiving task", ex);
        } finally {
            completeListener.accept(success);
        }
    }
}
