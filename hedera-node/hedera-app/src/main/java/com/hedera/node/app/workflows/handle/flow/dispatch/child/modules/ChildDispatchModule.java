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

package com.hedera.node.app.workflows.handle.flow.dispatch.child.modules;

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_UPDATE;
import static com.hedera.node.app.workflows.handle.flow.util.FlowUtils.CONTRACT_OPERATIONS;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeAccumulator;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.fees.ResourcePriceCalculatorImpl;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.records.FinalizeContext;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.fees.ResourcePriceCalculator;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.throttle.ThrottleAdviser;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.store.ServiceApiFactory;
import com.hedera.node.app.store.StoreFactoryImpl;
import com.hedera.node.app.store.WritableStoreFactory;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.TriggeredFinalizeContext;
import com.hedera.node.app.workflows.handle.flow.DispatchHandleContext;
import com.hedera.node.app.workflows.handle.flow.dispatch.child.ChildDispatchComponent;
import com.hedera.node.app.workflows.handle.flow.dispatch.child.ChildDispatchScope;
import com.hedera.node.app.workflows.handle.flow.dispatch.child.logic.ChildDispatchFactory;
import com.hedera.node.app.workflows.handle.flow.dispatch.logic.DispatchProcessor;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.info.NetworkInfo;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import javax.inject.Provider;

/**
 * Module for providing the child dispatch dependencies required for {@link ChildDispatchComponent}.
 */
@Module
public interface ChildDispatchModule {
    @Binds
    @ChildDispatchScope
    HandleContext bindHandleContext(DispatchHandleContext handleContext);

    @Binds
    @ChildDispatchScope
    ResourcePriceCalculator bindResourcePriceCalculator(@NonNull ResourcePriceCalculatorImpl resourcePriceCalculator);

    @Binds
    @ChildDispatchScope
    FeeContext bindFeeContext(DispatchHandleContext feeContext);

    @Provides
    @ChildDispatchScope
    static Fees provideFees(
            @NonNull final FeeContext feeContext,
            @NonNull final HandleContext.TransactionCategory childCategory,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final HederaFunctionality topLevelFunction,
            @NonNull @ChildQualifier final TransactionInfo childTxnInfo) {
        return switch (childCategory) {
            case SCHEDULED -> dispatcher.dispatchComputeFees(feeContext).onlyServiceComponent();
            case PRECEDING -> {
                if (CONTRACT_OPERATIONS.contains(topLevelFunction) || childTxnInfo.functionality() == CRYPTO_UPDATE) {
                    yield Fees.FREE;
                } else {
                    yield dispatcher.dispatchComputeFees(feeContext);
                }
            }
            case CHILD -> Fees.FREE;
            case USER -> throw new IllegalStateException("Should not dispatch child with user transaction category");
        };
    }

    @Provides
    @ChildDispatchScope
    @ChildQualifier
    static ReadableStoreFactory provideReadableStoreFactory(@ChildQualifier SavepointStackImpl stack) {
        return new ReadableStoreFactory(stack);
    }

    @Provides
    @ChildDispatchScope
    static FeeAccumulator provideFeeAccumulator(
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder,
            @NonNull final ServiceApiFactory serviceApiFactory) {
        final var tokenApi = serviceApiFactory.getApi(TokenServiceApi.class);
        return new FeeAccumulator(tokenApi, recordBuilder);
    }

    @Provides
    @ChildDispatchScope
    static ServiceApiFactory provideServiceApiFactory(
            @ChildQualifier @NonNull final SavepointStackImpl stack,
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService) {
        return new ServiceApiFactory(stack, configuration, storeMetricsService);
    }

    @Provides
    @ChildDispatchScope
    static StoreFactory storeFactory(
            @NonNull final ReadableStoreFactory readableStoreFactory,
            @NonNull final WritableStoreFactory writableStoreFactory,
            @NonNull final ServiceApiFactory serviceApiFactory) {
        return new StoreFactoryImpl(readableStoreFactory, writableStoreFactory, serviceApiFactory);
    }

    @Provides
    @ChildDispatchScope
    @ChildQualifier
    static Key providePayerKey() {
        // This is used exclusively for signature-usage fees, which should all zero out for a child dispatch.
        // (FUTURE) Rework things so that this implementation detail is hidden.
        return Key.DEFAULT;
    }

    @Provides
    @ChildDispatchScope
    static WritableEntityIdStore provideWritableEntityIdStore(
            @ChildQualifier @NonNull final SavepointStackImpl stack,
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService) {
        final var storeFactory =
                new WritableStoreFactory(stack, EntityIdService.NAME, configuration, storeMetricsService);
        return storeFactory.getStore(WritableEntityIdStore.class);
    }

    @Provides
    @ChildDispatchScope
    static WritableStoreFactory provideWritableStoreFactory(
            @ChildQualifier @NonNull final SavepointStackImpl stack,
            @ChildQualifier @NonNull final TransactionInfo txnInfo,
            @NonNull final Configuration configuration,
            @NonNull final ServiceScopeLookup serviceScopeLookup,
            @NonNull final StoreMetricsService storeMetricsService) {
        return new WritableStoreFactory(
                stack, serviceScopeLookup.getServiceName(txnInfo.txBody()), configuration, storeMetricsService);
    }

    @Provides
    @ChildDispatchScope
    static FinalizeContext provideFinalizeContext(
            @ChildQualifier @NonNull final ReadableStoreFactory readableStoreFactory,
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder,
            @ChildQualifier @NonNull final SavepointStackImpl stack,
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService) {
        final var writableStoreFactory =
                new WritableStoreFactory(stack, TokenService.NAME, configuration, storeMetricsService);
        return new TriggeredFinalizeContext(
                readableStoreFactory, writableStoreFactory, recordBuilder, recordBuilder.consensusNow(), configuration);
    }

    @Provides
    @ChildDispatchScope
    static Set<Key> provideRequiredKeys(@ChildQualifier @NonNull final PreHandleResult preHandleResult) {
        return preHandleResult.requiredKeys();
    }

    @Provides
    @ChildDispatchScope
    static Set<Account> provideHollowAccounts(@ChildQualifier @NonNull final PreHandleResult preHandleResult) {
        return preHandleResult.hollowAccounts();
    }

    @Provides
    @ChildDispatchScope
    static DispatchHandleContext provideDispatchHandleContext(
            @NonNull @ChildQualifier final TransactionInfo transactionInfo,
            @NonNull final Configuration configuration,
            @NonNull final Authorizer authorizer,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final ResourcePriceCalculator resourcePriceCalculator,
            @NonNull final FeeManager feeManager,
            @NonNull final StoreFactoryImpl storeFactory,
            @NonNull final AccountID syntheticPayer,
            @NonNull final KeyVerifier verifier,
            @NonNull @ChildQualifier final Key payerkey,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull @ChildQualifier final SavepointStackImpl stack,
            @NonNull final WritableEntityIdStore entityIdStore,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final RecordCache recordCache,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder,
            @NonNull final Provider<ChildDispatchComponent.Factory> childDispatchFactory,
            @NonNull final ChildDispatchFactory childDispatchLogic,
            @NonNull final ChildDispatchComponent dispatch,
            @NonNull final DispatchProcessor dispatchProcessor,
            @NonNull final ThrottleAdviser throttleAdviser) {
        return new DispatchHandleContext(
                recordBuilder.consensusNow(),
                transactionInfo,
                configuration,
                authorizer,
                blockRecordManager,
                resourcePriceCalculator,
                feeManager,
                storeFactory,
                syntheticPayer,
                verifier,
                payerkey,
                exchangeRateManager,
                stack,
                entityIdStore,
                dispatcher,
                recordCache,
                networkInfo,
                recordBuilder,
                childDispatchFactory,
                childDispatchLogic,
                dispatch,
                dispatchProcessor,
                throttleAdviser);
    }
}
