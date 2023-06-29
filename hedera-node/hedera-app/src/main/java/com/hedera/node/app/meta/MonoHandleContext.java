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

package com.hedera.node.app.meta;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Objects;
import java.util.function.LongSupplier;
import javax.inject.Singleton;

/**
 * A {@link HandleContext} implementation that primarily uses adapters of {@code mono-service} utilities. These adapters
 * will either be replaced with new implementations; or refactored and ported from {@code mono-service} into
 * {@code hedera-app} at a later time.
 */
@Singleton
public class MonoHandleContext implements HandleContext {

    private final TransactionBody txBody;
    private final LongSupplier nums;
    private final ExpiryValidator expiryValidator;
    private final TransactionContext txnCtx;
    private final AttributeValidator attributeValidator;
    private final ReadableStoreFactory readableStoreFactory;
    private final SingleTransactionRecordBuilder recordBuilder;

    public MonoHandleContext(
            @NonNull final TransactionBody txBody,
            @NonNull final EntityIdSource ids,
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final AttributeValidator attributeValidator,
            @NonNull final TransactionContext txnCtx,
            @NonNull final ReadableStoreFactory readableStoreFactory,
            @NonNull final SingleTransactionRecordBuilder recordBuilder) {
        this.txBody = Objects.requireNonNull(txBody);
        this.nums = Objects.requireNonNull(ids)::newAccountNumber;
        this.txnCtx = Objects.requireNonNull(txnCtx);
        this.expiryValidator = Objects.requireNonNull(expiryValidator);
        this.attributeValidator = Objects.requireNonNull(attributeValidator);
        this.readableStoreFactory = Objects.requireNonNull(readableStoreFactory);
        this.recordBuilder = Objects.requireNonNull(recordBuilder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Instant consensusNow() {
        return txnCtx.consensusTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public TransactionBody body() {
        return txBody;
    }

    @NonNull
    @Override
    public AccountID payer() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Configuration configuration() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Nullable
    @Override
    public Key payerKey() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long newEntityNum() {
        return nums.getAsLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AttributeValidator attributeValidator() {
        return attributeValidator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public ExpiryValidator expiryValidator() {
        return expiryValidator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public SignatureVerification verificationFor(@NonNull Key key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    @NonNull
    public SignatureVerification verificationFor(@NonNull Bytes evmAlias) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    @NonNull
    public <C> C readableStore(@NonNull final Class<C> storeInterface) {
        Objects.requireNonNull(storeInterface, "storeInterface must not be null");
        return readableStoreFactory.getStore(storeInterface);
    }

    @Override
    @NonNull
    public <C> C writableStore(@NonNull Class<C> storeInterface) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    @NonNull
    public <T> T recordBuilder(@NonNull final Class<T> recordBuilderClass) {
        Objects.requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        if (!recordBuilderClass.isInstance(recordBuilder)) {
            throw new IllegalArgumentException("Not a valid record builder class");
        }
        return recordBuilderClass.cast(recordBuilder);
    }

    @Override
    @NonNull
    public <T> T dispatchPrecedingTransaction(@NonNull TransactionBody txBody, @NonNull Class<T> recordBuilderClass) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    @NonNull
    public <T> T dispatchChildTransaction(@NonNull TransactionBody txBody, @NonNull Class<T> recordBuilderClass) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    @NonNull
    public <T> T dispatchRemovableChildTransaction(
            @NonNull TransactionBody txBody, @NonNull Class<T> recordBuilderClass) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    @NonNull
    public <T> T addChildRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    @NonNull
    public <T> T addRemovableChildRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    @NonNull
    public SavepointStack savepointStack() {
        throw new UnsupportedOperationException("Not yet implemented!");
    }
}
