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

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_3;
import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

import com.swirlds.base.state.Startable;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.PlatformStatNames;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.Stoppable;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadMetricsConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.ConsensusMetrics;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.stats.AverageAndMax;
import com.swirlds.platform.stats.AverageStat;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Prehandles transactions (both system and application). Ensures that application transactions are prehandled before
 * handling the consensus round containing the transactions. There is currently no requirement for strong ordering
 * between prehandling and handling of system transactions, and so none is currently enforced.
 */
public class PreConsensusEventHandler implements Clearable, Startable {

    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(PreConsensusEventHandler.class);

    /** The initial size of the pre-consensus event queue. */
    private static final int INITIAL_PRE_CONS_EVENT_QUEUE_CAPACITY = 100;

    private final NodeId selfId;

    private final QueueThread<EventImpl> queueThread;

    private final ConsensusMetrics consensusMetrics;

    /**
     * The class responsible for all interactions with the swirld state
     */
    private final SwirldStateManager swirldStateManager;

    /**
     * @param metrics            metrics system
     * @param threadManager      responsible for managing thread lifecycles
     * @param selfId             the ID of this node
     * @param swirldStateManager manages states
     * @param consensusMetrics   metrics relating to consensus
     * @param threadConfig       configuration for the thread system
     */
    public PreConsensusEventHandler(
            @NonNull final Metrics metrics,
            @NonNull final ThreadManager threadManager,
            @NonNull final NodeId selfId,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final ConsensusMetrics consensusMetrics,
            @NonNull final ThreadConfig threadConfig) {
        Objects.requireNonNull(metrics);
        Objects.requireNonNull(threadManager);
        Objects.requireNonNull(threadConfig);

        this.selfId = Objects.requireNonNull(selfId);
        this.swirldStateManager = Objects.requireNonNull(swirldStateManager);
        this.consensusMetrics = Objects.requireNonNull(consensusMetrics);
        final BlockingQueue<EventImpl> queue = new PriorityBlockingQueue<>(
                INITIAL_PRE_CONS_EVENT_QUEUE_CAPACITY, EventUtils::consensusPriorityComparator);

        queueThread = new QueueThreadConfiguration<EventImpl>(threadManager)
                .setNodeId(selfId)
                .setQueue(queue)
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("thread-curr")
                .setStopBehavior(Stoppable.StopBehavior.BLOCKING)
                .setHandler(swirldStateManager::handlePreConsensusEvent)
                .setLogAfterPauseDuration(threadConfig.logStackTracePauseDuration())
                .setMetricsConfiguration(new QueueThreadMetricsConfiguration(metrics).enableBusyTimeMetric())
                .build();

        final AverageAndMax avgQ1PreConsEvents = new AverageAndMax(
                metrics,
                INTERNAL_CATEGORY,
                PlatformStatNames.PRE_CONSENSUS_QUEUE_SIZE,
                "average number of events in the preconsensus queue (q1) waiting to be handled",
                FORMAT_10_3,
                AverageStat.WEIGHT_VOLATILE);
        metrics.addUpdater(() -> avgQ1PreConsEvents.update(queueThread.size()));
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
        logger.info(RECONNECT.getMarker(), "preconsensus handler: preparing for reconnect");
        queueThread.clear();
        logger.info(RECONNECT.getMarker(), "preconsensus handler: ready for reconnect");
    }

    /**
     * Do prehandle processing on the preconsensus event and add it to the queue (q1) for handling.
     */
    public void preconsensusEvent(final EventImpl event) {
        // we don't need empty pre-consensus events
        if (event == null || event.isEmpty()) {
            return;
        }

        // All events are supplied for preHandle
        swirldStateManager.preHandle(event);

        try {
            // update the estimate now, so the queue can sort on it
            event.estimateTime(
                    selfId,
                    consensusMetrics.getAvgSelfCreatedTimestamp(),
                    consensusMetrics.getAvgOtherReceivedTimestamp());
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
