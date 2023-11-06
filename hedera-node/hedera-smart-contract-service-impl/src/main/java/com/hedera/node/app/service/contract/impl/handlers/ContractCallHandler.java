/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.handlers;

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.throwIfUnsuccessful;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.TransactionComponent;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONTRACT_CALL}.
 */
@Singleton
public class ContractCallHandler implements TransactionHandler {
    private static final long INTRINSIC_GAS_LOWER_BOUND = 21_000L;
    private final Provider<TransactionComponent.Factory> provider;
    private final GasCalculator gasCalculator;

    @Inject
    public ContractCallHandler(
            @NonNull final Provider<TransactionComponent.Factory> provider,
            @NonNull final GasCalculator gasCalculator) {
        this.provider = requireNonNull(provider);
        this.gasCalculator = requireNonNull(gasCalculator);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        // Create the transaction-scoped component
        final var component = provider.get().create(context, CONTRACT_CALL);

        // Run its in-scope transaction and get the outcome
        final var outcome = component.contextTransactionProcessor().call();

        // Assemble the appropriate top-level record for the result
        context.recordBuilder(ContractCallRecordBuilder.class)
                .contractCallResult(outcome.result())
                .contractID(outcome.recipientIdIfCalled());
        throwIfUnsuccessful(outcome.status());
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        // No non-payer signatures to verify
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txnBody) throws PreCheckException {
        var op = txnBody.contractCall();

        validateTruePreCheck(
                op.gas()
                        >= Math.max(
                                gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false),
                                INTRINSIC_GAS_LOWER_BOUND),
                INSUFFICIENT_GAS);
        validateTruePreCheck(op.amount() >= 0, CONTRACT_NEGATIVE_VALUE);

        // TODO: inject config
        //        final var configuration = configProvider.getConfiguration();
        //        final var maxGasPerSec =
        //                configuration.getConfigData(ContractsConfig.class).maxGasPerSec();
        validateTruePreCheck(op.gas() <= 1_000_001L, MAX_GAS_LIMIT_EXCEEDED);
    }
}
