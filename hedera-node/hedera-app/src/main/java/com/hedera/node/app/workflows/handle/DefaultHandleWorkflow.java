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

package com.hedera.node.app.workflows.handle;

import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransaction;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransactionPreHandleResultP2;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransactionPreHandleResultP3;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Transaction;
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
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.dispatch.ChildDispatchFactory;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.steps.HollowAccountCompletions;
import com.hedera.node.app.workflows.handle.steps.NodeStakeUpdates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
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

    private final NodeStakeUpdates nodeStakeUpdates;
    private final BlockRecordManager blockRecordManager;
    private final DispatchProcessor dispatchProcessor;
    private final HollowAccountCompletions hollowAccountCompletions;
    private final StoreMetricsService storeMetricsService;
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
            @NonNull final NodeStakeUpdates nodeStakeUpdates,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final DispatchProcessor dispatchProcessor,
            @NonNull final HollowAccountCompletions hollowAccountCompletions,
            @NonNull final StoreMetricsService storeMetricsService,
            @NonNull final Authorizer authorizer,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final FeeManager feeManager,
            @NonNull final RecordCache recordCache,
            @NonNull final ServiceScopeLookup serviceScopeLookup,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final ChildDispatchFactory childDispatchFactory,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final NetworkUtilizationManager networkUtilizationManager) {
        this.nodeStakeUpdates = requireNonNull(nodeStakeUpdates);
        this.blockRecordManager = requireNonNull(blockRecordManager);
        this.dispatchProcessor = requireNonNull(dispatchProcessor);
        this.hollowAccountCompletions = requireNonNull(hollowAccountCompletions);
        this.storeMetricsService = requireNonNull(storeMetricsService);
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
     * Executes the handle workflow. This method is the entry point for handling a
     * user transaction. It processes the staking period time hook, advances the
     * consensus clock, expires schedules, logs the user transaction, finalizes
     * hollow accounts, and processes the dispatch.
     *
     * @param userTxn the user transaction component
     */
    public void execute(@NonNull final UserTxn userTxn) {
        requireNonNull(userTxn);
        updateNodeStakes(userTxn);
        blockRecordManager.advanceConsensusClock(userTxn.consensusNow(), userTxn.state());
        expireSchedules(userTxn);
        if (logger.isDebugEnabled()) {
            logUserTxn(userTxn);
        }
        final var userDispatch = dispatchFor(userTxn);
        hollowAccountCompletions.completeHollowAccounts(userTxn, userDispatch);
        dispatchProcessor.processDispatch(userDispatch);
    }

    /**
     * Returns the user dispatch for the given user transaction.
     *
     * @param userTxn the user transaction
     * @return the user dispatch
     */
    private UserDispatch dispatchFor(@NonNull final UserTxn userTxn) {
        return userTxn.dispatch(
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
                initializeUserRecord(userTxn),
                networkUtilizationManager);
    }

    public SingleTransactionRecordBuilderImpl initializeUserRecord(@NonNull final UserTxn userTxn) {
        return initializeUserRecord(userTxn.recordListBuilder().userTransactionRecordBuilder(), userTxn.txnInfo());
    }

    /**
     * Initializes the user record with the transaction information. The record builder list is initialized with the
     * transaction, transaction bytes, transaction ID, exchange rate, and memo.
     * @param recordBuilder the record builder
     * @param txnInfo the transaction info
     */
    // TODO: Guarantee that this never throws an exception
    public SingleTransactionRecordBuilderImpl initializeUserRecord(
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder, @NonNull final TransactionInfo txnInfo) {
        requireNonNull(txnInfo);
        requireNonNull(recordBuilder);
        final var transaction = txnInfo.transaction();
        // If the transaction uses the legacy body bytes field instead of explicitly setting
        // its signed bytes, the record will have the hash of its bytes as serialized by PBJ
        final Bytes transactionBytes;
        if (transaction.signedTransactionBytes().length() > 0) {
            transactionBytes = transaction.signedTransactionBytes();
        } else {
            transactionBytes = Transaction.PROTOBUF.toBytes(transaction);
        }
        return recordBuilder
                .transaction(txnInfo.transaction())
                .transactionBytes(transactionBytes)
                .transactionID(txnInfo.txBody().transactionIDOrThrow())
                .exchangeRate(exchangeRateManager.exchangeRates())
                .memo(txnInfo.txBody().memo());
    }

    private void updateNodeStakes(@NonNull final UserTxn userTxn) {
        try {
            nodeStakeUpdates.process(userTxn.stack(), userTxn.tokenContextImpl());
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
     * Expire schedules that are due to be executed between the last handled
     * transaction time and the current consensus time.
     *
     * @param userTxn the user transaction
     */
    private void expireSchedules(@NonNull UserTxn userTxn) {
        if (userTxn.isGenesisTxn()) {
            return;
        }
        final var lastHandledTxnTime = userTxn.lastHandledConsensusTime();
        if (userTxn.consensusNow().getEpochSecond() > lastHandledTxnTime.getEpochSecond()) {
            final var firstSecondToExpire = lastHandledTxnTime.getEpochSecond();
            final var lastSecondToExpire = userTxn.consensusNow().getEpochSecond() - 1;
            final var scheduleStore = new WritableStoreFactory(
                            userTxn.stack(), ScheduleService.NAME, userTxn.config(), storeMetricsService)
                    .getStore(WritableScheduleStore.class);
            scheduleStore.purgeExpiredSchedulesBetween(firstSecondToExpire, lastSecondToExpire);
            userTxn.stack().commitFullStack();
        }
    }
}
