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

package com.hedera.node.app.workflows.handle;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.spi.config.GlobalDynamicConfig;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.state.WrappedHederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

public class HandleContextManager {

    private final TransactionChecker checker;
    private final ServiceScopeLookup serviceScopeLookup;
    private final TransactionDispatcher dispatcher;
    private final HandleWorkflow handleWorkflow;

    public HandleContextManager(
            @NonNull final HederaState state,
            @NonNull final Map<Key, SignatureVerification> signatureVerifications,
            @NonNull final GlobalDynamicConfig config,
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final AttributeValidator attributeValidator,
            @NonNull final HandleWorkflow handleWorkflow) {
        this.signatureVerifications = requireNonNull(signatureVerifications, "signatureVerifications must not be null");
        this.handleWorkflow = requireNonNull(handleWorkflow, "handleWorkflow must not be null");
    }

    @NonNull
    public SingleTransactionRecord dispatchPrecedingTransaction(
            @NonNull final WrappedHederaState state,
            @NonNull final TransactionBody txBody,
            @NonNull final AccountID creator)
            throws HandleException {
        requireNonNull(state, "state must not be null");
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(creator, "creator must not be null");

        if (state.isModified()) {
            throw new IllegalStateException("Cannot dispatch a preceding transaction when the state has been modified");
        }
        if (stackEntry.previous != null) {
            throw new IllegalStateException(
                    "Cannot dispatch a preceding transaction when a child transaction has been dispatched");
        }

        final var result = dispatchTransaction(txBody, creator, state);

        handleWorkflow.finalize(state, result);

        return result;
    }

    @NonNull
    public SingleTransactionRecord dispatchChildTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final AccountID creator)
            throws HandleException {
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(creator, "creator must not be null");

        // TODO: Calculate the stacked stack and consensus time for the child transaction
        final var stackedState = stackEntry.state;
        final var consensusNow = consensusNow();
        final var recordBuilder = new SingleTransactionRecordBuilder();
        this.stackEntry = new StackEntry(
                stackedState,
                this.stackEntry
        );

        this.lastChildRecord = dispatchTransaction(consensusNow, creator, txBody, stackEntry.state);
        return lastChildRecord;
    }

    private SingleTransactionRecord dispatchTransaction(
            @NonNull TransactionBody txBody,
            @NonNull AccountID creator,
            @NonNull HederaState state) {
        requireNonNull(txBody, "txBody must not be null");

        final var recordBuilder = new SingleTransactionRecordBuilder();

        try {
            checker.checkTransactionBody(txBody);
            // TODO: Add handler-specific validations
        } catch (PreCheckException e) {
            recordBuilder.status(e.responseCode());
            return recordBuilder.build();
        }

        try {
            final var serviceScope = serviceScopeLookup.getServiceName(txBody);
            final HandleContextImpl context = new HandleContextImpl(this, txBody, recordBuilder, state, serviceScope, consensusNow, config, expiryValidator, attributeValidator);
            dispatcher.dispatchHandle(context);
        }

    }
}
