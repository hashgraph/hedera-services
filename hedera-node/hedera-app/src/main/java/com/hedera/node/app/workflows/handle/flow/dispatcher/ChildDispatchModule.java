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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.fees.ChildFeeContextImpl;
import com.hedera.node.app.fees.FeeAccumulatorImpl;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.workflows.ComputeDispatchFeesAsTopLevel;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.ServiceApiFactory;
import com.hedera.node.app.workflows.handle.flow.DueDiligenceInfo;
import com.hedera.node.app.workflows.handle.flow.FlowHandleContext;
import com.hedera.node.app.workflows.handle.flow.annotations.DispatchScope;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.swirlds.config.api.Configuration;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;

@Module
public interface ChildDispatchModule {
    @Binds
    @DispatchScope
    HandleContext bindHandleContext(FlowHandleContext handleContext);

    @Provides
    @DispatchScope
    static FeeContext bindFeeContext(
            @NonNull HandleContext context,
            @NonNull TransactionInfo txnInfo,
            @NonNull final FeeManager feeManager,
            @NonNull final AccountID syntheticPayer,
            @NonNull final Authorizer authorizer,
            @NonNull final ComputeDispatchFeesAsTopLevel computeDispatchFeesAsTopLevel) {
        return new ChildFeeContextImpl(
                feeManager,
                context,
                txnInfo.txBody(),
                syntheticPayer,
                computeDispatchFeesAsTopLevel == ComputeDispatchFeesAsTopLevel.NO,
                authorizer,
                0);
    }

    @Provides
    @DispatchScope
    static Fees provideFees(
            @NonNull HandleContext handleContext,
            @NonNull TransactionInfo txnInfo,
            @NonNull AccountID syntheticPayer,
            @NonNull HandleContext.TransactionCategory childCategory) {
        if (childCategory != HandleContext.TransactionCategory.SCHEDULED) {
            return Fees.FREE;
        }
        return handleContext
                .dispatchComputeFees(txnInfo.txBody(), syntheticPayer, ComputeDispatchFeesAsTopLevel.YES)
                .onlyServiceComponent();
    }

    @Provides
    @DispatchScope
    static ReadableStoreFactory provideReadableStoreFactory(SavepointStackImpl stack) {
        return new ReadableStoreFactory(stack);
    }

    @Provides
    @DispatchScope
    static DueDiligenceInfo provideDueDiligenceInfo() {
        return new DueDiligenceInfo(AccountID.DEFAULT, ResponseCodeEnum.OK);
    }

    @Provides
    @DispatchScope
    static FeeAccumulator provideFeeAccumulator(
            @NonNull SavepointStackImpl stack,
            @NonNull Configuration configuration,
            @NonNull StoreMetricsService storeMetricsService,
            @NonNull SingleTransactionRecordBuilderImpl recordBuilder) {
        final var serviceApiFactory = new ServiceApiFactory(stack, configuration, storeMetricsService);
        final var tokenApi = serviceApiFactory.getApi(TokenServiceApi.class);
        return new FeeAccumulatorImpl(tokenApi, recordBuilder);
    }
}
