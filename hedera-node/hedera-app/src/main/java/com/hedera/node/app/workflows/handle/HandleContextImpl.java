/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.node.app.spi.HapiUtils.functionOf;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.PRECEDING;
import static com.hedera.node.app.state.HederaRecordCache.DuplicateCheckResult.NO_DUPLICATE;
import static com.hedera.node.app.workflows.handle.HandleContextImpl.PrecedingTransactionCategory.LIMITED_CHILD_RECORDS;
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
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
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
import com.hedera.node.app.spi.workflows.FunctionalityResourcePrices;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.TransactionKeys;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.state.WrappedHederaState;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.TransactionChecker;
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
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
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

    private ReadableStoreFactory readableStoreFactory;
    private AttributeValidator attributeValidator;
    private ExpiryValidator expiryValidator;
    private ExchangeRateInfo exchangeRateInfo;

    /**
     * Constructs a {@link HandleContextImpl}.
     *
     * @param txBody                The {@link TransactionBody} of the transaction
     * @param functionality         The {@link HederaFunctionality} of the transaction
     * @param signatureMapSize      The size of the {@link com.hedera.hapi.node.base.SignatureMap} of the transaction
     * @param payer                 The {@link AccountID} of the payer
     * @param payerKey              The {@link Key} of the payer
     * @param networkInfo           The {@link NetworkInfo} of the network
     * @param category              The {@link TransactionCategory} of the transaction (either user, preceding, or child)
     * @param recordBuilder         The main {@link SingleTransactionRecordBuilderImpl}
     * @param stack                 The {@link SavepointStackImpl} used to manage savepoints
     * @param configuration         The current {@link Configuration}
     * @param verifier              The {@link KeyVerifier} used to verify signatures and hollow accounts
     * @param recordListBuilder     The {@link RecordListBuilder} used to build the record stream
     * @param checker               The {@link TransactionChecker} used to check dispatched transaction
     * @param dispatcher            The {@link TransactionDispatcher} used to dispatch child transactions
     * @param serviceScopeLookup    The {@link ServiceScopeLookup} used to look up the scope of a service
     * @param feeManager            The {@link FeeManager} used to convert usage into fees
     * @param exchangeRateManager   The {@link ExchangeRateManager} used to obtain exchange rate information
     * @param userTransactionConsensusTime The consensus time of the user transaction, not any child transactions
     * @param authorizer            The {@link Authorizer} used to authorize the transaction
     * @param solvencyPreCheck      The {@link SolvencyPreCheck} used to validate if the account is able to pay the fees
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
            @NonNull final SolvencyPreCheck solvencyPreCheck) {
        this.txBody = requireNonNull(txBody, "txBody must not be null");
        this.functionality = requireNonNull(functionality, "functionality must not be null");
        this.payer = requireNonNull(payer, "payer must not be null");
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
                    false);
            final var tokenApi = serviceApiFactory.getApi(TokenServiceApi.class);
            this.feeAccumulator = new FeeAccumulatorImpl(tokenApi, recordBuilder);
        }

        this.exchangeRateManager = requireNonNull(exchangeRateManager, "exchangeRateManager must not be null");
        this.solvencyPreCheck = requireNonNull(solvencyPreCheck, "solvencyPreCheck must not be null");
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
        // TODO - how do we get the active congestion multiplier?
        return new FunctionalityResourcePrices(
                requireNonNull(feeManager.getFeeData(functionality, userTransactionConsensusTime, subType)), 1L);
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
    public Instant currentTime() {
        return consensusNow();
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
        dispatcher.dispatchPreHandle(nestedContext);
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
        return authorizer.isSuperUser(payer());
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
            @NonNull final TransactionBody txBody, @NonNull final AccountID syntheticPayerId) {
        var bodyToDispatch = txBody;
        if (!txBody.hasTransactionID()) {
            // Legacy mono fee calculators frequently estimate an entity's lifetime using the epoch second of the
            // transaction id/ valid start as the current consensus time; ensure those will behave sensibly here
            bodyToDispatch = txBody.copyBuilder()
                    .transactionID(TransactionID.newBuilder()
                            .transactionValidStart(Timestamp.newBuilder()
                                    .seconds(consensusNow().getEpochSecond())
                                    .nanos(consensusNow().getNano())))
                    .build();
        }
        return dispatcher.dispatchComputeFees(
                new ChildFeeContextImpl(feeManager, this, bodyToDispatch, syntheticPayerId));
    }

    @Override
    @NonNull
    public <T> T dispatchPrecedingTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final Class<T> recordBuilderClass,
            @NonNull final Predicate<Key> callback,
            @NonNull final AccountID syntheticPayerId) {
        final Supplier<SingleTransactionRecordBuilderImpl> recordBuilderFactory =
                () -> recordListBuilder.addPreceding(configuration(), LIMITED_CHILD_RECORDS);
        final var result = doDispatchPrecedingTransaction(
                syntheticPayerId, txBody, recordBuilderFactory, recordBuilderClass, callback);

        // a preceding transaction must be committed immediately
        stack.commitFullStack();
        stack.createSavepoint();

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
            @NonNull final Predicate<Key> callback,
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
            @NonNull final Predicate<Key> callback) {
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        requireNonNull(callback, "callback must not be null");

        if (category != TransactionCategory.USER) {
            throw new IllegalArgumentException("Only user-transactions can dispatch preceding transactions");
        }

        if (stack.depth() > 1) {
            throw new IllegalStateException(
                    "Cannot dispatch a preceding transaction when a savepoint has been created");
        }

        // This condition fails, because for auto-account creation we charge fees, before dispatching the transaction,
        // and the state will be modified.

        //         if (current().isModified()) {
        //                    throw new IllegalStateException("Cannot dispatch a preceding transaction when the state
        // has been modified");
        //         }

        // run the transaction
        final var precedingRecordBuilder = recordBuilderFactory.get();
        dispatchSyntheticTxn(syntheticPayer, txBody, PRECEDING, precedingRecordBuilder, callback);

        return castRecordBuilder(precedingRecordBuilder, recordBuilderClass);
    }

    @NonNull
    @Override
    public <T> T dispatchChildTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final Class<T> recordBuilderClass,
            @NonNull final Predicate<Key> callback,
            @NonNull final AccountID syntheticPayerId) {
        final Supplier<SingleTransactionRecordBuilderImpl> recordBuilderFactory =
                () -> recordListBuilder.addChild(configuration());
        return doDispatchChildTransaction(syntheticPayerId, txBody, recordBuilderFactory, recordBuilderClass, callback);
    }

    @NonNull
    @Override
    public <T> T dispatchRemovableChildTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final Class<T> recordBuilderClass,
            @NonNull final Predicate<Key> callback,
            @NonNull final AccountID syntheticPayerId) {
        final Supplier<SingleTransactionRecordBuilderImpl> recordBuilderFactory =
                () -> recordListBuilder.addRemovableChild(configuration());
        return doDispatchChildTransaction(syntheticPayerId, txBody, recordBuilderFactory, recordBuilderClass, callback);
    }

    @NonNull
    private <T> T doDispatchChildTransaction(
            @NonNull final AccountID syntheticPayer,
            @NonNull final TransactionBody txBody,
            @NonNull final Supplier<SingleTransactionRecordBuilderImpl> recordBuilderFactory,
            @NonNull final Class<T> recordBuilderClass,
            @NonNull final Predicate<Key> callback) {
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        requireNonNull(callback, "callback must not be null");

        if (category == PRECEDING) {
            throw new IllegalArgumentException("A preceding transaction cannot have child transactions");
        }

        // run the child-transaction
        final var childRecordBuilder = recordBuilderFactory.get();
        dispatchSyntheticTxn(syntheticPayer, txBody, CHILD, childRecordBuilder, callback);

        return castRecordBuilder(childRecordBuilder, recordBuilderClass);
    }

    private void dispatchSyntheticTxn(
            @NonNull final AccountID syntheticPayer,
            @NonNull final TransactionBody txBody,
            @NonNull final TransactionCategory childCategory,
            @NonNull final SingleTransactionRecordBuilderImpl childRecordBuilder,
            @NonNull final Predicate<Key> callback) {
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

        final var childStack = new SavepointStackImpl(current());
        final HederaFunctionality function;
        try {
            function = functionOf(txBody);
        } catch (final UnknownHederaFunctionality e) {
            logger.error("Possible bug: unknown function in transaction body", e);
            childRecordBuilder.status(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
            return;
        }

        final var childVerifier = new DelegateKeyVerifier(callback);

        Key childPayerKey = null;
        if (transactionID != null) {
            final var accountStore = readableStoreFactory().getStore(ReadableAccountStore.class);
            try {
                childPayerKey =
                        accountStore.getAccountById(transactionID.accountID()).key();
            } catch (final NullPointerException ex) {
                childRecordBuilder.status(ResponseCodeEnum.INVALID_TRANSACTION_ID);
                return;
            }
        }

        try {
            validate(
                    verifier,
                    function,
                    body(),
                    payer(),
                    payerKey,
                    childCategory,
                    networkInfo().selfNodeInfo().nodeId());
        } catch (final PreCheckException e) {
            childRecordBuilder.status(e.responseCode());
            return;
        }

        final var childContext = new HandleContextImpl(
                txBody,
                function,
                0,
                syntheticPayer,
                childPayerKey,
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
                solvencyPreCheck);

        try {
            dispatcher.dispatchHandle(childContext);
            childRecordBuilder.status(ResponseCodeEnum.SUCCESS);
            childStack.commitFullStack();
        } catch (final HandleException e) {
            childRecordBuilder.status(e.getStatus());
            recordListBuilder.revertChildrenOf(recordBuilder);
        }
    }

    private void validate(
            @NonNull final KeyVerifier keyVerifier,
            final HederaFunctionality function,
            final TransactionBody transactionBody,
            final AccountID payer,
            final Key payerKey,
            final TransactionCategory txCategory,
            final long nodeID)
            throws PreCheckException {

        final PreHandleContextImpl preHandleContext;

        preHandleContext =
                new PreHandleContextImpl(readableStoreFactory(), transactionBody, payer, configuration(), dispatcher);
        dispatcher.dispatchPreHandle(preHandleContext);

        // Check for duplicate transactions. It is perfectly normal for there to be duplicates -- it is valid for
        // a user to intentionally submit duplicates to multiple nodes as a hedge against dishonest nodes, or for
        // other reasons. If we find a duplicate, we *will not* execute the transaction, we will simply charge
        // the payer (whether the payer from the transaction or the node in the event of a due diligence failure)
        // and create an appropriate record to save in state and send to the record stream.
        final var duplicateCheckResult = recordCache.hasDuplicate(transactionBody.transactionID(), nodeID);
        if (duplicateCheckResult != NO_DUPLICATE) {
            throw new PreCheckException(DUPLICATE_TRANSACTION);
        }

        // Check the status and solvency of the payer
        final var fee = dispatchComputeFees(body(), payer);
        final var payerAccount = solvencyPreCheck.getPayerAccount(readableStoreFactory(), payer);
        solvencyPreCheck.checkSolvency(body(), payer, functionality, payerAccount, fee, true);

        // Check the time box of the transaction
        checker.checkTimeBox(transactionBody, userTransactionConsensusTime);

        // Check if the payer has the required permissions
        if (!authorizer.isAuthorized(payer, function)) {
            if (function == HederaFunctionality.SYSTEM_DELETE) {
                throw new PreCheckException(ResponseCodeEnum.NOT_SUPPORTED);
            }
            throw new PreCheckException(ResponseCodeEnum.UNAUTHORIZED);
        }

        // Check if the transaction is privileged and if the payer has the required privileges
        final var privileges = authorizer.hasPrivilegedAuthorization(payer, functionality, transactionBody);
        if (privileges == SystemPrivilege.UNAUTHORIZED) {
            throw new PreCheckException(ResponseCodeEnum.AUTHORIZATION_FAILED);
        }
        if (privileges == SystemPrivilege.IMPERMISSIBLE) {
            throw new PreCheckException(ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE);
        }

        // Skip payer verification when dispatching any child transaction
        if (!(txCategory.equals(CHILD) || txCategory.equals(PRECEDING))) {
            // Check all signature verifications. This will also wait, if validation is still ongoing.
            final var payerKeyVerification = keyVerifier.verificationFor(payerKey);
            if (payerKeyVerification.failed()) {
                throw new PreCheckException(INVALID_SIGNATURE);
            }
        }

        // verify all the keys
        for (final var key : preHandleContext.requiredNonPayerKeys()) {
            final var verification = keyVerifier.verificationFor(key);
            if (verification.failed()) {
                throw new PreCheckException(INVALID_SIGNATURE);
            }
        }
        // If there are any hollow accounts whose signatures need to be verified, verify them
        for (final var hollowAccount : preHandleContext.requiredHollowAccounts()) {
            final var verification = keyVerifier.verificationFor(hollowAccount.alias());
            if (verification.failed()) {
                throw new PreCheckException(INVALID_SIGNATURE);
            }
        }
    }

    @Override
    @NonNull
    public <T> T addChildRecordBuilder(@NonNull final Class<T> recordBuilderClass) {
        final var result = recordListBuilder.addChild(configuration());
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
    public void revertChildRecords() {
        recordListBuilder.revertChildrenOf(recordBuilder);
    }

    public enum PrecedingTransactionCategory {
        UNLIMITED_CHILD_RECORDS,
        LIMITED_CHILD_RECORDS
    }
}
