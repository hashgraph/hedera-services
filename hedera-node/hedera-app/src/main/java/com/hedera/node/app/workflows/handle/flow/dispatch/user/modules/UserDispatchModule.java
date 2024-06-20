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
import com.hedera.node.app.fees.FeeAccumulator;
import com.hedera.node.app.fees.ResourcePriceCalculatorImpl;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.records.FinalizeContext;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.DefaultKeyVerifier;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.fees.ResourcePriceCalculator;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ServiceApiFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.TokenContextImpl;
import com.hedera.node.app.workflows.handle.flow.DispatchHandleContext;
import com.hedera.node.app.workflows.handle.flow.dispatch.Dispatch;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.UserDispatchComponent;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.UserDispatchScope;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.logic.UserRecordInitializer;
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

/**
 * The module that provides the dependencies for the user dispatch.
 */
@Module(subcomponents = {})
public interface UserDispatchModule {
    /**
     * Binds the {@link Dispatch} to the {@link UserDispatchComponent}. Whenever Dispatch is requested, it will
     * return the Dispatch instance bound to the UserDispatchComponent in this scope.
     * @param userDispatchComponent userDispatchComponent
     * @return Dispatch instance bound to the UserDispatchComponent
     */
    @Binds
    @UserDispatchScope
    Dispatch bindDispatch(@NonNull UserDispatchComponent userDispatchComponent);

    /**
     * Binds the {@link FinalizeContext} to the {@link TokenContextImpl}. Whenever FinalizeContext is requested, it will
     * return the FinalizeContext instance bound to the TokenContextImpl in this scope.
     * @param tokenContext tokenContext
     * @return FinalizeContext instance bound to the TokenContextImpl
     */
    @Binds
    @UserDispatchScope
    FinalizeContext bindFinalizeContext(@NonNull TokenContextImpl tokenContext);

    /**
     * Binds the {@link HandleContext} to the {@link DispatchHandleContext}. Whenever HandleContext is requested, it will
     * return the HandleContext instance bound to the DispatchHandleContext in this scope.
     * @param handleContext handleContext
     * @return HandleContext instance bound to the DispatchHandleContext
     */
    @Binds
    @UserDispatchScope
    HandleContext bindHandleContext(@NonNull DispatchHandleContext handleContext);

    /**
     * Binds the {@link ResourcePriceCalculator} to the {@link ResourcePriceCalculatorImpl}.
     * Whenever ResourcePriceCalculator is requested, it will return the ResourcePriceCalculator instance bound
     * to the ResourcePriceCalculatorImpl in this scope.
     * @param resourcePriceCalculator resourcePriceCalculator
     * @return ResourcePriceCalculator instance bound to the ResourcePriceCalculatorImpl
     */
    @Binds
    @UserDispatchScope
    ResourcePriceCalculator bindResourcePriceCalculator(@NonNull ResourcePriceCalculatorImpl resourcePriceCalculator);

    /**
     * Binds the {@link FeeContext} to the {@link DispatchHandleContext}. Whenever FeeContext is requested, it will
     * return the FeeContext instance bound to the DispatchHandleContext in this scope.
     * @param handleContext handleContext
     * @return FeeContext instance bound to the DispatchHandleContext
     */
    @Binds
    @UserDispatchScope
    FeeContext bindFeeContext(@NonNull DispatchHandleContext handleContext);

    /**
     * Provides the required keys for the transaction from preHandleResult, when requested.
     * @param preHandleResult preHandleResult
     * @return required keys for the transaction
     */
    @Provides
    @UserDispatchScope
    static Set<Key> provideRequiredKeys(@NonNull final PreHandleResult preHandleResult) {
        return preHandleResult.requiredKeys();
    }

    /**
     * Provides the hollow accounts from preHandleResult, when requested in UserDispatchScope.
     * @param preHandleResult preHandleResult
     * @return hollow accounts
     */
    @Provides
    @UserDispatchScope
    static Set<Account> provideHollowAccounts(@NonNull final PreHandleResult preHandleResult) {
        return preHandleResult.hollowAccounts();
    }

    /**
     * Provides the payer account ID from txnInfo, when requested in UserDispatchScope.
     * @param txnInfo txnInfo
     * @return payer account ID
     */
    @Provides
    @UserDispatchScope
    static AccountID provideSyntheticPayer(@NonNull final TransactionInfo txnInfo) {
        return txnInfo.payerID();
    }

    /**
     * Provides the key verifier when requested in UserDispatchScope.
     * @param hederaConfig hederaConfig
     * @param txnInfo txnInfo
     * @param preHandleResult preHandleResult
     * @return key verifier
     */
    @Provides
    @UserDispatchScope
    static KeyVerifier provideKeyVerifier(
            @NonNull final HederaConfig hederaConfig,
            @NonNull final TransactionInfo txnInfo,
            @NonNull final PreHandleResult preHandleResult) {
        return new DefaultKeyVerifier(
                txnInfo.signatureMap().sigPair().size(), hederaConfig, preHandleResult.getVerificationResults());
    }

    /**
     * Provides the fees for the transaction when requested in UserDispatchScope.
     * @param feeContext feeContext
     * @param dispatcher dispatcher
     * @return fees for the transaction
     */
    @Provides
    @UserDispatchScope
    static Fees provideFees(@NonNull final FeeContext feeContext, @NonNull final TransactionDispatcher dispatcher) {
        return dispatcher.dispatchComputeFees(feeContext);
    }

    /**
     * Provides the ServiceApiFactory when requested in UserDispatchScope.
     * @param stack stack
     * @param configuration configuration
     * @param storeMetricsService storeMetricsService
     * @return ServiceApiFactory
     */
    @Provides
    @UserDispatchScope
    static ServiceApiFactory provideServiceApiFactory(
            @NonNull final SavepointStackImpl stack,
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService) {
        return new ServiceApiFactory(stack, configuration, storeMetricsService);
    }

    /**
     * Provides the FeeAccumulator when requested in UserDispatchScope.
     * @param recordBuilder recordBuilder
     * @param serviceApiFactory serviceApiFactory
     * @return FeeAccumulator
     */
    @Provides
    @UserDispatchScope
    static FeeAccumulator provideFeeAccumulator(
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder,
            @NonNull final ServiceApiFactory serviceApiFactory) {
        final var tokenApi = serviceApiFactory.getApi(TokenServiceApi.class);
        return new FeeAccumulator(tokenApi, recordBuilder);
    }

    /**
     * Provides the WritableEntityIdStore when requested in UserDispatchScope.
     * @param stack stack
     * @param configuration configuration
     * @param storeMetricsService storeMetricsService
     * @return WritableEntityIdStore
     */
    @Provides
    @UserDispatchScope
    static WritableEntityIdStore provideWritableEntityIdStore(
            @NonNull final SavepointStackImpl stack,
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService) {
        final var entityIdsFactory =
                new WritableStoreFactory(stack, EntityIdService.NAME, configuration, storeMetricsService);
        return entityIdsFactory.getStore(WritableEntityIdStore.class);
    }

    /**
     * Provides the WritableStoreFactory when requested in UserDispatchScope.
     * @param stack stack
     * @param txnInfo txnInfo
     * @param configuration configuration
     * @param serviceScopeLookup serviceScopeLookup
     * @param storeMetricsService storeMetricsService
     * @return WritableStoreFactory
     */
    @Provides
    @UserDispatchScope
    static WritableStoreFactory provideWritableStoreFactory(
            @NonNull final SavepointStackImpl stack,
            @NonNull final TransactionInfo txnInfo,
            @NonNull final Configuration configuration,
            @NonNull final ServiceScopeLookup serviceScopeLookup,
            @NonNull final StoreMetricsService storeMetricsService) {
        return new WritableStoreFactory(
                stack, serviceScopeLookup.getServiceName(txnInfo.txBody()), configuration, storeMetricsService);
    }

    /**
     * Provides HandleContext.TransactionCategory as USER when requested in UserDispatchScope.
     * @return transaction category
     */
    @Provides
    @UserDispatchScope
    static HandleContext.TransactionCategory provideTransactionCategory() {
        return HandleContext.TransactionCategory.USER;
    }

    /**
     * Provides the user record when requested in UserDispatchScope.
     * @param recordListBuilder recordListBuilder
     * @param userRecordInitializer userRecordInitializer
     * @param txnInfo txnInfo
     * @return user record
     */
    @Provides
    @UserDispatchScope
    static SingleTransactionRecordBuilderImpl provideUserTransactionRecordBuilder(
            @NonNull final RecordListBuilder recordListBuilder,
            @NonNull final UserRecordInitializer userRecordInitializer,
            @NonNull final TransactionInfo txnInfo) {
        return userRecordInitializer.initializeUserRecord(recordListBuilder.userTransactionRecordBuilder(), txnInfo);
    }
}
