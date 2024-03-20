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

import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class encapsulates all logic for the teacher's sending task.
 */
public class TeacherPullVirtualTreeSendTask {

    private static final Logger logger = LogManager.getLogger(TeacherPullVirtualTreeSendTask.class);

    private static final String NAME = "reconnect-teacher-sender";

    private final StandardWorkGroup workGroup;
    private final AsyncOutputStream<PullVirtualTreeResponse> out;
    private final VirtualTeacherTreeView view;

    private final AtomicBoolean allRequestsReceived;

    /**
     * Create new thread that will send data lessons and queries for a subtree.
     *
     * @param reconnectConfig       the configuration for reconnect
     * @param workGroup             the work group managing the reconnect
     * @param out                   the output stream, this object is responsible for closing this object when finished
     *                              class
     * @param view                  an object that interfaces with the subtree
     */
    public TeacherPullVirtualTreeSendTask(
            @NonNull final ReconnectConfig reconnectConfig,
            final StandardWorkGroup workGroup,
            final AsyncOutputStream<PullVirtualTreeResponse> out,
            final VirtualTeacherTreeView view,
            final AtomicBoolean allRequestsReceived) {
        this.workGroup = workGroup;
        this.out = out;
        this.view = view;
        this.allRequestsReceived = allRequestsReceived;
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
        try (out) {
            while (!allRequestsReceived.get() || view.hasPendingResponses()) {
                final PullVirtualTreeResponse response = view.getNextResponse();
                if (response == null) {
                    Thread.onSpinWait();
                    continue;
                }
//                logger.info(RECONNECT.getMarker(), "TOREMOVE Teacher send path: " + response.getPath());
//                System.err.println("TOREMOVE Teacher send path: " + response.getPath());
                out.sendAsync(response);
            }
//            logger.info(RECONNECT.getMarker(), "TOREMOVE Teacher send done");
//            System.err.println("TOREMOVE Teacher send done");
        } catch (final InterruptedException ex) {
            logger.warn(RECONNECT.getMarker(), "Teacher's sending task is interrupted");
            Thread.currentThread().interrupt();
        } catch (final Exception ex) {
            throw new MerkleSynchronizationException("Exception in the teacher's sending task", ex);
        }
    }
}
