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
    private final TransactionCategory category;
    private final SingleTransactionRecordBuilder recordBuilder;
    private final SavepointStackImpl stack;
    private final WritableStoreFactory writableStoreFactory;
    private final HandleContextBase base;
    private final HandleContextService service;

    private ReadableStoreFactory readableStoreFactory;

    public HandleContextImpl(
            @NonNull final String serviceScope,
            @NonNull final TransactionBody txBody,
            @NonNull final TransactionCategory category,
            @NonNull final SingleTransactionRecordBuilder recordBuilder,
            @NonNull final SavepointStackImpl stack,
            @NonNull final HandleContextBase base,
            @NonNull final HandleContextService service) {
        requireNonNull(serviceScope, "serviceScope must not be null");
        this.txBody = requireNonNull(txBody, "txBody must not be null");
        this.category = requireNonNull(category, "category must not be null");
        this.recordBuilder = requireNonNull(recordBuilder, "recordBuilder must not be null");
        this.stack = requireNonNull(stack, "stack must not be null");
        this.base = requireNonNull(base, "base must not be null");
        this.service = requireNonNull(service, "service must not be null");

        this.consensusNow = recordBuilder.consensusNow();
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
        return category;
    }

    @Override
    @NonNull
    public Configuration configuration() {
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
        // TODO: This code is shared with PreHandleResult and should probably be moved to a shared place
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
    public <T> T recordBuilder(@NonNull final Class<T> recordBuilderClass) {
        requireNonNull(recordBuilderClass, "singleTransactionRecordBuilderClass must not be null");
        return castRecordBuilder(recordBuilder, recordBuilderClass);
    }

    private static <T> T castRecordBuilder(
            @NonNull final SingleTransactionRecordBuilder recordBuilder, @NonNull final Class<T> recordBuilderClass) {
        if (!recordBuilderClass.isInstance(recordBuilder)) {
            throw new IllegalArgumentException("Not a valid record builder class");
        }
        return recordBuilderClass.cast(recordBuilder);
    }

    @Override
    @NonNull
    public <T> T dispatchPrecedingTransaction(
            @NonNull final TransactionBody txBody, @NonNull final Class<T> recordBuilderClass) {
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(recordBuilderClass, "singleTransactionRecordBuilderClass must not be null");

        if (category != TransactionCategory.USER) {
            throw new IllegalArgumentException("Only user-transactions can dispatch preceding transactions");
        }
        if (stack.depth() > 1) {
            throw new IllegalStateException(
                    "Cannot dispatch a preceding transaction when child transactions have been dispatched");
        }
        if (stack.peek().state().isModified()) {
            throw new IllegalStateException("Cannot dispatch a preceding transaction when the state has been modified");
        }

        // run the transaction
        final var result =
                service.dispatchPrecedingTransaction(txBody, stack.peek().state(), base);
        return castRecordBuilder(result, recordBuilderClass);
    }

    @Override
    @NonNull
    public <T> T dispatchChildTransaction(
            @NonNull final TransactionBody txBody, @NonNull final Class<T> recordBuilderClass) {
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(recordBuilderClass, "singleTransactionRecordBuilderClass must not be null");

        if (category == TransactionCategory.PRECEDING) {
            throw new IllegalArgumentException("A preceding transaction cannot have child transactions");
        }

        // create a savepoint
        stack.createSavepoint();

        // run the child-transaction
        final var result = service.dispatchChildTransaction(txBody, stack.peek().state(), base);

        // rollback if the child-transaction failed
        if (result.status() != ResponseCodeEnum.OK) {
            stack.rollback();
        }

        return castRecordBuilder(result, recordBuilderClass);
    }

    @NonNull
    @Override
    public SavepointStack savepointStack() {
        return stack;
    }
}
