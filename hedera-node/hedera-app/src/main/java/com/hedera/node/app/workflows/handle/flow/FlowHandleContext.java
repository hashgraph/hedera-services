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

package com.hedera.node.app.workflows.handle.flow;

import static com.hedera.hapi.util.HapiUtils.functionOf;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.util.UnknownHederaFunctionality;
import com.hedera.node.app.fees.ChildFeeContextImpl;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.signature.KeyVerifier;
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
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.ServiceApiFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.flow.components.ChildDispatchComponent;
import com.hedera.node.app.workflows.handle.flow.dispatcher.ChildDispatchLogic;
import com.hedera.node.app.workflows.handle.flow.dispatcher.Dispatch;
import com.hedera.node.app.workflows.handle.flow.dispatcher.DispatchLogic;
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
import java.util.Collections;
import java.util.Map;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * The HandleContext Implementation
 */
public class FlowHandleContext implements HandleContext, FeeContext {
    private final Instant consensusNow;
    private final TransactionInfo txnInfo;
    private final Configuration configuration;
    private final Authorizer authorizer;
    private final BlockRecordManager blockRecordManager;
    private final FeeManager feeManager;
    private final ReadableStoreFactory readableStoreFactory;
    private final AccountID syntheticPayer;
    private final KeyVerifier verifier;
    private final Key payerkey;
    private final FeeAccumulator feeAccumulator;
    private final ExchangeRateManager exchangeRateManager;
    private final SavepointStackImpl stack;
    private final WritableEntityIdStore entityIdStore;
    private final AttributeValidator attributeValidator;
    private final ExpiryValidator expiryValidator;
    private final TransactionDispatcher dispatcher;
    private final RecordCache recordCache;
    private final WritableStoreFactory writableStoreFactory;
    private final ServiceApiFactory serviceApiFactory;
    private final NetworkInfo networkInfo;
    private final SingleTransactionRecordBuilderImpl recordBuilder;
    private final Provider<ChildDispatchComponent.Factory> childDispatchFactory;
    private final ChildDispatchLogic childDispatchLogic;
    private final Dispatch parentDispatch;
    private final DispatchLogic dispatchLogic;

    @Inject
    public FlowHandleContext(
            final Instant consensusNow,
            final TransactionInfo transactionInfo,
            final Configuration configuration,
            final Authorizer authorizer,
            final BlockRecordManager blockRecordManager,
            final FeeManager feeManager,
            final ReadableStoreFactory storeFactory,
            final AccountID syntheticPayer,
            final KeyVerifier verifier,
            final Key payerkey,
            final FeeAccumulator feeAccumulator,
            final ExchangeRateManager exchangeRateManager,
            final SavepointStackImpl stack,
            final WritableEntityIdStore entityIdStore,
            final TransactionDispatcher dispatcher,
            final RecordCache recordCache,
            final WritableStoreFactory writableStoreFactory,
            final ServiceApiFactory serviceApiFactory,
            final NetworkInfo networkInfo,
            final SingleTransactionRecordBuilderImpl recordBuilder,
            final Provider<ChildDispatchComponent.Factory> childDispatchFactory,
            final ChildDispatchLogic childDispatchLogic,
            final Dispatch parentDispatch,
            final DispatchLogic dispatchLogic) {
        this.consensusNow = consensusNow;
        this.txnInfo = transactionInfo;
        this.configuration = configuration;
        this.authorizer = authorizer;
        this.blockRecordManager = blockRecordManager;
        this.feeManager = feeManager;
        this.readableStoreFactory = storeFactory;
        this.syntheticPayer = syntheticPayer;
        this.verifier = verifier;
        this.payerkey = payerkey;
        this.feeAccumulator = feeAccumulator;
        this.exchangeRateManager = exchangeRateManager;
        this.stack = stack;
        this.entityIdStore = entityIdStore;
        this.childDispatchFactory = childDispatchFactory;
        this.childDispatchLogic = childDispatchLogic;
        this.parentDispatch = parentDispatch;
        this.dispatchLogic = dispatchLogic;
        // TODO : Provide these two from UserTxnScope after deleting mono code
        this.attributeValidator = new AttributeValidatorImpl(this);
        this.expiryValidator = new ExpiryValidatorImpl(this);
        this.dispatcher = dispatcher;
        this.recordCache = recordCache;
        this.writableStoreFactory = writableStoreFactory;
        this.serviceApiFactory = serviceApiFactory;
        this.networkInfo = networkInfo;
        this.recordBuilder = recordBuilder;
    }

    @NonNull
    @Override
    public Instant consensusNow() {
        return consensusNow;
    }

    @NonNull
    @Override
    public TransactionBody body() {
        return txnInfo.txBody();
    }

    @NonNull
    @Override
    public AccountID payer() {
        return syntheticPayer;
    }

    @NonNull
    @Override
    public Configuration configuration() {
        return configuration;
    }

    @Nullable
    @Override
    public Authorizer authorizer() {
        return authorizer;
    }

    @Override
    public int numTxnSignatures() {
        return verifier.numSignaturesVerified();
    }

    @NonNull
    @Override
    public BlockRecordInfo blockRecordInfo() {
        return blockRecordManager;
    }

    @NonNull
    @Override
    public FunctionalityResourcePrices resourcePricesFor(
            @NonNull final HederaFunctionality functionality, @NonNull final SubType subType) {
        return new FunctionalityResourcePrices(
                requireNonNull(feeManager.getFeeData(functionality, consensusNow, subType)),
                feeManager.congestionMultiplierFor(txnInfo.txBody(), functionality, readableStoreFactory));
    }

    @NonNull
    @Override
    public FeeCalculator feeCalculator(@NonNull final SubType subType) {
        return feeManager.createFeeCalculator(
                txnInfo.txBody(),
                payerkey,
                txnInfo.functionality(),
                numTxnSignatures(),
                SignatureMap.PROTOBUF.measureRecord(txnInfo.signatureMap()),
                consensusNow,
                subType,
                false,
                readableStoreFactory);
    }

    @NonNull
    @Override
    public FeeAccumulator feeAccumulator() {
        return feeAccumulator;
    }

    @NonNull
    @Override
    public ExchangeRateInfo exchangeRateInfo() {
        return exchangeRateManager.exchangeRateInfo(stack.peek());
    }

    @Override
    public long newEntityNum() {
        return entityIdStore.incrementAndGet();
    }

    @Override
    public long peekAtNewEntityNum() {
        return entityIdStore.peekAtNextNumber();
    }

    @NonNull
    @Override
    public AttributeValidator attributeValidator() {
        return attributeValidator;
    }

    @NonNull
    @Override
    public ExpiryValidator expiryValidator() {
        return expiryValidator;
    }

    @NonNull
    @Override
    public TransactionKeys allKeysForTransaction(
            @NonNull final TransactionBody nestedTxn, @NonNull final AccountID payerForNested)
            throws PreCheckException {
        dispatcher.dispatchPureChecks(nestedTxn);
        final var nestedContext =
                new PreHandleContextImpl(readableStoreFactory, nestedTxn, payerForNested, configuration(), dispatcher);
        try {
            dispatcher.dispatchPreHandle(nestedContext);
        } catch (final PreCheckException ignored) {
            // We must ignore/translate the exception here, as this is key gathering, not transaction validation.
            throw new PreCheckException(ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS);
        }
        return nestedContext;
    }

    @NonNull
    @Override
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

    @NonNull
    @Override
    public SignatureVerification verificationFor(@NonNull final Bytes evmAlias) {
        requireNonNull(evmAlias, "evmAlias must not be null");
        return verifier.verificationFor(evmAlias);
    }

    @Override
    public boolean isSuperUser() {
        throw new UnsupportedOperationException("This method should be deleted");
    }

    @Override
    public SystemPrivilege hasPrivilegedAuthorization() {
        return authorizer.hasPrivilegedAuthorization(txnInfo.payerID(), txnInfo.functionality(), txnInfo.txBody());
    }

    @NonNull
    @Override
    public RecordCache recordCache() {
        return recordCache;
    }

    @NonNull
    @Override
    public <T> T readableStore(@NonNull final Class<T> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        return readableStoreFactory.getStore(storeInterface);
    }

    @NonNull
    @Override
    public <T> T writableStore(@NonNull final Class<T> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        return writableStoreFactory.getStore(storeInterface);
    }

    @NonNull
    @Override
    public <T> T serviceApi(@NonNull final Class<T> apiInterface) {
        requireNonNull(apiInterface, "apiInterface must not be null");
        return serviceApiFactory.getApi(apiInterface);
    }

    @NonNull
    @Override
    public NetworkInfo networkInfo() {
        return networkInfo;
    }

    @NonNull
    @Override
    public <T> T recordBuilder(@NonNull final Class<T> recordBuilderClass) {
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        return castRecordBuilder(recordBuilder, recordBuilderClass);
    }

    @Override
    public Fees dispatchComputeFees(
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
                computeDispatchFeesAsTopLevel == ComputeDispatchFeesAsTopLevel.NO,
                authorizer,
                numTxnSignatures()));
    }

    @NonNull
    @Override
    public <T> T dispatchPrecedingTransaction(
            @NonNull final TransactionBody childTxnBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> childCallback,
            final AccountID childSyntheticPayerId) {
        return dispatchForRecord(
                childTxnBody,
                recordBuilderClass,
                childCallback,
                childSyntheticPayerId,
                ExternalizedRecordCustomizer.NOOP_EXTERNALIZED_RECORD_CUSTOMIZER,
                TransactionCategory.PRECEDING,
                SingleTransactionRecordBuilderImpl.ReversingBehavior.IRREVERSIBLE,
                true);
    }

    @NonNull
    @Override
    public <T> T dispatchReversiblePrecedingTransaction(
            @NonNull final TransactionBody childTxBody,
            @NonNull final Class<T> recordBuilderClass,
            @NonNull final Predicate<Key> childCallback,
            final AccountID childSyntheticPayer) {
        return dispatchForRecord(
                childTxBody,
                recordBuilderClass,
                childCallback,
                childSyntheticPayer,
                ExternalizedRecordCustomizer.NOOP_EXTERNALIZED_RECORD_CUSTOMIZER,
                TransactionCategory.PRECEDING,
                SingleTransactionRecordBuilderImpl.ReversingBehavior.REVERSIBLE,
                false);
    }

    @NonNull
    @Override
    public <T> T dispatchRemovablePrecedingTransaction(
            @NonNull final TransactionBody childTxBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> childCallback,
            final AccountID childSyntheticPayer) {
        return dispatchForRecord(
                childTxBody,
                recordBuilderClass,
                childCallback,
                childSyntheticPayer,
                ExternalizedRecordCustomizer.NOOP_EXTERNALIZED_RECORD_CUSTOMIZER,
                TransactionCategory.PRECEDING,
                SingleTransactionRecordBuilderImpl.ReversingBehavior.REMOVABLE,
                false);
    }

    @NonNull
    @Override
    public <T> T dispatchChildTransaction(
            @NonNull final TransactionBody childTxBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> childCallback,
            @NonNull final AccountID childSyntheticPayerId,
            @NonNull final TransactionCategory childCategory) {
        return dispatchForRecord(
                childTxBody,
                recordBuilderClass,
                childCallback,
                childSyntheticPayerId,
                ExternalizedRecordCustomizer.NOOP_EXTERNALIZED_RECORD_CUSTOMIZER,
                childCategory,
                SingleTransactionRecordBuilderImpl.ReversingBehavior.REVERSIBLE,
                false);
    }

    @NonNull
    @Override
    public <T> T dispatchRemovableChildTransaction(
            @NonNull final TransactionBody childTxBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> childCallback,
            @NonNull final AccountID childSyntheticPayerId,
            @NonNull final ExternalizedRecordCustomizer customizer) {
        return dispatchForRecord(
                childTxBody,
                recordBuilderClass,
                childCallback,
                childSyntheticPayerId,
                customizer,
                TransactionCategory.CHILD,
                SingleTransactionRecordBuilderImpl.ReversingBehavior.REMOVABLE,
                false);
    }

    @NonNull
    @Override
    public <T> T addChildRecordBuilder(@NonNull final Class<T> recordBuilderClass) {
        final var result = parentDispatch.recordListBuilder().addChild(configuration(), CHILD);
        return castRecordBuilder(result, recordBuilderClass);
    }

    @NonNull
    @Override
    public <T> T addRemovableChildRecordBuilder(@NonNull final Class<T> recordBuilderClass) {
        final var result = parentDispatch.recordListBuilder().addRemovableChild(configuration());
        return castRecordBuilder(result, recordBuilderClass);
    }

    @NonNull
    @Override
    public SavepointStack savepointStack() {
        return stack;
    }

    @Override
    public void revertRecordsFrom(@NonNull final RecordListCheckPoint recordListCheckPoint) {
        parentDispatch.recordListBuilder().revertChildrenFrom(recordListCheckPoint);
    }

    @Override
    public boolean shouldThrottleNOfUnscaled(final int n, final HederaFunctionality function) {
        // TODO: Implement this
        return false;
    }

    @Override
    public boolean hasThrottleCapacityForChildTransactions() {
        // TODO: Implement this
        return true;
    }

    @NonNull
    @Override
    public RecordListCheckPoint createRecordListCheckPoint() {
        final var precedingRecordBuilders = parentDispatch.recordListBuilder().precedingRecordBuilders();
        final var childRecordBuilders = parentDispatch.recordListBuilder().childRecordBuilders();

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

    @NonNull
    @Override
    public Map<AccountID, Long> dispatchPaidRewards() {
        // TODO: Implement this
        return Collections.emptyMap();
    }

    private <T> T dispatchForRecord(
            @NonNull final TransactionBody txBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> verifier,
            final AccountID syntheticPayer,
            @NonNull ExternalizedRecordCustomizer customizer,
            TransactionCategory category,
            SingleTransactionRecordBuilderImpl.ReversingBehavior reversingBehavior,
            boolean commitStack) {
        final var childDispatch = childDispatchLogic.createChildDispatch(
                parentDispatch,
                txBody,
                verifier,
                syntheticPayer,
                category,
                childDispatchFactory,
                customizer,
                reversingBehavior);
        dispatchLogic.dispatch(childDispatch, parentDispatch.recordListBuilder());
        if (commitStack) {
            stack.commitFullStack();
        }
        return castRecordBuilder(childDispatch.recordBuilder(), recordBuilderClass);
    }

    private static <T> T castRecordBuilder(
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder,
            @NonNull final Class<T> recordBuilderClass) {
        if (!recordBuilderClass.isInstance(recordBuilder)) {
            throw new IllegalArgumentException("Not a valid record builder class");
        }
        return recordBuilderClass.cast(recordBuilder);
    }
}
