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

import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.store.WritableStoreFactory;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.ScheduleExpirationHook;
import com.hedera.node.app.workflows.handle.StakingPeriodTimeHook;
import com.hedera.node.app.workflows.handle.flow.dispatch.UserDispatch;
import com.hedera.node.app.workflows.handle.flow.dispatch.child.ChildDispatchFactory;
import com.hedera.node.app.workflows.handle.flow.dispatch.helpers.DispatchProcessor;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.UserRecordInitializer;
import com.swirlds.state.spi.info.NetworkInfo;
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
    private final UserRecordInitializer userRecordInitializer;
    private final Authorizer authorizer;
    private final NetworkInfo networkInfo;
    private final FeeManager feeManager;
    private final RecordCache recordCache;
    private final ServiceScopeLookup serviceScopeLookup;
    private final ExchangeRateManager exchangeRateManager;
    private final ChildDispatchFactory childDispatchFactory;
    private final TransactionDispatcher dispatcher;
    private final NetworkUtilizationManager networkUtilizationManager;

    @Inject
    public DefaultHandleWorkflow(
            @NonNull final StakingPeriodTimeHook stakingPeriodTimeHook,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final DispatchProcessor dispatchProcessor,
            @NonNull final HollowAccountCompleter hollowAccountFinalization,
            @NonNull final ScheduleExpirationHook scheduleExpirationHook,
            @NonNull final StoreMetricsService storeMetricsService,
            @NonNull final UserRecordInitializer userRecordInitializer,
            @NonNull final Authorizer authorizer,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final FeeManager feeManager,
            @NonNull final RecordCache recordCache,
            @NonNull final ServiceScopeLookup serviceScopeLookup,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final ChildDispatchFactory childDispatchFactory,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final NetworkUtilizationManager networkUtilizationManager) {
        this.stakingPeriodTimeHook = requireNonNull(stakingPeriodTimeHook);
        this.blockRecordManager = requireNonNull(blockRecordManager);
        this.dispatchProcessor = requireNonNull(dispatchProcessor);
        this.hollowAccountFinalization = requireNonNull(hollowAccountFinalization);
        this.scheduleExpirationHook = requireNonNull(scheduleExpirationHook);
        this.storeMetricsService = requireNonNull(storeMetricsService);
        this.userRecordInitializer = requireNonNull(userRecordInitializer);
        this.authorizer = requireNonNull(authorizer);
        this.networkInfo = requireNonNull(networkInfo);
        this.feeManager = requireNonNull(feeManager);
        this.recordCache = requireNonNull(recordCache);
        this.serviceScopeLookup = requireNonNull(serviceScopeLookup);
        this.exchangeRateManager = requireNonNull(exchangeRateManager);
        this.childDispatchFactory = requireNonNull(childDispatchFactory);
        this.dispatcher = requireNonNull(dispatcher);
        this.networkUtilizationManager = requireNonNull(networkUtilizationManager);
    }

    /**
     * Executes the handle workflow. This method is the entry point for handling a user transaction.
     * It processes the staking period time hook, advances the consensus clock, expires schedules, logs the
     * user transaction, finalizes hollow accounts, and processes the dispatch.
     *
     * @param userTxn the user transaction component
     */
    public void execute(@NonNull final UserTxn userTxn) {
        requireNonNull(userTxn);
        processStakingPeriodTimeHook(userTxn);
        blockRecordManager.advanceConsensusClock(userTxn.consensusNow(), userTxn.state());
        expireSchedules(userTxn);

        if (logger.isDebugEnabled()) {
            logUserTxn(userTxn);
        }

        final var userDispatch = UserDispatch.from(
                userTxn.consensusNow(),
                userTxn.stack(),
                userTxn.preHandleResult(),
                userTxn.creatorInfo(),
                userTxn.configuration(),
                userTxn.platformState(),
                userTxn.tokenContext(),
                userTxn.recordListBuilder(),
                userRecordInitializer,
                authorizer,
                networkInfo,
                feeManager,
                recordCache,
                dispatchProcessor,
                blockRecordManager,
                serviceScopeLookup,
                storeMetricsService,
                exchangeRateManager,
                childDispatchFactory,
                dispatcher,
                networkUtilizationManager);

        hollowAccountFinalization.finalizeHollowAccounts(userTxn, userDispatch);
        dispatchProcessor.processDispatch(userDispatch);
    }

    private void processStakingPeriodTimeHook(@NonNull final UserTxn userTxn) {
        try {
            stakingPeriodTimeHook.process(userTxn.stack(), userTxn.tokenContext());
        } catch (final Exception e) {
            // If anything goes wrong, we log the error and continue
            logger.error("Failed to process staking period time hook", e);
        }
    }

    private void logUserTxn(@NonNull final UserTxn userTxn) {
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
     *
     * @param userTxn the user transaction component
     */
    public void expireSchedules(@NonNull UserTxn userTxn) {
        final var lastHandledTxnTime = userTxn.lastHandledConsensusTime();
        if (lastHandledTxnTime == Instant.EPOCH) {
            return;
        }
        if (userTxn.consensusNow().getEpochSecond() > lastHandledTxnTime.getEpochSecond()) {
            final var firstSecondToExpire = lastHandledTxnTime.getEpochSecond();
            final var lastSecondToExpire = userTxn.consensusNow().getEpochSecond() - 1;
            final var scheduleStore = new WritableStoreFactory(
                            userTxn.stack(), ScheduleService.NAME, userTxn.configuration(), storeMetricsService)
                    .getStore(WritableScheduleStore.class);
            // purge all expired schedules between the first consensus time of last block and the current consensus time
            scheduleExpirationHook.processExpiredSchedules(scheduleStore, firstSecondToExpire, lastSecondToExpire);
            // commit the stack
            userTxn.stack().commitFullStack();
        }
    }
}
