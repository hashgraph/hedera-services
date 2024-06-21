/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.LearnerTreeView;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.virtualmap.internal.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A task running on the learner side, which is responsible for getting responses from the teacher.
 *
 * <p>The task keeps running as long as the corresponding {@link LearnerPullVirtualTreeSendTask}
 * is alive, or some responses are expected from the teacher.
 *
 * <p>For every response from the teacher, the learner view is notified, which in turn notifies
 * the current traversal order, so it can recalculate the next virtual path to request.
 */
public class LearnerPullVirtualTreeReceiveTask {

    private static final Logger logger = LogManager.getLogger(LearnerPullVirtualTreeReceiveTask.class);

    private static final String NAME = "reconnect-learner-receiver";

    private final StandardWorkGroup workGroup;
    private final AsyncInputStream in;
    private final Map<Integer, LearnerTreeView<?>> views;

    // Indicates the number learner sender tasks still sending requests to the teacher
    private final Set<Integer> sendersRunning;

    // Number of requests sent to teacher / responses expected from the teacher. Increased in
    // the sending task, decreased in this task
    private final Map<Integer, AtomicLong> expectedResponses;

    // Indicates if a response for path 0 (virtual root node) has been received
    private final Map<Integer, CountDownLatch> rootResponsesReceived;

    private final Consumer<Integer> completeListener;

    /**
     * Create a thread for receiving responses to queries from the teacher.
     *
     * @param workGroup
     * 		the work group that will manage this thread
     * @param in
     * 		the input stream, this object is responsible for closing this when finished
     * @param sendersRunning
     * 		the number of sender tasks still running
     */
    public LearnerPullVirtualTreeReceiveTask(
            final StandardWorkGroup workGroup,
            final AsyncInputStream in,
            final Map<Integer, LearnerTreeView<?>> views,
            final Set<Integer> sendersRunning,
            final Map<Integer, AtomicLong> expectedResponses,
            final Map<Integer, CountDownLatch> rootResponsesReceived,
            final Consumer<Integer> completeListener) {
        this.workGroup = workGroup;
        this.in = in;
        this.views = views;
        this.sendersRunning = sendersRunning;
        this.expectedResponses = expectedResponses;
        this.rootResponsesReceived = rootResponsesReceived;
        this.completeListener = completeListener;
    }

    public void exec() {
        workGroup.execute(NAME, this::run);
    }

    private LearnerPullVirtualTreeView<?, ?> findView(final int viewId) {
        final LearnerPullVirtualTreeView<?, ?> view = (LearnerPullVirtualTreeView<?, ?>) views.get(viewId);
        if (view == null) {
            throw new MerkleSynchronizationException("Unknown view: " + viewId);
        }
        return view;
    }

    private void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                final PullVirtualTreeResponse response =
                        in.readAnticipatedMessage(viewId -> new PullVirtualTreeResponse(findView(viewId)));
                if (response == null) {
                    if (expectedResponses.isEmpty() || !in.isAlive()) {
                        break;
                    }
                    Thread.sleep(0, 1);
                    continue;
                }
                final int viewId = response.getViewId();
                final AtomicLong viewExpectedResponses = expectedResponses.get(viewId);
                viewExpectedResponses.decrementAndGet();
                if (response.getPath() == Path.INVALID_PATH) {
                    // There may be other messages for this view being handled by other threads
                    while (viewExpectedResponses.get() != 0) {
                        Thread.onSpinWait();
                    }
                    completeListener.accept(viewId);
                    expectedResponses.remove(viewId);
                } else if (response.getPath() == 0) {
                    final CountDownLatch rootResponseReceived = rootResponsesReceived.get(viewId);
                    rootResponseReceived.countDown();
                }
            }
        } catch (final Exception ex) {
            workGroup.handleError(ex);
        }
    }
}
