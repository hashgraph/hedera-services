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

package com.hedera.node.app.workflows.handle.flow.infra;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.throttle.ThrottleAccumulator.canAutoCreate;
import static com.hedera.node.app.throttle.ThrottleAccumulator.isGasThrottled;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

public class ThrottleLogic {
    private final ThrottleServiceManager throttleServiceManager;
    private final TransactionInfo txnInfo;
    private final TokenContext tokenContext;
    private final RecordListBuilder recordListBuilder;
    private final SavepointStackImpl stack;
    private final NetworkInfo networkInfo;
    private final HandleWorkflowMetrics handleWorkflowMetrics;
    private final NetworkUtilizationManager networkUtilizationManager;
    private final Configuration configuration;

    @Inject
    public ThrottleLogic(
            final ThrottleServiceManager throttleServiceManager,
            final TransactionInfo txnInfo,
            final TokenContext tokenContext,
            final RecordListBuilder recordListBuilder,
            final SavepointStackImpl stack,
            final NetworkInfo networkInfo,
            final HandleWorkflowMetrics handleWorkflowMetrics,
            final NetworkUtilizationManager networkUtilizationManager,
            final Configuration configuration) {
        this.throttleServiceManager = throttleServiceManager;
        this.txnInfo = txnInfo;
        this.tokenContext = tokenContext;
        this.recordListBuilder = recordListBuilder;
        this.stack = stack;
        this.networkInfo = networkInfo;
        this.handleWorkflowMetrics = handleWorkflowMetrics;
        this.networkUtilizationManager = networkUtilizationManager;
        this.configuration = configuration;
    }

    public void manageThrottleSnapshotsAndCapacity() {
        final var txnBody = txnInfo.txBody();
        final var functionality = txnInfo.functionality();
        final var recordBuilder = recordListBuilder.userTransactionRecordBuilder();

        // If a transaction appeared to try an auto-creation, and hence used
        // frontend throttle capacity; but then failed, we need to reclaim the
        // frontend throttle capacity on the node that submitted the transaction
        reclaimCapacityIfNeeded(txnBody, functionality, recordBuilder);
        throttleServiceManager.saveThrottleSnapshotsAndCongestionLevelStartsTo(stack);
    }

    public void updateGasThrottleIfNeeded() {
        final var txnBody = txnInfo.txBody();
        final var functionality = txnInfo.functionality();
        final var recordBuilder = recordListBuilder.userTransactionRecordBuilder();

        // After a contract operation was handled (i.e., not throttled), update the
        // gas throttle by leaking any unused gas
        if (isGasThrottled(functionality)
                && recordBuilder.status() != CONSENSUS_GAS_EXHAUSTED
                && recordBuilder.hasContractResult()) {
            final var gasUsed = recordBuilder.getGasUsedForContractTxn();
            handleWorkflowMetrics.addGasUsed(gasUsed);
            final var contractsConfig = configuration.getConfigData(ContractsConfig.class);
            if (contractsConfig.throttleThrottleByGas()) {
                final var gasLimitForContractTx = getGasLimitForContractTx(txnBody, functionality);
                final var excessAmount = gasLimitForContractTx - gasUsed;
                networkUtilizationManager.leakUnusedGasPreviouslyReserved(txnInfo, excessAmount);
            }
        }
    }

    private void reclaimCapacityIfNeeded(
            final TransactionBody txnBody,
            final HederaFunctionality functionality,
            final SingleTransactionRecordBuilderImpl recordBuilder) {
        if (isUnSuccessfulAutoCreation(txnBody, functionality, recordBuilder)) {
            final var numImplicitCreations = throttleServiceManager.numImplicitCreations(
                    txnBody, tokenContext.readableStore(ReadableAccountStore.class));
            if (usedSelfFrontendThrottleCapacity(numImplicitCreations, txnBody)) {
                throttleServiceManager.reclaimFrontendThrottleCapacity(numImplicitCreations);
            }
        }
    }

    private static boolean isUnSuccessfulAutoCreation(
            final TransactionBody txnBody,
            final HederaFunctionality functionality,
            final SingleTransactionRecordBuilderImpl recordBuilder) {
        return txnBody != null && canAutoCreate(functionality) && recordBuilder.status() != SUCCESS;
    }

    private boolean usedSelfFrontendThrottleCapacity(
            final int numImplicitCreations, @NonNull final TransactionBody txnBody) {
        return numImplicitCreations > 0
                && txnBody.nodeAccountIDOrThrow()
                        .equals(networkInfo.selfNodeInfo().accountId());
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
