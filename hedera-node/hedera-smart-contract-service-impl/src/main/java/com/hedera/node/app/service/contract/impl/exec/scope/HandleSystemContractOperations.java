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

package com.hedera.node.app.service.contract.impl.exec.scope;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.transactionWith;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Predicate;
import javax.inject.Inject;
import org.apache.tuweni.bytes.Bytes;

/**
 * Provides the "extended" scope a Hedera system contract needs to perform its operations.
 *
 * <p>This lets an EVM smart contract make atomic changes scoped to a message frame, even though
 * these changes involve state that it cannot mutate directly via the {@code ContractService}'s
 * {@code WritableStates}.
 */
@TransactionScope
public class HandleSystemContractOperations implements SystemContractOperations {
    private final HandleContext context;

    @Inject
    public HandleSystemContractOperations(@NonNull final HandleContext context) {
        this.context = requireNonNull(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Predicate<Key> activeSignatureTestWith(@NonNull final VerificationStrategy strategy) {
        return strategy.asSignatureTestIn(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull <T> T dispatch(
            @NonNull final TransactionBody syntheticBody,
            @NonNull final VerificationStrategy strategy,
            @NonNull final AccountID syntheticPayerId,
            @NonNull final Class<T> recordBuilderClass) {
        requireNonNull(syntheticBody);
        requireNonNull(strategy);
        requireNonNull(syntheticPayerId);
        requireNonNull(recordBuilderClass);
        return context.dispatchChildTransaction(
                syntheticBody, recordBuilderClass, activeSignatureTestWith(strategy), syntheticPayerId, CHILD);
    }

    @Override
    public ContractCallRecordBuilder externalizePreemptedDispatch(
            @NonNull final TransactionBody syntheticBody, @NonNull final ResponseCodeEnum preemptingStatus) {
        requireNonNull(syntheticBody);
        requireNonNull(preemptingStatus);

        return context.addChildRecordBuilder(ContractCallRecordBuilder.class)
                .transaction(transactionWith(syntheticBody))
                .status(preemptingStatus);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void externalizeResult(
            @NonNull final ContractFunctionResult result, @NonNull final ResponseCodeEnum responseStatus) {
        final var childRecordBuilder = context.addChildRecordBuilder(ContractCallRecordBuilder.class);
        childRecordBuilder
                .transaction(Transaction.DEFAULT)
                .contractID(result.contractID())
                .status(responseStatus)
                .contractCallResult(result);
    }

    @Override
    public void externalizeResult(
            @NonNull final ContractFunctionResult result,
            @NonNull final ResponseCodeEnum responseStatus,
            @NonNull Transaction transaction) {
        requireNonNull(transaction);
        context.addChildRecordBuilder(ContractCallRecordBuilder.class)
                .transaction(transaction)
                .status(responseStatus)
                .contractCallResult(result);
    }

    @Override
    public Transaction syntheticTransactionForHtsCall(Bytes input, ContractID contractID, boolean isViewCall) {
        var functionParameters = tuweniToPbjBytes(input);
        var contractCallBodyBuilder =
                ContractCallTransactionBody.newBuilder().contractID(contractID).functionParameters(functionParameters);
        if (isViewCall) {
            contractCallBodyBuilder.gas(1L);
        }
        var transactionBody = TransactionBody.newBuilder()
                .transactionID(TransactionID.DEFAULT)
                .contractCall(contractCallBodyBuilder.build())
                .build();
        return transactionWith(transactionBody);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public ExchangeRate currentExchangeRate() {
        return context.exchangeRateInfo().activeRate(context.consensusNow());
    }
}
