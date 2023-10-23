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
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
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
    private final AccountID senderId;
    private final TransactionBody syntheticBody;
    private final Class<T> recordBuilderType;
    private final VerificationStrategy verificationStrategy;
    private final DispatchGasCalculator dispatchGasCalculator;

    /**
     * Convenience overload that slightly eases construction for the most common case.
     *
     * @param attempt the attempt to translate to a dispatching
     * @param syntheticBody the synthetic body to dispatch
     * @param recordBuilderType the type of the record builder to expect from the dispatch
     * @param dispatchGasCalculator the dispatch gas calculator to use
     */
    public DispatchForResponseCodeHtsCall(
            @NonNull final HtsCallAttempt attempt,
            @NonNull final TransactionBody syntheticBody,
            @NonNull final Class<T> recordBuilderType,
            @NonNull final DispatchGasCalculator dispatchGasCalculator) {
        this(
                attempt.enhancement(),
                attempt.systemContractGasCalculator(),
                attempt.addressIdConverter().convertSender(attempt.senderAddress()),
                syntheticBody,
                recordBuilderType,
                attempt.defaultVerificationStrategy(),
                dispatchGasCalculator);
    }

    /**
     * More general constructor, for cases where perhaps a custom {@link VerificationStrategy} is needed.
     *
     * @param enhancement the enhancement to use
     * @param senderId the id of the spender
     * @param syntheticBody the synthetic body to dispatch
     * @param recordBuilderType the type of the record builder to expect from the dispatch
     * @param verificationStrategy the verification strategy to use
     * @param dispatchGasCalculator the dispatch gas calculator to use
     */
    public <U extends SingleTransactionRecordBuilder> DispatchForResponseCodeHtsCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final AccountID senderId,
            @NonNull final TransactionBody syntheticBody,
            @NonNull final Class<T> recordBuilderType,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final DispatchGasCalculator dispatchGasCalculator) {
        super(gasCalculator, enhancement);
        this.senderId = Objects.requireNonNull(senderId);
        this.syntheticBody = Objects.requireNonNull(syntheticBody);
        this.recordBuilderType = Objects.requireNonNull(recordBuilderType);
        this.verificationStrategy = Objects.requireNonNull(verificationStrategy);
        this.dispatchGasCalculator = Objects.requireNonNull(dispatchGasCalculator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull PricedResult execute() {
        final var recordBuilder =
                systemContractOperations().dispatch(syntheticBody, verificationStrategy, senderId, recordBuilderType);
        final var gasRequirement =
                dispatchGasCalculator.gasRequirement(syntheticBody, gasCalculator, enhancement, senderId);
        return completionWith(recordBuilder.status(), gasRequirement);
    }
}
