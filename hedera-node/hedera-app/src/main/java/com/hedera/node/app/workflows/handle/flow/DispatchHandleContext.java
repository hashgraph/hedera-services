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

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CREATE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.util.HapiUtils.functionOf;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshot;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.util.UnknownHederaFunctionality;
import com.hedera.node.app.fees.ChildFeeContextImpl;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.records.RecordBuildersImpl;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.FeeCalculatorFactory;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.fees.ResourcePriceCalculator;
import com.hedera.node.app.spi.ids.EntityNumGenerator;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.records.RecordBuilders;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.ComputeDispatchFeesAsTopLevel;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.TransactionKeys;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.store.StoreFactoryImpl;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.flow.dispatch.Dispatch;
import com.hedera.node.app.workflows.handle.flow.dispatch.child.ChildDispatchComponent;
import com.hedera.node.app.workflows.handle.flow.dispatch.child.logic.ChildDispatchFactory;
import com.hedera.node.app.workflows.handle.flow.dispatch.logic.DispatchProcessor;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.handle.validation.AttributeValidatorImpl;
import com.hedera.node.app.workflows.handle.validation.ExpiryValidatorImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.info.NetworkInfo;
import dagger.Reusable;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * The HandleContext Implementation
 */
@Reusable
public class DispatchHandleContext implements HandleContext, FeeContext {
    private final Instant consensusNow;
    private final TransactionInfo txnInfo;
    private final Configuration configuration;
    private final Authorizer authorizer;
    private final BlockRecordManager blockRecordManager;
    private final ResourcePriceCalculator resourcePriceCalculator;
    private final FeeManager feeManager;
    private final StoreFactoryImpl storeFactory;
    private final AccountID syntheticPayer;
    private final KeyVerifier verifier;
    private final Key payerKey;
    private final ExchangeRateManager exchangeRateManager;
    private final SavepointStackImpl stack;
    private final EntityNumGenerator entityNumGenerator;
    private final AttributeValidator attributeValidator;
    private final ExpiryValidator expiryValidator;
    private final TransactionDispatcher dispatcher;
    private final RecordCache recordCache;
    private final NetworkInfo networkInfo;
    private final RecordBuilders recordBuilders;
    private final Provider<ChildDispatchComponent.Factory> childDispatchProvider;
    private final ChildDispatchFactory childDispatchFactory;
    private final Dispatch currentDispatch;
    private final DispatchProcessor dispatchProcessor;
    private final NetworkUtilizationManager networkUtilizationManager;
    private Map<AccountID, Long> dispatchPaidRewards;

    @Inject
    public DispatchHandleContext(
            @NonNull final Instant consensusNow,
            @NonNull final TransactionInfo transactionInfo,
            @NonNull final Configuration configuration,
            @NonNull final Authorizer authorizer,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final ResourcePriceCalculator resourcePriceCalculator,
            @NonNull final FeeManager feeManager,
            @NonNull final StoreFactoryImpl storeFactory,
            @NonNull final AccountID syntheticPayer,
            @NonNull final KeyVerifier verifier,
            @NonNull final Key payerKey,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final SavepointStackImpl stack,
            @NonNull final EntityNumGenerator entityNumGenerator,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final RecordCache recordCache,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final RecordBuilders recordBuilders,
            @NonNull final Provider<ChildDispatchComponent.Factory> childDispatchProvider,
            @NonNull final ChildDispatchFactory childDispatchLogic,
            @NonNull final Dispatch parentDispatch,
            @NonNull final DispatchProcessor dispatchProcessor,
            @NonNull final NetworkUtilizationManager networkUtilizationManager) {
        this.consensusNow = requireNonNull(consensusNow);
        this.txnInfo = requireNonNull(transactionInfo);
        this.configuration = requireNonNull(configuration);
        this.authorizer = requireNonNull(authorizer);
        this.blockRecordManager = requireNonNull(blockRecordManager);
        this.resourcePriceCalculator = requireNonNull(resourcePriceCalculator);
        this.feeManager = requireNonNull(feeManager);
        this.storeFactory = requireNonNull(storeFactory);
        this.syntheticPayer = requireNonNull(syntheticPayer);
        this.verifier = requireNonNull(verifier);
        this.payerKey = requireNonNull(payerKey);
        this.exchangeRateManager = requireNonNull(exchangeRateManager);
        this.stack = requireNonNull(stack);
        this.entityNumGenerator = requireNonNull(entityNumGenerator);
        this.childDispatchProvider = requireNonNull(childDispatchProvider);
        this.childDispatchFactory = requireNonNull(childDispatchLogic);
        this.currentDispatch = requireNonNull(parentDispatch);
        this.dispatchProcessor = requireNonNull(dispatchProcessor);
        this.networkUtilizationManager = requireNonNull(networkUtilizationManager);
        this.attributeValidator = new AttributeValidatorImpl(this);
        this.expiryValidator = new ExpiryValidatorImpl(this);
        this.dispatcher = requireNonNull(dispatcher);
        this.recordCache = requireNonNull(recordCache);
        this.networkInfo = requireNonNull(networkInfo);
        this.recordBuilders = requireNonNull(recordBuilders);
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

    @Override
    public Fees dispatchComputeFees(
            @NonNull final TransactionBody childTxBody, @NonNull final AccountID syntheticPayerId) {
        requireNonNull(childTxBody);
        requireNonNull(syntheticPayerId);
        return dispatchComputeFees(childTxBody, syntheticPayerId, ComputeDispatchFeesAsTopLevel.NO);
    }

    @NonNull
    @Override
    public BlockRecordInfo blockRecordInfo() {
        return blockRecordManager;
    }

    @NonNull
    @Override
    public ResourcePriceCalculator resourcePriceCalculator() {
        return resourcePriceCalculator;
    }

    @NonNull
    private FeeCalculator createFeeCalculator(@NonNull final SubType subType) {
        return feeManager.createFeeCalculator(
                ensureTxnId(txnInfo.txBody()),
                payerKey,
                txnInfo.functionality(),
                numTxnSignatures(),
                SignatureMap.PROTOBUF.measureRecord(txnInfo.signatureMap()),
                consensusNow,
                subType,
                false,
                storeFactory.asReadOnly());
    }

    @NonNull
    @Override
    public FeeCalculatorFactory feeCalculatorFactory() {
        return this::createFeeCalculator;
    }

    @NonNull
    @Override
    public ExchangeRateInfo exchangeRateInfo() {
        return exchangeRateManager.exchangeRateInfo(stack.peek());
    }

    @Override
    public EntityNumGenerator entityNumGenerator() {
        return entityNumGenerator;
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
        final var nestedContext = new PreHandleContextImpl(
                storeFactory.asReadOnly(), nestedTxn, payerForNested, configuration(), dispatcher);
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
    public SystemPrivilege hasPrivilegedAuthorization() {
        return authorizer.hasPrivilegedAuthorization(
                requireNonNull(txnInfo.payerID()), txnInfo.functionality(), txnInfo.txBody());
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
        return storeFactory.readableStore(storeInterface);
    }

    @NonNull
    @Override
    public StoreFactory storeFactory() {
        return storeFactory;
    }

    @NonNull
    @Override
    public NetworkInfo networkInfo() {
        return networkInfo;
    }

    @NonNull
    @Override
    public RecordBuilders recordBuilders() {
        return recordBuilders;
    }

    @Override
    public Fees dispatchComputeFees(
            @NonNull final TransactionBody txBody,
            @NonNull final AccountID syntheticPayerId,
            @NonNull final ComputeDispatchFeesAsTopLevel computeDispatchFeesAsTopLevel) {
        final var bodyToDispatch = ensureTxnId(txBody);
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
                storeFactory.asReadOnly(),
                consensusNow));
    }

    @NonNull
    private TransactionBody ensureTxnId(final @NonNull TransactionBody txBody) {
        if (!txBody.hasTransactionID()) {
            // Legacy mono fee calculators frequently estimate an entity's lifetime using the epoch second of the
            // transaction id/ valid start as the current consensus time; ensure those will behave sensibly here
            return txBody.copyBuilder()
                    .transactionID(TransactionID.newBuilder()
                            .accountID(syntheticPayer)
                            .transactionValidStart(Timestamp.newBuilder()
                                    .seconds(consensusNow().getEpochSecond())
                                    .nanos(consensusNow().getNano())))
                    .build();
        }
        return txBody;
    }

    @NonNull
    @Override
    public <T> T dispatchPrecedingTransaction(
            @NonNull final TransactionBody childTxnBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> childCallback,
            final AccountID childSyntheticPayerId) {
        requireNonNull(childTxnBody, "childTxnBody must not be null");
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        requireNonNull(childSyntheticPayerId, "childSyntheticPayerId must not be null");

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
        requireNonNull(childTxBody, "childTxBody must not be null");
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        requireNonNull(childSyntheticPayerId, "childSyntheticPayerId must not be null");
        requireNonNull(childCategory, "childCategory must not be null");

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
        requireNonNull(childTxBody, "childTxBody must not be null");
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        requireNonNull(childSyntheticPayerId, "childSyntheticPayerId must not be null");
        requireNonNull(customizer, "customizer must not be null");

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
    public SavepointStack savepointStack() {
        return stack;
    }

    @Override
    public boolean shouldThrottleNOfUnscaled(final int n, @NonNull final HederaFunctionality function) {
        requireNonNull(function);
        return networkUtilizationManager.shouldThrottleNOfUnscaled(n, function, consensusNow);
    }

    @Override
    public boolean hasThrottleCapacityForChildTransactions() {
        var isAllowed = true;
        final var childRecords = currentDispatch.recordListBuilder().childRecordBuilders();
        @Nullable List<ThrottleUsageSnapshot> snapshotsIfNeeded = null;

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
                    snapshotsIfNeeded = networkUtilizationManager.getUsageSnapshots();
                }

                final var childTxInfo = TransactionInfo.from(
                        childTx,
                        childTxBody,
                        childTx.sigMapOrElse(SignatureMap.DEFAULT),
                        childTx.signedTransactionBytes(),
                        childTxFunctionality);
                final var shouldThrottleTxn = networkUtilizationManager.shouldThrottle(
                        childTxInfo, currentDispatch.stack().peek(), consensusNow);
                if (shouldThrottleTxn) {
                    isAllowed = false;
                }
            }
        }
        if (!isAllowed) {
            networkUtilizationManager.resetUsageThrottlesTo(snapshotsIfNeeded);
        }
        return isAllowed;
    }

    @NonNull
    @Override
    public Map<AccountID, Long> dispatchPaidRewards() {
        return dispatchPaidRewards == null ? emptyMap() : dispatchPaidRewards;
    }

    private <T> T dispatchForRecord(
            @NonNull final TransactionBody childTxBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> childVerifier,
            @NonNull final AccountID syntheticPayer,
            @NonNull final ExternalizedRecordCustomizer customizer,
            @NonNull final TransactionCategory category,
            @NonNull final SingleTransactionRecordBuilderImpl.ReversingBehavior reversingBehavior,
            final boolean commitStack) {
        final var childDispatch = childDispatchFactory.createChildDispatch(
                currentDispatch,
                childTxBody,
                childVerifier,
                syntheticPayer,
                category,
                childDispatchProvider,
                customizer,
                reversingBehavior);
        dispatchProcessor.processDispatch(childDispatch);
        if (commitStack) {
            stack.commitFullStack();
        }
        // This can be non-empty for SCHEDULED dispatches, if rewards are paid for the triggered transaction
        final var paidStakingRewards = childDispatch.recordBuilder().getPaidStakingRewards();
        if (!paidStakingRewards.isEmpty()) {
            if (dispatchPaidRewards == null) {
                dispatchPaidRewards = new LinkedHashMap<>();
            }
            paidStakingRewards.forEach(aa -> dispatchPaidRewards.put(aa.accountIDOrThrow(), aa.amount()));
        }
        return RecordBuildersImpl.castRecordBuilder(childDispatch.recordBuilder(), recordBuilderClass);
    }
}
