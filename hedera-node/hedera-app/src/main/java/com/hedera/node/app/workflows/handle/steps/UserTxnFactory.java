/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.HederaFunctionality.STATE_SIGNATURE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;
import static com.hedera.node.app.workflows.handle.TransactionType.GENESIS_TRANSACTION;
import static com.hedera.node.app.workflows.handle.TransactionType.POST_UPGRADE_TRANSACTION;
import static com.hedera.node.app.workflows.handle.dispatch.ChildDispatchFactory.functionOfTxn;
import static com.hedera.node.app.workflows.handle.dispatch.ChildDispatchFactory.getKeyVerifier;
import static com.hedera.node.app.workflows.handle.dispatch.ChildDispatchFactory.getTxnInfoFrom;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.PRE_HANDLE_FAILURE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.SO_FAR_SO_GOOD;
import static com.hedera.node.app.workflows.standalone.impl.StandaloneDispatchFactory.getTxnCategory;
import static com.hedera.node.config.types.StreamMode.RECORDS;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.node.app.blocks.BlockStreamManager;
import com.hedera.node.app.blocks.impl.BoundaryStateChangeListener;
import com.hedera.node.app.blocks.impl.KVStateChangeListener;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeAccumulator;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.fees.ResourcePriceCalculatorImpl;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.EntityNumGeneratorImpl;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.token.api.FeeStreamBuilder;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.AppKeyVerifier;
import com.hedera.node.app.signature.DefaultKeyVerifier;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleContext.DispatchMetadata;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.state.DeduplicationCache;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.store.ServiceApiFactory;
import com.hedera.node.app.store.StoreFactoryImpl;
import com.hedera.node.app.store.WritableStoreFactory;
import com.hedera.node.app.throttle.AppThrottleAdviser;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.Dispatch;
import com.hedera.node.app.workflows.handle.DispatchHandleContext;
import com.hedera.node.app.workflows.handle.DispatchProcessor;
import com.hedera.node.app.workflows.handle.RecordDispatch;
import com.hedera.node.app.workflows.handle.TransactionType;
import com.hedera.node.app.workflows.handle.dispatch.ChildDispatchFactory;
import com.hedera.node.app.workflows.handle.record.TokenContextImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.hedera.node.app.workflows.purechecks.PureChecksContextImpl;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.node.config.data.ConsensusConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.types.StreamMode;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import com.swirlds.state.lifecycle.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UserTxnFactory {

    private final ConfigProvider configProvider;
    private final KVStateChangeListener kvStateChangeListener;
    private final BoundaryStateChangeListener boundaryStateChangeListener;
    private final PreHandleWorkflow preHandleWorkflow;
    private final Authorizer authorizer;
    private final NetworkInfo networkInfo;
    private final FeeManager feeManager;
    private final DispatchProcessor dispatchProcessor;
    private final ServiceScopeLookup serviceScopeLookup;
    private final ExchangeRateManager exchangeRateManager;
    private final TransactionDispatcher dispatcher;
    private final NetworkUtilizationManager networkUtilizationManager;
    private final StreamMode streamMode;
    private final Function<SemanticVersion, SoftwareVersion> softwareVersionFactory;
    private final BlockRecordManager blockRecordManager;
    private final BlockStreamManager blockStreamManager;
    private final ChildDispatchFactory childDispatchFactory;
    private final TransactionChecker transactionChecker;
    private final DeduplicationCache deduplicationCache;

    @Inject
    public UserTxnFactory(
            @NonNull final ConfigProvider configProvider,
            @NonNull final KVStateChangeListener kvStateChangeListener,
            @NonNull final BoundaryStateChangeListener boundaryStateChangeListener,
            @NonNull final PreHandleWorkflow preHandleWorkflow,
            @NonNull final Authorizer authorizer,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final FeeManager feeManager,
            @NonNull final DispatchProcessor dispatchProcessor,
            @NonNull final ServiceScopeLookup serviceScopeLookup,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final NetworkUtilizationManager networkUtilizationManager,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final BlockStreamManager blockStreamManager,
            @NonNull final ChildDispatchFactory childDispatchFactory,
            @NonNull final Function<SemanticVersion, SoftwareVersion> softwareVersionFactory,
            @NonNull final TransactionChecker transactionChecker,
            @NonNull final DeduplicationCache deduplicationCache) {
        this.configProvider = requireNonNull(configProvider);
        this.kvStateChangeListener = requireNonNull(kvStateChangeListener);
        this.boundaryStateChangeListener = requireNonNull(boundaryStateChangeListener);
        this.preHandleWorkflow = requireNonNull(preHandleWorkflow);
        this.authorizer = requireNonNull(authorizer);
        this.networkInfo = requireNonNull(networkInfo);
        this.feeManager = requireNonNull(feeManager);
        this.dispatcher = requireNonNull(dispatcher);
        this.networkUtilizationManager = requireNonNull(networkUtilizationManager);
        this.dispatchProcessor = requireNonNull(dispatchProcessor);
        this.serviceScopeLookup = requireNonNull(serviceScopeLookup);
        this.exchangeRateManager = requireNonNull(exchangeRateManager);
        this.blockStreamManager = requireNonNull(blockStreamManager);
        this.blockRecordManager = requireNonNull(blockRecordManager);
        this.childDispatchFactory = requireNonNull(childDispatchFactory);
        this.streamMode = configProvider
                .getConfiguration()
                .getConfigData(BlockStreamConfig.class)
                .streamMode();
        this.softwareVersionFactory = softwareVersionFactory;
        this.transactionChecker = requireNonNull(transactionChecker);
        this.deduplicationCache = requireNonNull(deduplicationCache);
    }

    /**
     * Creates a new {@link UserTxn} instance from the given parameters.
     *
     * @param state the state the transaction will be applied to
     * @param creatorInfo the node information of the creator
     * @param platformTxn the transaction itself
     * @param consensusNow the current consensus time
     * @param type the type of the transaction
     * @param stateSignatureTxnCallback a callback to be called when encountering a {@link StateSignatureTransaction}
     * @return the new user transaction, or {@code null} if the transaction is not a user transaction
     */
    public @Nullable UserTxn createUserTxn(
            @NonNull final State state,
            @NonNull final NodeInfo creatorInfo,
            @NonNull final ConsensusTransaction platformTxn,
            @NonNull final Instant consensusNow,
            @NonNull final TransactionType type,
            @NonNull final Consumer<StateSignatureTransaction> stateSignatureTxnCallback) {
        requireNonNull(state);
        requireNonNull(creatorInfo);
        requireNonNull(platformTxn);
        requireNonNull(consensusNow);
        requireNonNull(type);
        final var config = configProvider.getConfiguration();
        final var stack = createRootSavepointStack(state, type);
        final var readableStoreFactory = new ReadableStoreFactory(stack, softwareVersionFactory);
        final var preHandleResult = preHandleWorkflow.getCurrentPreHandleResult(
                creatorInfo, platformTxn, readableStoreFactory, stateSignatureTxnCallback);
        final var txnInfo = requireNonNull(preHandleResult.txInfo());
        if (txnInfo.functionality() == STATE_SIGNATURE_TRANSACTION) {
            return null;
        }
        final var tokenContext = new TokenContextImpl(
                config,
                stack,
                consensusNow,
                new WritableEntityIdStore(stack.getWritableStates(EntityIdService.NAME)),
                softwareVersionFactory);
        return new UserTxn(
                type,
                txnInfo.functionality(),
                consensusNow,
                state,
                txnInfo,
                tokenContext,
                stack,
                preHandleResult,
                readableStoreFactory,
                config,
                creatorInfo);
    }

    /**
     * Creates a new {@link UserTxn} for synthetic transaction body.
     *
     * @param state the state the transaction will be applied to
     * @param creatorInfo the node information of the creator
     * @param consensusNow the current consensus time
     * @param type the type of the transaction
     * @param body synthetic transaction body
     * @return the new user transaction
     */
    public UserTxn createUserTxn(
            @NonNull final State state,
            @NonNull final NodeInfo creatorInfo,
            @NonNull final Instant consensusNow,
            @NonNull final TransactionType type,
            @NonNull final AccountID payerId,
            @NonNull final TransactionBody body) {
        requireNonNull(state);
        requireNonNull(creatorInfo);
        requireNonNull(consensusNow);
        requireNonNull(type);
        requireNonNull(payerId);
        requireNonNull(body);
        final var config = configProvider.getConfiguration();
        final var stack = createRootSavepointStack(state, type);
        final var readableStoreFactory = new ReadableStoreFactory(stack, softwareVersionFactory);
        final var functionality = functionOfTxn(body);
        final var preHandleResult = preHandleSyntheticTransaction(body, payerId, config, readableStoreFactory);
        final var entityIdStore = new WritableEntityIdStore(stack.getWritableStates(EntityIdService.NAME));
        final var tokenContext =
                new TokenContextImpl(config, stack, consensusNow, entityIdStore, softwareVersionFactory);
        return new UserTxn(
                type,
                functionality,
                consensusNow,
                state,
                preHandleResult.txInfo(),
                tokenContext,
                stack,
                preHandleResult,
                readableStoreFactory,
                config,
                creatorInfo);
    }

    /**
     * Creates a new {@link Dispatch} instance for this user transaction in the given context.
     *
     * @param userTxn user transaction
     * @param exchangeRates the exchange rates to use
     * @return the new dispatch instance
     */
    public Dispatch createDispatch(@NonNull final UserTxn userTxn, @NonNull final ExchangeRateSet exchangeRates) {
        requireNonNull(userTxn);
        requireNonNull(exchangeRates);
        final var preHandleResult = userTxn.preHandleResult();
        final var keyVerifier = new DefaultKeyVerifier(
                userTxn.txnInfo().signatureMap().sigPair().size(),
                userTxn.config().getConfigData(HederaConfig.class),
                preHandleResult.getVerificationResults());
        final var category = getTxnCategory(preHandleResult);
        final var baseBuilder = userTxn.initBaseBuilder(exchangeRates);
        return createDispatch(userTxn, baseBuilder, keyVerifier, category);
    }

    /**
     * Creates a new {@link Dispatch} instance for this user transaction in the given context.
     *
     * @param userTxn user transaction
     * @param baseBuilder the base record builder
     * @param keyVerifierCallback key verifier callback
     * @param category transaction category
     * @return the new dispatch instance
     */
    public Dispatch createDispatch(
            @NonNull final UserTxn userTxn,
            @NonNull final StreamBuilder baseBuilder,
            @NonNull final Predicate<Key> keyVerifierCallback,
            @NonNull final HandleContext.TransactionCategory category) {
        final var config = userTxn.config();
        final var keyVerifier = getKeyVerifier(keyVerifierCallback, config, emptySet());
        return createDispatch(userTxn, baseBuilder, keyVerifier, category);
    }

    private Dispatch createDispatch(
            @NonNull final UserTxn userTxn,
            @NonNull final StreamBuilder baseBuilder,
            @NonNull final AppKeyVerifier keyVerifier,
            @NonNull final HandleContext.TransactionCategory transactionCategory) {
        final var config = userTxn.config();
        final var txnInfo = userTxn.txnInfo();
        final var preHandleResult = userTxn.preHandleResult();
        final var stack = userTxn.stack();
        final var consensusNow = userTxn.consensusNow();
        final var creatorInfo = userTxn.creatorInfo();
        final var tokenContextImpl = userTxn.tokenContextImpl();
        final var entityIdStore = new WritableEntityIdStore(stack.getWritableStates(EntityIdService.NAME));

        final var readableStoreFactory = new ReadableStoreFactory(stack, softwareVersionFactory);
        final var entityNumGenerator = new EntityNumGeneratorImpl(entityIdStore);
        final var writableStoreFactory =
                new WritableStoreFactory(stack, serviceScopeLookup.getServiceName(txnInfo.txBody()), entityIdStore);
        final var serviceApiFactory = new ServiceApiFactory(stack, config);
        final var priceCalculator =
                new ResourcePriceCalculatorImpl(consensusNow, txnInfo, feeManager, readableStoreFactory);
        final var storeFactory = new StoreFactoryImpl(readableStoreFactory, writableStoreFactory, serviceApiFactory);
        final var throttleAdvisor = new AppThrottleAdviser(networkUtilizationManager, consensusNow);
        final var feeAccumulator =
                new FeeAccumulator(serviceApiFactory.getApi(TokenServiceApi.class), (FeeStreamBuilder) baseBuilder);
        final var dispatchHandleContext = new DispatchHandleContext(
                consensusNow,
                creatorInfo,
                txnInfo,
                config,
                authorizer,
                streamMode != RECORDS ? blockStreamManager : blockRecordManager,
                priceCalculator,
                feeManager,
                storeFactory,
                requireNonNull(txnInfo.payerID()),
                keyVerifier,
                txnInfo.functionality(),
                preHandleResult.payerKey() == null ? Key.DEFAULT : preHandleResult.payerKey(),
                exchangeRateManager,
                stack,
                entityNumGenerator,
                dispatcher,
                networkInfo,
                childDispatchFactory,
                dispatchProcessor,
                throttleAdvisor,
                feeAccumulator,
                DispatchMetadata.EMPTY_METADATA,
                transactionChecker,
                deduplicationCache,
                preHandleResult.innerResults());
        final var fees = dispatcher.dispatchComputeFees(dispatchHandleContext);
        if (streamMode != RECORDS) {
            final var congestionMultiplier = feeManager.congestionMultiplierFor(
                    txnInfo.txBody(), txnInfo.functionality(), storeFactory.asReadOnly());
            if (congestionMultiplier > 1) {
                baseBuilder.congestionMultiplier(congestionMultiplier);
            }
        }
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
                transactionCategory,
                tokenContextImpl,
                preHandleResult,
                transactionCategory == SCHEDULED
                        ? HandleContext.ConsensusThrottling.OFF
                        : HandleContext.ConsensusThrottling.ON,
                null);
    }

    /**
     * Creates a new root savepoint stack for the given state and transaction type, where genesis and
     * post-upgrade transactions have the maximum number of preceding records; and other transaction
     * types only support the number of preceding records specified in the network configuration.
     * @param state the state the stack is based on
     * @param type the type of the transaction
     * @return the new root savepoint stack
     */
    public SavepointStackImpl createRootSavepointStack(
            @NonNull final State state, @NonNull final TransactionType type) {
        final var config = configProvider.getConfiguration();
        final var consensusConfig = config.getConfigData(ConsensusConfig.class);
        final var blockStreamConfig = config.getConfigData(BlockStreamConfig.class);
        final var maxPrecedingRecords = (type == GENESIS_TRANSACTION || type == POST_UPGRADE_TRANSACTION)
                ? Integer.MAX_VALUE
                : consensusConfig.handleMaxPrecedingRecords();
        return SavepointStackImpl.newRootStack(
                state,
                maxPrecedingRecords,
                consensusConfig.handleMaxFollowingRecords(),
                boundaryStateChangeListener,
                kvStateChangeListener,
                blockStreamConfig.streamMode());
    }

    private PreHandleResult preHandleSyntheticTransaction(
            @NonNull final TransactionBody body,
            @NonNull final AccountID syntheticPayerId,
            @NonNull final Configuration config,
            @NonNull final ReadableStoreFactory readableStoreFactory) {
        try {
            final var pureChecksContext = new PureChecksContextImpl(body, config, dispatcher, transactionChecker);
            dispatcher.dispatchPureChecks(pureChecksContext);
            final var preHandleContext = new PreHandleContextImpl(
                    readableStoreFactory, body, syntheticPayerId, config, dispatcher, transactionChecker);
            dispatcher.dispatchPreHandle(preHandleContext);
            final var txInfo = getTxnInfoFrom(syntheticPayerId, body);
            return new PreHandleResult(
                    null,
                    null,
                    SO_FAR_SO_GOOD,
                    OK,
                    txInfo,
                    preHandleContext.requiredNonPayerKeys(),
                    null,
                    preHandleContext.requiredHollowAccounts(),
                    null,
                    null,
                    0);
        } catch (final PreCheckException e) {
            return new PreHandleResult(
                    null,
                    null,
                    PRE_HANDLE_FAILURE,
                    e.responseCode(),
                    null,
                    emptySet(),
                    null,
                    emptySet(),
                    null,
                    null,
                    0);
        }
    }
}
