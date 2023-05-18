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

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

public class HandleContextImpl implements HandleContext {

    private final Instant consensusNow;
    private final TransactionBody txBody;
    private final TransactionCategory transactionCategory;
    private final SingleTransactionRecordBuilder recordBuilder;
    private final SavepointStackImpl stack;
    private final WritableStoreFactory writableStoreFactory;
    private final HandleContextBase base;
    private final TransactionRunner runner;

    private ReadableStoreFactory readableStoreFactory;

    public HandleContextImpl(
            @NonNull final String serviceScope,
            @NonNull final Instant consensusNow,
            @NonNull final TransactionBody txBody,
            @NonNull final TransactionCategory transactionCategory,
            @NonNull final SingleTransactionRecordBuilder recordBuilder,
            @NonNull final SavepointStackImpl stack,
            @NonNull final HandleContextBase base,
            @NonNull final TransactionRunner runner) {
        requireNonNull(serviceScope, "serviceScope must not be null");
        this.consensusNow = requireNonNull(consensusNow, "consensusNow must not be null");
        this.txBody = requireNonNull(txBody, "txBody must not be null");
        this.transactionCategory = requireNonNull(transactionCategory, "transactionCategory must not be null");
        this.recordBuilder = requireNonNull(recordBuilder, "recordBuilder must not be null");
        this.stack = requireNonNull(stack, "stack must not be null");
        this.base = requireNonNull(base, "base must not be null");
        this.runner = requireNonNull(runner, "runner must not be null");

        this.writableStoreFactory = new WritableStoreFactory(stack, serviceScope);
    }

    @Override
    @NonNull
    public Instant consensusNow() {
        return consensusNow;
    }

    @Override
    @NonNull
    public TransactionBody body() {
        return txBody;
    }

    @Override
    @NonNull
    public TransactionCategory category() {
        return transactionCategory;
    }

    @Override
    @NonNull
    public Configuration config() {
        return stack.peek().config();
    }

    @Override
    public long newEntityNum() {
        return stack.peek().newEntityNum();
    }

    @Override
    @NonNull
    public AttributeValidator attributeValidator() {
        return stack.peek().attributeValidator();
    }

    @Override
    @NonNull
    public ExpiryValidator expiryValidator() {
        return stack.peek().expiryValidator();
    }

    @Override
    @Nullable
    public SignatureVerification verificationFor(@NonNull final Key key) {
        requireNonNull(key, "key must not be null");
        return base.keyVerifications().get(key);
    }

    @Override
    @Nullable
    public SignatureVerification verificationFor(@NonNull final Bytes evmAlias) {
        requireNonNull(evmAlias, "evmAlias must not be null");
        if (evmAlias.length() == 20) {
            for (final var result : base.keyVerifications().values()) {
                final var account = result.evmAlias();
                if (account != null && evmAlias.matchesPrefix(account)) {
                    return result;
                }
            }
        }
        return null;
    }

    @Override
    @NonNull
    public <C> C readableStore(@NonNull Class<C> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        if (readableStoreFactory == null) {
            readableStoreFactory = new ReadableStoreFactory(stack);
        }
        return readableStoreFactory.getStore(storeInterface);
    }

    @Override
    @NonNull
    public <C> C writableStore(@NonNull Class<C> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        return writableStoreFactory.getStore(storeInterface);
    }

    @Override
    @NonNull
    public <T> T recordBuilder(@NonNull Class<T> recordBuilderClass) {
        requireNonNull(recordBuilderClass, "singleTransactionRecordBuilderClass must not be null");
        if (!recordBuilderClass.isInstance(recordBuilder)) {
            throw new IllegalArgumentException("Not a valid record builder class");
        }
        return recordBuilderClass.cast(recordBuilder);
    }

    @Override
    @NonNull
    public ResponseCodeEnum dispatchPrecedingTransaction(@NonNull final TransactionBody txBody) {
        requireNonNull(txBody, "txBody must not be null");
        if (transactionCategory != TransactionCategory.USER) {
            throw new IllegalArgumentException("Only user-transactions can dispatch preceding transactions");
        }
        final var state = stack.peek().state();
        if (state.isModified()) {
            throw new IllegalStateException("Cannot dispatch a preceding transaction when the state has been modified");
        }
        if (stack.depth() > 1) {
            throw new IllegalStateException(
                    "Cannot dispatch a preceding transaction when child transactions have been dispatched");
        }

        // Calculate next available slot for preceding transaction
        final var timeSlot = base.timeSlotCalculator().getNextAvailablePrecedingSlot();

        // run the transaction
        final var currentRecordBuilder =
                base.recordBuilderList().remove(base.recordBuilderList().size() - 1);
        final var result = runner.run(timeSlot, txBody, TransactionCategory.PRECEDING, stack.peek(), base);
        base.recordBuilderList().add(currentRecordBuilder);

        return result;
    }

    @Override
    @NonNull
    public ResponseCodeEnum dispatchChildTransaction(@NonNull final TransactionBody txBody) {
        requireNonNull(txBody, "txBody must not be null");
        if (transactionCategory == TransactionCategory.PRECEDING) {
            throw new IllegalArgumentException("A preceding transaction cannot have child transactions");
        }

        // Calculate next available slot for child transaction
        final var timeSlot = base.timeSlotCalculator().getNextAvailableChildSlot();

        // create a savepoint
        stack.createSavepoint();

        // run the child-transaction
        final var result = runner.run(timeSlot, txBody, TransactionCategory.CHILD, stack.peek(), base);

        // rollback if the child-transaction failed
        if (result != ResponseCodeEnum.OK) {
            stack.rollback();
        }

        return result;
    }

    @NonNull
    @Override
    public SavepointStack savepointStack() {
        return stack;
    }

    public static class Builder {
        private String serviceScope;
        private Instant consensusNow;
        private TransactionBody txBody;
        private TransactionCategory transactionCategory;
        private SingleTransactionRecordBuilder recordBuilder;
        private SavepointStackImpl stack;
        private HandleContextBase base;
        private TransactionRunner runner;

        public HandleContext build() {
            return new HandleContextImpl(
                    serviceScope, consensusNow, txBody, transactionCategory, recordBuilder, stack, base, runner);
        }

        public String serviceScope() {
            return serviceScope;
        }

        public Builder serviceScope(String serviceScope) {
            this.serviceScope = serviceScope;
            return this;
        }

        public Instant consensusNow() {
            return consensusNow;
        }

        public Builder consensusNow(Instant consensusNow) {
            this.consensusNow = consensusNow;
            return this;
        }

        public TransactionBody txBody() {
            return txBody;
        }

        public Builder txBody(TransactionBody txBody) {
            this.txBody = txBody;
            return this;
        }

        public TransactionCategory transactionCategory() {
            return transactionCategory;
        }

        public Builder transactionCategory(TransactionCategory transactionCategory) {
            this.transactionCategory = transactionCategory;
            return this;
        }

        public SingleTransactionRecordBuilder recordBuilder() {
            return recordBuilder;
        }

        public Builder recordBuilder(SingleTransactionRecordBuilder recordBuilder) {
            this.recordBuilder = recordBuilder;
            return this;
        }

        public SavepointStackImpl stack() {
            return stack;
        }

        public Builder stack(SavepointStackImpl stack) {
            this.stack = stack;
            return this;
        }

        public HandleContextBase base() {
            return base;
        }

        public Builder base(HandleContextBase base) {
            this.base = base;
            return this;
        }

        public TransactionRunner runner() {
            return runner;
        }

        public Builder runner(TransactionRunner runner) {
            this.runner = runner;
            return this;
        }
    }
}
