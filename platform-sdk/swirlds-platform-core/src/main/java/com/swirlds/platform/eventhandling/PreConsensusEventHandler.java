/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.eventhandling;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

import com.swirlds.base.state.Startable;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.ConsensusMetrics;
import com.swirlds.platform.observers.PreConsensusEventObserver;
import com.swirlds.platform.state.SwirldStateManager;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Created by a Platform to manage the flow of pre-consensus events to SwirldState (1 instance or 3 depending on the
 * SwirldState implemented). It contains a thread queue that contains a queue of pre-consensus events (q1) and a
 * SwirldStateManager which applies those events to the state
 */
public class PreConsensusEventHandler implements PreConsensusEventObserver, Clearable, Startable {

    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(PreConsensusEventHandler.class);

    /** The initial size of the pre-consensus event queue. */
    private static final int INITIAL_PRE_CONS_EVENT_QUEUE_CAPACITY = 100;

    private final NodeId selfId;

    private final QueueThread<EventImpl> queueThread;

    private final ConsensusMetrics metrics;

    /**
     * The class responsible for all interactions with the swirld state
     */
    private final SwirldStateManager swirldStateManager;

    /**
     * @param threadManager
     * 		responsible for managing thread lifecycles
     * @param selfId
     * 		the ID of this node
     * @param swirldStateManager
     * 		manages states
     * @param metrics
     * 		metrics relating to consensus
     */
    public PreConsensusEventHandler(
            final ThreadManager threadManager,
            final NodeId selfId,
            final SwirldStateManager swirldStateManager,
            final ConsensusMetrics metrics) {
        this.selfId = selfId;
        this.swirldStateManager = swirldStateManager;
        this.metrics = metrics;
        final BlockingQueue<EventImpl> queue = new PriorityBlockingQueue<>(
                INITIAL_PRE_CONS_EVENT_QUEUE_CAPACITY, EventUtils::consensusPriorityComparator);
        queueThread = new QueueThreadConfiguration<EventImpl>(threadManager)
                .setNodeId(selfId.getId())
                .setQueue(queue)
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("thread-curr")
                .setStopBehavior(swirldStateManager.getStopBehavior())
                // DO NOT turn the line below into a lambda reference because it will execute the getter, not the
                // runnable returned by the getter.
                .setWaitForItemRunnable(swirldStateManager.getPreConsensusWaitForWorkRunnable())
                .setHandler(swirldStateManager::handlePreConsensusEvent)
                .setLogAfterPauseDuration(ConfigurationHolder.getInstance()
                        .get()
                        .getConfigData(ThreadConfig.class)
                        .logStackTracePauseDuration())
                .build();
    }

    /**
     * Starts the queue thread.
     */
    @Override
    public void start() {
        queueThread.start();
    }

    /**
     * Stops the queue thread. For unit testing purposes only.
     */
    public void stop() {
        queueThread.stop();
    }

    @Override
    public void clear() {
        logger.info(RECONNECT.getMarker(), "pre-consensus handler: preparing for reconnect");
        queueThread.clear();
        logger.info(RECONNECT.getMarker(), "pre-consensus handler: ready for reconnect");
    }

    /**
     * Do pre-handle processing on the pre-consensus event and add it to the queue (q1) for handling. Events that are
     * null or empty are discarded immediately. All other events go through pre-handle processing. Events that should
     * not be handled pre-consensus according to {@link SwirldStateManager#discardPreConsensusEvent(EventImpl)} are not
     * added to the queue.
     */
    @Override
    public void preConsensusEvent(final EventImpl event) {
        // we don't need empty pre-consensus events
        if (event == null || event.isEmpty()) {
            return;
        }

        // All events are supplied for preHandle
        swirldStateManager.preHandle(event);

        // some events should not be applied as pre-consensus, so discard them
        if (swirldStateManager.discardPreConsensusEvent(event)) {
            return;
        }

        try {
            // update the estimate now, so the queue can sort on it
            event.estimateTime(selfId, metrics.getAvgSelfCreatedTimestamp(), metrics.getAvgOtherReceivedTimestamp());
            queueThread.put(event);
        } catch (final InterruptedException e) {
            logger.error(EXCEPTION.getMarker(), "error:{} event:{}", e, event);
            Thread.currentThread().interrupt();
        }
    }

    public int getQueueSize() {
        return queueThread.size();
    }
}
