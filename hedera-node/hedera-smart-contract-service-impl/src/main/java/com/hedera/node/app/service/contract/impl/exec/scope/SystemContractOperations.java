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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes;

public interface SystemContractOperations {
    /**
     * Attempts to dispatch the given {@code syntheticTransaction} in the context of the current
     * {@link HandleHederaOperations}, performing signature verification with priority given to the included
     * {@code VerificationStrategy}.
     *
     * <p>If the result is {@code SUCCESS}, but this scope or any of its parents revert, the record
     * of this dispatch should have its stateful side effects cleared and its result set to {@code REVERTED_SUCCESS}.
     *
     * @param syntheticBody the synthetic transaction to dispatch
     * @param strategy             the non-cryptographic signature verification to use
     * @param syntheticPayerId     the payer of the synthetic transaction
     * @param recordBuilderClass  the class of the record builder to use
     * @return the result of the dispatch
     */
    @NonNull
    <T> T dispatch(
            @NonNull TransactionBody syntheticBody,
            @NonNull VerificationStrategy strategy,
            @NonNull AccountID syntheticPayerId,
            @NonNull Class<T> recordBuilderClass);

    /**
     * Externalizes the preemption of the given {@code syntheticTransaction} hat would have otherwise been
     * dispatched in the context of the current {@link HandleHederaOperations}.
     *
     * @param syntheticBody the preempted dispatch
     * @param preemptingStatus the status code causing the preemption
     * @return the record of the preemption
     */
    ContractCallRecordBuilder externalizePreemptedDispatch(
            @NonNull TransactionBody syntheticBody, @NonNull ResponseCodeEnum preemptingStatus);

    /**
     * Returns a {@link Predicate} that tests whether the given {@link Key} is active based on the
     * given verification strategy.
     *
     * @param strategy the verification strategy to use
     * @return a {@link Predicate} that tests whether the given {@link Key} is active
     */
    @NonNull
    Predicate<Key> activeSignatureTestWith(@NonNull VerificationStrategy strategy);

    /**
     * Attempts to create a child record of the current record, with the given {@code result}
     *
     * @param result    contract function result
     */
    void externalizeResult(
            @NonNull final ContractFunctionResult result, @NonNull final ResponseCodeEnum responseStatus);

    /**
     * Attempts to create a child record of the current record, with the given {@code result}
     *
     * @param result    contract function result
     */
    void externalizeResult(
            @NonNull ContractFunctionResult result,
            @NonNull ResponseCodeEnum responseStatus,
            @NonNull Transaction transaction);

    /**
     * Generate synthetic transaction for child hts call
     *
     * @param input
     * @param contractID
     * @param isViewCall
     * @return
     */
    Transaction syntheticTransactionForHtsCall(Bytes input, ContractID contractID, boolean isViewCall);

    /**
     * Returns the {@link ExchangeRate} for the current consensus time.  This will enable the translation from hbars
     * to dollars
     *
     * @return ExchangeRate for the current consensus time
     */
    @NonNull
    ExchangeRate currentExchangeRate();
}
