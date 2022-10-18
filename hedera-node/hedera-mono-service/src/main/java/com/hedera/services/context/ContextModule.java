/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context;

import com.hederahashgraph.api.proto.java.AccountID;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import java.time.Instant;
import java.util.function.Supplier;
import javax.inject.Singleton;

@Module
public interface ContextModule {
    @Binds
    @Singleton
    CurrentPlatformStatus bindCurrentPlatformStatus(ContextPlatformStatus contextPlatformStatus);

    @Binds
    @Singleton
    TransactionContext bindTransactionContext(BasicTransactionContext txnCtx);

    @Provides
    @Singleton
    static Supplier<Instant> provideConsensusTime(TransactionContext txnCtx) {
        return txnCtx::consensusTime;
    }

    @Provides
    @Singleton
    static AccountID provideEffectiveNodeAccount(NodeInfo nodeInfo) {
        return nodeInfo.hasSelfAccount() ? nodeInfo.selfAccount() : AccountID.getDefaultInstance();
    }
}
