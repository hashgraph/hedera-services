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

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.fees.FeeAccumulatorImpl;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.records.FinalizeContext;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.ServiceApiFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.TriggeredFinalizeContext;
import com.hedera.node.app.workflows.handle.flow.DueDiligenceInfo;
import com.hedera.node.app.workflows.handle.flow.FlowHandleContext;
import com.hedera.node.app.workflows.handle.flow.annotations.ChildDispatchScope;
import com.hedera.node.app.workflows.handle.flow.qualifiers.ChildQualifier;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.swirlds.config.api.Configuration;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

@Module
public interface ChildDispatchModule {
    @Binds
    @ChildDispatchScope
    @ChildQualifier
    HandleContext bindHandleContext(FlowHandleContext handleContext);

    @Binds
    @ChildDispatchScope
    @ChildQualifier
    FeeContext bindFeeContext(FlowHandleContext feeContext);

    @Provides
    @ChildDispatchScope
    @ChildQualifier
    static Fees provideFees(
            @ChildQualifier @NonNull FeeContext feeContext,
            @NonNull HandleContext.TransactionCategory childCategory,
            @NonNull TransactionDispatcher dispatcher) {
        if (childCategory != HandleContext.TransactionCategory.SCHEDULED) {
            return Fees.FREE;
        }
        return dispatcher.dispatchComputeFees(feeContext).onlyServiceComponent();
    }

    @Provides
    @ChildDispatchScope
    @ChildQualifier
    static ReadableStoreFactory provideReadableStoreFactory(@ChildQualifier SavepointStackImpl stack) {
        return new ReadableStoreFactory(stack);
    }

    @Provides
    @ChildDispatchScope
    @ChildQualifier
    static FeeAccumulator provideFeeAccumulator(
            @ChildQualifier @NonNull SingleTransactionRecordBuilderImpl recordBuilder,
            @ChildQualifier @NonNull ServiceApiFactory serviceApiFactory) {
        final var tokenApi = serviceApiFactory.getApi(TokenServiceApi.class);
        return new FeeAccumulatorImpl(tokenApi, recordBuilder);
    }

    @Provides
    @ChildDispatchScope
    @ChildQualifier
    static DueDiligenceInfo provideDueDiligenceInfo(NodeInfo creator) {
        return new DueDiligenceInfo(creator.accountId(), ResponseCodeEnum.OK);
    }

    @Provides
    @ChildDispatchScope
    @ChildQualifier
    static ServiceApiFactory provideServiceApiFactory(
            @ChildQualifier @NonNull final SavepointStackImpl stack,
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService) {
        return new ServiceApiFactory(stack, configuration, storeMetricsService);
    }

    @Provides
    @ChildDispatchScope
    @ChildQualifier
    static Key providePayerKey() {
        return Key.DEFAULT;
    }

    @Provides
    @ChildDispatchScope
    @ChildQualifier
    static WritableEntityIdStore provideEntityIdStore(
            @ChildQualifier SavepointStackImpl stack,
            Configuration configuration,
            StoreMetricsService storeMetricsService) {
        final var entityIdsFactory =
                new WritableStoreFactory(stack, EntityIdService.NAME, configuration, storeMetricsService);
        return entityIdsFactory.getStore(WritableEntityIdStore.class);
    }

    @Provides
    @ChildDispatchScope
    @ChildQualifier
    static WritableStoreFactory provideWritableStoreFactory(
            @ChildQualifier SavepointStackImpl stack,
            @ChildQualifier TransactionInfo txnInfo,
            Configuration configuration,
            ServiceScopeLookup serviceScopeLookup,
            StoreMetricsService storeMetricsService) {
        return new WritableStoreFactory(
                stack, serviceScopeLookup.getServiceName(txnInfo.txBody()), configuration, storeMetricsService);
    }

    @Provides
    @ChildDispatchScope
    @ChildQualifier
    static FinalizeContext provideTriggeredFinalizeContext(
            @ChildQualifier @NonNull final ReadableStoreFactory readableStoreFactory,
            @ChildQualifier @NonNull final WritableStoreFactory writableStoreFactory,
            @ChildQualifier @NonNull final SingleTransactionRecordBuilderImpl recordBuilder,
            @NonNull final Instant consensusNow,
            @NonNull final Configuration configuration) {
        return new TriggeredFinalizeContext(
                readableStoreFactory, writableStoreFactory, recordBuilder, consensusNow, configuration);
    }
}
