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

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;
import static com.hedera.node.app.workflows.handle.TransactionType.GENESIS_TRANSACTION;
import static com.hedera.node.app.workflows.handle.TransactionType.ORDINARY_TRANSACTION;
import static com.hedera.node.app.workflows.handle.TransactionType.POST_UPGRADE_TRANSACTION;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeAccumulator;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.fees.ResourcePriceCalculatorImpl;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.EntityNumGeneratorImpl;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.records.BlockRecordService;
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
import com.hedera.node.app.workflows.handle.TransactionType;
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
import com.swirlds.state.MerkleState;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

public record UserTxn(
        @NonNull TransactionType type,
        @NonNull HederaFunctionality functionality,
        @NonNull Instant consensusNow,
        @NonNull MerkleState state,
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
            @NonNull final MerkleState state,
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

        final TransactionType type;
        if (lastHandledConsensusTime.equals(Instant.EPOCH)) {
            type = GENESIS_TRANSACTION;
        } else if (isUpgradeBoundary(platformState, state)) {
            type = POST_UPGRADE_TRANSACTION;
        } else {
            type = ORDINARY_TRANSACTION;
        }
        final var isGenesis = lastHandledConsensusTime.equals(Instant.EPOCH);
        final var config = configProvider.getConfiguration();
        final var consensusConfig = config.getConfigData(ConsensusConfig.class);
        final var stack = SavepointStackImpl.newRootStack(
                state,
                isGenesis ? Integer.MAX_VALUE : consensusConfig.handleMaxPrecedingRecords(),
                consensusConfig.handleMaxFollowingRecords());
        final var readableStoreFactory = new ReadableStoreFactory(stack);
        final var preHandleResult =
                handleWorkflow.getCurrentPreHandleResult(creatorInfo, platformTxn, readableStoreFactory);
        final var txnInfo = requireNonNull(preHandleResult.txInfo());
        final var tokenContext =
                new TokenContextImpl(config, storeMetricsService, stack, blockRecordManager, consensusNow);
        return new UserTxn(
                type,
                txnInfo.functionality(),
                consensusNow,
                state,
                platformState,
                event,
                platformTxn,
                txnInfo,
                tokenContext,
                stack,
                preHandleResult,
                readableStoreFactory,
                config,
                lastHandledConsensusTime,
                creatorInfo);
    }

    /**
     * Returns whether the given state indicates this transaction is the first after an upgrade.
     * @param platformState the platform state
     * @param state the Hedera state
     * @return whether the given state indicates this transaction is the first after an upgrade
     */
    private static boolean isUpgradeBoundary(
            @NonNull final PlatformState platformState, @NonNull final HederaState state) {
        if (platformState.getFreezeTime() == null
                || !platformState.getFreezeTime().equals(platformState.getLastFrozenTime())) {
            return false;
        } else {
            // Check the state directly here instead of going through BlockManager to allow us
            // to manipulate this condition easily in embedded tests
            final var blockInfo = state.getReadableStates(BlockRecordService.NAME)
                    .<BlockInfo>getSingleton(BLOCK_INFO_STATE_KEY)
                    .get();
            return !requireNonNull(blockInfo).migrationRecordsStreamed();
        }
    }

    /**
     * Creates a new {@link Dispatch} instance for this user transaction in the given context.
     *
     * @param authorizer the authorizer to use
     * @param networkInfo the network information
     * @param feeManager the fee manager
     * @param recordCache the record cache
     * @param dispatchProcessor the dispatch processor
     * @param blockRecordManager the block record manager
     * @param serviceScopeLookup the service scope lookup
     * @param storeMetricsService the store metrics service
     * @param exchangeRateManager the exchange rate manager
     * @param childDispatchFactory the child dispatch factory
     * @param dispatcher the transaction dispatcher
     * @param networkUtilizationManager the network utilization manager
     * @param baseBuilder the base record builder
     * @return the new dispatch instance
     */
    public Dispatch newDispatch(
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
            @NonNull final NetworkUtilizationManager networkUtilizationManager,
            // @UserTxnScope
            @NonNull final SingleTransactionRecordBuilder baseBuilder) {
        final var keyVerifier = new DefaultKeyVerifier(
                txnInfo.signatureMap().sigPair().size(),
                config.getConfigData(HederaConfig.class),
                preHandleResult.getVerificationResults());
        final var readableStoreFactory = new ReadableStoreFactory(stack);
        final var writableStoreFactory = new WritableStoreFactory(
                stack, serviceScopeLookup.getServiceName(txnInfo.txBody()), config, storeMetricsService);
        final var serviceApiFactory = new ServiceApiFactory(stack, config, storeMetricsService);
        final var priceCalculator =
                new ResourcePriceCalculatorImpl(consensusNow, txnInfo, feeManager, readableStoreFactory);
        final var storeFactory = new StoreFactoryImpl(readableStoreFactory, writableStoreFactory, serviceApiFactory);
        final var entityNumGenerator = new EntityNumGeneratorImpl(
                new WritableStoreFactory(stack, EntityIdService.NAME, config, storeMetricsService)
                        .getStore(WritableEntityIdStore.class));
        final var throttleAdvisor = new AppThrottleAdviser(networkUtilizationManager, consensusNow, stack);
        final var dispatchHandleContext = new DispatchHandleContext(
                consensusNow,
                creatorInfo,
                txnInfo,
                config,
                authorizer,
                blockRecordManager,
                priceCalculator,
                feeManager,
                storeFactory,
                requireNonNull(txnInfo.payerID()),
                keyVerifier,
                platformState,
                txnInfo.functionality(),
                preHandleResult.payerKey() == null ? Key.DEFAULT : preHandleResult.payerKey(),
                exchangeRateManager,
                stack,
                entityNumGenerator,
                dispatcher,
                recordCache,
                networkInfo,
                childDispatchFactory,
                dispatchProcessor,
                throttleAdvisor);
        final var fees = dispatcher.dispatchComputeFees(dispatchHandleContext);
        final var feeAccumulator = new FeeAccumulator(
                serviceApiFactory.getApi(TokenServiceApi.class), (SingleTransactionRecordBuilderImpl) baseBuilder);
        return new RecordDispatch(
                baseBuilder,
                config,
                fees,
                txnInfo,
                requireNonNull(txnInfo.payerID()),
                readableStoreFactory,
                feeAccumulator,
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

    /**
     * Returns the base stream builder for this user transaction.
     * @return the base stream builder
     */
    public SingleTransactionRecordBuilder baseBuilder() {
        return stack.getBaseBuilder(SingleTransactionRecordBuilder.class);
    }
}
