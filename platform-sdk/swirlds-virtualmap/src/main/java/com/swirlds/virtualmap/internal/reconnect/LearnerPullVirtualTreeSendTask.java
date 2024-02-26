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

import com.swirlds.common.io.streams.SerializableDataOutputStream;
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
    private final SerializableDataOutputStream out;
    private final VirtualLearnerTreeView view;
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
            final SerializableDataOutputStream out,
            final VirtualLearnerTreeView view,
            final AtomicBoolean senderIsFinished,
            final CountDownLatch rootResponseReceived) {
        this.workGroup = workGroup;
        this.out = out;
        this.view = view;
        this.senderIsFinished = senderIsFinished;
        this.rootResponseReceived = rootResponseReceived;
    }

    public void start() {
        workGroup.execute(NAME, this::run);
    }

    private void run() {
        try {
            long path = Path.ROOT_PATH;
            out.writeLong(path);
            out.flush();
            view.anticipateMesssage();
            if (!rootResponseReceived.await(60, TimeUnit.SECONDS)) {
                throw new MerkleSynchronizationException("Timed out waiting for root node response from the teacher");
            }

            int requestCounter = 0;
            long lastFlushTime = System.currentTimeMillis();
            path = view.getNextPathToSend(path + 1);
            while (true) {
                out.writeLong(path);
                final long now = System.currentTimeMillis();
                if ((requestCounter++ == 128) || (now - lastFlushTime > 128)) {
                    out.flush();
                    requestCounter = 0;
                    lastFlushTime = now;
                }
                if (path == Path.INVALID_PATH) {
                    break;
                }
                view.anticipateMesssage();
                path = view.getNextPathToSend(path + 1);
            }
            out.flush();
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
