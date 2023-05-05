/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.workflows.handle;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.config.GlobalDynamicConfig;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

public class HandleContextImpl implements HandleContext {

    private final Instant consensusNow;
    private final TransactionBody txBody;
    private final ReadableStoreFactory readableStoreFactory;
    private final WritableStoreFactory writableStoreFactory;
    private final SingleTransactionRecordBuilder recordBuilder;

    public HandleContextImpl(
            @NonNull final HederaState state,
            @NonNull final String serviceScope,
            @NonNull final TransactionBody txBody,
            @NonNull final Instant consensusNow,
            @NonNull final SingleTransactionRecordBuilder recordBuilder) {
        requireNonNull(state, "The argument 'state' must not be null");
        requireNonNull(serviceScope, "The argument 'serviceScope' must not be null");
        this.txBody = requireNonNull(txBody, "The argument 'txBody' must not be null");
        this.consensusNow = requireNonNull(consensusNow, "The argument 'consensusNow' must not be null");
        this.recordBuilder = requireNonNull(recordBuilder, "The argument 'recordBuilder' must not be null");

        this.readableStoreFactory = new ReadableStoreFactory(state);
        this.writableStoreFactory = new WritableStoreFactory(state, serviceScope);
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
    public GlobalDynamicConfig config() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public long newEntityNum() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    @NonNull
    public AttributeValidator attributeValidator() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    @NonNull
    public ExpiryValidator expiryValidator() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    @Nullable
    public SignatureVerification verificationFor(@NonNull Key key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    @NonNull
    public <C> C readableStore(@NonNull Class<C> storeInterface) {
        return readableStoreFactory.getStore(storeInterface);
    }

    @Override
    @NonNull
    public <C> C writableStore(@NonNull Class<C> storeInterface) {
        return writableStoreFactory.getStore(storeInterface);
    }

    @Override
    @NonNull
    public <T> T recordBuilder(@NonNull Class<T> singleTransactionRecordBuilderClass) {
        if (! singleTransactionRecordBuilderClass.isInstance(recordBuilder)) {
            throw new IllegalArgumentException("Not a valid record builder class");
        }
        return singleTransactionRecordBuilderClass.cast(recordBuilder);
    }

    @Override
    @NonNull
    public TransactionResult dispatchPrecedingTransaction(@NonNull TransactionBody txBody) throws HandleException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    @NonNull
    public TransactionResult dispatchChildTransaction(@NonNull TransactionBody txBody) throws HandleException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    @NonNull
    public TransactionStack transactionStack() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

}
