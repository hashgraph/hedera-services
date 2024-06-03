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

package com.hedera.node.app.workflows.handle.flow;

import static com.hedera.hapi.node.base.HederaFunctionality.NONE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransactionPreHandleResultP2;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransactionPreHandleResultP3;
import static com.hedera.node.app.workflows.handle.flow.util.HandleUtils.extraRewardReceivers;
import static com.hedera.node.app.workflows.handle.flow.util.HandleUtils.extractTransactionBaseData;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.SO_FAR_SO_GOOD;
import static java.util.Collections.emptyMap;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.token.records.ParentRecordFinalizer;
import com.hedera.node.app.signature.DefaultKeyVerifier;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.PlatformStateUpdateFacility;
import com.hedera.node.app.workflows.handle.SystemFileUpdateFacility;
import com.hedera.node.app.workflows.handle.TokenContextImpl;
import com.hedera.node.app.workflows.handle.flow.infra.CronHandlerLogic;
import com.hedera.node.app.workflows.handle.flow.infra.HandleValidations;
import com.hedera.node.app.workflows.handle.flow.infra.HollowAccountFinalizer;
import com.hedera.node.app.workflows.handle.flow.infra.ThrottleLogic;
import com.hedera.node.app.workflows.handle.flow.infra.fees.FeeLogic;
import com.hedera.node.app.workflows.handle.flow.modules.HandleContextComponent;
import com.hedera.node.app.workflows.handle.flow.util.ValidationResult;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class DefaultHandleProcess implements HandleProcess {
    private static final Logger logger = LogManager.getLogger(DefaultHandleProcess.class);
    private final BlockRecordManager blockRecordManager;
    private final PreHandleResult preHandleResult;
    private final CronHandlerLogic cronProcesses;
    private final ExchangeRateManager exchangeRateManager;
    private final HandleValidations validator;
    private final FeeLogic feeLogic;
    private final HollowAccountFinalizer hollowAccountFinalizer;
    final TransactionDispatcher dispatcher;
    private final HederaRecordCache recordCache;
    private final ThrottleLogic throttleLogic;
    private final NetworkUtilizationManager networkUtilizationManager;
    private final SavepointStackImpl stack;
    private final Provider<HandleContextComponent.Factory> handleContextProvider;
    private final DefaultKeyVerifier keyVerifier;
    private final SystemFileUpdateFacility systemFileUpdateFacility;
    private final PlatformStateUpdateFacility platformStateUpdateFacility;
    private final HandleWorkflowMetrics handleWorkflowMetrics;
    private final ParentRecordFinalizer transactionFinalizer;
    private final TokenContextImpl tokenContext;

    @Inject
    public DefaultHandleProcess(
            final BlockRecordManager blockRecordManager,
            final PreHandleResult preHandleResult,
            final CronHandlerLogic cronProcesses,
            final ExchangeRateManager exchangeRateManager,
            final HandleValidations validator,
            final FeeLogic feeLogic,
            final HollowAccountFinalizer hollowAccountFinalizer,
            final TransactionDispatcher dispatcher,
            final HederaRecordCache recordCache,
            final ThrottleLogic throttleLogic,
            final NetworkUtilizationManager networkUtilizationManager,
            final SavepointStackImpl stack,
            final Provider<HandleContextComponent.Factory> handleContextProvider,
            final DefaultKeyVerifier keyVerifier,
            final SystemFileUpdateFacility systemFileUpdateFacility,
            final PlatformStateUpdateFacility platformStateUpdateFacility,
            final HandleWorkflowMetrics handleWorkflowMetrics,
            final ParentRecordFinalizer transactionFinalizer,
            final TokenContextImpl tokenContext) {
        this.blockRecordManager = blockRecordManager;
        this.preHandleResult = preHandleResult;
        this.cronProcesses = cronProcesses;
        this.exchangeRateManager = exchangeRateManager;
        this.validator = validator;
        this.feeLogic = feeLogic;
        this.hollowAccountFinalizer = hollowAccountFinalizer;
        this.dispatcher = dispatcher;
        this.recordCache = recordCache;
        this.throttleLogic = throttleLogic;
        this.networkUtilizationManager = networkUtilizationManager;
        this.stack = stack;
        this.handleContextProvider = handleContextProvider;
        this.keyVerifier = keyVerifier;
        this.systemFileUpdateFacility = systemFileUpdateFacility;
        this.platformStateUpdateFacility = platformStateUpdateFacility;
        this.handleWorkflowMetrics = handleWorkflowMetrics;
        this.transactionFinalizer = transactionFinalizer;
        this.tokenContext = tokenContext;
    }

    @Override
    public void processUserTransaction(
            @NonNull final Instant consensusNow,
            @NonNull final HederaState state,
            @NonNull final PlatformState platformState,
            @NonNull final ConsensusEvent platformEvent,
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction platformTxn,
            @NonNull final RecordListBuilder recordListBuilder) {
        // (FUTURE) We actually want to consider exporting synthetic transactions on every
        // first post-upgrade transaction, not just the first transaction after genesis.
        final var consTimeOfLastHandledTxn = blockRecordManager.consTimeOfLastHandledTxn();
        final var isFirstTransaction = !consTimeOfLastHandledTxn.isAfter(Instant.EPOCH);
        final var recordBuilder = recordListBuilder.userTransactionRecordBuilder();

        cronProcesses.processCronJobs(new SavepointStackImpl(state));

        TransactionBody txBody = null;
        AccountID payer = null;
        Fees fees = null;
        TransactionInfo txnInfo = null;
        HederaFunctionality functionality = NONE;
        Map<AccountID, Long> prePaidRewards = emptyMap();
        try {
            txnInfo = preHandleResult.txInfo();
            if (txnInfo == null) {
                // FUTURE: Charge node generic penalty, set values in record builder, and remove log statement
                logger.error("Bad transaction from creator {}", creator);
                return;
            }
            logStartUserTransaction(platformTxn, txBody, payer);
            // Initialize record builder list
            recordBuilder
                    .transaction(txnInfo.transaction())
                    .transactionBytes(transactionBytesFrom(txnInfo))
                    .transactionID(txnInfo.transactionID())
                    .exchangeRate(exchangeRateManager.exchangeRates())
                    .memo(txBody.memo());

            final var handleContext = handleContextProvider
                    .get()
                    .create(
                            stack,
                            HandleContext.TransactionCategory.USER,
                            keyVerifier,
                            blockRecordManager,
                            recordBuilder)
                    .handleContext();

            final var validationResult = validator.validate(creator.nodeId());
            // Calculate the fee
            fees = dispatcher.dispatchComputeFees((FeeContext) handleContext);

            if (validationResult.status() != SO_FAR_SO_GOOD) {
                recordBuilder.status(validationResult.responseCodeEnum());
                feeLogic.chargeFees(validationResult, creator, fees);
            } else {
                try {
                    // Any hollow accounts that must sign to have all needed signatures, need to be finalized
                    // as a result of transaction being handled.
                    hollowAccountFinalizer.finalizeHollowAccountsIfAny();
                    // If the payer is authorized to waive fees, then we don't charge them
                    feeLogic.chargeFees(validationResult, creator, fees);
                    if (networkUtilizationManager.wasLastTxnGasThrottled()) {
                        // Don't charge the payer the service fee component, because the user-submitted transaction
                        // was fully valid but network capacity was unavailable to satisfy it
                        fees = fees.withoutServiceComponent();
                        throw new HandleException(CONSENSUS_GAS_EXHAUSTED);
                    }

                    // Dispatch the transaction to the handler
                    dispatcher.dispatchHandle(handleContext);
                    // Possibly charge assessed fees for preceding child transactions; but
                    // only if not a contract operation, since these dispatches were already
                    // charged using gas.
                    feeLogic.chargeForPrecedingTxns();

                    // Only ScheduleCreate and ScheduleSign can trigger paid staking rewards via
                    // dispatch; and only if this top-level transaction was successful
                    prePaidRewards = handleContext.dispatchPaidRewards();

                    // Notify responsible facility if system-file was uploaded.
                    // Returns SUCCESS if no system-file was uploaded
                    final var fileUpdateResult = systemFileUpdateFacility.handleTxBody(stack, txBody);

                    recordBuilder
                            .status(SUCCESS)
                            .exchangeRate(exchangeRateManager.exchangeRates())
                            .status(fileUpdateResult);

                    // Notify if platform state was updated
                    platformStateUpdateFacility.handleTxBody(stack, platformState, txBody);

                } catch (final HandleException e) {
                    rollbackAndChargeFees(
                            creator, fees, recordListBuilder, e.getStatus(), e.shouldRollbackStack(), validationResult);
                }
                throttleLogic.updateGasThrottleIfNeeded();
            }
        } catch (final Exception e) {
            logger.error("Possibly CATASTROPHIC failure while handling a user transaction", e);
            if (txnInfo == null) {
                updateRecordBuilder(recordBuilder, platformTxn.getApplicationPayload(), creator);
            }
            rollbackAndChargeFees(creator, fees, recordListBuilder, ResponseCodeEnum.FAIL_INVALID, true, null);
        }

        if (shouldSwitchConsensusSecond(consensusNow, isFirstTransaction, consTimeOfLastHandledTxn)) {
            handleWorkflowMetrics.switchConsensusSecond();
        }

        throttleLogic.manageThrottleSnapshotsAndCapacity();
        try {
            transactionFinalizer.finalizeParentRecord(
                    payer,
                    tokenContext,
                    functionality,
                    extraRewardReceivers(txBody, functionality, recordBuilder),
                    prePaidRewards);
        } catch (final Exception e) {
            logger.error(
                    "Possibly CATASTROPHIC error: failed to finalize parent record for transaction {}",
                    txnInfo.transactionID(),
                    e);
            nullOutSideEffectsAndRollback(recordListBuilder, stack);
        }

        // Commit all state changes
        stack.commitFullStack();

        // store all records at once, build() records end of transaction to log
        final var recordListResult = recordListBuilder.build();
        if (payer != null) { // temporary check to avoid NPE
            recordCache.add(creator.nodeId(), payer, recordListResult.records());
        }
    }

    private void updateRecordBuilder(
            final SingleTransactionRecordBuilderImpl recordBuilder,
            final Bytes applicationPayload,
            final NodeInfo creator) {
        final var baseData = extractTransactionBaseData(applicationPayload);
        if (baseData.transaction() == null) {
            // FUTURE: Charge node generic penalty, set values in record builder, and remove log statement
            logger.error("Failed to parse transaction from creator: {}", creator);
            return;
        }
        final var txBody = baseData.txBody();
        recordBuilder.transaction(baseData.transaction()).transactionBytes(baseData.transactionBytes());
        if (txBody != null && txBody.hasTransactionID()) {
            recordBuilder.transactionID(txBody.transactionIDOrThrow());
        }
    }

    private void rollbackAndChargeFees(
            final @NonNull NodeInfo creator,
            final Fees fees,
            final @NonNull RecordListBuilder recordListBuilder,
            final ResponseCodeEnum responseCodeEnum,
            final boolean shouldRollbackStack,
            final ValidationResult validationResult) {
        // In case of a ContractCall when it reverts, the gas charged should not be rolled back
        rollback(shouldRollbackStack, responseCodeEnum, stack, recordListBuilder);
        if (shouldRollbackStack) {
            // Only re-charge fees if we rolled back the stack
            feeLogic.chargeFees(validationResult, creator, fees);
        }
    }

    private void nullOutSideEffectsAndRollback(
            final @NonNull RecordListBuilder recordListBuilder, final SavepointStackImpl stack) {
        // Undo any changes made to the state
        final var userTransactionRecordBuilder = recordListBuilder.userTransactionRecordBuilder();
        userTransactionRecordBuilder.nullOutSideEffectFields();
        rollback(true, ResponseCodeEnum.FAIL_INVALID, stack, recordListBuilder);
    }

    /**
     * Rolls back the stack and sets the status of the transaction in case of a failure.
     *
     * @param rollbackStack whether to rollback the stack. Will be false when the failure is due to a
     * {@link HandleException} that is due to a contract call revert.
     * @param status the status to set
     * @param stack the save point stack to rollback
     * @param recordListBuilder the record list builder to revert
     */
    private void rollback(
            final boolean rollbackStack,
            @NonNull final ResponseCodeEnum status,
            @NonNull final SavepointStackImpl stack,
            @NonNull final RecordListBuilder recordListBuilder) {
        if (rollbackStack) {
            stack.rollbackFullStack();
        }
        final var userTransactionRecordBuilder = recordListBuilder.userTransactionRecordBuilder();
        userTransactionRecordBuilder.status(status);
        recordListBuilder.revertChildrenOf(userTransactionRecordBuilder);
    }

    private static boolean shouldSwitchConsensusSecond(
            final @NonNull Instant consensusNow,
            final boolean isFirstTransaction,
            final Instant consTimeOfLastHandledTxn) {
        return isFirstTransaction || consensusNow.getEpochSecond() > consTimeOfLastHandledTxn.getEpochSecond();
    }

    private void logStartUserTransaction(
            final @NonNull ConsensusTransaction platformTxn, final TransactionBody txBody, final AccountID payer) {
        // Log start of user transaction to transaction state log
        logStartUserTransaction(platformTxn, txBody, payer);
        logStartUserTransactionPreHandleResultP2(preHandleResult);
        logStartUserTransactionPreHandleResultP3(preHandleResult);
    }

    private Bytes transactionBytesFrom(TransactionInfo txnInfo) {
        final var transaction = txnInfo.transaction();
        if (transaction.signedTransactionBytes().length() > 0) {
            return transaction.signedTransactionBytes();
        } else {
            // in this case, recorder hash the transaction itself, not its bodyBytes.
            return Transaction.PROTOBUF.toBytes(transaction);
        }
    }
}
