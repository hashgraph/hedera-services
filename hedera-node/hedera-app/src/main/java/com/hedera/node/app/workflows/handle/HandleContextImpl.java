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
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.stack.TransactionStackEntry;
import com.hedera.node.app.workflows.handle.stack.TransactionStackImpl;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Map;

public class HandleContextImpl implements HandleContext {

    private final HandleContextManager manager;
    private final TransactionBody txBody;
    private final String serviceScope;
    private final Map<Key, SignatureVerification> signatureVerifications;

    private final SingleTransactionRecordBuilder recordBuilder;
    private final TransactionStackImpl stack;

    private ReadableStoreFactory readableStoreFactory;
    private WritableStoreFactory writableStoreFactory;



    public HandleContextImpl(
            @NonNull final HandleContextManager manager,
            @NonNull final TransactionBody txBody,
            @NonNull final String serviceScope,
            @NonNull final Map<Key, SignatureVerification> signatureVerifications,
            @NonNull final SingleTransactionRecordBuilder recordBuilder,
            @NonNull final HederaState state,
            @NonNull final Instant consensusNow,
            @NonNull final Configuration config,
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final AttributeValidator attributeValidator) {
        this.manager = requireNonNull(manager, "manager must not be null");
        this.txBody = requireNonNull(txBody, "txBody must not be null");
        this.serviceScope = requireNonNull(serviceScope, "serviceScope must not be null");
        this.signatureVerifications = requireNonNull(signatureVerifications, "signatureVerifications must not be null");
        this.recordBuilder = requireNonNull(recordBuilder, "recordBuilder must not be null");

        final var stackEntry = new TransactionStackEntry(
                requireNonNull(state, "state must not be null"),
                requireNonNull(consensusNow, "consensusNow must not be null"),
                requireNonNull(config, "config must not be null"),
                requireNonNull(attributeValidator, "attributeValidator must not be null"),
                requireNonNull(expiryValidator, "expiryValidator must not be null"));
        this.stack = new TransactionStackImpl(stackEntry);
    }

    @Override
    @NonNull
    public Instant consensusNow() {
        return stack.peek().consensusNow();
    }

    @Override
    @NonNull
    public TransactionBody body() {
        return txBody;
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
    public SignatureVerification verificationFor(@NonNull Key key) {
        requireNonNull(key, "key must not be null");
        return signatureVerifications.get(key);
    }

    @Override
    @NonNull
    public <C> C readableStore(@NonNull Class<C> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        return stack.peek().readableStore(storeInterface);
    }

    @Override
    @NonNull
    public <C> C writableStore(@NonNull Class<C> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        return stack.peek().writableStore(storeInterface, serviceScope);
    }

//    @NonNull
//    public <C> C readableStore(@NonNull final Class<C> storeInterface) {
//        requireNonNull(storeInterface, "storeInterface must not be null");
//        if (readableStoreFactory == null) {
//            readableStoreFactory = new ReadableStoreFactory(state);
//        }
//        return readableStoreFactory.getStore(storeInterface);
//    }
//
//    @NonNull
//    public <C> C writableStore(@NonNull final Class<C> storeInterface, @NonNull final String serviceScope) {
//        requireNonNull(storeInterface, "storeInterface must not be null");
//        requireNonNull(serviceScope, "serviceScope must not be null");
//        if (writableStoreFactory == null) {
//            writableStoreFactory = new WritableStoreFactory(state);
//        }
//        return writableStoreFactory.getStore(storeInterface, serviceScope);
//    }


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
    public SingleTransactionRecord dispatchPrecedingTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final AccountID creator)
            throws HandleException {
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(creator, "creator must not be null");

        return manager.dispatchPrecedingTransaction(txBody, creator);
    }

    @Override
    @NonNull
    public SingleTransactionRecord dispatchChildTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final AccountID creator)
            throws HandleException {
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(creator, "creator must not be null");

        return manager.dispatchChildTransaction(txBody, creator);
    }

    @NonNull
    @Override
    public TransactionStack transactionStack() {
        return stack;
    }
}
