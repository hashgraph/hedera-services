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
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.virtualmap.internal.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A task running on the learner side, which is responsible for sending requests to the teacher.
 *
 * <p>The very first request to send is for path 0 (virtual root node). A response to this request
 * is waited for before any other requests are sent, because root node response contains virtual
 * tree path range on the teacher side.
 *
 * <p>After the root response has been received, this task keeps sending requests according to
 * the provided {@link NodeTraversalOrder}. After the next path to request is {@link
 * Path#INVALID_PATH}, this request is sent to indicate that there will be no more requests from
 * the learner, and this task is finished.
 */
@SuppressWarnings("rawtypes")
public class LearnerPullVirtualTreeSendTask {

    private static final Logger logger = LogManager.getLogger(LearnerPullVirtualTreeSendTask.class);

    private static final String NAME = "reconnect-learner-sender";

    private final StandardWorkGroup workGroup;
    private final int viewId;
    private final AsyncOutputStream out;
    private final LearnerPullVirtualTreeView view;

    // Max time to wait for path 0 (virtual root) response from the teacher
    private final Duration rootResponseTimeout;

    // Indicates if a response for path 0 (virtual root) has been received
    private final CountDownLatch rootResponseReceived;

    // Number of requests sent to teacher / responses expected from the teacher. Increased in
    // this task, decreased in the receiving task
    private final AtomicLong responsesExpected;

    private final AtomicBoolean rootRequestSent;
    private final AtomicBoolean lastPathSent;

    /**
     * Create a thread for sending node requests to the teacher.
     *
     * @param reconnectConfig
     *      the reconnect configuration
     * @param workGroup
     * 		the work group that will manage this thread
     * @param out
     * 		the output stream, this object is responsible for closing this when finished
     * @param view
     * 		the view to be used when touching the merkle tree
     * @param responsesExpected
     *      number of responses expected from the teacher, increased by one every time a request
     *      is sent
     */
    public LearnerPullVirtualTreeSendTask(
            final ReconnectConfig reconnectConfig,
            final StandardWorkGroup workGroup,
            final int viewId,
            final AsyncOutputStream out,
            final LearnerPullVirtualTreeView view,
            final CountDownLatch rootResponseReceived,
            final AtomicLong responsesExpected,
            final AtomicBoolean rootRequestSent,
            final AtomicBoolean lastPathSent) {
        this.workGroup = workGroup;
        this.viewId = viewId;
        this.out = out;
        this.view = view;
        this.rootResponseReceived = rootResponseReceived;
        this.responsesExpected = responsesExpected;
        this.rootRequestSent = rootRequestSent;
        this.lastPathSent = lastPathSent;

        this.rootResponseTimeout = reconnectConfig.pullLearnerRootResponseTimeout();
    }

    void exec() {
        workGroup.execute(NAME, this::run);
    }

    private void run() {
        try {
            if (rootRequestSent.compareAndSet(false, true)) {
                // Send a request for the root node first. The response will contain virtual tree path range
                out.sendAsync(viewId, new PullVirtualTreeRequest(Path.ROOT_PATH, new Hash()));
                view.getMapStats().incrementTransfersFromLearner();
                responsesExpected.incrementAndGet();
            }
            if (!rootResponseReceived.await(rootResponseTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new MerkleSynchronizationException(
                        "Timed out waiting for root node response from the teacher, view=" + viewId);
            }

            while (!Thread.currentThread().isInterrupted()) {
                final long path = view.getNextPathToSend();
                if (path == Path.INVALID_PATH) {
                    if (lastPathSent.compareAndSet(false, true)) {
                        responsesExpected.incrementAndGet();
                        out.sendAsync(viewId, new PullVirtualTreeRequest(path, null));
                    }
                    break;
                }
                if (path < 0) {
                    // No path available to send yet. Slow down
                    Thread.sleep(1);
                    continue;
                }
                responsesExpected.incrementAndGet();
                out.sendAsync(viewId, new PullVirtualTreeRequest(path, view.getNodeHash(path)));
                view.getMapStats().incrementTransfersFromLearner();
            }
        } catch (final InterruptedException ex) {
            logger.warn(RECONNECT.getMarker(), "Learner sending task is interrupted");
            Thread.currentThread().interrupt();
        } catch (final Exception ex) {
            workGroup.handleError(ex);
        }
    }
}
