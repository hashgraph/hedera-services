/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToTuweniBytes;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import org.hyperledger.besu.datatypes.Wei;

public record HederaEvmTransaction(
        @NonNull AccountID senderId,
        @Nullable AccountID relayerId,
        @Nullable ContractID contractId,
        long nonce,
        @NonNull Bytes payload,
        @Nullable Bytes chainId,
        long value,
        long gasLimit,
        long offeredGasPrice,
        long maxGasAllowance,
        @Nullable ContractCreateTransactionBody hapiCreation,
        @Nullable HandleException exception) {
    public static final long NOT_APPLICABLE = -1L;

    public boolean hasExpectedNonce() {
        return nonce != NOT_APPLICABLE;
    }

    public boolean hasOfferedGasPrice() {
        return offeredGasPrice != NOT_APPLICABLE;
    }

    public boolean hasMaxGasAllowance() {
        return maxGasAllowance != NOT_APPLICABLE;
    }

    public boolean isCreate() {
        return contractId == null;
    }

    public boolean needsInitcodeExternalizedOnFailure() {
        return hapiCreation != null && !hapiCreation.hasInitcode();
    }

    public boolean isEthereumTransaction() {
        return relayerId != null;
    }

    public boolean isContractCall() {
        return !isEthereumTransaction() && !isCreate();
    }

    public boolean isException() {
        return exception != null;
    }

    public boolean permitsMissingContract() {
        return isEthereumTransaction() && hasValue();
    }

    public @NonNull ContractID contractIdOrThrow() {
        return Objects.requireNonNull(contractId);
    }

    public boolean hasValue() {
        return value > 0;
    }

    public org.apache.tuweni.bytes.Bytes evmPayload() {
        return pbjToTuweniBytes(payload);
    }

    public Wei weiValue() {
        return Wei.of(value);
    }

    public long gasAvailable(final long intrinsicGas) {
        return gasLimit - intrinsicGas;
    }

    public long upfrontCostGiven(final long gasPrice) {
        final var gasCost = gasCostGiven(gasPrice);
        return gasCost == Long.MAX_VALUE ? Long.MAX_VALUE : gasCost + value;
    }

    public long unusedGas(final long gasUsed) {
        return gasLimit - gasUsed;
    }

    public long gasCostGiven(final long gasPrice) {
        try {
            return Math.multiplyExact(gasLimit, gasPrice);
        } catch (ArithmeticException ignore) {
            return Long.MAX_VALUE;
        }
    }

    public long offeredGasCost() {
        try {
            return Math.multiplyExact(gasLimit, offeredGasPrice);
        } catch (ArithmeticException ignore) {
            return Long.MAX_VALUE;
        }
    }

    public boolean requiresFullRelayerAllowance() {
        return offeredGasPrice == 0L;
    }

    /**
     * @return a copy of this transaction with the given {@code exception}
     */
    public HederaEvmTransaction withException(@NonNull final HandleException exception) {
        return new HederaEvmTransaction(
                this.senderId,
                this.relayerId,
                this.contractId,
                this.nonce,
                this.payload,
                this.chainId,
                this.value,
                this.gasLimit,
                this.offeredGasPrice,
                this.maxGasAllowance,
                this.hapiCreation,
                exception);
    }
}
