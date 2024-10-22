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

package com.hedera.node.app.workflows.handle;

import static com.hedera.hapi.node.base.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;
import static com.hedera.hapi.util.HapiUtils.functionOf;
import static com.hedera.node.app.workflows.handle.stack.SavepointStackImpl.castBuilder;
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
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.util.UnknownHederaFunctionality;
import com.hedera.node.app.fees.ChildFeeContextImpl;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeAccumulator;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.signature.AppKeyVerifier;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.FeeCalculatorFactory;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.fees.ResourcePriceCalculator;
import com.hedera.node.app.spi.ids.EntityNumGenerator;
import com.hedera.node.app.spi.key.KeyVerifier;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.throttle.ThrottleAdviser;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.ComputeDispatchFeesAsTopLevel;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.TransactionKeys;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.store.StoreFactoryImpl;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.dispatch.ChildDispatchFactory;
import com.hedera.node.app.workflows.handle.record.RecordStreamBuilder;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.handle.validation.AttributeValidatorImpl;
import com.hedera.node.app.workflows.handle.validation.ExpiryValidatorImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The {@link HandleContext} implementation.
 */
public class DispatchHandleContext implements HandleContext, FeeContext {
    private final Instant consensusNow;
    private final NodeInfo creatorInfo;
    private final TransactionInfo txnInfo;
    private final Configuration config;
    private final Authorizer authorizer;
    private final BlockRecordInfo blockRecordInfo;
    private final ResourcePriceCalculator resourcePriceCalculator;
    private final FeeManager feeManager;
    private final StoreFactoryImpl storeFactory;
    private final AccountID payerId;
    private final AppKeyVerifier verifier;
    private final HederaFunctionality topLevelFunction;
    private final Key payerKey;
    private final ExchangeRateManager exchangeRateManager;
    private final SavepointStackImpl stack;
    private final EntityNumGenerator entityNumGenerator;
    private final AttributeValidator attributeValidator;
    private final ExpiryValidator expiryValidator;
    private final TransactionDispatcher dispatcher;
    private final NetworkInfo networkInfo;
    private final ChildDispatchFactory childDispatchFactory;
    private final DispatchProcessor dispatchProcessor;
    private final ThrottleAdviser throttleAdviser;
    private final FeeAccumulator feeAccumulator;
    private Map<AccountID, Long> dispatchPaidRewards;

    public DispatchHandleContext(
            @NonNull final Instant consensusNow,
            @NonNull final NodeInfo creatorInfo,
            @NonNull final TransactionInfo transactionInfo,
            @NonNull final Configuration config,
            @NonNull final Authorizer authorizer,
            @NonNull final BlockRecordInfo blockRecordInfo,
            @NonNull final ResourcePriceCalculator resourcePriceCalculator,
            @NonNull final FeeManager feeManager,
            @NonNull final StoreFactoryImpl storeFactory,
            @NonNull final AccountID payerId,
            @NonNull final AppKeyVerifier verifier,
            @NonNull final HederaFunctionality topLevelFunction,
            @NonNull final Key payerKey,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final SavepointStackImpl stack,
            @NonNull final EntityNumGenerator entityNumGenerator,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final ChildDispatchFactory childDispatchLogic,
            @NonNull final DispatchProcessor dispatchProcessor,
            @NonNull final ThrottleAdviser throttleAdviser,
            @NonNull final FeeAccumulator feeAccumulator) {
        this.consensusNow = requireNonNull(consensusNow);
        this.creatorInfo = requireNonNull(creatorInfo);
        this.txnInfo = requireNonNull(transactionInfo);
        this.config = requireNonNull(config);
        this.authorizer = requireNonNull(authorizer);
        this.blockRecordInfo = requireNonNull(blockRecordInfo);
        this.resourcePriceCalculator = requireNonNull(resourcePriceCalculator);
        this.feeManager = requireNonNull(feeManager);
        this.storeFactory = requireNonNull(storeFactory);
        this.payerId = requireNonNull(payerId);
        this.verifier = requireNonNull(verifier);
        this.topLevelFunction = requireNonNull(topLevelFunction);
        this.payerKey = requireNonNull(payerKey);
        this.exchangeRateManager = requireNonNull(exchangeRateManager);
        this.stack = requireNonNull(stack);
        this.entityNumGenerator = requireNonNull(entityNumGenerator);
        this.childDispatchFactory = requireNonNull(childDispatchLogic);
        this.dispatchProcessor = requireNonNull(dispatchProcessor);
        this.throttleAdviser = requireNonNull(throttleAdviser);
        this.feeAccumulator = requireNonNull(feeAccumulator);
        this.attributeValidator = new AttributeValidatorImpl(this);
        this.expiryValidator = new ExpiryValidatorImpl(this);
        this.dispatcher = requireNonNull(dispatcher);
        this.networkInfo = requireNonNull(networkInfo);
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
        return payerId;
    }

    @Override
    public boolean tryToChargePayer(final long amount) {
        return feeAccumulator.chargeNetworkFee(payerId, amount);
    }

    @NonNull
    @Override
    public Configuration configuration() {
        return config;
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
        return blockRecordInfo;
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
        return exchangeRateManager.exchangeRateInfo(stack);
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
            throw new PreCheckException(UNRESOLVABLE_REQUIRED_SIGNERS);
        }
        return nestedContext;
    }

    @NonNull
    @Override
    public KeyVerifier keyVerifier() {
        return verifier;
    }

    @Override
    public SystemPrivilege hasPrivilegedAuthorization() {
        return authorizer.hasPrivilegedAuthorization(payerId, txnInfo.functionality(), txnInfo.txBody());
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
                            .accountID(payerId)
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
            @NonNull final AccountID childSyntheticPayerId) {
        requireNonNull(childTxnBody, "childTxnBody must not be null");
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        requireNonNull(childSyntheticPayerId, "childSyntheticPayerId must not be null");

        return dispatchForRecord(
                childTxnBody,
                recordBuilderClass,
                childCallback,
                childSyntheticPayerId,
                ExternalizedRecordCustomizer.NOOP_RECORD_CUSTOMIZER,
                TransactionCategory.PRECEDING,
                StreamBuilder.ReversingBehavior.IRREVERSIBLE,
                true,
                ConsensusThrottling.ON);
    }

    @NonNull
    @Override
    public <T> T dispatchRemovablePrecedingTransaction(
            @NonNull final TransactionBody childTxBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> childCallback,
            final AccountID childSyntheticPayer,
            @NonNull final ConsensusThrottling consensusThrottling) {
        return dispatchForRecord(
                childTxBody,
                recordBuilderClass,
                childCallback,
                childSyntheticPayer,
                ExternalizedRecordCustomizer.NOOP_RECORD_CUSTOMIZER,
                TransactionCategory.PRECEDING,
                StreamBuilder.ReversingBehavior.REMOVABLE,
                false,
                consensusThrottling);
    }

    @NonNull
    @Override
    public <T> T dispatchChildTransaction(
            @NonNull final TransactionBody childTxBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> childCallback,
            @NonNull final AccountID childSyntheticPayerId,
            @NonNull final TransactionCategory childCategory,
            @NonNull final ConsensusThrottling consensusThrottling) {
        requireNonNull(childTxBody, "childTxBody must not be null");
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        requireNonNull(childSyntheticPayerId, "childSyntheticPayerId must not be null");
        requireNonNull(childCategory, "childCategory must not be null");

        return dispatchForRecord(
                childTxBody,
                recordBuilderClass,
                childCallback,
                childSyntheticPayerId,
                ExternalizedRecordCustomizer.NOOP_RECORD_CUSTOMIZER,
                childCategory,
                StreamBuilder.ReversingBehavior.REVERSIBLE,
                false,
                consensusThrottling);
    }

    @NonNull
    @Override
    public <T> T dispatchRemovableChildTransaction(
            @NonNull final TransactionBody childTxBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> childCallback,
            @NonNull final AccountID childSyntheticPayerId,
            @NonNull final ExternalizedRecordCustomizer customizer,
            @NonNull final ConsensusThrottling throttleStrategy) {
        requireNonNull(childTxBody, "childTxBody must not be null");
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        requireNonNull(childSyntheticPayerId, "childSyntheticPayerId must not be null");
        requireNonNull(customizer, "customizer must not be null");
        requireNonNull(throttleStrategy, "throttleStrategy must not be null");

        return dispatchForRecord(
                childTxBody,
                recordBuilderClass,
                childCallback,
                childSyntheticPayerId,
                customizer,
                TransactionCategory.CHILD,
                StreamBuilder.ReversingBehavior.REMOVABLE,
                false,
                throttleStrategy);
    }

    @NonNull
    @Override
    public SavepointStack savepointStack() {
        return stack;
    }

    @NonNull
    @Override
    public ThrottleAdviser throttleAdviser() {
        return throttleAdviser;
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
            @NonNull final RecordStreamBuilder.ReversingBehavior reversingBehavior,
            final boolean commitStack,
            @NonNull final ConsensusThrottling throttleStrategy) {
        final var childDispatch = childDispatchFactory.createChildDispatch(
                childTxBody,
                childVerifier,
                syntheticPayer,
                category,
                customizer,
                reversingBehavior,
                config,
                stack,
                storeFactory.asReadOnly(),
                creatorInfo,
                topLevelFunction,
                throttleAdviser,
                consensusNow,
                blockRecordInfo,
                throttleStrategy);
        dispatchProcessor.processDispatch(childDispatch);
        if (commitStack) {
            stack.commitTransaction(childDispatch.recordBuilder());
        }
        // This can be non-empty for SCHEDULED dispatches, if rewards are paid for the triggered transaction
        final var paidStakingRewards = childDispatch.recordBuilder().getPaidStakingRewards();
        if (!paidStakingRewards.isEmpty()) {
            if (dispatchPaidRewards == null) {
                dispatchPaidRewards = new LinkedHashMap<>();
            }
            paidStakingRewards.forEach(aa -> dispatchPaidRewards.put(aa.accountIDOrThrow(), aa.amount()));
        }
        return castBuilder(childDispatch.recordBuilder(), recordBuilderClass);
    }
}
