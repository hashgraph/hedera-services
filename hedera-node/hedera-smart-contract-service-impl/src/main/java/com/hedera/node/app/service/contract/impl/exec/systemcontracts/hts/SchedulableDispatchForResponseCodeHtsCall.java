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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A specialized version of {@link DispatchForResponseCodeHtsCall} that supports scheduling transactions.
 * This class extends the base dispatch functionality to include a schedulable transaction body,
 * which is required for operations that need to be scheduled for future execution.
 *
 * <p>This class is used primarily in the context of HTS (Hedera Token Service) operations
 * that need to be scheduled, such as token creation or updates. It provides various constructors
 * to support different initialization scenarios and maintains the schedulable transaction body
 * that will be executed when the schedule triggers.
 */
public class SchedulableDispatchForResponseCodeHtsCall extends DispatchForResponseCodeHtsCall {

    private final SchedulableTransactionBody schedulableTransactionBody;

    /**
     * Creates a new instance with full configuration options.
     *
     * @param enhancement the Hedera world updater enhancement
     * @param gasCalculator calculator for system contract gas costs
     * @param sender the account ID of the transaction sender
     * @param transactionBody the transaction body
     * @param verificationStrategy strategy for transaction verification
     * @param dispatchGasCalculator calculator for dispatch gas costs
     * @param failureCustomizer customizer for handling failures
     * @param outputFn function for processing output
     * @param schedulableTransactionBody the transaction body to be scheduled
     */
    public SchedulableDispatchForResponseCodeHtsCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final AccountID sender,
            @NonNull final TransactionBody transactionBody,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final DispatchGasCalculator dispatchGasCalculator,
            @NonNull final FailureCustomizer failureCustomizer,
            @NonNull final OutputFn outputFn,
            @NonNull final SchedulableTransactionBody schedulableTransactionBody) {
        super(
                enhancement,
                gasCalculator,
                sender,
                transactionBody,
                verificationStrategy,
                dispatchGasCalculator,
                failureCustomizer,
                outputFn);
        this.schedulableTransactionBody = schedulableTransactionBody;
    }

    /**
     * Creates a new instance with minimal configuration.
     *
     * @param attempt the HTS call attempt
     * @param syntheticBody optional synthetic transaction body
     * @param dispatchGasCalculator calculator for dispatch gas costs
     * @param schedulableTransactionBody the transaction body to be scheduled
     */
    public SchedulableDispatchForResponseCodeHtsCall(
            @NonNull final HtsCallAttempt attempt,
            @Nullable final TransactionBody syntheticBody,
            @NonNull final DispatchGasCalculator dispatchGasCalculator,
            @NonNull final SchedulableTransactionBody schedulableTransactionBody) {
        super(attempt, syntheticBody, dispatchGasCalculator);
        this.schedulableTransactionBody = schedulableTransactionBody;
    }

    /**
     * Creates a new instance with failure handling configuration.
     *
     * @param attempt the HTS call attempt
     * @param syntheticBody optional synthetic transaction body
     * @param dispatchGasCalculator calculator for dispatch gas costs
     * @param failureCustomizer customizer for handling failures
     * @param schedulableTransactionBody the transaction body to be scheduled
     */
    public SchedulableDispatchForResponseCodeHtsCall(
            @NonNull final HtsCallAttempt attempt,
            @Nullable final TransactionBody syntheticBody,
            @NonNull final DispatchGasCalculator dispatchGasCalculator,
            @NonNull final FailureCustomizer failureCustomizer,
            @NonNull final SchedulableTransactionBody schedulableTransactionBody) {
        super(attempt, syntheticBody, dispatchGasCalculator, failureCustomizer);
        this.schedulableTransactionBody = schedulableTransactionBody;
    }

    /**
     * Creates a new instance with output processing configuration.
     *
     * @param attempt the HTS call attempt
     * @param syntheticBody optional synthetic transaction body
     * @param dispatchGasCalculator calculator for dispatch gas costs
     * @param outputFn function for processing output
     * @param schedulableTransactionBody the transaction body to be scheduled
     */
    public SchedulableDispatchForResponseCodeHtsCall(
            @NonNull final HtsCallAttempt attempt,
            @Nullable final TransactionBody syntheticBody,
            @NonNull final DispatchGasCalculator dispatchGasCalculator,
            @NonNull final OutputFn outputFn,
            @NonNull final SchedulableTransactionBody schedulableTransactionBody) {
        super(attempt, syntheticBody, dispatchGasCalculator, outputFn);
        this.schedulableTransactionBody = schedulableTransactionBody;
    }

    /**
     * Returns the schedulable transaction body for this call.
     *
     * @return the schedulable transaction body
     */
    @Override
    public @NonNull SchedulableTransactionBody asSchedulableDispatchIn() {
        return schedulableTransactionBody;
    }
}
