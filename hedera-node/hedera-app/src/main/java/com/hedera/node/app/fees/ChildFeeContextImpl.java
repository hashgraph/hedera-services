/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.fees;

import static com.hedera.node.app.spi.HapiUtils.functionOf;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.UnknownHederaFunctionality;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.handle.HandleContextImpl;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * A {@link FeeContext} to use when computing the cost of a child transaction within
 * a given {@link com.hedera.node.app.spi.workflows.HandleContext}.
 */
public class ChildFeeContextImpl implements FeeContext {
    private final FeeManager feeManager;
    private final HandleContextImpl context;
    private final TransactionBody body;
    private final AccountID payerId;
    private final boolean computeFeesAsInternalDispatch;

    public ChildFeeContextImpl(
            @NonNull final FeeManager feeManager,
            @NonNull final HandleContextImpl context,
            @NonNull final TransactionBody body,
            @NonNull final AccountID payerId,
            final boolean computeFeesAsInternalDispatch) {
        this.feeManager = Objects.requireNonNull(feeManager);
        this.context = Objects.requireNonNull(context);
        this.body = Objects.requireNonNull(body);
        this.payerId = Objects.requireNonNull(payerId);
        this.computeFeesAsInternalDispatch = computeFeesAsInternalDispatch;
    }

    @Override
    public @NonNull AccountID payer() {
        return payerId;
    }

    @Override
    public @NonNull TransactionBody body() {
        return body;
    }

    @Override
    public @NonNull FeeCalculator feeCalculator(@NonNull final SubType subType) {
        try {
            var storeFactory = new ReadableStoreFactory((HederaState) context.savepointStack());
            return feeManager.createFeeCalculator(
                    body,
                    Key.DEFAULT,
                    functionOf(body),
                    0,
                    0,
                    context.consensusNow(),
                    subType,
                    computeFeesAsInternalDispatch,
                    storeFactory);
        } catch (UnknownHederaFunctionality e) {
            throw new IllegalStateException(
                    "Child fee context was constructed with invalid transaction body " + body, e);
        }
    }

    @Override
    public <T> @NonNull T readableStore(@NonNull final Class<T> storeInterface) {
        return context.readableStore(storeInterface);
    }

    @Override
    public @Nullable Configuration configuration() {
        return context.configuration();
    }

    @Override
    public @Nullable Authorizer authorizer() {
        return context.authorizer();
    }

    @Override
    public int numTxnSignatures() {
        return context.numTxnSignatures();
    }
}
