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

package com.hedera.node.app.workflows.handle.flow.dispatch.logic;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.hapi.utils.ethereum.EthTxData.populateEthTxData;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;
import static com.hedera.node.app.throttle.ThrottleAccumulator.canAutoCreate;
import static com.hedera.node.app.workflows.handle.flow.txn.WorkDone.FEES_ONLY;
import static com.hedera.node.app.workflows.handle.flow.txn.WorkDone.USER_TRANSACTION;
import static com.hedera.node.app.workflows.handle.flow.util.FlowUtils.CONTRACT_OPERATIONS;
import static com.hedera.node.app.workflows.handle.flow.util.FlowUtils.isContractOperation;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.workflows.handle.flow.dispatch.Dispatch;
import com.hedera.node.app.workflows.handle.flow.txn.WorkDone;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DispatchUsageManager {
    private final NetworkInfo networkInfo;
    private final HandleWorkflowMetrics handleWorkflowMetrics;
    private final ThrottleServiceManager throttleServiceManager;
    private final NetworkUtilizationManager networkUtilizationManager;

    @Inject
    public DispatchUsageManager(
            @NonNull final NetworkInfo networkInfo,
            @NonNull final HandleWorkflowMetrics handleWorkflowMetrics,
            @NonNull final ThrottleServiceManager throttleServiceManager,
            @NonNull final NetworkUtilizationManager networkUtilizationManager) {
        this.networkInfo = networkInfo;
        this.handleWorkflowMetrics = handleWorkflowMetrics;
        this.throttleServiceManager = throttleServiceManager;
        this.networkUtilizationManager = networkUtilizationManager;
    }

    /**
     * Tracks usage of the given dispatch before it is sent to a handler.
     *
     * @param dispatch the dispatch
     * @throws ThrottleException if the dispatch should be throttled
     */
    public void screenForCapacity(@NonNull final Dispatch dispatch) throws ThrottleException {
        if (isContractOperation(dispatch)) {
            networkUtilizationManager.trackTxn(dispatch.txnInfo(), dispatch.consensusNow(), dispatch.stack());
            if (networkUtilizationManager.wasLastTxnGasThrottled()) {
                throw new ThrottleException(CONSENSUS_GAS_EXHAUSTED);
            }
        }
    }

    /**
     * Tracks the final work done by handling this user transaction.
     *
     * @param workDone the work done
     */
    public void trackUsage(@NonNull final Dispatch dispatch, @NonNull final WorkDone workDone) {
        // In the current system we only trackUsage utilization for user transactions
        if (dispatch.txnCategory() != USER) {
            return;
        }
        if (workDone == FEES_ONLY) {
            networkUtilizationManager.trackFeePayments(dispatch.consensusNow(), dispatch.stack());
        } else if (workDone == USER_TRANSACTION) {
            // (FUTURE) When throttling is better encapsulated as a dispatch-scope concern, call trackTxn()
            // in only one place; for now we have already tracked utilization for contract operations
            // at point of dispatch so we could detect CONSENSUS_GAS_EXHAUSTED
            final var function = dispatch.txnInfo().functionality();
            if (!CONTRACT_OPERATIONS.contains(function)) {
                networkUtilizationManager.trackTxn(dispatch.txnInfo(), dispatch.consensusNow(), dispatch.stack());
            } else {
                leakUnusedGas(dispatch);
            }
            if (canAutoCreate(function) && dispatch.recordBuilder().status() != SUCCESS) {
                reclaimFailedCryptoCreateCapacity(dispatch);
            }
        }
        throttleServiceManager.saveThrottleSnapshotsAndCongestionLevelStartsTo(dispatch.stack());
    }

    /**
     * Leaks the unused gas for a contract dispatch.
     *
     * @param dispatch the dispatch
     */
    private void leakUnusedGas(@NonNull final Dispatch dispatch) {
        final var recordBuilder = dispatch.recordBuilder();
        // (FUTURE) There can be cases where the EVM halts and consumes all gas even though not
        // much actual work was done; in such cases, the gas used is still reported to be at
        // least 80% of the gas limit. If we want to be more precise, we can probably use the
        // EVM action tracer to get a better estimate of the actual gas used and the gas limit.
        if (recordBuilder.hasContractResult()) {
            final var gasUsed = recordBuilder.getGasUsedForContractTxn();
            handleWorkflowMetrics.addGasUsed(gasUsed);
            final var contractsConfig = dispatch.config().getConfigData(ContractsConfig.class);
            if (contractsConfig.throttleThrottleByGas()) {
                final var txnInfo = dispatch.txnInfo();
                final var gasLimitForContractTx = getGasLimitForContractTx(txnInfo.txBody(), txnInfo.functionality());
                final var excessAmount = gasLimitForContractTx - gasUsed;
                networkUtilizationManager.leakUnusedGasPreviouslyReserved(txnInfo, excessAmount);
            }
        }
    }

    /**
     * Reclaims the throttle capacity for a failed dispatch that tried to implicitly
     * perform {@link HederaFunctionality#CRYPTO_CREATE} operations.
     *
     * @param dispatch the dispatch
     */
    private void reclaimFailedCryptoCreateCapacity(@NonNull final Dispatch dispatch) {
        final var numImplicitCreations = throttleServiceManager.numImplicitCreations(
                dispatch.txnInfo().txBody(), dispatch.readableStoreFactory().getStore(ReadableAccountStore.class));
        if (usedSelfFrontendThrottleCapacity(
                numImplicitCreations, dispatch.txnInfo().txBody())) {
            throttleServiceManager.reclaimFrontendThrottleCapacity(numImplicitCreations);
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
