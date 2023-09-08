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

package com.swirlds.platform.network;

import static com.swirlds.common.units.UnitConstants.MILLISECONDS_TO_MICROSECONDS;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.common.system.transaction.internal.SystemTransactionPing;
import com.swirlds.platform.components.common.query.SystemTransactionSubmitter;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is responsible for creating system transactions containing network statistics. The system transactions
 * created by this class are standard transactions (not priority transactions).
 */
public final class NetworkStatsTransmitter implements Startable, Stoppable {
    private static final Logger logger = LogManager.getLogger(NetworkStatsTransmitter.class);
    private final SystemTransactionSubmitter transactionSubmitter;
    private final NetworkMetrics networkMetrics;
    private final ScheduledExecutorService executorService;
    private final BasicConfig basicConfig;

    /**
     * @param platformContext
     * 		the context of this platform
     * @param transactionSubmitter
     * 		a submitter of non-priority system transactions
     * @param networkMetrics
     * 		metrics related to the network
     */
    public NetworkStatsTransmitter(
            final PlatformContext platformContext,
            final SystemTransactionSubmitter transactionSubmitter,
            final NetworkMetrics networkMetrics) {
        this.transactionSubmitter = transactionSubmitter;
        this.networkMetrics = networkMetrics;
        this.executorService = new ScheduledThreadPoolExecutor(1);
        basicConfig = platformContext.getConfiguration().getConfigData(BasicConfig.class);
    }

    @Override
    public void start() {
        if (basicConfig.enablePingTrans()) {
            executorService.scheduleAtFixedRate(
                    this::transmitStatsWithExceptionHandling, 0, basicConfig.pingTransFreq(), TimeUnit.SECONDS);
        }
    }

    @Override
    public void stop() {
        executorService.shutdown();
    }

    /**
     * Create system transactions that transmit stats information.
     */
    private void transmitStats() {
        // Send a transaction giving the average ping time from self to all others (in microseconds).
        // This data will eventually be used by chatter to optimize broadcast trees when they are implemented.
        final Map<NodeId, Double> nodePingAvgs = networkMetrics.getAvgPingMilliseconds();
        // 2023-05-16: This transaction is no longer going to be used and does not support non-contiguous node IDs.
        // 2023-05-16: the order of the ping values is not being guaranteed.
        final int[] avgPingMilliseconds = nodePingAvgs.keySet().stream()
                .mapToInt(nodeId -> (int) (nodePingAvgs.get(nodeId) * MILLISECONDS_TO_MICROSECONDS))
                .toArray();
        final SystemTransaction systemTransaction = new SystemTransactionPing(avgPingMilliseconds);
        final boolean good = transactionSubmitter.submit(systemTransaction);
        if (!good) {
            logger.error(EXCEPTION.getMarker(), "failed to create ping time system transaction)");
        }
    }

    private void transmitStatsWithExceptionHandling() {
        try {
            transmitStats();
        } catch (Exception e) {
            logger.error(EXCEPTION.getMarker(), "exception while creating ping time system transaction)", e);
        }
    }
}
