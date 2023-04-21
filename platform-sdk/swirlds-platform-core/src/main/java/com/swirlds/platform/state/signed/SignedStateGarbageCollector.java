/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.signed;

import com.swirlds.base.state.Startable;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import java.time.Duration;

/**
 * Deletes signed states on a background thread.
 */
public class SignedStateGarbageCollector implements Startable {

    /**
     * The number of states that are permitted to wait in the deletion queue.
     */
    public static final int DELETION_QUEUE_CAPACITY = 25;

    /**
     * <p>
     * This queue thread is responsible for deleting/archiving signed states on a background thread.
     * </p>
     *
     * <p>
     * If, in the future, state deletion ever becomes a bottleneck, then it is safe to change this into a
     * {@link com.swirlds.common.threading.framework.QueueThreadPool QueueThreadPool}.
     * </p>
     */
    private final QueueThread<Runnable> deletionQueue;

    private final SignedStateMetrics signedStateMetrics;

    /**
     * Create a new garbage collector for signed states.
     *
     * @param threadManager
     * 		responsible for creating and managing threads
     * @param signedStateMetrics
     * 		metrics for signed states
     */
    public SignedStateGarbageCollector(final ThreadManager threadManager, final SignedStateMetrics signedStateMetrics) {
        this.signedStateMetrics = signedStateMetrics;
        deletionQueue = new QueueThreadConfiguration<Runnable>(threadManager)
                .setComponent("platform")
                .setThreadName("signed-state-deleter")
                .setMaxBufferSize(1)
                .setCapacity(DELETION_QUEUE_CAPACITY)
                .setHandler(Runnable::run)
                .build();
    }

    /**
     * Attempt to execute an operation on the garbage collection thread.
     *
     * @param operation
     * 		the operation to be executed
     * @return true if the operation will be eventually executed, false if the operation was rejected
     * 		and will not be executed (due to a backlog of operations)
     */
    public boolean executeOnGarbageCollectionThread(final Runnable operation) {
        if (signedStateMetrics != null) {
            signedStateMetrics.getStateDeletionQueueAvgMetric().update(deletionQueue.size());
        }
        return deletionQueue.offer(operation);
    }

    /**
     * Report the time required to delete a state.
     */
    public void reportDeleteTime(final Duration deletionTime) {
        if (signedStateMetrics != null) {
            signedStateMetrics.getStateDeletionTimeAvgMetric().update(deletionTime.toMillis());
        }
    }

    /**
     * Report the time required to archive a state.
     */
    public void reportArchiveTime(final Duration archiveTime) {
        if (signedStateMetrics != null) {
            signedStateMetrics.getStateArchivalTimeAvgMetric().update(archiveTime.toMillis());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        deletionQueue.start();
    }

    /**
     * Stop the background thread.
     */
    public void stop() {
        deletionQueue.stop();
    }
}
