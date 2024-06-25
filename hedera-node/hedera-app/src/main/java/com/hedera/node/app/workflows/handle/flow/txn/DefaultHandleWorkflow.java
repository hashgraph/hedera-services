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

package com.hedera.node.app.workflows.handle.flow.txn;

import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransaction;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransactionPreHandleResultP2;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransactionPreHandleResultP3;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.store.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.ScheduleExpirationHook;
import com.hedera.node.app.workflows.handle.StakingPeriodTimeHook;
import com.hedera.node.app.workflows.handle.flow.dispatch.helpers.DispatchProcessor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The default implementation of the handle workflow.
 */
@Singleton
public class DefaultHandleWorkflow {
    private static final Logger logger = LogManager.getLogger(DefaultHandleWorkflow.class);

    private final StakingPeriodTimeHook stakingPeriodTimeHook;
    private final BlockRecordManager blockRecordManager;
    private final DispatchProcessor dispatchProcessor;
    private final HollowAccountCompleter hollowAccountFinalization;
    private final ScheduleExpirationHook scheduleExpirationHook;
    private final StoreMetricsService storeMetricsService;

    @Inject
    public DefaultHandleWorkflow(
            @NonNull final StakingPeriodTimeHook stakingPeriodTimeHook,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final DispatchProcessor dispatchProcessor,
            @NonNull final HollowAccountCompleter hollowAccountFinalization,
            @NonNull final ScheduleExpirationHook scheduleExpirationHook,
            @NonNull final StoreMetricsService storeMetricsService) {
        this.stakingPeriodTimeHook = requireNonNull(stakingPeriodTimeHook);
        this.blockRecordManager = requireNonNull(blockRecordManager);
        this.dispatchProcessor = requireNonNull(dispatchProcessor);
        this.hollowAccountFinalization = requireNonNull(hollowAccountFinalization);
        this.scheduleExpirationHook = scheduleExpirationHook;
        this.storeMetricsService = storeMetricsService;
    }

    /**
     * Executes the handle workflow. This method is the entry point for handling a user transaction.
     * It processes the staking period time hook, advances the consensus clock, expires schedules, logs the
     * user transaction, finalizes hollow accounts, and processes the dispatch.
     *
     * @param userTxn the user transaction component
     */
    public void execute(@NonNull final UserTxnComponent userTxn) {
        requireNonNull(userTxn);
        processStakingPeriodTimeHook(userTxn);
        blockRecordManager.advanceConsensusClock(userTxn.consensusNow(), userTxn.state());
        expireSchedules(userTxn);

        if (logger.isDebugEnabled()) {
            logUserTxn(userTxn);
        }

        final var userDispatch = userTxn.userDispatchProvider().get().create();
        hollowAccountFinalization.finalizeHollowAccounts(userTxn, userDispatch);
        dispatchProcessor.processDispatch(userDispatch);
    }

    private void processStakingPeriodTimeHook(UserTxnComponent userTxn) {
        try {
            stakingPeriodTimeHook.process(userTxn.stack(), userTxn.tokenContext());
        } catch (final Exception e) {
            // If anything goes wrong, we log the error and continue
            logger.error("Failed to process staking period time hook", e);
        }
    }

    private void logUserTxn(@NonNull final UserTxnComponent userTxn) {
        // Log start of user transaction to transaction state log
        logStartUserTransaction(
                userTxn.platformTxn(),
                userTxn.txnInfo().txBody(),
                userTxn.txnInfo().payerID());
        logStartUserTransactionPreHandleResultP2(userTxn.preHandleResult());
        logStartUserTransactionPreHandleResultP3(userTxn.preHandleResult());
    }

    /**
     * Expire schedules that are due to be executed between the last handled transaction time and the current consensus
     * time.
     * @param userTxnContext the user transaction component
     */
    public void expireSchedules(@NonNull UserTxnComponent userTxnContext) {
        final var lastHandledTxnTime = userTxnContext.lastHandledConsensusTime();
        if (lastHandledTxnTime == Instant.EPOCH) {
            return;
        }
        if (userTxnContext.consensusNow().getEpochSecond() > lastHandledTxnTime.getEpochSecond()) {
            final var firstSecondToExpire = lastHandledTxnTime.getEpochSecond();
            final var lastSecondToExpire = userTxnContext.consensusNow().getEpochSecond() - 1;
            final var scheduleStore = new WritableStoreFactory(
                            userTxnContext.stack(),
                            ScheduleService.NAME,
                            userTxnContext.configuration(),
                            storeMetricsService)
                    .getStore(WritableScheduleStore.class);
            // purge all expired schedules between the first consensus time of last block and the current consensus time
            scheduleExpirationHook.processExpiredSchedules(scheduleStore, firstSecondToExpire, lastSecondToExpire);
            // commit the stack
            userTxnContext.stack().commitFullStack();
        }
    }
}
