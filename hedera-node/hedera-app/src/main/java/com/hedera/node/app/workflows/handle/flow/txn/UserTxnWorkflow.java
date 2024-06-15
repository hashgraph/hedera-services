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

import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.hapi.utils.ethereum.EthTxData.populateEthTxData;
import static com.hedera.node.app.throttle.ThrottleAccumulator.canAutoCreate;
import static com.hedera.node.app.workflows.handle.flow.txn.WorkDone.FEES_ONLY;
import static com.hedera.node.app.workflows.handle.flow.txn.WorkDone.USER_TRANSACTION;
import static com.hedera.node.app.workflows.handle.flow.util.FlowUtils.ALERT_MESSAGE;
import static com.hedera.node.app.workflows.handle.flow.util.FlowUtils.CONTRACT_OPERATIONS;
import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.logic.UserRecordInitializer;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.hedera.node.app.workflows.handle.record.GenesisRecordsConsensusHook;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@UserTxnScope
public class UserTxnWorkflow {
    private static final Logger logger = LogManager.getLogger(UserTxnWorkflow.class);

    private final SoftwareVersion version;
    private final InitTrigger initTrigger;
    private final SkipHandleWorkflow skipHandleWorkflow;
    private final DefaultHandleWorkflow defaultHandleWorkflow;
    private final GenesisRecordsConsensusHook genesisRecordsHook;
    private final UserTransactionComponent userTxn;
    private final BlockRecordManager blockRecordManager;
    private final HederaRecordCache recordCache;
    private final NetworkUtilizationManager networkUtilizationManager;
    private final HandleWorkflowMetrics handleWorkflowMetrics;
    private final ThrottleServiceManager throttleServiceManager;
    private final NetworkInfo networkInfo;
    private final UserRecordInitializer userRecordInitializer;

    @Inject
    public UserTxnWorkflow(
            @NonNull final SoftwareVersion version,
            @NonNull final InitTrigger initTrigger,
            @NonNull final SkipHandleWorkflow skipHandleWorkflow,
            @NonNull final DefaultHandleWorkflow defaultHandleWorkflow,
            @NonNull final GenesisRecordsConsensusHook genesisRecordsHook,
            @NonNull final UserTransactionComponent userTxn,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final HederaRecordCache recordCache,
            @NonNull final NetworkUtilizationManager networkUtilizationManager,
            @NonNull final HandleWorkflowMetrics handleWorkflowMetrics,
            @NonNull final ThrottleServiceManager throttleServiceManager,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final UserRecordInitializer userRecordInitializer) {
        this.version = requireNonNull(version);
        this.initTrigger = requireNonNull(initTrigger);
        this.skipHandleWorkflow = requireNonNull(skipHandleWorkflow);
        this.defaultHandleWorkflow = requireNonNull(defaultHandleWorkflow);
        this.genesisRecordsHook = requireNonNull(genesisRecordsHook);
        this.userTxn = requireNonNull(userTxn);
        this.blockRecordManager = requireNonNull(blockRecordManager);
        this.recordCache = requireNonNull(recordCache);
        this.networkUtilizationManager = requireNonNull(networkUtilizationManager);
        this.handleWorkflowMetrics = requireNonNull(handleWorkflowMetrics);
        this.throttleServiceManager = requireNonNull(throttleServiceManager);
        this.networkInfo = requireNonNull(networkInfo);
        this.userRecordInitializer = requireNonNull(userRecordInitializer);
    }

    /**
     * Executes the user transaction and returns a stream of records that capture all
     * side effects on state that are stipulated by the pre-block-stream contract with
     * mirror nodes.
     *
     * <p>Never throws an exception without a fundamental breakdown in the integrity
     * of the system invariants. If there is an internal error when executing the
     * transaction, returns a stream of a single {@link ResponseCodeEnum#FAIL_INVALID}
     * record with no other side effects.
     *
     * <p><b>IMPORTANT:</b> With block streams, this contract will expand to include
     * all side effects on state, no exceptions.
     *
     * @return the stream of records
     */
    public Stream<SingleTransactionRecord> execute() {
        try {
            return sideEffectsOfExecution();
        } catch (final Exception e) {
            logger.error("{} - exception thrown while handling user transaction", ALERT_MESSAGE, e);
            return getFailInvalidRecordStream();
        }
    }

    /**
     * Executes the user transaction and returns a stream of records that capture all
     * side effects on state that are stipulated by the pre-block-stream contract with
     * mirror nodes.
     *
     * @return the stream of records
     */
    private Stream<SingleTransactionRecord> sideEffectsOfExecution() {
        if (isOlderSoftwareEvent()) {
            skipHandleWorkflow.execute(userTxn);
        } else {
            final var isGenesisTxn = userTxn.lastHandledConsensusTime().equals(Instant.EPOCH);
            if (isGenesisTxn) {
                genesisRecordsHook.process(userTxn.tokenContext());
            }
            final var workDone = defaultHandleWorkflow.execute(userTxn);
            updateMetrics(isGenesisTxn);
            trackUsage(workDone);
        }
        return buildAndCacheResult(userTxn.recordListBuilder());
    }

    /**
     * Returns a stream of a single {@link ResponseCodeEnum#FAIL_INVALID} record.
     *
     * @return the failure record
     */
    private Stream<SingleTransactionRecord> getFailInvalidRecordStream() {
        final var failInvalidRecordListBuilder = new RecordListBuilder(userTxn.consensusNow());
        final var recordBuilder = failInvalidRecordListBuilder.userTransactionRecordBuilder();
        userRecordInitializer.initializeUserRecord(recordBuilder, userTxn.txnInfo());
        recordBuilder.status(FAIL_INVALID);
        userTxn.stack().rollbackFullStack();
        return buildAndCacheResult(failInvalidRecordListBuilder);
    }

    /**
     * Builds and caches the result of the user transaction.
     *
     * @param builder the record list builder
     * @return the stream of records
     */
    private Stream<SingleTransactionRecord> buildAndCacheResult(@NonNull final RecordListBuilder builder) {
        final var result = builder.build();
        recordCache.add(
                userTxn.creator().nodeId(), requireNonNull(userTxn.txnInfo().payerID()), result.records());
        return result.records().stream();
    }

    /**
     * Tracks the work done by handling this user transaction.
     *
     * @param workDone the work done
     */
    private void trackUsage(@NonNull final WorkDone workDone) {
        if (workDone == FEES_ONLY) {
            networkUtilizationManager.trackFeePayments(userTxn.consensusNow(), userTxn.stack());
        } else if (workDone == USER_TRANSACTION) {
            // (FUTURE) When throttling is better encapsulated as a dispatch-scope concern,
            // call trackTxn() in one place only in the DispatchProcessor; for now this is
            // best way to continue simulating legacy behavior
            if (!CONTRACT_OPERATIONS.contains(userTxn.txnInfo().functionality())) {
                // We track utilization for contract operations the DispatchProcessor
                networkUtilizationManager.trackTxn(userTxn.txnInfo(), userTxn.consensusNow(), userTxn.stack());
            } else {
                leakUnusedGas(userTxn.recordListBuilder().userTransactionRecordBuilder());
            }
            if (canAutoCreate(userTxn.functionality())) {
                reclaimCryptoCreateCapacityIfFailed(userTxn.recordListBuilder().userTransactionRecordBuilder());
            }
        }
        throttleServiceManager.saveThrottleSnapshotsAndCongestionLevelStartsTo(userTxn.stack());
    }

    /**
     * Updates the metrics for the workflow.
     *
     * @param isFirstTxn true if this is the first transaction
     */
    private void updateMetrics(final boolean isFirstTxn) {
        if (isFirstTxn
                || userTxn.consensusNow().getEpochSecond()
                        > blockRecordManager.consTimeOfLastHandledTxn().getEpochSecond()) {
            handleWorkflowMetrics.switchConsensusSecond();
        }
    }

    /**
     * Reclaims the throttle capacity for a failed {@link HederaFunctionality#CRYPTO_CREATE} transaction .
     *
     * @param recordBuilder the record builder
     */
    private void reclaimCryptoCreateCapacityIfFailed(@NonNull final SingleTransactionRecordBuilderImpl recordBuilder) {
        if (recordBuilder.status() != SUCCESS) {
            final var numImplicitCreations = throttleServiceManager.numImplicitCreations(
                    userTxn.txnInfo().txBody(),
                    // userTxn.readableStoreFactory has extra one layer of indirection through the stack.
                    // So use the userTxn.tokenContext() to get the readable store.
                    userTxn.tokenContext().readableStore(ReadableAccountStore.class));
            if (usedSelfFrontendThrottleCapacity(
                    numImplicitCreations, userTxn.txnInfo().txBody())) {
                throttleServiceManager.reclaimFrontendThrottleCapacity(numImplicitCreations);
            }
        }
    }

    /**
     * Returns true if the transaction used frontend throttle capacity on this node.
     *
     * @param numImplicitCreations the number of implicit creations
     * @param txnBody the transaction body
     * @return true if the transaction used frontend throttle capacity on this node
     */
    private boolean usedSelfFrontendThrottleCapacity(
            final int numImplicitCreations, @NonNull final TransactionBody txnBody) {
        return numImplicitCreations > 0
                && txnBody.nodeAccountIDOrThrow()
                        .equals(networkInfo.selfNodeInfo().accountId());
    }

    /**
     * Leaks the unused gas for a contract transaction.
     *
     * @param recordBuilder the record builder
     */
    private void leakUnusedGas(final SingleTransactionRecordBuilderImpl recordBuilder) {
        // (FUTURE) There can be cases where the EVM halts and consumes all gas even though not
        // much actual work was done; in such cases, the gas used is still reported to be at
        // least 80% of the gas limit. If we want to be more precise, we can probably use the
        // EVM action tracer to get a better estimate of the actual gas used and the gas limit.
        if (recordBuilder.hasContractResult()) {
            final var gasUsed = recordBuilder.getGasUsedForContractTxn();
            handleWorkflowMetrics.addGasUsed(gasUsed);
            final var contractsConfig = userTxn.configuration().getConfigData(ContractsConfig.class);
            if (contractsConfig.throttleThrottleByGas()) {
                final var gasLimitForContractTx =
                        getGasLimitForContractTx(userTxn.txnInfo().txBody(), userTxn.functionality());
                final var excessAmount = gasLimitForContractTx - gasUsed;
                networkUtilizationManager.leakUnusedGasPreviouslyReserved(userTxn.txnInfo(), excessAmount);
            }
        }
    }

    /**
     * Returns true if the software event is older than the current software version.
     *
     * @return true if the software event is older than the current software version
     */
    private boolean isOlderSoftwareEvent() {
        return this.initTrigger != EVENT_STREAM_RECOVERY
                && version.compareTo(userTxn.platformEvent().getSoftwareVersion()) > 0;
    }

    /**
     * Returns the gas limit for a contract transaction.
     *
     * @param txnBody the transaction body
     * @param function the functionality
     * @return the gas limit for a contract transaction
     */
    private static long getGasLimitForContractTx(
            @NonNull final TransactionBody txnBody, @NonNull final HederaFunctionality function) {
        return switch (function) {
            case CONTRACT_CREATE -> txnBody.contractCreateInstanceOrElse(ContractCreateTransactionBody.DEFAULT)
                    .gas();
            case CONTRACT_CALL -> txnBody.contractCallOrElse(ContractCallTransactionBody.DEFAULT)
                    .gas();
            case ETHEREUM_TRANSACTION -> getGasLimitFromEthTxData(txnBody);
            default -> 0L;
        };
    }

    /**
     * Returns the gas limit for an Ethereum transaction.
     *
     * @param txn the transaction
     * @return the gas limit for an Ethereum transaction
     */
    private static long getGasLimitFromEthTxData(@NonNull final TransactionBody txn) {
        if (!txn.hasEthereumTransaction()) {
            return 0L;
        }
        final var ethTxData = populateEthTxData(
                txn.ethereumTransactionOrThrow().ethereumData().toByteArray());
        return ethTxData != null ? ethTxData.gasLimit() : 0L;
    }
}
