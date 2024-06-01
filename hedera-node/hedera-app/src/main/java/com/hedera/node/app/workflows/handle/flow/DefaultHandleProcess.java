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
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransaction;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransactionPreHandleResultP2;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransactionPreHandleResultP3;
import static com.hedera.node.app.throttle.ThrottleAccumulator.canAutoCreate;
import static com.hedera.node.app.throttle.ThrottleAccumulator.isGasThrottled;
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
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.HandleContextImpl;
import com.hedera.node.app.workflows.handle.flow.fees.FeeLogic;
import com.hedera.node.app.workflows.handle.flow.infra.CronProcessingLogic;
import com.hedera.node.app.workflows.handle.flow.infra.HandleValidations;
import com.hedera.node.app.workflows.handle.flow.infra.HollowAccountFinalizer;
import com.hedera.node.app.workflows.handle.flow.infra.PreHandleLogic;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class DefaultHandleProcess implements HandleProcess {
    private static final Logger logger = LogManager.getLogger(DefaultHandleProcess.class);
    private final BlockRecordManager blockRecordManager;
    private final PreHandleLogic preHandleLogic;
    private final CronProcessingLogic cronProcesses;
    private final HandleContextImpl handleContext;
    private final ExchangeRateManager exchangeRateManager;
    private final HandleValidations validator;
    private final FeeLogic feeLogic;
    private final HollowAccountFinalizer hollowAccountFinalizer;

    @Inject
    public DefaultHandleProcess(
            final BlockRecordManager blockRecordManager,
            final PreHandleLogic preHandleLogic,
            final CronProcessingLogic cronProcesses,
            final HandleContextImpl handleContext,
            final ExchangeRateManager exchangeRateManager,
            final HandleValidations validator,
            final FeeLogic feeLogic,
            final HollowAccountFinalizer hollowAccountFinalizer) {
        this.blockRecordManager = blockRecordManager;
        this.preHandleLogic = preHandleLogic;
        this.cronProcesses = cronProcesses;
        this.handleContext = handleContext;
        this.exchangeRateManager = exchangeRateManager;
        this.validator = validator;
        this.feeLogic = feeLogic;
        this.hollowAccountFinalizer = hollowAccountFinalizer;
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

        final var stack = new SavepointStackImpl(state);
        cronProcesses.processCronJobs(new SavepointStackImpl(state));

        TransactionBody txBody = null;
        AccountID payer = null;
        Fees fees = null;
        TransactionInfo transactionInfo = null;
        HederaFunctionality functionality = NONE;
        Map<AccountID, Long> prePaidRewards = emptyMap();
        try {
            final var preHandleResult = preHandleLogic.getCurrentPreHandleResult(creator, platformTxn);
            transactionInfo = preHandleResult.txInfo();

            if (transactionInfo == null) {
                // FUTURE: Charge node generic penalty, set values in record builder, and remove log statement
                logger.error("Bad transaction from creator {}", creator);
                return;
            }

            // Get the parsed data
            final var transaction = transactionInfo.transaction();
            txBody = transactionInfo.txBody();
            payer = transactionInfo.payerID();
            functionality = transactionInfo.functionality();

            final Bytes transactionBytes;
            if (transaction.signedTransactionBytes().length() > 0) {
                transactionBytes = transaction.signedTransactionBytes();
            } else {
                // in this case, recorder hash the transaction itself, not its bodyBytes.
                transactionBytes = Transaction.PROTOBUF.toBytes(transaction);
            }

            // Log start of user transaction to transaction state log
            logStartUserTransaction(platformTxn, txBody, payer);
            logStartUserTransactionPreHandleResultP2(preHandleResult);
            logStartUserTransactionPreHandleResultP3(preHandleResult);

            // Initialize record builder list
            recordBuilder
                    .transaction(transactionInfo.transaction())
                    .transactionBytes(transactionBytes)
                    .transactionID(transactionInfo.transactionID())
                    .exchangeRate(exchangeRateManager.exchangeRates())
                    .memo(txBody.memo());

            final var validationResult = validator.validate(creator.nodeId());
            if (validationResult.status() != SO_FAR_SO_GOOD) {
                feeLogic.chargeFees(validationResult, creator);
            } else {
                try {
                    // Any hollow accounts that must sign to have all needed signatures, need to be finalized
                    // as a result of transaction being handled.
                    hollowAccountFinalizer.finalizeHollowAccountsIfAny();

                    // If the payer is authorized to waive fees, then we don't charge them
                    feeLogic.chargeFees(validationResult, creator);

                    if (networkUtilizationManager.wasLastTxnGasThrottled()) {
                        // Don't charge the payer the service fee component, because the user-submitted transaction
                        // was fully valid but network capacity was unavailable to satisfy it
                        fees = fees.withoutServiceComponent();
                        throw new HandleException(CONSENSUS_GAS_EXHAUSTED);
                    }

                    // Dispatch the transaction to the handler
                    dispatcher.dispatchHandle(context);
                    // Possibly charge assessed fees for preceding child transactions; but
                    // only if not a contract operation, since these dispatches were already
                    // charged using gas. [FUTURE - stop setting transactionFee in recordBuilder
                    // at the point of dispatch, so we no longer need this special case here.]
                    final var isContractOp = DISPATCHING_CONTRACT_TRANSACTIONS.contains(functionality);
                    if (!isContractOp
                            && !recordListBuilder.precedingRecordBuilders().isEmpty()) {
                        // We intentionally charge fees even if the transaction failed (may need to update
                        // mono-service to this behavior?)
                        final var childFees = recordListBuilder.precedingRecordBuilders().stream()
                                .mapToLong(SingleTransactionRecordBuilderImpl::transactionFee)
                                .sum();
                        // If the payer is authorized to waive fees, then we don't charge them
                        if (!hasWaivedFees && !feeAccumulator.chargeNetworkFee(payer, childFees)) {
                            throw new HandleException(INSUFFICIENT_PAYER_BALANCE);
                        }
                    }
                    recordBuilder.status(SUCCESS);
                    // Only ScheduleCreate and ScheduleSign can trigger paid staking rewards via
                    // dispatch; and only if this top-level transaction was successful
                    prePaidRewards = context.dispatchPaidRewards();

                    // Notify responsible facility if system-file was uploaded.
                    // Returns SUCCESS if no system-file was uploaded
                    final var fileUpdateResult = systemFileUpdateFacility.handleTxBody(stack, txBody);

                    recordBuilder
                            .exchangeRate(exchangeRateManager.exchangeRates())
                            .status(fileUpdateResult);

                    // Notify if platform state was updated
                    platformStateUpdateFacility.handleTxBody(stack, platformState, txBody);

                } catch (final HandleException e) {
                    // In case of a ContractCall when it reverts, the gas charged should not be rolled back
                    rollback(e.shouldRollbackStack(), e.getStatus(), stack, recordListBuilder);
                    if (!hasWaivedFees && e.shouldRollbackStack()) {
                        // Only re-charge fees if we rolled back the stack
                        feeAccumulator.chargeFees(payer, creator.accountId(), fees);
                    }
                }

                // After a contract operation was handled (i.e., not throttled), update the
                // gas throttle by leaking any unused gas
                if (isGasThrottled(functionality)
                        && recordBuilder.status() != CONSENSUS_GAS_EXHAUSTED
                        && recordBuilder.hasContractResult()) {
                    final var gasUsed = recordBuilder.getGasUsedForContractTxn();
                    handleWorkflowMetrics.addGasUsed(gasUsed);
                    final var contractsConfig = configuration.getConfigData(ContractsConfig.class);
                    if (contractsConfig.throttleThrottleByGas()) {
                        final var gasLimitForContractTx =
                                getGasLimitForContractTx(transactionInfo.txBody(), functionality);
                        final var excessAmount = gasLimitForContractTx - gasUsed;
                        networkUtilizationManager.leakUnusedGasPreviouslyReserved(transactionInfo, excessAmount);
                    }
                }
            }
        } catch (final Exception e) {
            logger.error("Possibly CATASTROPHIC failure while handling a user transaction", e);
            if (transactionInfo == null) {
                final var baseData = extractTransactionBaseData(platformTxn.getApplicationPayload());
                if (baseData.transaction() == null) {
                    // FUTURE: Charge node generic penalty, set values in record builder, and remove log statement
                    logger.error("Failed to parse transaction from creator: {}", creator);
                    return;
                }
                functionality = baseData.functionality();
                txBody = baseData.txBody();
                payer = baseData.payer();
                recordBuilder.transaction(baseData.transaction()).transactionBytes(baseData.transactionBytes());
                if (txBody != null && txBody.hasTransactionID()) {
                    recordBuilder.transactionID(txBody.transactionIDOrThrow());
                }
            }
            // We should always rollback stack including gas charges when there is an unexpected exception
            rollback(true, ResponseCodeEnum.FAIL_INVALID, stack, recordListBuilder);
            if (payer != null && fees != null) {
                try {
                    feeAccumulator.chargeFees(payer, creator.accountId(), fees);
                } catch (final Exception chargeException) {
                    logger.error(
                            "Unable to charge account {} a penalty after an unexpected exception {}. Cause of the failed charge:",
                            payer,
                            e,
                            chargeException);
                }
            }
        }

        if (isFirstTransaction || consensusNow.getEpochSecond() > consTimeOfLastHandledTxn.getEpochSecond()) {
            handleWorkflowMetrics.switchConsensusSecond();
        }

        // If a transaction appeared to try an auto-creation, and hence used
        // frontend throttle capacity; but then failed, we need to reclaim the
        // frontend throttle capacity on the node that submitted the transaction
        if (txBody != null && canAutoCreate(functionality) && recordBuilder.status() != SUCCESS) {
            final var numImplicitCreations = throttleServiceManager.numImplicitCreations(
                    txBody, tokenServiceContext.readableStore(ReadableAccountStore.class));
            if (usedSelfFrontendThrottleCapacity(numImplicitCreations, txBody)) {
                throttleServiceManager.reclaimFrontendThrottleCapacity(numImplicitCreations);
            }
        }

        throttleServiceManager.saveThrottleSnapshotsAndCongestionLevelStartsTo(stack);
        try {
            transactionFinalizer.finalizeParentRecord(
                    payer,
                    tokenServiceContext,
                    functionality,
                    extraRewardReceivers(txBody, functionality, recordBuilder),
                    prePaidRewards);
        } catch (final Exception e) {
            logger.error(
                    "Possibly CATASTROPHIC error: failed to finalize parent record for transaction {}",
                    transactionInfo.transactionID(),
                    e);

            // Undo any changes made to the state
            final var userTransactionRecordBuilder = recordListBuilder.userTransactionRecordBuilder();
            userTransactionRecordBuilder.nullOutSideEffectFields();
            rollback(true, ResponseCodeEnum.FAIL_INVALID, stack, recordListBuilder);
        }

        // Commit all state changes
        stack.commitFullStack();

        // store all records at once, build() records end of transaction to log
        final var recordListResult = recordListBuilder.build();
        if (payer != null) { // temporary check to avoid NPE
            recordCache.add(creator.nodeId(), payer, recordListResult.records());
        }
    }
}
