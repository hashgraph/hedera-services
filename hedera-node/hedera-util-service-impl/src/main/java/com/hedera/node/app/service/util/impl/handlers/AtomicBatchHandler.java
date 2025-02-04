/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.util.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INNER_TRANSACTION_FAILED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.spi.workflows.DispatchOptions.atomicBatchDispatch;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.PureChecksContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.config.data.AtomicBatchConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#ATOMIC_BATCH}.
 */
@Singleton
public class AtomicBatchHandler implements TransactionHandler {
    /**
     * Constructs a {@link AtomicBatchHandler}
     */
    @Inject
    public AtomicBatchHandler() {}

    /**
     * Performs checks independent of state or context.
     *
     * @param context the context to check
     */
    @Override
    public void pureChecks(@NonNull final PureChecksContext context) throws PreCheckException {
        // TODO
    }

    /**
     * This method is called during the pre-handle workflow.
     *
     * @param context the {@link PreHandleContext} which collects all information
     * @throws PreCheckException if any issue happens on the pre handle level
     */
    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        // TODO
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var batchConfig = context.configuration().getConfigData(AtomicBatchConfig.class);
        final var op = context.body().atomicBatchOrThrow();
        if (batchConfig.isEnabled()) {
            final var transactions = op.transactions();
            for (final var transaction : transactions) {
                TransactionBody body;
                try {
                    body = context.bodyFromTransaction(transaction);
                } catch (HandleException e) {
                    // we should have validated the inner transaction in preChecks already, so this should not happen.
                    throw new HandleException(INNER_TRANSACTION_FAILED);
                }
                final var payerId = body.transactionIDOrThrow().accountIDOrThrow();
                // all the inner transactions' keys are verified in PreHandleWorkflow
                final var dispatchOptions = atomicBatchDispatch(payerId, body, StreamBuilder.class);
                final var streamBuilder = context.dispatch(dispatchOptions);
                if (streamBuilder == null || streamBuilder.status() != SUCCESS) {
                    throw new HandleException(INNER_TRANSACTION_FAILED);
                }
            }
        }
    }

    @Override
    public @NonNull Fees calculateFees(@NonNull final FeeContext feeContext) {
        requireNonNull(feeContext);
        final var calculator = feeContext.feeCalculatorFactory().feeCalculator(SubType.DEFAULT);
        calculator.resetUsage();
        // adjust the price based on the number of signatures
        calculator.addVerificationsPerTransaction(Math.max(0, feeContext.numTxnSignatures() - 1));
        return calculator.calculate();
    }
}
