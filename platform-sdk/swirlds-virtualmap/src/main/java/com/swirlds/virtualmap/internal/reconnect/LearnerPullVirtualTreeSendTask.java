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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.virtualmap.internal.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LearnerPullVirtualTreeSendTask {

    private static final Logger logger = LogManager.getLogger(LearnerPullVirtualTreeSendTask.class);

    private static final String NAME = "reconnect-learner-sender";

    private final StandardWorkGroup workGroup;
    private final AsyncOutputStream<PullVirtualTreeRequest> out;
    private final VirtualLearnerTreeView view;
    private final NodeTraversalOrder traversalOrder;
    private final AtomicBoolean senderIsFinished;
    private final CountDownLatch rootResponseReceived;

    /**
     * Create a thread for sending node requests to the teacher.
     *
     * @param workGroup
     * 		the work group that will manage this thread
     * @param out
     * 		the output stream, this object is responsible for closing this when finished
     * @param view
     * 		the view to be used when touching the merkle tree
     * @param senderIsFinished
     * 		becomes true once the sending thread has finished
     */
    public LearnerPullVirtualTreeSendTask(
            final StandardWorkGroup workGroup,
            final AsyncOutputStream<PullVirtualTreeRequest> out,
            final VirtualLearnerTreeView view,
            final NodeTraversalOrder traversalOrder,
            final AtomicBoolean senderIsFinished,
            final CountDownLatch rootResponseReceived) {
        this.workGroup = workGroup;
        this.out = out;
        this.view = view;
        this.traversalOrder = traversalOrder;
        this.senderIsFinished = senderIsFinished;
        this.rootResponseReceived = rootResponseReceived;
    }

    void exec() {
        workGroup.execute(NAME, this::run);
    }

    private void run() {
        try (out) {
            // assuming root is always dirty
            out.sendAsync(new PullVirtualTreeRequest(view, Path.ROOT_PATH, new Hash()));
            view.anticipateMesssage();
            if (!rootResponseReceived.await(60, TimeUnit.SECONDS)) {
                throw new MerkleSynchronizationException("Timed out waiting for root node response from the teacher");
            }

            while (true) {
                final long path = traversalOrder.getNextPathToSend();
                // logger.info(RECONNECT.getMarker(), "TOREMOVE Learner send path: " + path);
//                System.err.println("TOREMOVE Learner send path: " + path);
                if (path < Path.INVALID_PATH) {
                    Thread.onSpinWait();
                    continue;
                }
                final Hash hash = path == Path.INVALID_PATH ? null : view.getNodeHash(path);
                out.sendAsync(new PullVirtualTreeRequest(view, path, hash));
                if (path == Path.INVALID_PATH) {
                    break;
                }
                view.anticipateMesssage();
            }
            // logger.info(RECONNECT.getMarker(), "TOREMOVE Learner send done");
//            System.err.println("TOREMOVE Learner send done");
        } catch (final InterruptedException ex) {
            logger.warn(RECONNECT.getMarker(), "Learner's sending task interrupted");
            Thread.currentThread().interrupt();
        } catch (final Exception ex) {
            throw new MerkleSynchronizationException("Exception in the learner's sending task", ex);
        } finally {
            senderIsFinished.set(true);
        }
    }
}
