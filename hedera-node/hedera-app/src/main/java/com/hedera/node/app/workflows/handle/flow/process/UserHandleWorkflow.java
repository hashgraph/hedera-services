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

import static com.hedera.node.app.throttle.ThrottleAccumulator.canAutoCreate;
import static com.hedera.node.app.workflows.handle.flow.util.DispatchUtils.ALERT_MESSAGE;
import static com.hedera.node.app.workflows.handle.flow.util.DispatchUtils.CONTRACT_OPERATIONS;
import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.workflows.handle.flow.dagger.annotations.UserTxnScope;
import com.hedera.node.app.workflows.handle.flow.dagger.components.UserTransactionComponent;
import com.hedera.node.app.workflows.handle.flow.records.UserRecordInitializer;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
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
public class UserHandleWorkflow {
    private static final Logger logger = LogManager.getLogger(UserHandleWorkflow.class);

    private final SoftwareVersion version;
    private final InitTrigger initTrigger;
    private final RecordListBuilder recordListBuilder;
    private final SkipUserTransactionProcess skipHandleProcess;
    private final MainUserTransactionProcess mainHandleProcess;
    private final GenesisUserTransactionProcess genesisHandleProcess;
    final UserTransactionComponent userTxn;
    private final BlockRecordManager blockRecordManager;
    private final HederaRecordCache recordCache;
    private final NetworkUtilizationManager networkUtilizationManager;
    private final HandleWorkflowMetrics handleWorkflowMetrics;
    private final ThrottleServiceManager throttleServiceManager;
    private final NetworkInfo networkInfo;
    private final UserRecordInitializer userRecordInitializer;

    @Inject
    public UserHandleWorkflow(
            @NonNull final SoftwareVersion version,
            @NonNull final InitTrigger initTrigger,
            @NonNull final RecordListBuilder recordListBuilder,
            @NonNull final SkipUserTransactionProcess skipHandleProcess,
            @NonNull final MainUserTransactionProcess mainHandleProcess,
            final GenesisUserTransactionProcess genesisHandleProcess,
            @NonNull final UserTransactionComponent userTxn,
            final BlockRecordManager blockRecordManager,
            final HederaRecordCache recordCache,
            final NetworkUtilizationManager networkUtilizationManager,
            final HandleWorkflowMetrics handleWorkflowMetrics,
            final ThrottleServiceManager throttleServiceManager,
            final NetworkInfo networkInfo,
            final UserRecordInitializer userRecordInitializer) {
        this.version = version;
        this.initTrigger = initTrigger;
        this.recordListBuilder = recordListBuilder;
        this.skipHandleProcess = skipHandleProcess;
        this.mainHandleProcess = mainHandleProcess;
        this.genesisHandleProcess = genesisHandleProcess;
        this.userTxn = userTxn;
        this.blockRecordManager = blockRecordManager;
        this.recordCache = recordCache;
        this.networkUtilizationManager = networkUtilizationManager;
        this.handleWorkflowMetrics = handleWorkflowMetrics;
        this.throttleServiceManager = throttleServiceManager;
        this.networkInfo = networkInfo;
        this.userRecordInitializer = userRecordInitializer;
    }

    public Stream<SingleTransactionRecord> execute() {
        try {
            return getComputedRecordStream();
        } catch (final Exception e) {
            logger.error("{} - exception thrown while handling user transaction", ALERT_MESSAGE, e);
            return getFailInvalidRecordStream();
        }
    }

    private Stream<SingleTransactionRecord> getFailInvalidRecordStream() {
        final var failInvalidRecordListBuilder = new RecordListBuilder(userTxn.consensusNow());
        final var recordBuilder = failInvalidRecordListBuilder.userTransactionRecordBuilder();
        userRecordInitializer.initializeUserRecord(recordBuilder, userTxn.txnInfo());
        recordBuilder.status(ResponseCodeEnum.FAIL_INVALID);
        userTxn.stack().rollbackFullStack();
        return buildAndCacheResult(failInvalidRecordListBuilder);
    }

    private Stream<SingleTransactionRecord> getComputedRecordStream() {
        if (isOlderSoftwareEvent()) {
            skipHandleProcess.processUserTransaction(userTxn);
        } else {
            final var isFirstTxn = blockRecordManager.consTimeOfLastHandledTxn().equals(Instant.EPOCH);
            if (isFirstTxn) {
                genesisHandleProcess.processUserTransaction(userTxn);
            }
            final var workDone = mainHandleProcess.processUserTransaction(userTxn);
            updateMetrics(isFirstTxn);
            trackUsage(workDone);
        }

        return buildAndCacheResult(userTxn.recordListBuilder());
    }

    private void updateMetrics(final boolean isFirstTxn) {
        if (isFirstTxn
                || userTxn.consensusNow().getEpochSecond()
                        > blockRecordManager.consTimeOfLastHandledTxn().getEpochSecond()) {
            handleWorkflowMetrics.switchConsensusSecond();
        }
    }

    private Stream<SingleTransactionRecord> buildAndCacheResult(RecordListBuilder builder) {
        final var result = builder.build();
        recordCache.add(
                userTxn.creator().nodeId(), requireNonNull(userTxn.txnInfo().payerID()), result.records());
        return result.records().stream();
    }

    private void trackUsage(final WorkDone workDone) {
        if (workDone == WorkDone.FEES_ONLY) {
            networkUtilizationManager.trackFeePayments(userTxn.consensusNow(), userTxn.stack());
        } else if (workDone == WorkDone.USER_TRANSACTION) {
            networkUtilizationManager.trackTxn(userTxn.txnInfo(), userTxn.consensusNow(), userTxn.stack());
            if (CONTRACT_OPERATIONS.contains(userTxn.txnInfo().functionality())) {
                leakUnusedGas(userTxn.recordListBuilder().userTransactionRecordBuilder());
            } else if (canAutoCreate(userTxn.functionality())) {
                reclaimCryptoCreateThrottle(userTxn.recordListBuilder().userTransactionRecordBuilder());
            }
        }
        throttleServiceManager.saveThrottleSnapshotsAndCongestionLevelStartsTo(userTxn.stack());
    }

    private void reclaimCryptoCreateThrottle(final SingleTransactionRecordBuilderImpl recordBuilder) {
        if (recordBuilder.status() != ResponseCodeEnum.SUCCESS) {
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

    private boolean usedSelfFrontendThrottleCapacity(
            final int numImplicitCreations, @NonNull final TransactionBody txnBody) {
        return numImplicitCreations > 0
                && txnBody.nodeAccountIDOrThrow()
                        .equals(networkInfo.selfNodeInfo().accountId());
    }

    private void leakUnusedGas(final SingleTransactionRecordBuilderImpl recordBuilder) {
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

    private boolean isOlderSoftwareEvent() {
        return this.initTrigger != EVENT_STREAM_RECOVERY
                && version.compareTo(userTxn.platformEvent().getSoftwareVersion()) > 0;
    }

    private static long getGasLimitForContractTx(final TransactionBody txnBody, final HederaFunctionality function) {
        return switch (function) {
            case CONTRACT_CREATE -> txnBody.contractCreateInstance().gas();
            case CONTRACT_CALL -> txnBody.contractCall().gas();
            case ETHEREUM_TRANSACTION -> getGasLimitFromEthTxData(txnBody);
            default -> 0L;
        };
    }

    private static long getGasLimitFromEthTxData(final TransactionBody txn) {
        final var ethTxBody = txn.ethereumTransaction();
        if (ethTxBody == null) return 0L;
        final var ethTxData =
                EthTxData.populateEthTxData(ethTxBody.ethereumData().toByteArray());
        return ethTxData != null ? ethTxData.gasLimit() : 0L;
    }
}
