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

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.components.StoreComponent;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.utils.NonAtomicReference;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Objects;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * A {@link HandleContext} implementation that primarily uses adapters of {@code mono-service}
 * utilities. These adapters will either be replaced with new implementations; or refactored
 * and ported from {@code mono-service} into {@code hedera-app} at a later time.
 */
@Singleton
public class MonoHandleContext implements HandleContext {
    private final LongSupplier nums;
    private final ExpiryValidator expiryValidator;
    private final TransactionContext txnCtx;
    private final AttributeValidator attributeValidator;
    private final NonAtomicReference<HederaState> mutableState;
    private final Provider<StoreComponent.Factory> storeFactory;

    @Inject
    public MonoHandleContext(
            @NonNull final EntityIdSource ids,
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final AttributeValidator attributeValidator,
            @NonNull final TransactionContext txnCtx,
            @NonNull final NonAtomicReference<HederaState> mutableState,
            @NonNull final Provider<StoreComponent.Factory> storeFactory) {
        this.nums = Objects.requireNonNull(ids)::newAccountNumber;
        this.txnCtx = Objects.requireNonNull(txnCtx);
        this.expiryValidator = Objects.requireNonNull(expiryValidator);
        this.attributeValidator = Objects.requireNonNull(attributeValidator);
        this.mutableState = Objects.requireNonNull(mutableState);
        this.storeFactory = Objects.requireNonNull(storeFactory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Instant consensusNow() {
        return txnCtx.consensusTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LongSupplier newEntityNumSupplier() {
        return nums;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AttributeValidator attributeValidator() {
        return attributeValidator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExpiryValidator expiryValidator() {
        return expiryValidator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <C> C createReadableStore(@NonNull Class<C> storeInterface) {
        // FUTURE: This is a temporary solution to the problem of creating a readable store factory.
        // Once HandleContext is created which is not Singleton and is created per request, we can
        // inject the store factory directly into the HandleContext constructor.
        // Currently, this ReadableStoreFactory is created per each handle as defined by the
        // custom scope @HandleScope
        final var readableStoreFactory =
                storeFactory.get().create(mutableState.get()).storeFactory();
        return readableStoreFactory.createStore(storeInterface);
    }

    @Nullable
    @Override
    public SignatureVerification verificationFor(@NonNull Key key) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Nullable
    @Override
    public SignatureVerification verificationFor(long hollowAccountNumber) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
