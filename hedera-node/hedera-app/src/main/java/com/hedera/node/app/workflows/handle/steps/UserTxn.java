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

package com.hedera.node.app.workflows.handle.steps;

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
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
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.DefaultKeyVerifier;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.store.ServiceApiFactory;
import com.hedera.node.app.store.StoreFactoryImpl;
import com.hedera.node.app.store.WritableStoreFactory;
import com.hedera.node.app.throttle.AppThrottleAdviser;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.Dispatch;
import com.hedera.node.app.workflows.handle.DispatchHandleContext;
import com.hedera.node.app.workflows.handle.DispatchProcessor;
import com.hedera.node.app.workflows.handle.HandleWorkflow;
import com.hedera.node.app.workflows.handle.RecordDispatch;
import com.hedera.node.app.workflows.handle.dispatch.ChildDispatchFactory;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.record.TokenContextImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.ConsensusConfig;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

public record UserTxn(
        boolean isGenesisTxn,
        @NonNull HederaFunctionality functionality,
        @NonNull Instant consensusNow,
        @NonNull HederaState state,
        @NonNull PlatformState platformState,
        @NonNull ConsensusEvent event,
        @NonNull ConsensusTransaction platformTxn,
        @NonNull TransactionInfo txnInfo,
        @NonNull TokenContextImpl tokenContextImpl,
        @NonNull SavepointStackImpl stack,
        @NonNull PreHandleResult preHandleResult,
        @NonNull ReadableStoreFactory readableStoreFactory,
        @NonNull Configuration config,
        @NonNull Instant lastHandledConsensusTime,
        @NonNull NodeInfo creatorInfo) {

    public static UserTxn from(
            // @UserTxnScope
            @NonNull final HederaState state,
            @NonNull final PlatformState platformState,
            @NonNull final ConsensusEvent event,
            @NonNull final NodeInfo creatorInfo,
            @NonNull final ConsensusTransaction platformTxn,
            @NonNull final Instant consensusNow,
            @NonNull final Instant lastHandledConsensusTime,
            // @Singleton
            @NonNull final ConfigProvider configProvider,
            @NonNull final StoreMetricsService storeMetricsService,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final HandleWorkflow handleWorkflow) {
        final var config = configProvider.getConfiguration();
        final SavepointStackImpl stack;
        final var isGenesis = lastHandledConsensusTime.equals(Instant.EPOCH);

        final var consensusConfig = config.getConfigData(ConsensusConfig.class);
        final var maxPrecedingBuilders = isGenesis ? Integer.MAX_VALUE : consensusConfig.handleMaxPrecedingRecords();
        final var maxFollowingBuilders = consensusConfig.handleMaxFollowingRecords();
        stack = SavepointStackImpl.newRootStack(state, maxPrecedingBuilders, maxFollowingBuilders);

        final var readableStoreFactory = new ReadableStoreFactory(stack);
        final var preHandleResult =
                handleWorkflow.getCurrentPreHandleResult(creatorInfo, platformTxn, readableStoreFactory);
        final var txnInfo = requireNonNull(preHandleResult.txInfo());
        return new UserTxn(
                isGenesis,
                txnInfo.functionality(),
                consensusNow,
                state,
                platformState,
                event,
                platformTxn,
                txnInfo,
                new TokenContextImpl(config, storeMetricsService, stack, blockRecordManager, consensusNow),
                stack,
                preHandleResult,
                readableStoreFactory,
                config,
                lastHandledConsensusTime,
                creatorInfo);
    }

    public Dispatch dispatch(
            // @Singleton
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
            @NonNull final SingleTransactionRecordBuilder builder,
            @NonNull final NetworkUtilizationManager networkUtilizationManager) {
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
                new RecordBuildersImpl(stack),
                childDispatchFactory,
                dispatchProcessor,
                new AppThrottleAdviser(networkUtilizationManager, consensusNow, stack));
        return new RecordDispatch(
                builder,
                config,
                dispatcher.dispatchComputeFees(dispatchHandleContext),
                txnInfo,
                requireNonNull(txnInfo.payerID()),
                readableStoreFactory,
                new FeeAccumulator(
                        serviceApiFactory.getApi(TokenServiceApi.class), (SingleTransactionRecordBuilderImpl) builder),
                keyVerifier,
                creatorInfo,
                consensusNow,
                preHandleResult.getRequiredKeys(),
                preHandleResult.getHollowAccounts(),
                dispatchHandleContext,
                stack,
                USER,
                tokenContextImpl,
                platformState,
                preHandleResult);
    }
}
