/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.workflows.dispatcher;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.CallContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.state.HederaState;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/** Default implementation of {@link CallContext} */
public class CallContextImpl implements CallContext {

    private final Dispatcher dispatcher;
    private final HederaState state;

    /**
     * Constructor of {@code CallContextImpl}
     *
     * @param dispatcher the {@link Dispatcher} that will be used to forward requests
     * @param state the {@link HederaState} of this {@code CallContext}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public CallContextImpl(@NonNull final Dispatcher dispatcher, @NonNull final HederaState state) {
        this.dispatcher = requireNonNull(dispatcher);
        this.state = requireNonNull(state);
    }

    @NonNull
    @Override
    public TransactionMetadata preHandle(
            @NonNull final TransactionBody transactionBody, @NonNull final AccountID payer) {
        requireNonNull(transactionBody);
        requireNonNull(payer);

        return dispatcher.dispatchPreHandle(state, transactionBody, payer);
    }
}
