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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class SchedulableDispatchForResponseCodeHtsCall extends DispatchForResponseCodeHtsCall {

    private final SchedulableTransactionBody builder;

    public SchedulableDispatchForResponseCodeHtsCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final AccountID sender,
            @NonNull final TransactionBody transactionBody,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final DispatchGasCalculator dispatchGasCalculator,
            @NonNull final FailureCustomizer failureCustomizer,
            @NonNull final OutputFn outputFn,
            @NonNull final SchedulableTransactionBody builder) {
        super(
                enhancement,
                gasCalculator,
                sender,
                transactionBody,
                verificationStrategy,
                dispatchGasCalculator,
                failureCustomizer,
                outputFn);
        this.builder = builder;
    }

    public SchedulableDispatchForResponseCodeHtsCall(
            @NonNull HtsCallAttempt attempt,
            @Nullable TransactionBody syntheticBody,
            @NonNull DispatchGasCalculator dispatchGasCalculator,
            SchedulableTransactionBody builder) {
        super(attempt, syntheticBody, dispatchGasCalculator);
        this.builder = builder;
    }

    public SchedulableDispatchForResponseCodeHtsCall(
            @NonNull HtsCallAttempt attempt,
            @Nullable TransactionBody syntheticBody,
            @NonNull DispatchGasCalculator dispatchGasCalculator,
            @NonNull FailureCustomizer failureCustomizer,
            SchedulableTransactionBody builder) {
        super(attempt, syntheticBody, dispatchGasCalculator, failureCustomizer);
        this.builder = builder;
    }

    public SchedulableDispatchForResponseCodeHtsCall(
            @NonNull HtsCallAttempt attempt,
            @Nullable TransactionBody syntheticBody,
            @NonNull DispatchGasCalculator dispatchGasCalculator,
            @NonNull OutputFn outputFn,
            SchedulableTransactionBody builder) {
        super(attempt, syntheticBody, dispatchGasCalculator, outputFn);
        this.builder = builder;
    }

    @Override
    public @NonNull SchedulableTransactionBody asSchedulableDispatchIn(@NonNull final MessageFrame frame) {
        requireNonNull(frame);
        return builder;
    }
}
