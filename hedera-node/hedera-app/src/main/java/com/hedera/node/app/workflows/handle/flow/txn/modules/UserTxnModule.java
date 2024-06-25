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

package com.hedera.node.app.workflows.handle.flow.txn.modules;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.signature.SignatureVerificationFuture;
import com.hedera.node.app.spi.throttle.ThrottleAdviser;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.throttle.AppThrottleAdviser;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.TokenContextImpl;
import com.hedera.node.app.workflows.handle.flow.txn.UserTxnScope;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.HederaState;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Map;

/**
 * The module that provides all the dependencies needed by user transaction component.
 * All the objects provided by dagger used in this component are annotated with @UserTxnScope
 * can also be accessed by ChildDispatchScope and UserDispatchScope since they are subcomponents
 * of UserTxnComponent.
 */
@Module
public interface UserTxnModule {
    @Binds
    @UserTxnScope
    TokenContext bindTokenContext(TokenContextImpl tokenContext);

    @Provides
    @UserTxnScope
    static Configuration provideConfiguration(@NonNull final ConfigProvider configProvider) {
        return configProvider.getConfiguration();
    }

    @Provides
    @UserTxnScope
    static HederaConfig provideHederaConfig(@NonNull final Configuration configuration) {
        return configuration.getConfigData(HederaConfig.class);
    }

    @Provides
    @UserTxnScope
    static boolean provideIsGenesis(@LastHandledTime final Instant lastHandledConsensusTime) {
        return lastHandledConsensusTime.equals(Instant.EPOCH);
    }

    @Provides
    @UserTxnScope
    static TransactionInfo provideTransactionInfo(@NonNull final PreHandleResult preHandleResult) {
        return preHandleResult.txInfo();
    }

    @Provides
    @UserTxnScope
    static Map<Key, SignatureVerificationFuture> provideKeyVerifications(
            @NonNull final PreHandleResult preHandleResult) {
        return preHandleResult.getVerificationResults();
    }

    @Provides
    @UserTxnScope
    static int provideLegacyFeeCalcNetworkVpt(@NonNull final TransactionInfo txnInfo) {
        return txnInfo.signatureMap().sigPair().size();
    }

    @Provides
    @UserTxnScope
    static HederaFunctionality provideFunctionality(@NonNull final TransactionInfo txnInfo) {
        return txnInfo.functionality();
    }

    @Provides
    @UserTxnScope
    static Key providePayerKey(@NonNull final PreHandleResult preHandleResult) {
        return preHandleResult.payerKey() == null ? Key.DEFAULT : preHandleResult.payerKey();
    }

    @Provides
    @UserTxnScope
    static HederaState provideHederaState(@NonNull final WorkingStateAccessor workingStateAccessor) {
        return workingStateAccessor.getHederaState();
    }

    @Provides
    @UserTxnScope
    static SavepointStackImpl provideSavepointStackImpl(@NonNull final HederaState state) {
        return new SavepointStackImpl(state);
    }

    @Provides
    @UserTxnScope
    static ReadableStoreFactory provideReadableStoreFactory(@NonNull final SavepointStackImpl stack) {
        return new ReadableStoreFactory(stack);
    }

    @Binds
    @UserTxnScope
    ThrottleAdviser bindThrottleAdviser(@NonNull AppThrottleAdviser throttleAdviser);
}
