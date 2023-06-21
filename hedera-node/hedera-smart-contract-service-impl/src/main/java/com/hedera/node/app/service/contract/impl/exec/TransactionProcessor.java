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

package com.hedera.node.app.service.contract.impl.exec;

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToBesuAddress;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.node.app.service.contract.impl.exec.processors.CustomMessageCallProcessor;
import com.hedera.node.app.service.contract.impl.hevm.*;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import com.hedera.node.app.spi.workflows.HandleException;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * Modeled after the Besu {@code MainnetTransactionProcessor}, so that all four HAPI
 * contract operations ({@code ContractCall}, {@code ContractCreate}, {@code EthereumTransaction},
 * {@code ContractCallLocal}) can reduce to a single code path.
 */
public class TransactionProcessor {
    public static final String CONFIG_CONTEXT_VARIABLE = "contractsConfig";

    private final GasCalculator gasCalculator;
    private final CustomMessageCallProcessor messageCallProcessor;
    private final ContractCreationProcessor contractCreationProcessor;

    public TransactionProcessor(
            @NonNull final GasCalculator gasCalculator,
            @NonNull final CustomMessageCallProcessor messageCallProcessor,
            @NonNull final ContractCreationProcessor contractCreationProcessor) {
        this.gasCalculator = Objects.requireNonNull(gasCalculator);
        this.messageCallProcessor = Objects.requireNonNull(messageCallProcessor);
        this.contractCreationProcessor = Objects.requireNonNull(contractCreationProcessor);
    }

    public HederaEvmTransactionResult processTransaction(
            @NonNull final HederaEvmTransaction transaction,
            @NonNull final HederaWorldUpdater worldUpdater,
            @NonNull final HederaEvmContext context,
            @NonNull final OperationTracer tracer,
            @NonNull final Configuration config) {
        try {
            final var initialCall = computeInitialCall(transaction, worldUpdater, context, config);
            // TODO - use CustomGasCharging when finished
        } catch (final HandleException failure) {
            return HederaEvmTransactionResult.abortFor(failure.getStatus());
        }
        throw new AssertionError("Not implemented");
    }

    private record InitialCall(@NonNull HederaEvmAccount fromAccount, @NonNull Address toAddress) {}

    private InitialCall computeInitialCall(
            @NonNull final HederaEvmTransaction transaction,
            @NonNull final HederaWorldUpdater worldUpdater,
            @NonNull final HederaEvmContext context,
            @NonNull final Configuration config) {

        final var from = worldUpdater.getHederaAccount(transaction.senderId());
        validateTrue(from != null, INVALID_ACCOUNT_ID);
        if (transaction.isCreate()) {
            throw new AssertionError("Not implemented");
        } else {
            final var to = worldUpdater.getHederaAccount(transaction.contractIdOrThrow());
            if (maybeLazyCreate(transaction, to, config)) {
                validateTrue(transaction.hasValue(), INVALID_CONTRACT_ID);
                final var evmAddress = transaction.contractIdOrThrow().evmAddressOrThrow();
                return new InitialCall(from, pbjToBesuAddress(evmAddress));
            }
        }
        throw new AssertionError("Not implemented");
    }

    private boolean maybeLazyCreate(
            @NonNull final HederaEvmTransaction transaction,
            @Nullable final HederaEvmAccount to,
            @NonNull final Configuration config) {
        return to == null &&
                transaction.isEthereumTransaction() &&
                messageCallProcessor.isImplicitCreationEnabled(config);
    }
}
