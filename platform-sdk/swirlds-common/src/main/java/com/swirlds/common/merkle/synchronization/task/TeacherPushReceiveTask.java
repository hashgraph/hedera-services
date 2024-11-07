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

package com.swirlds.common.merkle.synchronization.task;

import static com.swirlds.logging.legacy.LogMarker.RECONNECT;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class encapsulates all logic for the teacher's receiving task.
 *
 * @param <T>
 * 		the type of data used by the view to represent a node
 */
public class TeacherPushReceiveTask<T> {

    private static final Logger logger = LogManager.getLogger(TeacherPushReceiveTask.class);

    private static final String NAME = "teacher-receive-task";

    private final StandardWorkGroup workGroup;
    private final int viewId;
    private final AsyncInputStream in;
    private final TeacherTreeView<T> view;
    private final AtomicBoolean senderIsFinished;

    private final Consumer<Integer> completeListener;

    /**
     * Create a thread for receiving responses to queries from the learner.
     *
     * @param workGroup
     * 		the work group that will manage this thread
     * @param in
     * 		the input stream, this object is responsible for closing this when finished
     * @param view
     * 		the view to be used when touching the merkle tree
     * @param senderIsFinished
     * 		becomes true once the sending thread has finished
     */
    public TeacherPushReceiveTask(
            final StandardWorkGroup workGroup,
            final int viewId,
            final AsyncInputStream in,
            final TeacherTreeView<T> view,
            final AtomicBoolean senderIsFinished,
            final Consumer<Integer> completeListener) {
        this.workGroup = workGroup;
        this.viewId = viewId;
        this.in = in;
        this.view = view;
        this.senderIsFinished = senderIsFinished;
        this.completeListener = completeListener;
    }

    public void start() {
        workGroup.execute(NAME, this::run);
    }

    private void run() {
        try {
            boolean finished = senderIsFinished.get();
            boolean responseExpected = view.isResponseExpected();

            while ((!finished || responseExpected) && !Thread.currentThread().isInterrupted()) {
                if (responseExpected) {
                    final QueryResponse response = in.readAnticipatedMessage(viewId, QueryResponse::new);
                    final T node = view.getNodeForNextResponse();
                    view.registerResponseForNode(node, response.doesLearnerHaveTheNode());
                } else {
                    MILLISECONDS.sleep(1);
                }

                finished = senderIsFinished.get();
                responseExpected = view.isResponseExpected();
            }
            completeListener.accept(viewId);
        } catch (final InterruptedException ex) {
            logger.warn(RECONNECT.getMarker(), "teacher's receiving thread interrupted");
            Thread.currentThread().interrupt();
        } catch (final Exception ex) {
            workGroup.handleError(ex);
        }
    }
}
