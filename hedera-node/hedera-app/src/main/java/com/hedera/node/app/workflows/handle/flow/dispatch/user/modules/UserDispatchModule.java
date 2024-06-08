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

package com.hedera.node.app.workflows.handle.flow.dispatch.user.modules;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.fees.FeeAccumulatorImpl;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.records.FinalizeContext;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.DefaultKeyVerifier;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ServiceApiFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.TokenContextImpl;
import com.hedera.node.app.workflows.handle.flow.FlowHandleContext;
import com.hedera.node.app.workflows.handle.flow.dispatch.Dispatch;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.UserDispatchComponent;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.UserDispatchScope;
import com.hedera.node.app.workflows.handle.flow.records.UserRecordInitializer;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.config.api.Configuration;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

@Module(subcomponents = {})
public interface UserDispatchModule {
    @Binds
    @UserDispatchScope
    Dispatch bindDispatch(UserDispatchComponent userDispatchComponent);

    @Provides
    @UserDispatchScope
    static Set<Key> provideRequiredKeys(PreHandleResult preHandleResult) {
        return preHandleResult.requiredKeys();
    }

    @Provides
    @UserDispatchScope
    static Set<Account> provideHollowAccounts(PreHandleResult preHandleResult) {
        return preHandleResult.hollowAccounts();
    }

    @Provides
    @UserDispatchScope
    static AccountID provideSyntheticPayer(TransactionInfo txnInfo) {
        return txnInfo.payerID();
    }

    @Provides
    @UserDispatchScope
    static KeyVerifier provideKeyVerifier(
            @NonNull HederaConfig hederaConfig, TransactionInfo txnInfo, PreHandleResult preHandleResult) {
        return new DefaultKeyVerifier(
                txnInfo.signatureMap().sigPair().size(), hederaConfig, preHandleResult.getVerificationResults());
    }

    @Provides
    @UserDispatchScope
    static Fees provideFees(@NonNull FeeContext feeContext, @NonNull TransactionDispatcher dispatcher) {
        return dispatcher.dispatchComputeFees(feeContext);
    }

    @Binds
    @UserDispatchScope
    HandleContext bindHandleContext(FlowHandleContext handleContext);

    @Binds
    @UserDispatchScope
    FeeContext bindFeeContext(FlowHandleContext handleContext);

    @Provides
    @UserDispatchScope
    static ServiceApiFactory provideServiceApiFactory(
            @NonNull final SavepointStackImpl stack,
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService) {
        return new ServiceApiFactory(stack, configuration, storeMetricsService);
    }

    @Provides
    @UserDispatchScope
    static FeeAccumulator provideFeeAccumulator(
            @NonNull SingleTransactionRecordBuilderImpl recordBuilder, @NonNull ServiceApiFactory serviceApiFactory) {
        final var tokenApi = serviceApiFactory.getApi(TokenServiceApi.class);
        return new FeeAccumulatorImpl(tokenApi, recordBuilder);
    }

    @Provides
    @UserDispatchScope
    static WritableEntityIdStore provideEntityIdStore(
            SavepointStackImpl stack, Configuration configuration, StoreMetricsService storeMetricsService) {
        final var entityIdsFactory =
                new WritableStoreFactory(stack, EntityIdService.NAME, configuration, storeMetricsService);
        return entityIdsFactory.getStore(WritableEntityIdStore.class);
    }

    @Provides
    @UserDispatchScope
    static WritableStoreFactory provideWritableStoreFactory(
            SavepointStackImpl stack,
            TransactionInfo txnInfo,
            Configuration configuration,
            ServiceScopeLookup serviceScopeLookup,
            StoreMetricsService storeMetricsService) {
        return new WritableStoreFactory(
                stack, serviceScopeLookup.getServiceName(txnInfo.txBody()), configuration, storeMetricsService);
    }

    @Provides
    @UserDispatchScope
    static HandleContext.TransactionCategory provideTransactionCategory() {
        return HandleContext.TransactionCategory.USER;
    }

    @Binds
    @UserDispatchScope
    FinalizeContext bindFinalizeContext(TokenContextImpl tokenContext);

    @Provides
    @UserDispatchScope
    static SingleTransactionRecordBuilderImpl provideUserTransactionRecordBuilder(
            @NonNull RecordListBuilder recordListBuilder,
            @NonNull UserRecordInitializer userRecordInitializer,
            TransactionInfo txnInfo) {
        userRecordInitializer.initializeUserRecord(recordListBuilder.userTransactionRecordBuilder(), txnInfo);
        return recordListBuilder.userTransactionRecordBuilder();
    }
}
