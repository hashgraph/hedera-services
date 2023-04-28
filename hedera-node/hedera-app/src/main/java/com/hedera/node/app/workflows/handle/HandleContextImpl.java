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

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.function.LongSupplier;

public class HandleContextImpl implements HandleContext {

    private final Instant consensusNow;
    private final HederaState state;
    private final TransactionBody txBody;
    private WritableStoreFactory storeFactory;

    public HandleContextImpl(
            @NonNull final HederaState state,
            @NonNull final TransactionBody txBody,
            @NonNull final Instant consensusNow) {
        this.state = requireNonNull(state, "The argument 'state' must not be null");
        this.txBody = requireNonNull(txBody, "The argument 'txBody' must not be null");
        this.consensusNow = requireNonNull(consensusNow, "The argument 'consensusNow' must not be null");
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
    public <C> C createStore(@NonNull Class<C> storeInterface) {
        if (storeFactory == null) {
            throw new IllegalStateException("The service-scope of the context has not been set yet");
        }
        return storeFactory.createStore(storeInterface);
    }

    @Override
    public LongSupplier newEntityNumSupplier() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public AttributeValidator attributeValidator() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ExpiryValidator expiryValidator() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void setServiceScope(@NonNull final String serviceName) {
        storeFactory = new WritableStoreFactory(state, serviceName);
    }
}
