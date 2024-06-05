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

package com.hedera.node.app.workflows.handle.flow.process;

import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartEvent;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartRound;

import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.workflows.handle.CacheWarmer;
import com.hedera.node.app.workflows.handle.flow.components.UserTransactionComponent;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StagedHandleWorkflow {
    private static final Logger logger = LogManager.getLogger(StagedHandleWorkflow.class);

    private final CacheWarmer cacheWarmer;
    private final BlockRecordManager blockRecordManager;
    private final ThrottleServiceManager throttleServiceManager;
    private final NetworkInfo networkInfo;
    private final Provider<UserTransactionComponent.Factory> userTxnProvider;
    private final HandleWorkflowMetrics handleWorkflowMetrics;

    public StagedHandleWorkflow(
            final CacheWarmer cacheWarmer,
            final BlockRecordManager blockRecordManager,
            final ThrottleServiceManager throttleServiceManager,
            final NetworkInfo networkInfo,
            final Provider<UserTransactionComponent.Factory> handleProvider,
            final HandleWorkflowMetrics handleWorkflowMetrics) {
        this.cacheWarmer = cacheWarmer;
        this.blockRecordManager = blockRecordManager;
        this.throttleServiceManager = throttleServiceManager;
        this.networkInfo = networkInfo;
        this.userTxnProvider = handleProvider;
        this.handleWorkflowMetrics = handleWorkflowMetrics;
    }

    /**
     * Handles the next {@link Round}
     *
     * @param state the writable {@link HederaState} that this round will work on
     * @param platformState
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

    private void handlePlatformTransaction(
            @NonNull final HederaState state,
            @NonNull final PlatformState platformState,
            @NonNull final ConsensusEvent platformEvent,
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction platformTxn) {
        final var handleStart = System.nanoTime();
        final var consensusNow = platformTxn.getConsensusTimestamp().minusNanos(1000 - 3L);

        blockRecordManager.startUserTransaction(consensusNow, state, platformState);
        final var userTxn =
                userTxnProvider.get().create(platformState, platformEvent, creator, platformTxn, consensusNow);

        final var recordStream = userTxn.recordStream().get();
        blockRecordManager.endUserTransaction(recordStream, state);

        handleWorkflowMetrics.updateTransactionDuration(
                userTxn.functionality(), (int) (System.nanoTime() - handleStart));
    }
}
