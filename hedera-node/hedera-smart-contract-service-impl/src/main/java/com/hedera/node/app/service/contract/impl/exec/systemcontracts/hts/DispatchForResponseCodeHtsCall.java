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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * An HTS call that simply dispatches a synthetic transaction body and returns a result that is
 * an encoded {@link com.hedera.hapi.node.base.ResponseCodeEnum}.
 *
 * @param <T> the type of the record builder to expect from the dispatch
 */
public class DispatchForResponseCodeHtsCall<T extends SingleTransactionRecordBuilder> extends AbstractHtsCall {
    private final AccountID spenderId;
    private final TransactionBody syntheticBody;
    private final Class<T> recordBuilderType;
    private final VerificationStrategy verificationStrategy;

    /**
     * Convenience overload that slightly eases construction for the most common case.
     *
     * @param attempt           the attempt to translate to a dispatching
     * @param syntheticBody     the synthetic body to dispatch
     * @param recordBuilderType the type of the record builder to expect from the dispatch
     */
    public DispatchForResponseCodeHtsCall(
            @NonNull final HtsCallAttempt attempt,
            @NonNull final TransactionBody syntheticBody,
            @NonNull final Class<T> recordBuilderType) {
        this(
                attempt.enhancement(),
                attempt.addressIdConverter().convertSender(attempt.senderAddress()),
                syntheticBody,
                recordBuilderType,
                attempt.defaultVerificationStrategy());
    }

    /**
     * More general constructor, for cases where perhaps a custom {@link VerificationStrategy} is needed.
     *
     * @param enhancement          the enhancement to use
     * @param spenderId            the id of the spender
     * @param syntheticBody        the synthetic body to dispatch
     * @param recordBuilderType    the type of the record builder to expect from the dispatch
     * @param verificationStrategy the verification strategy to use
     */
    public <U extends SingleTransactionRecordBuilder> DispatchForResponseCodeHtsCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID spenderId,
            @NonNull final TransactionBody syntheticBody,
            @NonNull final Class<T> recordBuilderType,
            @NonNull final VerificationStrategy verificationStrategy) {
        super(enhancement);
        this.spenderId = Objects.requireNonNull(spenderId);
        this.syntheticBody = Objects.requireNonNull(syntheticBody);
        this.recordBuilderType = Objects.requireNonNull(recordBuilderType);
        this.verificationStrategy = Objects.requireNonNull(verificationStrategy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull PricedResult execute() {
        // TODO - gas calculation
        final var recordBuilder =
                systemContractOperations().dispatch(syntheticBody, verificationStrategy, spenderId, recordBuilderType);

        return completionWith(recordBuilder.status(), 0L);
    }
}
