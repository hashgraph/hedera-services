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
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.common.utility.throttle.RateLimiter;
import com.swirlds.virtualmap.internal.Path;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class encapsulates all logic for the teacher's sending thread.
 */
public class TeacherPullVirtualTreeReceiveTask {

    private static final Logger logger = LogManager.getLogger(TeacherPullVirtualTreeReceiveTask.class);

    private static final String NAME = "reconnect-teacher-receiver";

    private final StandardWorkGroup workGroup;
    private final SerializableDataInputStream in;
    private final AsyncOutputStream<PullVirtualTreeResponse> out;
    private final VirtualTeacherTreeView view;

    private final RateLimiter rateLimiter;
    private final int sleepNanos;

    private final AtomicBoolean allRequestsReceived;

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
            @NonNull final Time time,
            @NonNull final ReconnectConfig reconnectConfig,
            final StandardWorkGroup workGroup,
            final SerializableDataInputStream in,
            final AsyncOutputStream<PullVirtualTreeResponse> out,
            final VirtualTeacherTreeView view,
            final AtomicBoolean allRequestsReceived) {
        this.workGroup = workGroup;
        this.in = in;
        this.out = out;
        this.view = view;
        this.allRequestsReceived = allRequestsReceived;

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
        try {
            while (true) {
                rateLimit();
                final PullVirtualTreeRequest request = new PullVirtualTreeRequest(view);
                request.deserialize(in, 0);
//                logger.info(RECONNECT.getMarker(), "TOREMOVE Teacher receive path: " + request.getPath());
//                System.err.println("TOREMOVE Teacher receive path: " + request.getPath());
                if (request.getPath() == Path.INVALID_PATH) {
                    logger.info(RECONNECT.getMarker(), "Teacher receiver is complete as requested by the learner");
                    break;
                }
                view.registerRequest(request);
            }
//            logger.info(RECONNECT.getMarker(), "TOREMOVE Teacher receive done");
//            System.err.println("TOREMOVE Teacher receive done");
        } catch (final InterruptedException ex) {
            logger.warn(RECONNECT.getMarker(), "Teacher's receiving task is interrupted");
            Thread.currentThread().interrupt();
        } catch (final Exception ex) {
            throw new MerkleSynchronizationException("Exception in the teacher's receiving task", ex);
        } finally {
            allRequestsReceived.set(true);
        }
    }
}
