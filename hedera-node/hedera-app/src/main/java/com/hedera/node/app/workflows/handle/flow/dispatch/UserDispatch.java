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

package com.hedera.node.app.workflows.handle.flow.dispatch;

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeAccumulator;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.fees.ResourcePriceCalculatorImpl;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.EntityNumGeneratorImpl;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.records.RecordBuildersImpl;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.records.FinalizeContext;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.AppKeyVerifier;
import com.hedera.node.app.signature.DefaultKeyVerifier;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.store.ServiceApiFactory;
import com.hedera.node.app.store.StoreFactoryImpl;
import com.hedera.node.app.store.WritableStoreFactory;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.TokenContextImpl;
import com.hedera.node.app.workflows.handle.flow.DispatchHandleContext;
import com.hedera.node.app.workflows.handle.flow.dispatch.child.ChildDispatchFactory;
import com.hedera.node.app.workflows.handle.flow.dispatch.helpers.DispatchProcessor;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.UserRecordInitializer;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Set;

public record UserDispatch(
        @NonNull SingleTransactionRecordBuilderImpl recordBuilder,
        @NonNull Configuration config,
        @NonNull Fees fees,
        @NonNull TransactionInfo txnInfo,
        @NonNull AccountID payerId,
        @NonNull ReadableStoreFactory readableStoreFactory,
        @NonNull FeeAccumulator feeAccumulator,
        @NonNull AppKeyVerifier keyVerifier,
        @NonNull NodeInfo creatorInfo,
        @NonNull Instant consensusNow,
        @NonNull Set<Key> requiredKeys,
        @NonNull Set<Account> hollowAccounts,
        @NonNull HandleContext handleContext,
        @NonNull SavepointStackImpl stack,
        @NonNull HandleContext.TransactionCategory txnCategory,
        @NonNull FinalizeContext finalizeContext,
        @NonNull RecordListBuilder recordListBuilder,
        @NonNull PlatformState platformState,
        @NonNull PreHandleResult preHandleResult)
        implements Dispatch {

    public static UserDispatch from(
            // @UserTxnScope
            @NonNull final Instant consensusNow,
            @NonNull final SavepointStackImpl stack,
            @NonNull final PreHandleResult preHandleResult,
            @NonNull final NodeInfo creatorInfo,
            @NonNull final Configuration config,
            @NonNull final PlatformState platformState,
            @NonNull final TokenContextImpl tokenContextImpl,
            @NonNull final RecordListBuilder recordListBuilder,
            // @Singleton
            @NonNull final UserRecordInitializer userRecordInitializer,
            @NonNull final Authorizer authorizer,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final FeeManager feeManager,
            @NonNull final RecordCache recordCache,
            @NonNull final DispatchProcessor dispatchProcessor,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final ServiceScopeLookup serviceScopeLookup,
            @NonNull final StoreMetricsService storeMetricsService,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final ChildDispatchFactory childDispatchFactory,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final NetworkUtilizationManager networkUtilizationManager) {
        final var txnInfo = requireNonNull(preHandleResult.txInfo());
        final var recordBuilder =
                userRecordInitializer.initializeUserRecord(recordListBuilder.userTransactionRecordBuilder(), txnInfo);
        final var keyVerifier = new DefaultKeyVerifier(
                txnInfo.signatureMap().sigPair().size(),
                config.getConfigData(HederaConfig.class),
                preHandleResult.getVerificationResults());

        final var readableStoreFactory = new ReadableStoreFactory(stack);
        final var writableStoreFactory = new WritableStoreFactory(
                stack, serviceScopeLookup.getServiceName(txnInfo.txBody()), config, storeMetricsService);
        final var serviceApiFactory = new ServiceApiFactory(stack, config, storeMetricsService);

        final var dispatchHandleContext = new DispatchHandleContext(
                consensusNow,
                creatorInfo,
                txnInfo,
                config,
                authorizer,
                blockRecordManager,
                new ResourcePriceCalculatorImpl(consensusNow, txnInfo, feeManager, readableStoreFactory),
                feeManager,
                new StoreFactoryImpl(readableStoreFactory, writableStoreFactory, serviceApiFactory),
                requireNonNull(txnInfo.payerID()),
                keyVerifier,
                platformState,
                txnInfo.functionality(),
                preHandleResult.payerKey() == null ? Key.DEFAULT : preHandleResult.payerKey(),
                exchangeRateManager,
                stack,
                new EntityNumGeneratorImpl(
                        new WritableStoreFactory(stack, EntityIdService.NAME, config, storeMetricsService)
                                .getStore(WritableEntityIdStore.class)),
                dispatcher,
                recordCache,
                networkInfo,
                new RecordBuildersImpl(recordBuilder, recordListBuilder, config),
                childDispatchFactory,
                dispatchProcessor,
                recordListBuilder,
                networkUtilizationManager);

        return new UserDispatch(
                recordBuilder,
                config,
                dispatcher.dispatchComputeFees(dispatchHandleContext),
                txnInfo,
                requireNonNull(txnInfo.payerID()),
                readableStoreFactory,
                new FeeAccumulator(serviceApiFactory.getApi(TokenServiceApi.class), recordBuilder),
                keyVerifier,
                creatorInfo,
                consensusNow,
                preHandleResult.getRequiredKeys(),
                preHandleResult.getHollowAccounts(),
                dispatchHandleContext,
                stack,
                USER,
                tokenContextImpl,
                recordListBuilder,
                platformState,
                preHandleResult);
    }
}
