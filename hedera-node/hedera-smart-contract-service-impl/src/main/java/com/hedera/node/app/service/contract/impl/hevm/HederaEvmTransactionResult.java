/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.hevm;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.errorMessageFor;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.accessTrackerFor;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.proxyUpdaterFor;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asPbjStateChanges;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.bloomForAll;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjLogsFrom;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.streams.ContractStateChanges;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason;
import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.StorageAccesses;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.List;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

public record HederaEvmTransactionResult(
        long gasUsed,
        long gasPrice,
        @NonNull AccountID senderId,
        @Nullable ContractID recipientId,
        @Nullable ContractID recipientEvmAddress,
        @NonNull Bytes output,
        @Nullable ExceptionalHaltReason haltReason,
        @Nullable Bytes revertReason,
        @NonNull List<Log> logs,
        @Nullable ContractStateChanges stateChanges) {
    public HederaEvmTransactionResult {
        requireNonNull(senderId);
        requireNonNull(output);
        requireNonNull(logs);
    }

    private static final Bytes MAX_STORAGE_EXCEEDED_REASON = Bytes.wrap(MAX_CONTRACT_STORAGE_EXCEEDED.name());
    private static final Bytes MAX_TOTAL_STORAGE_EXCEEDED_REASON =
            Bytes.wrap(MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED.name());

    /**
     * Converts this result to a {@link ContractFunctionResult} for a transaction based on the given
     * {@link RootProxyWorldUpdater}.
     *
     * @param updater the world updater
     * @return the result
     */
    public ContractFunctionResult asProtoResultOf(@NonNull final RootProxyWorldUpdater updater) {
        return asProtoResultOf(null, updater);
    }

    /**
     * Converts this result to a {@link ContractFunctionResult} for a transaction based on the given
     * {@link RootProxyWorldUpdater} and maybe {@link EthTxData}.
     *
     * @param ethTxData the Ethereum transaction data if relevant
     * @param updater   the world updater
     * @return the result
     */
    public ContractFunctionResult asProtoResultOf(
            @Nullable final EthTxData ethTxData, @NonNull final RootProxyWorldUpdater updater) {
        if (haltReason != null) {
            return withMaybeEthFields(asUncommittedFailureResult(errorMessageFor(haltReason)), ethTxData);
        } else if (revertReason != null) {
            // This curious presentation of the revert reason is needed for backward compatibility
            return withMaybeEthFields(asUncommittedFailureResult(errorMessageForRevert(revertReason)), ethTxData);
        } else {
            return withMaybeEthFields(asSuccessResultForCommitted(updater), ethTxData);
        }
    }

    /**
     * Converts this result to a {@link ContractFunctionResult} for a query response.
     *
     * @return the result
     */
    public ContractFunctionResult asQueryResult() {
        if (haltReason != null) {
            return asUncommittedFailureResult(errorMessageFor(haltReason)).build();
        } else if (revertReason != null) {
            return asUncommittedFailureResult(errorMessageForRevert(revertReason))
                    .build();
        } else {
            return asSuccessResultForQuery();
        }
    }
    /**
     * Returns the final status of this transaction result.
     *
     * @return the status
     */
    public ResponseCodeEnum finalStatus() {
        if (haltReason != null) {
            return CustomExceptionalHaltReason.statusFor(haltReason);
        } else if (revertReason != null) {
            if (revertReason.equals(MAX_STORAGE_EXCEEDED_REASON)) {
                return MAX_CONTRACT_STORAGE_EXCEEDED;
            } else if (revertReason.equals(MAX_TOTAL_STORAGE_EXCEEDED_REASON)) {
                return MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED;
            } else {
                return CONTRACT_REVERT_EXECUTED;
            }
        } else {
            return SUCCESS;
        }
    }

    /**
     * Create a result for a transaction that succeeded.
     *
     * @param gasUsed the gas used by the transaction
     * @return the result
     */
    public static HederaEvmTransactionResult successFrom(
            final long gasUsed,
            @NonNull final AccountID senderId,
            @NonNull final ContractID recipientId,
            @NonNull final ContractID recipientEvmAddress,
            @NonNull final MessageFrame frame) {
        requireNonNull(frame);
        return successFrom(
                gasUsed,
                frame.getGasPrice(),
                senderId,
                recipientId,
                recipientEvmAddress,
                frame.getOutputData(),
                frame.getLogs(),
                allStateAccessesFrom(frame));
    }

    public static HederaEvmTransactionResult successFrom(
            final long gasUsed,
            @NonNull final Wei gasPrice,
            @NonNull final AccountID senderId,
            @NonNull final ContractID recipientId,
            @NonNull final ContractID recipientEvmAddress,
            @NonNull final org.apache.tuweni.bytes.Bytes output,
            @NonNull final List<Log> logs,
            @Nullable final ContractStateChanges stateChanges) {
        return new HederaEvmTransactionResult(
                gasUsed,
                requireNonNull(gasPrice).toLong(),
                requireNonNull(senderId),
                requireNonNull(recipientId),
                requireNonNull(recipientEvmAddress),
                tuweniToPbjBytes(requireNonNull(output)),
                null,
                null,
                requireNonNull(logs),
                stateChanges);
    }

    /**
     * Create a result for a transaction that failed.
     *
     * @param gasUsed the gas used by the transaction
     * @return the result
     */
    public static HederaEvmTransactionResult failureFrom(
            final long gasUsed, @NonNull final AccountID senderId, @NonNull final MessageFrame frame) {
        requireNonNull(frame);
        return new HederaEvmTransactionResult(
                gasUsed,
                frame.getGasPrice().toLong(),
                requireNonNull(senderId),
                null,
                null,
                Bytes.EMPTY,
                frame.getExceptionalHaltReason().orElse(null),
                frame.getRevertReason().map(ConversionUtils::tuweniToPbjBytes).orElse(null),
                Collections.emptyList(),
                stateReadsFrom(frame));
    }

    /**
     * Create a result for a transaction that failed due to resource exhaustion.
     *
     * @param gasUsed  the gas used by the transaction
     * @param gasPrice the gas price of the transaction
     * @param reason   the reason for the failure
     * @return the result
     */
    public static HederaEvmTransactionResult resourceExhaustionFrom(
            @NonNull final AccountID senderId,
            final long gasUsed,
            final long gasPrice,
            @NonNull final ResponseCodeEnum reason) {
        requireNonNull(reason);
        return new HederaEvmTransactionResult(
                gasUsed,
                gasPrice,
                requireNonNull(senderId),
                null,
                null,
                Bytes.EMPTY,
                null,
                Bytes.wrap(reason.name()),
                Collections.emptyList(),
                null);
    }

    private ContractFunctionResult withMaybeEthFields(
            @NonNull final ContractFunctionResult.Builder builder, @Nullable final EthTxData ethTxData) {
        if (ethTxData != null) {
            builder.gas(ethTxData.gasLimit())
                    .amount(ethTxData.getAmount())
                    .senderId(senderId)
                    .functionParameters(Bytes.wrap(ethTxData.callData()));
        }
        return builder.build();
    }

    private ContractFunctionResult.Builder asUncommittedFailureResult(@NonNull final String errorMessage) {
        requireNonNull(errorMessage);
        return ContractFunctionResult.newBuilder().gasUsed(gasUsed).errorMessage(errorMessage);
    }

    private ContractFunctionResult.Builder asSuccessResultForCommitted(@NonNull final RootProxyWorldUpdater updater) {
        final var createdIds = updater.getCreatedContractIds();
        return ContractFunctionResult.newBuilder()
                .gasUsed(gasUsed)
                .bloom(bloomForAll(logs))
                .contractCallResult(output)
                .contractID(recipientId)
                .createdContractIDs(createdIds)
                .logInfo(pbjLogsFrom(logs))
                .evmAddress(recipientEvmAddressIfCreatedIn(createdIds))
                .contractNonces(updater.getUpdatedContractNonces())
                .errorMessage(null);
    }

    private ContractFunctionResult asSuccessResultForQuery() {
        return ContractFunctionResult.newBuilder()
                .gasUsed(gasUsed)
                .bloom(bloomForAll(logs))
                .contractCallResult(output)
                .contractID(recipientId)
                .logInfo(pbjLogsFrom(logs))
                .errorMessage(null)
                .build();
    }

    private @Nullable Bytes recipientEvmAddressIfCreatedIn(@NonNull final List<ContractID> contractIds) {
        return contractIds.contains(recipientId)
                ? requireNonNull(recipientEvmAddress).evmAddressOrThrow()
                : null;
    }

    public boolean isSuccess() {
        return revertReason == null && haltReason == null;
    }

    private static @Nullable ContractStateChanges allStateAccessesFrom(@NonNull final MessageFrame frame) {
        return stateChangesFrom(frame, true);
    }

    private static @Nullable ContractStateChanges stateReadsFrom(@NonNull final MessageFrame frame) {
        return stateChangesFrom(frame, false);
    }

    private static String errorMessageForRevert(@NonNull final Bytes reason) {
        requireNonNull(reason);
        return "0x" + reason.toHex();
    }

    private static @Nullable ContractStateChanges stateChangesFrom(
            @NonNull final MessageFrame frame, final boolean includeWrites) {
        requireNonNull(frame);
        final var accessTracker = accessTrackerFor(frame);
        if (accessTracker == null) {
            return null;
        } else {
            final List<StorageAccesses> accesses;
            if (includeWrites) {
                final var worldUpdater = proxyUpdaterFor(frame);
                accesses = accessTracker.getReadsMergedWith(worldUpdater.pendingStorageUpdates());
            } else {
                accesses = accessTracker.getJustReads();
            }
            return asPbjStateChanges(accesses);
        }
    }
}
