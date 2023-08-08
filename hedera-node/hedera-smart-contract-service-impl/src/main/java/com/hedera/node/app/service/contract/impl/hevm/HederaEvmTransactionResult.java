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

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.accessTrackerFor;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.proxyUpdaterFor;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asPbjStateChanges;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.bloomForAll;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjLogsFrom;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.streams.ContractStateChanges;
import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.StorageAccesses;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.List;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

public record HederaEvmTransactionResult(
        long gasUsed,
        long gasPrice,
        @Nullable ContractID recipientId,
        @Nullable ContractID recipientEvmAddress,
        @NonNull Bytes output,
        @Nullable String haltReason,
        @Nullable Bytes revertReason,
        @NonNull List<Log> logs,
        @Nullable ContractStateChanges stateChanges) {
    public HederaEvmTransactionResult {
        requireNonNull(output);
        requireNonNull(logs);
    }

    /**
     * Converts this result to a {@link ContractFunctionResult} for a transaction based on the given
     * {@link RootProxyWorldUpdater}.
     *
     * @param updater the world updater
     * @return the result
     */
    public ContractFunctionResult asProtoResultOf(@NonNull final RootProxyWorldUpdater updater) {
        if (haltReason != null) {
            throw new AssertionError("Not implemented");
        } else if (revertReason != null) {
            throw new AssertionError("Not implemented");
        } else {
            return asSuccessResultForCommitted(updater);
        }
    }

    /**
     * Returns the final status of this transaction result.
     *
     * @return the status
     */
    public ResponseCodeEnum finalStatus() {
        if (haltReason != null) {
            throw new AssertionError("Not implemented");
        } else if (revertReason != null) {
            throw new AssertionError("Not implemented");
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
            @NonNull final ContractID recipientId,
            @NonNull final ContractID recipientEvmAddress,
            @NonNull final MessageFrame frame) {
        requireNonNull(frame);
        return successFrom(
                gasUsed,
                frame.getGasPrice(),
                recipientId,
                recipientEvmAddress,
                frame.getOutputData(),
                frame.getLogs(),
                allStateAccessesFrom(frame));
    }

    public static HederaEvmTransactionResult successFrom(
            final long gasUsed,
            @NonNull final Wei gasPrice,
            @NonNull final ContractID recipientId,
            @NonNull final ContractID recipientEvmAddress,
            @NonNull final org.apache.tuweni.bytes.Bytes output,
            @NonNull final List<Log> logs,
            @Nullable final ContractStateChanges stateChanges) {
        return new HederaEvmTransactionResult(
                gasUsed,
                requireNonNull(gasPrice).toLong(),
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
    public static HederaEvmTransactionResult failureFrom(final long gasUsed, @NonNull final MessageFrame frame) {
        requireNonNull(frame);
        return new HederaEvmTransactionResult(
                gasUsed,
                frame.getGasPrice().toLong(),
                null,
                null,
                Bytes.EMPTY,
                frame.getExceptionalHaltReason().map(Object::toString).orElse(null),
                frame.getRevertReason().map(ConversionUtils::tuweniToPbjBytes).orElse(null),
                Collections.emptyList(),
                stateReadsFrom(frame));
    }

    /**
     * Create a result for a transaction that failed due to resource exhaustion.
     *
     * @param gasUsed the gas used by the transaction
     * @param gasPrice the gas price of the transaction
     * @param reason the reason for the failure
     * @return the result
     */
    public static HederaEvmTransactionResult resourceExhaustionFrom(
            final long gasUsed, final long gasPrice, @NonNull final ResponseCodeEnum reason) {
        requireNonNull(reason);
        return new HederaEvmTransactionResult(
                gasUsed,
                gasPrice,
                null,
                null,
                Bytes.EMPTY,
                null,
                Bytes.wrap(reason.name()),
                Collections.emptyList(),
                null);
    }

    private ContractFunctionResult asSuccessResultForCommitted(@NonNull final RootProxyWorldUpdater updater) {
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
