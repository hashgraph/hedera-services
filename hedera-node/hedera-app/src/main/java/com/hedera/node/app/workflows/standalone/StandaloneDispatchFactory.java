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

package com.hedera.node.app.workflows.standalone;

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;
import static com.hedera.node.app.workflows.handle.dispatch.ChildDispatchFactory.NO_OP_KEY_VERIFIER;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeAccumulator;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.fees.ResourcePriceCalculatorImpl;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.EntityNumGeneratorImpl;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.info.NodeInfoImpl;
import com.hedera.node.app.records.impl.BlockRecordInfoImpl;
import com.hedera.node.app.service.token.api.FeeStreamBuilder;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.store.ServiceApiFactory;
import com.hedera.node.app.store.StoreFactoryImpl;
import com.hedera.node.app.store.WritableStoreFactory;
import com.hedera.node.app.throttle.AppThrottleAdviser;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.Dispatch;
import com.hedera.node.app.workflows.handle.DispatchHandleContext;
import com.hedera.node.app.workflows.handle.DispatchProcessor;
import com.hedera.node.app.workflows.handle.RecordDispatch;
import com.hedera.node.app.workflows.handle.dispatch.ChildDispatchFactory;
import com.hedera.node.app.workflows.handle.record.TokenContextImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.ConsensusConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import com.swirlds.state.State;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StandaloneDispatchFactory {
    private static final PreHandleResult UNUSABLE_PRE_HANDLE_RESULT = new PreHandleResult(
            null,
            null,
            PreHandleResult.Status.UNKNOWN_FAILURE,
            ResponseCodeEnum.OK,
            null,
            null,
            null,
            null,
            null,
            null,
            -1);

    private final FeeManager feeManager;
    private final Authorizer authorizer;
    private final NetworkInfo networkInfo;
    private final PlatformState platformState;
    private final ConfigProvider configProvider;
    private final DispatchProcessor dispatchProcessor;
    private final PreHandleWorkflow preHandleWorkflow;
    private final ServiceScopeLookup serviceScopeLookup;
    private final ExchangeRateManager exchangeRateManager;
    private final StoreMetricsService storeMetricsService;
    private final ChildDispatchFactory childDispatchFactory;
    private final TransactionDispatcher transactionDispatcher;
    private final NetworkUtilizationManager networkUtilizationManager;

    @Inject
    public StandaloneDispatchFactory(
            @NonNull final FeeManager feeManager,
            @NonNull final Authorizer authorizer,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final PlatformState platformState,
            @NonNull final ConfigProvider configProvider,
            @NonNull final DispatchProcessor dispatchProcessor,
            @NonNull final PreHandleWorkflow preHandleWorkflow,
            @NonNull final ServiceScopeLookup serviceScopeLookup,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final StoreMetricsService storeMetricsService,
            @NonNull final ChildDispatchFactory childDispatchFactory,
            @NonNull final TransactionDispatcher transactionDispatcher,
            @NonNull final NetworkUtilizationManager networkUtilizationManager) {
        this.feeManager = requireNonNull(feeManager);
        this.authorizer = requireNonNull(authorizer);
        this.networkInfo = requireNonNull(networkInfo);
        this.platformState = requireNonNull(platformState);
        this.configProvider = requireNonNull(configProvider);
        this.dispatchProcessor = requireNonNull(dispatchProcessor);
        this.preHandleWorkflow = requireNonNull(preHandleWorkflow);
        this.childDispatchFactory = requireNonNull(childDispatchFactory);
        this.transactionDispatcher = requireNonNull(transactionDispatcher);
        this.serviceScopeLookup = requireNonNull(serviceScopeLookup);
        this.exchangeRateManager = requireNonNull(exchangeRateManager);
        this.storeMetricsService = requireNonNull(storeMetricsService);
        this.networkUtilizationManager = requireNonNull(networkUtilizationManager);
    }

    public Dispatch newDispatch(
            @NonNull final State state,
            @NonNull final TransactionBody transactionBody,
            @NonNull final Instant consensusNow) {
        final var config = configProvider.getConfiguration();
        final var consensusConfig = config.getConfigData(ConsensusConfig.class);
        final var stack = SavepointStackImpl.newRootStack(
                state, consensusConfig.handleMaxPrecedingRecords(), consensusConfig.handleMaxFollowingRecords());
        final var readableStoreFactory = new ReadableStoreFactory(stack);
        final var consensusTransaction = consensusTransactionFor(transactionBody);
        final var creatorInfo = creatorInfoFor(transactionBody);
        final var preHandleResult =
                preHandleWorkflow.getCurrentPreHandleResult(creatorInfo, consensusTransaction, readableStoreFactory);
        final var tokenContext = new TokenContextImpl(config, storeMetricsService, stack, consensusNow);
        final var txnInfo = requireNonNull(preHandleResult.txInfo());
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
        final var baseBuilder = stack.getBaseBuilder(StreamBuilder.class);
        final var feeAccumulator =
                new FeeAccumulator(serviceApiFactory.getApi(TokenServiceApi.class), (FeeStreamBuilder) baseBuilder);
        final var blockRecordInfo = BlockRecordInfoImpl.from(state);
        final var dispatchHandleContext = new DispatchHandleContext(
                consensusNow,
                creatorInfo,
                txnInfo,
                config,
                authorizer,
                blockRecordInfo,
                priceCalculator,
                feeManager,
                storeFactory,
                requireNonNull(txnInfo.payerID()),
                NO_OP_KEY_VERIFIER,
                platformState,
                txnInfo.functionality(),
                preHandleResult.payerKey() == null ? Key.DEFAULT : preHandleResult.payerKey(),
                exchangeRateManager,
                stack,
                entityNumGenerator,
                transactionDispatcher,
                networkInfo,
                childDispatchFactory,
                dispatchProcessor,
                throttleAdvisor,
                feeAccumulator);
        final var fees = transactionDispatcher.dispatchComputeFees(dispatchHandleContext);
        return new RecordDispatch(
                baseBuilder,
                config,
                fees,
                txnInfo,
                requireNonNull(txnInfo.payerID()),
                readableStoreFactory,
                feeAccumulator,
                NO_OP_KEY_VERIFIER,
                creatorInfo,
                consensusNow,
                preHandleResult.getRequiredKeys(),
                preHandleResult.getHollowAccounts(),
                dispatchHandleContext,
                stack,
                USER,
                tokenContext,
                platformState,
                preHandleResult);
    }

    private ConsensusTransaction consensusTransactionFor(@NonNull final TransactionBody transactionBody) {
        final var transaction = Transaction.newBuilder().body(transactionBody).build();
        final var transactionBytes = Transaction.PROTOBUF.toBytes(transaction);
        final var consensusTransaction = new TransactionWrapper(EventTransaction.newBuilder()
                .applicationTransaction(transactionBytes)
                .build());
        consensusTransaction.setMetadata(UNUSABLE_PRE_HANDLE_RESULT);
        return consensusTransaction;
    }

    private NodeInfo creatorInfoFor(@NonNull final TransactionBody transactionBody) {
        return new NodeInfoImpl(0, transactionBody.nodeAccountIDOrThrow(), 0, "", -1, "", -1, "", "", Bytes.EMPTY, "");
    }
}
