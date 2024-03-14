/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle;

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CREATE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.spi.HapiUtils.functionOf;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.PRECEDING;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;
import static com.hedera.node.app.state.HederaRecordCache.DuplicateCheckResult.NO_DUPLICATE;
import static com.hedera.node.app.workflows.handle.HandleContextImpl.PrecedingTransactionCategory.LIMITED_CHILD_RECORDS;
import static com.hedera.node.app.workflows.handle.HandleWorkflow.extraRewardReceivers;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.ChildFeeContextImpl;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeAccumulatorImpl;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.fees.NoOpFeeAccumulator;
import com.hedera.node.app.fees.NoOpFeeCalculator;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.records.ChildRecordFinalizer;
import com.hedera.node.app.service.token.records.ParentRecordFinalizer;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.DelegateKeyVerifier;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.spi.UnknownHederaFunctionality;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.ComputeDispatchFeesAsTopLevel;
import com.hedera.node.app.spi.workflows.FunctionalityResourcePrices;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.TransactionKeys;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.RecordListCheckPoint;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.state.WrappedHederaState;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.throttle.SynchronizedThrottleAccumulator;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.ServiceApiFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.handle.validation.AttributeValidatorImpl;
import com.hedera.node.app.workflows.handle.validation.ExpiryValidatorImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.PlatformState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The default implementation of {@link HandleContext}.
 */
public class HandleContextImpl implements HandleContext, FeeContext {

    private static final Logger logger = LogManager.getLogger(HandleContextImpl.class);

    private final TransactionBody txBody;
    private final HederaFunctionality functionality;
    private final AccountID payer;
    private AccountID topLevelPayer;
    private final Key payerKey;
    private final NetworkInfo networkInfo;
    private final TransactionCategory category;
    private final SingleTransactionRecordBuilderImpl recordBuilder;
    private final SavepointStackImpl stack;
    private final Configuration configuration;
    private final KeyVerifier verifier;
    private final RecordListBuilder recordListBuilder;
    private final TransactionChecker checker;
    private final TransactionDispatcher dispatcher;
    private final ServiceScopeLookup serviceScopeLookup;
    private final ServiceApiFactory serviceApiFactory;
    private final WritableStoreFactory writableStoreFactory;
    private final BlockRecordInfo blockRecordInfo;
    private final HederaRecordCache recordCache;
    private final FeeAccumulator feeAccumulator;
    private final Function<SubType, FeeCalculator> feeCalculatorCreator;
    private final FeeManager feeManager;
    private final Instant userTransactionConsensusTime;
    private final ExchangeRateManager exchangeRateManager;
    private final Authorizer authorizer;
    private final SolvencyPreCheck solvencyPreCheck;
    private final ChildRecordFinalizer childRecordFinalizer;
    private final ParentRecordFinalizer parentRecordFinalizer;
    private final NetworkUtilizationManager networkUtilizationManager;
    private final SynchronizedThrottleAccumulator synchronizedThrottleAccumulator;

    private ReadableStoreFactory readableStoreFactory;
    private AttributeValidator attributeValidator;
    private ExpiryValidator expiryValidator;
    private ExchangeRateInfo exchangeRateInfo;
    private Set<AccountID> dispatchPaidStakerIds;
    private PlatformState platformState;

    /**
     * Constructs a {@link HandleContextImpl}.
     *
     * @param txBody The {@link TransactionBody} of the transaction
     * @param functionality The {@link HederaFunctionality} of the transaction
     * @param signatureMapSize The size of the {@link com.hedera.hapi.node.base.SignatureMap} of the transaction
     * @param payer The {@link AccountID} of the payer
     * @param payerKey The {@link Key} of the payer
     * @param networkInfo The {@link NetworkInfo} of the network
     * @param category The {@link TransactionCategory} of the transaction (either user, preceding, or child)
     * @param recordBuilder The main {@link SingleTransactionRecordBuilderImpl}
     * @param stack The {@link SavepointStackImpl} used to manage savepoints
     * @param configuration The current {@link Configuration}
     * @param verifier The {@link KeyVerifier} used to verify signatures and hollow accounts
     * @param recordListBuilder The {@link RecordListBuilder} used to build the record stream
     * @param checker The {@link TransactionChecker} used to check dispatched transaction
     * @param dispatcher The {@link TransactionDispatcher} used to dispatch child transactions
     * @param serviceScopeLookup The {@link ServiceScopeLookup} used to look up the scope of a service
     * @param feeManager The {@link FeeManager} used to convert usage into fees
     * @param exchangeRateManager The {@link ExchangeRateManager} used to obtain exchange rate information
     * @param userTransactionConsensusTime The consensus time of the user transaction, not any child transactions
     * @param authorizer The {@link Authorizer} used to authorize the transaction
     * @param solvencyPreCheck The {@link SolvencyPreCheck} used to validate if the account is able to pay the fees
     * @param childRecordFinalizer The {@link ChildRecordFinalizer} used to finalize child records
     * @param parentRecordFinalizer The {@link ParentRecordFinalizer} used to finalize parent records (if schedule dispatch)
     * @param networkUtilizationManager The {@link NetworkUtilizationManager} used to manage the tracking of backend network throttling
     * @param synchronizedThrottleAccumulator The {@link SynchronizedThrottleAccumulator} used to manage the tracking of frontend network throttling
     * @param platformState The {@link PlatformState} of the node
     */
    public HandleContextImpl(
            @NonNull final TransactionBody txBody,
            @NonNull final HederaFunctionality functionality,
            final int signatureMapSize,
            @NonNull final AccountID payer,
            @Nullable final Key payerKey,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final TransactionCategory category,
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder,
            @NonNull final SavepointStackImpl stack,
            @NonNull final Configuration configuration,
            @NonNull final KeyVerifier verifier,
            @NonNull final RecordListBuilder recordListBuilder,
            @NonNull final TransactionChecker checker,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final ServiceScopeLookup serviceScopeLookup,
            @NonNull final BlockRecordInfo blockRecordInfo,
            @NonNull final HederaRecordCache recordCache,
            @NonNull final FeeManager feeManager,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final Instant userTransactionConsensusTime,
            @NonNull final Authorizer authorizer,
            @NonNull final SolvencyPreCheck solvencyPreCheck,
            @NonNull final ChildRecordFinalizer childRecordFinalizer,
            @NonNull final ParentRecordFinalizer parentRecordFinalizer,
            @NonNull final NetworkUtilizationManager networkUtilizationManager,
            @NonNull final SynchronizedThrottleAccumulator synchronizedThrottleAccumulator,
            @NonNull final PlatformState platformState) {
        this.txBody = requireNonNull(txBody, "txBody must not be null");
        this.functionality = requireNonNull(functionality, "functionality must not be null");
        this.payer = requireNonNull(payer, "payer must not be null");
        this.topLevelPayer = requireNonNull(payer, "payer must not be null");
        this.payerKey = payerKey;
        this.networkInfo = requireNonNull(networkInfo, "networkInfo must not be null");
        this.category = requireNonNull(category, "category must not be null");
        this.recordBuilder = requireNonNull(recordBuilder, "recordBuilder must not be null");
        this.stack = requireNonNull(stack, "stack must not be null");
        this.configuration = requireNonNull(configuration, "configuration must not be null");
        this.verifier = requireNonNull(verifier, "verifier must not be null");
        this.recordListBuilder = requireNonNull(recordListBuilder, "recordListBuilder must not be null");
        this.checker = requireNonNull(checker, "checker must not be null");
        this.dispatcher = requireNonNull(dispatcher, "dispatcher must not be null");
        this.serviceScopeLookup = requireNonNull(serviceScopeLookup, "serviceScopeLookup must not be null");
        this.blockRecordInfo = requireNonNull(blockRecordInfo, "blockRecordInfo must not be null");
        this.recordCache = requireNonNull(recordCache, "recordCache must not be null");
        this.feeManager = requireNonNull(feeManager, "feeManager must not be null");
        this.userTransactionConsensusTime =
                requireNonNull(userTransactionConsensusTime, "userTransactionConsensusTime must not be null");
        this.authorizer = requireNonNull(authorizer, "authorizer must not be null");
        this.childRecordFinalizer = requireNonNull(childRecordFinalizer, "childRecordFinalizer must not be null");
        this.parentRecordFinalizer = requireNonNull(parentRecordFinalizer, "parentRecordFinalizer must not be null");
        this.networkUtilizationManager =
                requireNonNull(networkUtilizationManager, "networkUtilization must not be null");
        this.synchronizedThrottleAccumulator =
                requireNonNull(synchronizedThrottleAccumulator, "synchronizedThrottleAccumulator must not be null");

        final var serviceScope = serviceScopeLookup.getServiceName(txBody);
        this.writableStoreFactory = new WritableStoreFactory(stack, serviceScope);
        this.serviceApiFactory = new ServiceApiFactory(stack, configuration);

        if (payerKey == null) {
            this.feeCalculatorCreator = ignore -> NoOpFeeCalculator.INSTANCE;
            this.feeAccumulator = NoOpFeeAccumulator.INSTANCE;
        } else {
            this.feeCalculatorCreator = subType -> feeManager.createFeeCalculator(
                    txBody,
                    payerKey,
                    functionality,
                    verifier.numSignaturesVerified(),
                    signatureMapSize,
                    userTransactionConsensusTime,
                    subType,
                    false,
                    readableStoreFactory());
            final var tokenApi = serviceApiFactory.getApi(TokenServiceApi.class);
            this.feeAccumulator = new FeeAccumulatorImpl(tokenApi, recordBuilder);
        }

        this.exchangeRateManager = requireNonNull(exchangeRateManager, "exchangeRateManager must not be null");
        this.solvencyPreCheck = requireNonNull(solvencyPreCheck, "solvencyPreCheck must not be null");
        this.platformState = requireNonNull(platformState, "platformState must not be null");
    }

    private WrappedHederaState current() {
        return stack.peek();
    }

    @Override
    @NonNull
    public Instant consensusNow() {
        return recordBuilder.consensusNow();
    }

    @Override
    @NonNull
    public TransactionBody body() {
        return txBody;
    }

    @NonNull
    @Override
    public AccountID payer() {
        return payer;
    }

    @Nullable
    @Override
    public Key payerKey() {
        return payerKey;
    }

    @NonNull
    @Override
    public FeeCalculator feeCalculator(@NonNull final SubType subType) {
        return feeCalculatorCreator.apply(subType);
    }

    @NonNull
    @Override
    public FunctionalityResourcePrices resourcePricesFor(
            @NonNull final HederaFunctionality functionality, @NonNull final SubType subType) {
        return new FunctionalityResourcePrices(
                requireNonNull(feeManager.getFeeData(functionality, userTransactionConsensusTime, subType)),
                feeManager.congestionMultiplierFor(txBody, functionality, readableStoreFactory));
    }

    @NonNull
    @Override
    public FeeAccumulator feeAccumulator() {
        return feeAccumulator;
    }

    @NonNull
    @Override
    public ExchangeRateInfo exchangeRateInfo() {
        if (exchangeRateInfo == null) {
            exchangeRateInfo = exchangeRateManager.exchangeRateInfo(current());
        }
        return exchangeRateInfo;
    }

    @Override
    @NonNull
    public Configuration configuration() {
        return configuration;
    }

    @Override
    @NonNull
    public Authorizer authorizer() {
        return authorizer;
    }

    @Override
    public int numTxnSignatures() {
        return verifier.numSignaturesVerified();
    }

    @Override
    @NonNull
    public BlockRecordInfo blockRecordInfo() {
        return blockRecordInfo;
    }

    /**
     * Create a new entity id number. This will be incremented by one for each new entity created. It is based on the
     * current WritableStoreFactory so will roll back if the transaction fails.
     *
     * @return new entity id number
     */
    @Override
    public long newEntityNum() {
        final var entityIdsFactory = new WritableStoreFactory(stack, EntityIdService.NAME);
        return entityIdsFactory.getStore(WritableEntityIdStore.class).incrementAndGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long peekAtNewEntityNum() {
        final var entityIdsFactory = new WritableStoreFactory(stack, EntityIdService.NAME);
        return entityIdsFactory.getStore(WritableEntityIdStore.class).peekAtNextNumber();
    }

    @Override
    @NonNull
    public AttributeValidator attributeValidator() {
        if (attributeValidator == null) {
            attributeValidator = new AttributeValidatorImpl(this);
        }
        return attributeValidator;
    }

    @Override
    @NonNull
    public ExpiryValidator expiryValidator() {
        if (expiryValidator == null) {
            expiryValidator = new ExpiryValidatorImpl(this);
        }
        return expiryValidator;
    }

    @NonNull
    @Override
    public TransactionKeys allKeysForTransaction(
            @NonNull final TransactionBody nestedTxn, @NonNull final AccountID payerForNested)
            throws PreCheckException {
        dispatcher.dispatchPureChecks(nestedTxn);
        final var nestedContext = new PreHandleContextImpl(
                readableStoreFactory(), nestedTxn, payerForNested, configuration(), dispatcher);
        try {
            dispatcher.dispatchPreHandle(nestedContext);
        } catch (final PreCheckException ignored) {
            // We must ignore/translate the exception here, as this is key gathering, not transaction validation.
            throw new PreCheckException(ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS);
        }
        return nestedContext;
    }

    @Override
    @NonNull
    public SignatureVerification verificationFor(@NonNull final Key key) {
        requireNonNull(key, "key must not be null");
        return verifier.verificationFor(key);
    }

    @NonNull
    @Override
    public SignatureVerification verificationFor(
            @NonNull final Key key, @NonNull final VerificationAssistant callback) {
        requireNonNull(key, "key must not be null");
        requireNonNull(callback, "callback must not be null");
        return verifier.verificationFor(key, callback);
    }

    @Override
    @NonNull
    public SignatureVerification verificationFor(@NonNull final Bytes evmAlias) {
        requireNonNull(evmAlias, "evmAlias must not be null");
        return verifier.verificationFor(evmAlias);
    }

    @Override
    public boolean isSuperUser() {
        return authorizer.isSuperUser(topLevelPayer);
    }

    @Override
    public SystemPrivilege hasPrivilegedAuthorization() {
        return authorizer.hasPrivilegedAuthorization(payer, functionality, txBody);
    }

    private ReadableStoreFactory readableStoreFactory() {
        if (readableStoreFactory == null) {
            readableStoreFactory = new ReadableStoreFactory(stack);
        }
        return readableStoreFactory;
    }

    @NonNull
    @Override
    public RecordCache recordCache() {
        return recordCache;
    }

    @Override
    @NonNull
    public <C> C readableStore(@NonNull final Class<C> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        return readableStoreFactory().getStore(storeInterface);
    }

    @Override
    @NonNull
    public <C> C writableStore(@NonNull final Class<C> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        return writableStoreFactory.getStore(storeInterface);
    }

    @Override
    public <T> @NonNull T serviceApi(@NonNull final Class<T> apiInterface) {
        requireNonNull(apiInterface, "apiInterface must not be null");
        return serviceApiFactory.getApi(apiInterface);
    }

    @Override
    public @NonNull NetworkInfo networkInfo() {
        return networkInfo;
    }

    @Override
    @NonNull
    public <T> T recordBuilder(@NonNull final Class<T> recordBuilderClass) {
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        return castRecordBuilder(recordBuilder, recordBuilderClass);
    }

    private static <T> T castRecordBuilder(
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder,
            @NonNull final Class<T> recordBuilderClass) {
        if (!recordBuilderClass.isInstance(recordBuilder)) {
            throw new IllegalArgumentException("Not a valid record builder class");
        }
        return recordBuilderClass.cast(recordBuilder);
    }

    @Override
    public @NonNull Fees dispatchComputeFees(
            @NonNull final TransactionBody txBody,
            @NonNull final AccountID syntheticPayerId,
            @NonNull final ComputeDispatchFeesAsTopLevel computeDispatchFeesAsTopLevel) {
        var bodyToDispatch = txBody;
        if (!txBody.hasTransactionID()) {
            // Legacy mono fee calculators frequently estimate an entity's lifetime using the epoch second of the
            // transaction id/ valid start as the current consensus time; ensure those will behave sensibly here
            bodyToDispatch = txBody.copyBuilder()
                    .transactionID(TransactionID.newBuilder()
                            .accountID(syntheticPayerId)
                            .transactionValidStart(Timestamp.newBuilder()
                                    .seconds(consensusNow().getEpochSecond())
                                    .nanos(consensusNow().getNano())))
                    .build();
        }
        try {
            // If the payer is authorized to waive fees, then we can skip the fee calculation.
            if (authorizer.hasWaivedFees(syntheticPayerId, functionOf(txBody), bodyToDispatch)) {
                return Fees.FREE;
            }
        } catch (UnknownHederaFunctionality ex) {
            throw new HandleException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
        }

        return dispatcher.dispatchComputeFees(new ChildFeeContextImpl(
                feeManager,
                this,
                bodyToDispatch,
                syntheticPayerId,
                computeDispatchFeesAsTopLevel == ComputeDispatchFeesAsTopLevel.NO));
    }

    @Override
    @NonNull
    public <T> T dispatchPrecedingTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> callback,
            @NonNull final AccountID syntheticPayerId) {
        final Supplier<SingleTransactionRecordBuilderImpl> recordBuilderFactory =
                () -> recordListBuilder.addPreceding(configuration(), LIMITED_CHILD_RECORDS);
        final var result = doDispatchPrecedingTransaction(
                syntheticPayerId, txBody, recordBuilderFactory, recordBuilderClass, callback);

        // a preceding transaction must be committed immediately
        stack.commitFullStack();

        return result;
    }

    @Override
    @NonNull
    public <T> T dispatchReversiblePrecedingTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final Class<T> recordBuilderClass,
            @NonNull final Predicate<Key> callback,
            @NonNull final AccountID syntheticPayerId) {
        final Supplier<SingleTransactionRecordBuilderImpl> recordBuilderFactory =
                () -> recordListBuilder.addReversiblePreceding(configuration());
        return doDispatchPrecedingTransaction(
                syntheticPayerId, txBody, recordBuilderFactory, recordBuilderClass, callback);
    }

    @Override
    @NonNull
    public <T> T dispatchRemovablePrecedingTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> callback,
            @NonNull final AccountID syntheticPayerId) {
        final Supplier<SingleTransactionRecordBuilderImpl> recordBuilderFactory =
                () -> recordListBuilder.addRemovablePreceding(configuration());
        return doDispatchPrecedingTransaction(
                syntheticPayerId, txBody, recordBuilderFactory, recordBuilderClass, callback);
    }

    @NonNull
    public <T> T doDispatchPrecedingTransaction(
            @NonNull final AccountID syntheticPayer,
            @NonNull final TransactionBody txBody,
            @NonNull final Supplier<SingleTransactionRecordBuilderImpl> recordBuilderFactory,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> callback) {
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");

        if (category != TransactionCategory.USER && category != TransactionCategory.CHILD) {
            throw new IllegalArgumentException("Only user- or child-transactions can dispatch preceding transactions");
        }

        final var precedingRecordBuilder = recordBuilderFactory.get();
        dispatchSyntheticTxn(syntheticPayer, txBody, PRECEDING, precedingRecordBuilder, callback);

        return castRecordBuilder(precedingRecordBuilder, recordBuilderClass);
    }

    @NonNull
    @Override
    public <T> T dispatchChildTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> callback,
            @NonNull final AccountID syntheticPayerId,
            @NonNull final TransactionCategory childCategory) {
        final Supplier<SingleTransactionRecordBuilderImpl> recordBuilderFactory =
                () -> recordListBuilder.addChild(configuration(), childCategory);
        return doDispatchChildTransaction(
                syntheticPayerId, txBody, recordBuilderFactory, recordBuilderClass, callback, childCategory);
    }

    @NonNull
    @Override
    public <T> T dispatchRemovableChildTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> callback,
            @NonNull final AccountID syntheticPayerId,
            @NonNull final ExternalizedRecordCustomizer customizer) {
        final Supplier<SingleTransactionRecordBuilderImpl> recordBuilderFactory =
                () -> recordListBuilder.addRemovableChildWithExternalizationCustomizer(configuration(), customizer);
        return doDispatchChildTransaction(
                syntheticPayerId, txBody, recordBuilderFactory, recordBuilderClass, callback, CHILD);
    }

    @NonNull
    private <T> T doDispatchChildTransaction(
            @NonNull final AccountID syntheticPayer,
            @NonNull final TransactionBody txBody,
            @NonNull final Supplier<SingleTransactionRecordBuilderImpl> recordBuilderFactory,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> callback,
            @NonNull final TransactionCategory childCategory) {
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        requireNonNull(childCategory, "childCategory must not be null");

        if (category == PRECEDING) {
            throw new IllegalArgumentException("A preceding transaction cannot have child transactions");
        }

        // run the child-transaction
        final var childRecordBuilder = recordBuilderFactory.get();
        dispatchSyntheticTxn(syntheticPayer, txBody, childCategory, childRecordBuilder, callback);

        return castRecordBuilder(childRecordBuilder, recordBuilderClass);
    }

    public @NonNull Set<AccountID> dispatchPaidStakerIds() {
        return dispatchPaidStakerIds == null ? emptySet() : dispatchPaidStakerIds;
    }

    private void dispatchSyntheticTxn(
            @NonNull final AccountID syntheticPayer,
            @NonNull final TransactionBody txBody,
            @NonNull final TransactionCategory childCategory,
            @NonNull final SingleTransactionRecordBuilderImpl childRecordBuilder,
            @Nullable final Predicate<Key> callback) {
        // Initialize record builder list
        final var bodyBytes = TransactionBody.PROTOBUF.toBytes(txBody);
        final var signedTransaction =
                SignedTransaction.newBuilder().bodyBytes(bodyBytes).build();
        final var signedTransactionBytes = SignedTransaction.PROTOBUF.toBytes(signedTransaction);
        final var transaction = Transaction.newBuilder()
                .signedTransactionBytes(signedTransactionBytes)
                .build();
        childRecordBuilder
                .transaction(transaction)
                .transactionBytes(signedTransactionBytes)
                .memo(txBody.memo());
        // If there are any failures in the child transaction, we don't want to set transfer list
        // to be compatible with mono-service
        if (childCategory == CHILD) {
            childRecordBuilder.transferList(null);
        }

        // Set the transactionId if provided
        final var transactionID = txBody.transactionID();
        if (transactionID != null) {
            childRecordBuilder.transactionID(transactionID);
        }

        try {
            // Synthetic transaction bodies do not have transaction ids, node account
            // ids, and so on; hence we don't need to validate them with the checker
            dispatcher.dispatchPureChecks(txBody);
        } catch (final PreCheckException e) {
            childRecordBuilder.status(e.responseCode());
            return;
        }

        final HederaFunctionality function;
        try {
            function = functionOf(txBody);
        } catch (final UnknownHederaFunctionality e) {
            logger.error("Possible bug: unknown function in transaction body", e);
            childRecordBuilder.status(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
            return;
        }

        // Any keys verified for this dispatch (including the payer key if
        // required) should incorporate the provided callback
        final var childVerifier = callback != null ? new DelegateKeyVerifier(callback) : verifier;
        final DispatchValidationResult dispatchValidationResult;
        try {
            // Note the first parameter sets up that if there is no callback, then the keys are
            // not verified here at all, which is apparently required for many contract calls.
            dispatchValidationResult = validate(
                    callback == null ? null : childVerifier,
                    callback,
                    function,
                    txBody,
                    syntheticPayer,
                    networkInfo().selfNodeInfo().nodeId(),
                    dispatchNeedsHapiPayerChecks(childCategory));
        } catch (final PreCheckException e) {
            // This will happen when the payer for a triggered transaction cannot afford the service fee,
            // part of normal operations
            childRecordBuilder.status(e.responseCode());
            return;
        }

        final var childStack = new SavepointStackImpl(current());
        final var childContext = new HandleContextImpl(
                txBody,
                function,
                0,
                syntheticPayer,
                dispatchValidationResult == null ? null : dispatchValidationResult.key(),
                networkInfo,
                childCategory,
                childRecordBuilder,
                childStack,
                configuration,
                childVerifier,
                recordListBuilder,
                checker,
                dispatcher,
                serviceScopeLookup,
                blockRecordInfo,
                recordCache,
                feeManager,
                exchangeRateManager,
                userTransactionConsensusTime,
                authorizer,
                solvencyPreCheck,
                childRecordFinalizer,
                parentRecordFinalizer,
                networkUtilizationManager,
                synchronizedThrottleAccumulator,
                platformState);

        // in order to work correctly isSuperUser(), we need to keep track of top level payer in child context
        childContext.setTopLevelPayer(topLevelPayer);

        if (dispatchValidationResult != null) {
            childContext.feeAccumulator.chargeFees(
                    syntheticPayer, networkInfo().selfNodeInfo().accountId(), dispatchValidationResult.fees());
        }
        try {
            dispatcher.dispatchHandle(childContext);
            childRecordBuilder.status(ResponseCodeEnum.SUCCESS);
        } catch (final HandleException e) {
            if (e.shouldRollbackStack()) {
                childStack.rollbackFullStack();
                if (dispatchValidationResult != null) {
                    childContext.feeAccumulator.chargeFees(
                            syntheticPayer, networkInfo().selfNodeInfo().accountId(), dispatchValidationResult.fees());
                }
            }
            childRecordBuilder.status(e.getStatus());
            recordListBuilder.revertChildrenOf(childRecordBuilder);
        }
        // For mono-service fidelity, we need to attach staking rewards for a
        // triggered transaction to the record of the child here, and not the
        // "parent" ScheduleCreate or ScheduleSign transaction
        if (childCategory == SCHEDULED) {
            final var finalizeContext = new TriggeredFinalizeContext(
                    new ReadableStoreFactory(childStack),
                    new WritableStoreFactory(childStack, TokenService.NAME),
                    childRecordBuilder,
                    consensusNow(),
                    configuration);
            parentRecordFinalizer.finalizeParentRecord(
                    payer, finalizeContext, function, extraRewardReceivers(txBody, function, childRecordBuilder));
            final var paidStakingRewards = childRecordBuilder.getPaidStakingRewards();
            if (!paidStakingRewards.isEmpty()) {
                if (dispatchPaidStakerIds == null) {
                    dispatchPaidStakerIds = new LinkedHashSet<>();
                }
                paidStakingRewards.forEach(aa -> dispatchPaidStakerIds.add(aa.accountIDOrThrow()));
            }
        } else {
            final var finalizeContext = new ChildFinalizeContextImpl(
                    new ReadableStoreFactory(childStack),
                    new WritableStoreFactory(childStack, TokenService.NAME),
                    childRecordBuilder);
            childRecordFinalizer.finalizeChildRecord(finalizeContext, function);
        }
        // For mono-service fidelity, we need to attach staking rewards for a
        // triggered transaction to the record of the child here, and not the
        // "parent" ScheduleCreate or ScheduleSign transaction
        if (childCategory == SCHEDULED) {
            final var finalizeContext = new TriggeredFinalizeContext(
                    new ReadableStoreFactory(childStack),
                    new WritableStoreFactory(childStack, TokenService.NAME),
                    childRecordBuilder,
                    consensusNow(),
                    configuration);
            parentRecordFinalizer.finalizeParentRecord(
                    payer, finalizeContext, function, extraRewardReceivers(txBody, function, childRecordBuilder));
            final var paidStakingRewards = childRecordBuilder.getPaidStakingRewards();
            if (!paidStakingRewards.isEmpty()) {
                if (dispatchPaidStakerIds == null) {
                    dispatchPaidStakerIds = new LinkedHashSet<>();
                }
                paidStakingRewards.forEach(aa -> dispatchPaidStakerIds.add(aa.accountIDOrThrow()));
            }
        } else {
            final var finalizeContext = new ChildFinalizeContextImpl(
                    new ReadableStoreFactory(childStack),
                    new WritableStoreFactory(childStack, TokenService.NAME),
                    childRecordBuilder);
            childRecordFinalizer.finalizeChildRecord(finalizeContext, function);
        }
        childStack.commitFullStack();
    }

    private @Nullable DispatchValidationResult validate(
            @Nullable final KeyVerifier keyVerifier,
            @Nullable final Predicate<Key> callback,
            @NonNull final HederaFunctionality function,
            @NonNull final TransactionBody transactionBody,
            @NonNull final AccountID syntheticPayerId,
            final long nodeID,
            final boolean enforceHapiPayerChecks)
            throws PreCheckException {

        final PreHandleContextImpl preHandleContext;
        preHandleContext = new PreHandleContextImpl(
                readableStoreFactory(), transactionBody, syntheticPayerId, configuration(), dispatcher);
        dispatcher.dispatchPreHandle(preHandleContext);

        DispatchValidationResult dispatchValidationResult = null;
        if (enforceHapiPayerChecks) {
            // In the current system only the schedule service needs to specify its
            // child transaction id, and will never use a duplicate, so this check is
            // redundant; but cheap enough to add up-front anyway.
            final var duplicateCheckResult = recordCache.hasDuplicate(transactionBody.transactionIDOrThrow(), nodeID);
            if (duplicateCheckResult != NO_DUPLICATE) {
                throw new PreCheckException(DUPLICATE_TRANSACTION);
            }

            // Check the status and solvency of the payer, using
            // the same calculation strategy as a top-level transaction
            // since mono-service did that for scheduled transactions
            final var serviceFee = dispatchComputeFees(
                            transactionBody, syntheticPayerId, ComputeDispatchFeesAsTopLevel.YES)
                    .copyBuilder()
                    .networkFee(0)
                    .nodeFee(0)
                    .build();
            final var payerAccount = solvencyPreCheck.getPayerAccount(readableStoreFactory(), syntheticPayerId);
            solvencyPreCheck.checkSolvency(
                    transactionBody, syntheticPayerId, function, payerAccount, serviceFee, false);

            // Note we do NOT want to enforce the "time box" on valid start for
            // transaction ids dispatched by the schedule service, since these ids derive from their
            // ScheduleCreate id, which could have happened long ago
            final var syntheticPayerKey = payerAccount.keyOrThrow();
            requireNonNull(keyVerifier, "keyVerifier must not be null when enforcing HAPI-style payer checks");
            validateKey(keyVerifier, callback, syntheticPayerKey);
            dispatchValidationResult = new DispatchValidationResult(syntheticPayerKey, serviceFee);
        }

        // Given the current HTS system contract interface and ScheduleService
        // allow list, it is impossible for any dispatched transaction to
        // require authorization; but again, this is cheap to add up-front
        assertPayerIsAuthorized(function, transactionBody, syntheticPayerId);

        // No matter if using HAPI-style payer checks, we need to verify any
        // additional signing requirements are met if given a non-null
        // "verification assistant" callback
        if (keyVerifier != null) {
            for (final var key : preHandleContext.requiredNonPayerKeys()) {
                validateKey(keyVerifier, callback, key);
            }
            for (final var hollowAccount : preHandleContext.requiredHollowAccounts()) {
                final var verification = keyVerifier.verificationFor(hollowAccount.alias());
                if (verification.failed()) {
                    throw new PreCheckException(INVALID_SIGNATURE);
                }
            }
        }
        return dispatchValidationResult;
    }

    /**
     * This method works around a corner case with the `KeyVerifier` design which prevents certain
     * previously verified keys from succeeding.  To correct that, we give the callback predicate
     * one final opportunity to accept each key if validation fails.
     * @param keyVerifier theKeyVerifier to use for signature validation
     * @param callback a Predicate possibly provided by the service to validate additional keys.
     * @param keyToValidate The Key to be validated and determined to meet or not meet signature requirements.
     * @throws PreCheckException if the Key does not meet signature requirements.
     */
    private static void validateKey(
            final @NonNull KeyVerifier keyVerifier, final @Nullable Predicate<Key> callback, final Key keyToValidate)
            throws PreCheckException {
        // We must *attempt* payer verification (in case the same key is required for other aspects of the
        // transaction); however, if the callback is set, then a failed verification can become
        // success if the callback accepts the payer key.  This works around an issue in scheduled
        // transactions where the payer for a child transaction is "deemed valid" even though that payer
        // did not sign the current user transaction.
        // @todo('9447') Remove this special case when fixing the "deemed valid" behavior.
        final SignatureVerification verification = keyVerifier.verificationFor(keyToValidate);
        final boolean callbackFailed = callback != null ? !callback.test(keyToValidate) : true;
        if (verification.failed() && callbackFailed) {
            throw new PreCheckException(INVALID_SIGNATURE);
        }
    }

    private void assertPayerIsAuthorized(
            @NonNull final HederaFunctionality function,
            @NonNull final TransactionBody transactionBody,
            @NonNull final AccountID syntheticPayerId)
            throws PreCheckException {
        // Check if the payer has the required permissions
        if (!authorizer.isAuthorized(syntheticPayerId, function)) {
            if (function == HederaFunctionality.SYSTEM_DELETE) {
                throw new PreCheckException(ResponseCodeEnum.NOT_SUPPORTED);
            }
            throw new PreCheckException(ResponseCodeEnum.UNAUTHORIZED);
        }

        // Check if the transaction is privileged and if the payer has the required privileges
        final var privileges = authorizer.hasPrivilegedAuthorization(syntheticPayerId, function, transactionBody);
        if (privileges == SystemPrivilege.UNAUTHORIZED) {
            throw new PreCheckException(ResponseCodeEnum.AUTHORIZATION_FAILED);
        }
        if (privileges == SystemPrivilege.IMPERMISSIBLE) {
            throw new PreCheckException(ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE);
        }
    }

    @Override
    @NonNull
    public <T> T addChildRecordBuilder(@NonNull final Class<T> recordBuilderClass) {
        final var result = recordListBuilder.addChild(configuration(), CHILD);
        return castRecordBuilder(result, recordBuilderClass);
    }

    @Override
    @NonNull
    public <T> T addPrecedingChildRecordBuilder(@NonNull final Class<T> recordBuilderClass) {
        final var result = recordListBuilder.addPreceding(configuration(), LIMITED_CHILD_RECORDS);
        return castRecordBuilder(result, recordBuilderClass);
    }

    @Override
    @NonNull
    public <T> T addRemovableChildRecordBuilder(@NonNull final Class<T> recordBuilderClass) {
        final var result = recordListBuilder.addRemovableChild(configuration());
        return castRecordBuilder(result, recordBuilderClass);
    }

    @Override
    @NonNull
    public SavepointStack savepointStack() {
        return stack;
    }

    @Override
    public void revertRecordsFrom(@NonNull final RecordListCheckPoint checkpoint) {
        recordListBuilder.revertChildrenFrom(checkpoint);
    }

    @NonNull
    @Override
    public RecordListCheckPoint createRecordListCheckPoint() {
        final var precedingRecordBuilders = recordListBuilder.precedingRecordBuilders();
        final var childRecordBuilders = recordListBuilder.childRecordBuilders();

        SingleTransactionRecordBuilder lastFollowing = null;
        SingleTransactionRecordBuilder firstPreceding = null;

        if (!precedingRecordBuilders.isEmpty()) {
            firstPreceding = precedingRecordBuilders.get(precedingRecordBuilders.size() - 1);
        }
        if (!childRecordBuilders.isEmpty()) {
            lastFollowing = childRecordBuilders.get(childRecordBuilders.size() - 1);
        }

        return new RecordListCheckPoint(firstPreceding, lastFollowing);
    }

    @Override
    public void reclaimPreviouslyReservedThrottle(int n, HederaFunctionality function) {
        synchronizedThrottleAccumulator.leakUnusedThrottlePreviouslyReserved(n, function);
    }

    @Override
    public boolean shouldThrottleNOfUnscaled(int n, HederaFunctionality function) {
        return networkUtilizationManager.shouldThrottleNOfUnscaled(n, function, userTransactionConsensusTime);
    }

    public boolean shouldThrottleTxn(TransactionInfo txInfo) {
        return networkUtilizationManager.shouldThrottle(txInfo, current(), userTransactionConsensusTime);
    }

    @Override
    public List<DeterministicThrottle.UsageSnapshot> getUsageSnapshots() {
        return networkUtilizationManager.getUsageSnapshots();
    }

    @Override
    public void resetUsageThrottlesTo(List<DeterministicThrottle.UsageSnapshot> snapshots) {
        networkUtilizationManager.resetUsageThrottlesTo(snapshots);
    }

    @Override
    public boolean hasThrottleCapacityForChildTransactions() {
        var isAllowed = true;
        final var childRecords = recordListBuilder.childRecordBuilders();
        @Nullable List<DeterministicThrottle.UsageSnapshot> snapshotsIfNeeded = null;

        for (int i = 0, n = childRecords.size(); i < n && isAllowed; i++) {
            final var childRecord = childRecords.get(i);
            if (Objects.equals(childRecord.status(), SUCCESS)) {
                final var childTx = childRecord.transaction();
                final var childTxBody = childRecord.transactionBody();
                HederaFunctionality childTxFunctionality;
                try {
                    childTxFunctionality = functionOf(childTxBody);
                } catch (UnknownHederaFunctionality e) {
                    throw new IllegalStateException("Invalid transaction body " + childTxBody, e);
                }

                if (childTxFunctionality == CONTRACT_CREATE || childTxFunctionality == CONTRACT_CALL) {
                    continue;
                }
                if (snapshotsIfNeeded == null) {
                    snapshotsIfNeeded = getUsageSnapshots();
                }

                final var childTxInfo = TransactionInfo.from(
                        childTx, childTxBody, childTx.sigMap(), childTx.signedTransactionBytes(), childTxFunctionality);
                if (shouldThrottleTxn(childTxInfo)) {
                    isAllowed = false;
                }
            }
        }
        if (!isAllowed) {
            resetUsageThrottlesTo(snapshotsIfNeeded);
        }
        return isAllowed;
    }

    @Override
    public boolean isSelfSubmitted() {
        return Objects.equals(
                body().nodeAccountID(), networkInfo().selfNodeInfo().accountId());
    }

    public enum PrecedingTransactionCategory {
        UNLIMITED_CHILD_RECORDS,
        LIMITED_CHILD_RECORDS
    }

    /**
     * Given the transaction category of a synthetic dispatch, returns whether
     * the category requires the synthetic payer to pass standard HAPI-style
     * checks; most notably that,
     * <ul>
     *     <li>The payer cannot be a contract account.</li>
     *     <li>The payer must be able to cover all the fees for the transaction.</li>
     * </ul>
     *
     * @return whether the category requires HAPI-style payer checks
     */
    private boolean dispatchNeedsHapiPayerChecks(@NonNull final TransactionCategory category) {
        return category == SCHEDULED;
    }

    private record DispatchValidationResult(@NonNull Key key, @NonNull Fees fees) {
        public DispatchValidationResult {
            requireNonNull(key);
        }
    }

    private void setTopLevelPayer(@NonNull AccountID topLevelPayer) {
        this.topLevelPayer = requireNonNull(topLevelPayer, "payer must not be null");
    }

    @Nullable
    @Override
    public Instant freezeTime() {
        return platformState.getFreezeTime();
    }
}
