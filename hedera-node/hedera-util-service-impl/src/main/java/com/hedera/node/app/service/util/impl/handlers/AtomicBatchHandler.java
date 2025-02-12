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

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.spi.workflows.DispatchOptions.atomicBatchDispatch;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.util.AtomicBatchTransactionBody;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.*;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.PureChecksContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.config.data.AtomicBatchConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#ATOMIC_BATCH}.
 */
@Singleton
public class AtomicBatchHandler implements TransactionHandler {

    private static final AccountID ATOMIC_BATCH_NODE_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(0).shardNum(0).realmNum(0).build();
    /**
     * Constructs a {@link AtomicBatchHandler}
     */
    @Inject
    public AtomicBatchHandler() {
        // exists for Dagger injection
    }

    /**
     * Performs checks independent of state or context.
     *
     * @param context the pure checks context
     */
    @Override
    public void pureChecks(@NonNull PureChecksContext context) throws PreCheckException {
        requireNonNull(context);
        final TransactionBody txn = context.body();
        requireNonNull(txn);
        final AtomicBatchTransactionBody transactionBody = txn.atomicBatchOrThrow();

        final List<Transaction> transactions = transactionBody.transactions();
        requireNonNull(transactions);

        if (transactions.isEmpty()) {
            throw new PreCheckException(BATCH_LIST_EMPTY);
        }

        // verify that the atomic batch transaction body supposed or not supposed to have a batch key
        if(txn.hasBatchKey()){
            throw new PreCheckException(BATCH_LIST_EMPTY);
        }

        Set<Transaction> set = new HashSet<>();
        for (final Transaction transaction : transactions) {
            if (transaction == null) {
                throw new PreCheckException(BATCH_LIST_CONTAINS_NULL_VALUES);
            }

            if (!set.add(transaction)) throw new PreCheckException(BATCH_LIST_CONTAINS_DUPLICATES);

            try {
                final var innerTrxBody = context.bodyFromTransaction(transaction);
                if (!innerTrxBody.hasBatchKey()) {
                    throw new PreCheckException(MISSING_BATCH_KEY);
                }

                if (!innerTrxBody.nodeAccountID().equals(ATOMIC_BATCH_NODE_ACCOUNT_ID)) {
                    throw new PreCheckException(INVALID_NODE_ACCOUNT_ID);
                }
                // transaction checker parse and check - to check validity of the transaction expire
            } catch (Exception e) {
                throw new PreCheckException(INVALID_TRANSACTION_BODY);
            }
        }
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
        final var op = context.body();
        final var atomicBatchTransactionBody = op.atomicBatchOrThrow();
        requireNonNull(op);
        List<Transaction> transactions = atomicBatchTransactionBody.transactions();

        if (transactions.size()
                > context.configuration().getConfigData(AtomicBatchConfig.class).maxNumberOfTransactions()) {
            throw new PreCheckException(BATCH_SIZE_LIMIT_EXCEEDED);
        }

        for (var transaction : transactions) {
            // check how to parse it correctly or maybe throw an exception
            final var body = context.bodyFromTransaction(transaction);
            final var payerId = body.transactionIDOrThrow().accountIDOrThrow();

            // this method will dispatch the prehandle transaction of each transaction in the batch
            context.executeInnerPreHandle(body, payerId);
            context.requireKeyOrThrow(body.batchKey(), BAD_ENCODING);
        }
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
