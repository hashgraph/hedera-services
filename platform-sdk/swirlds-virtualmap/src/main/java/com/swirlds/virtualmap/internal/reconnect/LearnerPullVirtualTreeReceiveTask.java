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

import static com.swirlds.logging.legacy.LogMarker.RECONNECT;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LearnerPullVirtualTreeReceiveTask {

    private static final Logger logger = LogManager.getLogger(LearnerPullVirtualTreeReceiveTask.class);

    private static final String NAME = "reconnect-learner-receiver";

    private final StandardWorkGroup workGroup;
    private final AsyncInputStream<PullVirtualTreeResponse> in;
    private final VirtualLearnerTreeView view;
    private final AtomicBoolean senderIsFinished;
    private final AtomicLong expectedResponses;
    private final CountDownLatch rootResponseReceived;

    /**
     * Create a thread for receiving responses to queries from the teacher.
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
    public LearnerPullVirtualTreeReceiveTask(
            final StandardWorkGroup workGroup,
            final AsyncInputStream<PullVirtualTreeResponse> in,
            final VirtualLearnerTreeView view,
            final AtomicBoolean senderIsFinished,
            final AtomicLong expectedResponses,
            final CountDownLatch rootResponseReceived) {
        this.workGroup = workGroup;
        this.in = in;
        this.view = view;
        this.senderIsFinished = senderIsFinished;
        this.expectedResponses = expectedResponses;
        this.rootResponseReceived = rootResponseReceived;
    }

    public void exec() {
        workGroup.execute(NAME, this::run);
    }

    private void run() {
        try (in;
                view) {
            boolean finished = senderIsFinished.get();
            boolean responseExpected = expectedResponses.get() > 0;

            while (!finished || responseExpected) {
                if (responseExpected) {
                    final PullVirtualTreeResponse response =
                            in.readAnticipatedMessage(); // will call the view to read hash and leaf
                    // logger.info(RECONNECT.getMarker(), "TOREMOVE Learner receive path: " + response.getPath());
//                    System.err.println("TOREMOVE Learner receive path: " + response.getPath());
                    if (response.getPath() == 0) {
                        rootResponseReceived.countDown();
                    }
                    expectedResponses.decrementAndGet();
                } else {
                    Thread.onSpinWait();
                }

                finished = senderIsFinished.get();
                responseExpected = expectedResponses.get() > 0;
            }
            // logger.info(RECONNECT.getMarker(), "TOREMOVE Learner receive done");
//            System.err.println("TOREMOVE Learner receive done");
        } catch (final InterruptedException ex) {
            logger.warn(RECONNECT.getMarker(), "Learner's receiving task interrupted");
            Thread.currentThread().interrupt();
        } catch (final Exception ex) {
            throw new MerkleSynchronizationException("Exception in the learner's receiving task", ex);
        }
    }
}
