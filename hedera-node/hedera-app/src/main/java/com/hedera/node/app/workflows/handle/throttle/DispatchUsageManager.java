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

package com.hedera.node.app.workflows.handle.throttle;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.workflows.handle.Dispatch;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumSet;
import java.util.Set;

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_ASSOCIATE_TO_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.hapi.utils.ethereum.EthTxData.populateEthTxData;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;
import static com.hedera.node.app.throttle.ThrottleAccumulator.canAutoAssociate;
import static com.hedera.node.app.throttle.ThrottleAccumulator.canAutoCreate;
import static java.util.Objects.requireNonNull;

@Singleton
public class DispatchUsageManager {
    public static final Set<HederaFunctionality> CONTRACT_OPERATIONS =
            EnumSet.of(HederaFunctionality.CONTRACT_CREATE, HederaFunctionality.CONTRACT_CALL, ETHEREUM_TRANSACTION);

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
     * Tracks usage of the given dispatch before it is sent to a handler. This is only checked for contract
     * operations now. This code will be moved into the contract-service module in the future.
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
        switch (workDone) {
            case FEES_ONLY -> networkUtilizationManager.trackFeePayments(dispatch.consensusNow(), dispatch.stack());
            case USER_TRANSACTION -> {
                // (FUTURE) When throttling is better encapsulated as a dispatch-scope concern, call trackTxn()
                // in only one place; for now we have already tracked utilization for contract operations
                // at point of dispatch so we could detect CONSENSUS_GAS_EXHAUSTED
                final var function = dispatch.txnInfo().functionality();
                if (!CONTRACT_OPERATIONS.contains(function)) {
                    networkUtilizationManager.trackTxn(dispatch.txnInfo(), dispatch.consensusNow(), dispatch.stack());
                } else {
                    leakUnusedGas(dispatch);
                }
                if (dispatch.recordBuilder().status() != SUCCESS) {
                    if (canAutoCreate(function)) {
                        reclaimFailedCryptoCreateCapacity(dispatch);
                    }
                    if (canAutoAssociate(function)) {
                        reclaimFailedTokenAssociate(dispatch);
                    }
                }
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
        final var readableAccountStore = dispatch.readableStoreFactory().getStore(ReadableAccountStore.class);
        final var numImplicitCreations =
                throttleServiceManager.numImplicitCreations(dispatch.txnInfo().txBody(), readableAccountStore);
        if (usedSelfFrontendThrottleCapacity(
                numImplicitCreations, dispatch.txnInfo().txBody())) {
            throttleServiceManager.reclaimFrontendThrottleCapacity(numImplicitCreations, CRYPTO_CREATE);
        }
    }

    /**
     * Reclaims the throttle capacity for a failed dispatch that tried to
     * perform {@link HederaFunctionality#TOKEN_ASSOCIATE_TO_ACCOUNT} operations for Auto Association.
     *
     * @param dispatch the dispatch
     */
    private void reclaimFailedTokenAssociate(@NonNull final Dispatch dispatch) {
        final var readableTokenRelStore = dispatch.readableStoreFactory().getStore(ReadableTokenRelationStore.class);
        final var numAutoAssociations =
                throttleServiceManager.numAutoAssociations(dispatch.txnInfo().txBody(), readableTokenRelStore);
        if (usedSelfFrontendThrottleCapacity(
                numAutoAssociations, dispatch.txnInfo().txBody())) {
            throttleServiceManager.reclaimFrontendThrottleCapacity(numAutoAssociations, TOKEN_ASSOCIATE_TO_ACCOUNT);
        }
    }

    /**
     * Returns true if the transaction used frontend throttle capacity on this node.
     *
     * @param numUsedCapacity the number of used capacity for either create ot auto associate operations
     * @param txnBody the transaction body
     * @return true if the transaction used frontend throttle capacity on this node
     */
    private boolean usedSelfFrontendThrottleCapacity(
            final int numUsedCapacity, @NonNull final TransactionBody txnBody) {
        return numUsedCapacity > 0
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
        if (function == CONTRACT_CREATE) {
            return txnBody.contractCreateInstanceOrElse(ContractCreateTransactionBody.DEFAULT)
                    .gas();
        } else if (function == ETHEREUM_TRANSACTION) {
            final var rawEthTxn = txnBody.ethereumTransactionOrElse(EthereumTransactionBody.DEFAULT);
            final var ethTxData = populateEthTxData(rawEthTxn.ethereumData().toByteArray());
            return ethTxData != null ? ethTxData.gasLimit() : 0L;
        } else {
            return txnBody.contractCallOrElse(ContractCallTransactionBody.DEFAULT)
                    .gas();
        }
    }

    /**
     * Checks if the given dispatch is a contract operation.
     * @param dispatch the dispatch
     * @return true if the dispatch is a contract operation, false otherwise
     */
    public static boolean isContractOperation(@NonNull Dispatch dispatch) {
        return CONTRACT_OPERATIONS.contains(dispatch.txnInfo().functionality());
    }

    /**
     * The work done by the dispatch. It can be either {@link WorkDone#FEES_ONLY} or {@link WorkDone#USER_TRANSACTION}.
     * {@link WorkDone#FEES_ONLY} is returned when the transaction has node or user errors. Otherwise, it will be
     * {@link WorkDone#USER_TRANSACTION}.
     */
    public enum WorkDone {
        FEES_ONLY,
        USER_TRANSACTION
    }

    /**
     * This class is used to throw a {@link ThrottleException} when a transaction is gas throttled.
     */
    public static class ThrottleException extends Exception {
        private final ResponseCodeEnum status;

        public ThrottleException(@NonNull final ResponseCodeEnum status) {
            this.status = requireNonNull(status);
        }

        /**
         * Gets the status of the exception.
         *
         * @return the status
         */
        public ResponseCodeEnum getStatus() {
            return status;
        }
    }
}
