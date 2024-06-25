/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle;

import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.PreHandleResultManager;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.UserRecordInitializer;
import com.hedera.node.app.workflows.handle.flow.txn.DefaultHandleWorkflow;
import com.hedera.node.app.workflows.handle.flow.txn.UserTxn;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.hedera.node.app.workflows.handle.record.GenesisWorkflow;
import com.hedera.node.config.ConfigProvider;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartEvent;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartRound;
import static java.util.Objects.requireNonNull;

/**
 * The handle workflow that is responsible for handling the next {@link Round} of transactions.
 */
@Singleton
public class HandleWorkflow {
    private static final Logger logger = LogManager.getLogger(HandleWorkflow.class);
    private final NetworkInfo networkInfo;
    private final ConfigProvider configProvider;
    private final StoreMetricsService storeMetricsService;
    private final BlockRecordManager blockRecordManager;
    private final CacheWarmer cacheWarmer;
    private final HandleWorkflowMetrics handleWorkflowMetrics;
    private final ThrottleServiceManager throttleServiceManager;
    private final PreHandleResultManager preHandleResultManager;
    private final UserRecordInitializer userRecordInitializer;
    private final SoftwareVersion version;
    private final InitTrigger initTrigger;
    private final DefaultHandleWorkflow defaultHandleWorkflow;
    private final GenesisWorkflow genesisWorkflow;
    private final HederaRecordCache recordCache;
    private final ExchangeRateManager exchangeRateManager;

    @Inject
    public HandleWorkflow(
            @NonNull final NetworkInfo networkInfo,
            @NonNull final ConfigProvider configProvider,
            @NonNull final StoreMetricsService storeMetricsService,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final CacheWarmer cacheWarmer,
            @NonNull final HandleWorkflowMetrics handleWorkflowMetrics,
            @NonNull final ThrottleServiceManager throttleServiceManager,
            @NonNull final UserRecordInitializer userRecordInitializer,
            @NonNull final PreHandleResultManager preHandleResultManager,
            @NonNull final SoftwareVersion version,
            @NonNull final InitTrigger initTrigger,
            @NonNull final DefaultHandleWorkflow defaultHandleWorkflow,
            @NonNull final GenesisWorkflow genesisWorkflow,
            @NonNull final HederaRecordCache recordCache,
            @NonNull final ExchangeRateManager exchangeRateManager) {
        this.networkInfo = requireNonNull(networkInfo, "networkInfo must not be null");
        this.configProvider = requireNonNull(configProvider);
        this.storeMetricsService = requireNonNull(storeMetricsService);
        this.blockRecordManager = requireNonNull(blockRecordManager, "recordManager must not be null");
        this.cacheWarmer = requireNonNull(cacheWarmer, "cacheWarmer must not be null");
        this.handleWorkflowMetrics = requireNonNull(handleWorkflowMetrics, "handleWorkflowMetrics must not be null");
        this.throttleServiceManager = requireNonNull(throttleServiceManager, "throttleServiceManager must not be null");
        this.userRecordInitializer = requireNonNull(userRecordInitializer);
        this.preHandleResultManager = requireNonNull(preHandleResultManager);
        this.version = requireNonNull(version);
        this.initTrigger = requireNonNull(initTrigger);
        this.defaultHandleWorkflow = requireNonNull(defaultHandleWorkflow);
        this.genesisWorkflow = requireNonNull(genesisWorkflow);
        this.recordCache = requireNonNull(recordCache);
        this.exchangeRateManager = requireNonNull(exchangeRateManager);
    }

    /**
     * Handles the next {@link Round}
     *
     * @param state the writable {@link HederaState} that this round will work on
     * @param platformState the {@link PlatformState} that this round will work on
     * @param round the next {@link Round} that needs to be processed
     */
    public void handleRound(
            @NonNull final HederaState state, @NonNull final PlatformState platformState, @NonNull final Round round) {
        // Keep track of whether any user transactions were handled. If so, then we will need to close the round
        // with the block record manager.
        final var userTransactionsHandled = new AtomicBoolean(false);

        // log start of round to transaction state log
        logStartRound(round);

        // warm the cache
        cacheWarmer.warm(state, round);

        // handle each event in the round
        for (final ConsensusEvent event : round) {
            final var creator = networkInfo.nodeInfo(event.getCreatorId().id());
            if (creator == null) {
                // We were given an event for a node that *does not exist in the address book*. This will be logged as
                // a warning, as this should never happen, and we will skip the event. The platform should guarantee
                // that we never receive an event that isn't associated with the address book, and every node in the
                // address book must have an account ID, since you cannot delete an account belonging to a node, and
                // you cannot change the address book non-deterministically.
                logger.warn("Received event from node {} which is not in the address book", event.getCreatorId());
                return;
            }

            // log start of event to transaction state log
            logStartEvent(event, creator);

            // handle each transaction of the event
            for (final var it = event.consensusTransactionIterator(); it.hasNext(); ) {
                final var platformTxn = it.next();
                try {
                    // skip system transactions
                    if (!platformTxn.isSystem()) {
                        userTransactionsHandled.set(true);
                        handlePlatformTransaction(state, platformState, event, creator, platformTxn);
                    }
                } catch (final Exception e) {
                    logger.fatal(
                            "Possibly CATASTROPHIC failure while running the handle workflow. "
                                    + "While this node may not die right away, it is in a bad way, most likely fatally.",
                            e);
                }
            }
        }

        // Update all throttle metrics once per round
        throttleServiceManager.updateAllMetrics();

        // Inform the BlockRecordManager that the round is complete, so it can update running-hashes in state
        // that have been being computed in background threads. The running hash has to be included in
        // state, but we want to synchronize with background threads as infrequently as possible. So once per
        // round is the minimum we can do.
        if (userTransactionsHandled.get()) {
            blockRecordManager.endRound(state);
        }
    }

    /**
     * Handles a platform transaction. This method is responsible for creating a {@link UserTxn} and
     * executing the workflow for the transaction. This produces a stream of records that are then passed to the
     * {@link BlockRecordManager} to be externalized.
     * @param state the writable {@link HederaState} that this transaction will work on
     * @param platformState the {@link PlatformState} that this transaction will work on
     * @param event the {@link ConsensusEvent} that this transaction belongs to
     * @param creator the {@link NodeInfo} of the creator of the transaction
     * @param txn the {@link ConsensusTransaction} to be handled
     */
    public void handlePlatformTransaction(
            @NonNull final HederaState state,
            @NonNull final PlatformState platformState,
            @NonNull final ConsensusEvent event,
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction txn) {
        final var handleStart = System.nanoTime();

        final var lastHandledConsTime = blockRecordManager.consTimeOfLastHandledTxn();
        final var consTime = txn.getConsensusTimestamp().minusNanos(1000 - 3L);
        // FUTURE: Use StreamMode enum to switch between blockStreams and/or recordStreams
        blockRecordManager.startUserTransaction(consTime, state, platformState);
        final var userTxn = UserTxn.from(
                state, platformState, event, creator, txn, consTime, lastHandledConsTime,
                configProvider, storeMetricsService, blockRecordManager, preHandleResultManager);
        final var workflow = userTxn.workflowWith(version, initTrigger, defaultHandleWorkflow, genesisWorkflow, recordCache, handleWorkflowMetrics, userRecordInitializer, exchangeRateManager);
        final var recordStream = workflow.execute();
        blockRecordManager.endUserTransaction(recordStream, state);

        handleWorkflowMetrics.updateTransactionDuration(
                userTxn.functionality(), (int) (System.nanoTime() - handleStart));
    }
}
