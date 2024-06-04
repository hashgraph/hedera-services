/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.flow.dispatcher;

import com.hedera.node.app.fees.FeeContextImpl;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.handle.flow.FlowHandleContext;
import com.hedera.node.app.workflows.handle.flow.annotations.UserTxnScope;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.HederaState;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;

@Module(subcomponents = {ChildDispatchComponent.class, UserDispatchComponent.class})
public interface UserDispatchModule {
    @Binds
    @UserTxnScope
    HandleContext bindHandleContext(FlowHandleContext handleContext);

    @Provides
    @UserTxnScope
    static FeeContext bindFeeContext(
            @NonNull UserTransactionComponent userTxn,
            @NonNull final FeeManager feeManager,
            @NonNull final ReadableStoreFactory storeFactory,
            @NonNull final Configuration configuration,
            @NonNull final Authorizer authorizer) {
        return new FeeContextImpl(
                userTxn.consensusNow(),
                userTxn.txnInfo(),
                userTxn.preHandleResult().payerKey(),
                userTxn.preHandleResult().payer(),
                feeManager,
                storeFactory,
                configuration,
                authorizer,
                userTxn.txnInfo().signatureMap().sigPair().size());
    }

    @Provides
    @UserTxnScope
    static ReadableStoreFactory provideReadableStoreFactory(SavepointStackImpl stack) {
        return new ReadableStoreFactory(stack);
    }

    @Provides
    @UserTxnScope
    static SavepointStackImpl provideSavepointStackImpl(@NonNull final HederaState state) {
        return new SavepointStackImpl(state);
    }
}
