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

package com.hedera.node.app.workflows.handle.flow.modules;

import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.workflows.handle.TokenContextImpl;
import com.hedera.node.app.workflows.handle.flow.annotations.PlatformTransactionScope;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.HederaState;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

@Module
public interface ContextModule {
    @Binds
    @PlatformTransactionScope
    TokenContext bindTokenContext(TokenContextImpl tokenContext);

    @Provides
    @PlatformTransactionScope
    static TokenContext provideTokenContext(
            @NonNull Configuration configuration,
            @NonNull StoreMetricsService storeMetricsService,
            @NonNull SavepointStackImpl stack,
            @NonNull RecordListBuilder recordListBuilder,
            @NonNull BlockRecordManager blockRecordManager,
            @NonNull HederaState state) {
        final var consTimeOfLastHandledTxn = blockRecordManager.consTimeOfLastHandledTxn();
        final var isFirstTransaction = !consTimeOfLastHandledTxn.isAfter(Instant.EPOCH);
        return new TokenContextImpl(
                configuration,
                state,
                storeMetricsService,
                stack,
                recordListBuilder,
                blockRecordManager,
                isFirstTransaction);
    }
}
