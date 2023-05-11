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
import com.hedera.node.app.spi.config.GlobalDynamicConfig;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;

public class StackableHandleContext implements HandleContext {

    private final Instant consensusNow;
    private final TransactionBody txBody;
    private final SingleTransactionRecordBuilder recordBuilder;

    // TODO: Eventually move to HandleContextManager
    private final GlobalDynamicConfig config;
    private final AttributeValidator attributeValidator;
    private final ExpiryValidator expiryValidator;
    private final Map<Key, SignatureVerification> signatureVerifications;
    private final HandleWorkflow handleWorkflow;

    private StackEntry stackEntry;
    private SingleTransactionRecord lastChildRecord;

    public StackableHandleContext(
            @NonNull final HederaState state,
            @NonNull final Instant consensusNow,
            @NonNull final TransactionBody txBody,
            @NonNull final Map<Key, SignatureVerification> signatureVerifications,
            @NonNull final SingleTransactionRecordBuilder recordBuilder,
            @NonNull final GlobalDynamicConfig config,
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final AttributeValidator attributeValidator,
            @NonNull final HandleWorkflow handleWorkflow) {
        this.consensusNow = requireNonNull(consensusNow, "consensusNow must not be null");
        this.txBody = requireNonNull(txBody, "txBody must not be null");
        this.recordBuilder = requireNonNull(recordBuilder, "recordBuilder must not be null");
        this.signatureVerifications = requireNonNull(signatureVerifications, "signatureVerifications must not be null");
        this.config = requireNonNull(config, "config must not be null");
        this.expiryValidator = requireNonNull(expiryValidator, "expiryValidator must not be null");
        this.attributeValidator = requireNonNull(attributeValidator, "attributeValidator must not be null");
        this.handleWorkflow = requireNonNull(handleWorkflow, "handleWorkflow must not be null");

        this.stackEntry = new StackEntry(
                requireNonNull(state, "state must not be null"),
                null
        );
    }

    @NonNull
    @Override
    public Instant consensusNow() {
        return consensusNow;
    }

    @NonNull
    @Override
    public TransactionBody body() {
        return txBody;
    }

    @Override
    @NonNull
    public GlobalDynamicConfig config() {
        return config;
    }

    @Override
    public long newEntityNum() {
        return stackEntry.newEntityNum();
    }

    @Override
    @NonNull
    public AttributeValidator attributeValidator() {
        return attributeValidator;
    }

    @Override
    @NonNull
    public ExpiryValidator expiryValidator() {
        return expiryValidator;
    }

    @Override
    @Nullable
    public SignatureVerification verificationFor(@NonNull Key key) {
        requireNonNull(key, "key must not be null");
        return signatureVerifications.get(key);
    }

    @Override
    @NonNull
    public <C> C readableStore(@NonNull Class<C> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        return stackEntry.readableStore(storeInterface);
    }

    @Override
    @NonNull
    public <C> C writableStore(@NonNull Class<C> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        return stackEntry.writableStore(storeInterface);
    }

    @NonNull
    @Override
    public <T> T recordBuilder(@NonNull Class<T> recordBuilderClass) {
        requireNonNull(recordBuilderClass, "singleTransactionRecordBuilderClass must not be null");
        if (!recordBuilderClass.isInstance(recordBuilder)) {
            throw new IllegalArgumentException("Not a valid record builder class");
        }
        return recordBuilderClass.cast(recordBuilder);
    }

    @Override
    @NonNull
    public SingleTransactionRecord dispatchPrecedingTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final AccountID creator)
            throws HandleException {
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(creator, "creator must not be null");

        if (stackEntry.writableStoreFactory != null) {
            // TODO: Would be better, if we could easily check if state has actually been modified
            throw new IllegalStateException(
                    "Cannot dispatch a preceding transaction when the state has been modified");
        }
        if (stackEntry.previous != null) {
            throw new IllegalStateException(
                    "Cannot dispatch a preceding transaction when a child transaction has been dispatched");
        }

        // TODO: Calculate the consensus time for the preceding transaction
        final var consensusNow = consensusNow();

        final var result = dispatchTransaction(consensusNow, creator, txBody, stackEntry.state);

        handleWorkflow.finalize(stackEntry.state, result);

        return result;
    }

    @Override
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

        this.lastChildRecord = dispatchTransaction(consensusNow, creator, txBody, stackedState);
        return lastChildRecord;
    }

    @Override
    @NonNull
    public TransactionStack transactionStack() {
        return new TransactionStack() {
            @NonNull
            @Override
            public SingleTransactionRecord peek() {
                if (lastChildRecord == null) {
                    throw new IllegalStateException("The transaction stack is empty");
                }
                return lastChildRecord;
            }

            @Override
            public void rollback(int level) {
                var currentEntry = stackEntry;
                for (int i = 0; i < level; i++) {
                    if (currentEntry.previous == null) {
                        throw new IllegalStateException("The transaction stack does not contain enough elements");
                    }
                    currentEntry = currentEntry.previous;
                }
                stackEntry = currentEntry;
            }
        };
    }

    private SingleTransactionRecord dispatchTransaction(
            @NonNull Instant consensusNow,
            @NonNull AccountID creator,
            @NonNull TransactionBody txBody,
            @NonNull HederaState state) {
        transactionChecker.checkTransactionBody(txBody);
        // TODO: Add handler.validate(txBody) once introduced
        // TODO: Check if config changed
        final var subContext = new StackableHandleContext(
                state,
                consensusNow,
                txBody,
                signatureVerifications,
                recordBuilder,
                config,
                expiryValidator,
                attributeValidator,
                handleWorkflow);
        TransactionDispatcher dispatcher = null;
        Function<Function<TransactionBody, String>, HandleContext> f =
                serviceGetter -> {
                    subContext.stackEntry.serviceScope = serviceGetter.apply(txBody);
                    return subContext;
                };
        dispatcher.dispatchHandle(f);

        final var f2 = (Function<TransactionBody, String> serviceGetter) -> build(serviceGetter.apply(txBody));

        dispatcher.dispatchHandle(serviceGetter -> build(serviceGetter.apply(txBody)));
        dispatcher.dispatchHandle(serviceGetter -> serviceGetter.andThen(this::build).apply(txBody));

        (serviceGetter -> this::build).apply(txBody);

        txBody -> serviceGetter


    }

    public HandleContext build(String s) {
        return null;
    }

    private static class StackEntry {
        private final HederaState state;
        private final StackEntry previous;

        private String serviceScope;
        private ReadableStoreFactory readableStoreFactory;
        private WritableStoreFactory writableStoreFactory;

        private StackEntry(
                @NonNull final HederaState state,
                @Nullable final StackEntry previous) {
            this.state = state;
            this.previous = previous;
        }

        private long newEntityNum() {
            // TODO: Implement StackEntry.newEntityNum()
            return 0;
        }

        @NonNull
        private <C> C readableStore(@NonNull final Class<C> storeInterface) {
            if (readableStoreFactory == null) {
                readableStoreFactory = new ReadableStoreFactory(state);
            }
            return readableStoreFactory.getStore(storeInterface);
        }

        @NonNull
        private <C> C writableStore(@NonNull Class<C> storeInterface) {
            if (serviceScope == null) {
                throw new IllegalStateException("The service scope was not set");
            }
            if (writableStoreFactory == null) {
                writableStoreFactory = new WritableStoreFactory(state, serviceScope);
            }
            return writableStoreFactory.getStore(storeInterface, serviceScope);
        }
    }

    public interface PreHandleWorkflow {

    }
}
