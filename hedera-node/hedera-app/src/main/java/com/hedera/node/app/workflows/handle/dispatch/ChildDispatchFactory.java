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

package com.hedera.node.app.workflows.handle.dispatch;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.util.UnknownHederaFunctionality;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.AppKeyVerifier;
import com.hedera.node.app.signature.DelegateKeyVerifier;
import com.hedera.node.app.signature.impl.SignatureVerificationImpl;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.hedera.node.app.spi.throttle.ThrottleAdviser;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.Dispatch;
import com.hedera.node.app.workflows.handle.DispatchProcessor;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.function.Predicate;

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.util.HapiUtils.functionOf;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.PRE_HANDLE_FAILURE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.SO_FAR_SO_GOOD;
import static java.util.Objects.requireNonNull;

/**
 * A factory for constructing child dispatches.This also gets the pre-handle result for the child transaction,
 * and signature verifications for the child transaction.
 */
@Singleton
public class ChildDispatchFactory {
    private static final NoOpKeyVerifier NO_OP_KEY_VERIFIER = new NoOpKeyVerifier();

    private final TransactionDispatcher dispatcher;
    private final ChildRecordBuilderFactory recordBuilderFactory;
    private final Authorizer authorizer;
    private final NetworkInfo networkInfo;
    private final FeeManager feeManager;
    private final RecordCache recordCache;
    private final DispatchProcessor dispatchProcessor;
    private final BlockRecordManager blockRecordManager;
    private final ServiceScopeLookup serviceScopeLookup;
    private final StoreMetricsService storeMetricsService;
    private final ExchangeRateManager exchangeRateManager;
    private final NetworkUtilizationManager networkUtilizationManager;

    @Inject
    public ChildDispatchFactory(
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final ChildRecordBuilderFactory recordBuilderFactory,
            @NonNull final Authorizer authorizer,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final FeeManager feeManager,
            @NonNull final RecordCache recordCache,
            @NonNull final DispatchProcessor dispatchProcessor,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final ServiceScopeLookup serviceScopeLookup,
            @NonNull final StoreMetricsService storeMetricsService,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final NetworkUtilizationManager networkUtilizationManager) {
        this.dispatcher = requireNonNull(dispatcher);
        this.recordBuilderFactory = requireNonNull(recordBuilderFactory);
        this.authorizer = requireNonNull(authorizer);
        this.networkInfo = requireNonNull(networkInfo);
        this.feeManager = requireNonNull(feeManager);
        this.recordCache = requireNonNull(recordCache);
        this.dispatchProcessor = requireNonNull(dispatchProcessor);
        this.blockRecordManager = requireNonNull(blockRecordManager);
        this.serviceScopeLookup = requireNonNull(serviceScopeLookup);
        this.storeMetricsService = requireNonNull(storeMetricsService);
        this.exchangeRateManager = requireNonNull(exchangeRateManager);
        this.networkUtilizationManager = requireNonNull(networkUtilizationManager);
    }

    /**
     * Creates a child dispatch. This method computes the transaction info and initializes record builder for the child
     * transaction.
     *
     * @param txBody the transaction body
     * @param callback the key verifier for child dispatch
     * @param syntheticPayerId the synthetic payer id
     * @param category the transaction category
     * @param customizer the externalized record customizer
     * @param reversingBehavior the reversing behavior
     * @param recordListBuilder the record list builder
     * @param config the configuration
     * @param stack the savepoint stack
     * @param readableStoreFactory the readable store factory
     * @param creatorInfo the node info of the creator
     * @param platformState the platform state
     * @param topLevelFunction the top level functionality
     * @return the child dispatch
     */
    public Dispatch createChildDispatch(
            @NonNull final TransactionBody txBody,
            @Nullable final Predicate<Key> callback,
            @NonNull final AccountID syntheticPayerId,
            @NonNull final HandleContext.TransactionCategory category,
            @NonNull final ExternalizedRecordCustomizer customizer,
            @NonNull final SingleTransactionRecordBuilderImpl.ReversingBehavior reversingBehavior,
            @NonNull final RecordListBuilder recordListBuilder,
            @NonNull final Configuration config,
            @NonNull final SavepointStackImpl stack,
            @NonNull final ReadableStoreFactory readableStoreFactory,
            @NonNull final NodeInfo creatorInfo,
            @NonNull final PlatformState platformState,
            @NonNull final HederaFunctionality topLevelFunction,
            @NonNull final ThrottleAdviser throttleAdviser) {
        final var preHandleResult =
                dispatchPreHandleForChildTxn(txBody, syntheticPayerId, config, readableStoreFactory);
        final var childTxnInfo = getTxnInfoFrom(txBody);
        final var recordBuilder = recordBuilderFactory.recordBuilderFor(
                childTxnInfo, recordListBuilder, config, category, reversingBehavior, customizer);
        final var childStack = new SavepointStackImpl(stack.peek());
        return ChildDispatch.from(
                recordBuilder,
                childTxnInfo,
                syntheticPayerId,
                category,
                childStack,
                preHandleResult,
                getKeyVerifier(callback),
                recordBuilder.consensusNow(),
                creatorInfo,
                config,
                platformState,
                recordListBuilder,
                topLevelFunction,
                throttleAdviser,
                authorizer,
                networkInfo,
                feeManager,
                recordCache,
                dispatchProcessor,
                blockRecordManager,
                serviceScopeLookup,
                storeMetricsService,
                exchangeRateManager,
                this,
                dispatcher);
    }

    /**
     * Dispatches the pre-handle checks for the child transaction. This runs pureChecks and then dispatches pre-handle
     * for child transaction.
     *
     * @param txBody the transaction body
     * @param syntheticPayerId the synthetic payer id
     * @param config the configuration
     * @param readableStoreFactory the readable store factory
     * @return the pre-handle result
     */
    private PreHandleResult dispatchPreHandleForChildTxn(
            @NonNull final TransactionBody txBody,
            @NonNull final AccountID syntheticPayerId,
            @NonNull final Configuration config,
            @NonNull final ReadableStoreFactory readableStoreFactory) {
        try {
            dispatcher.dispatchPureChecks(txBody);
            final var preHandleContext =
                    new PreHandleContextImpl(readableStoreFactory, txBody, syntheticPayerId, config, dispatcher);
            dispatcher.dispatchPreHandle(preHandleContext);
            return new PreHandleResult(
                    null,
                    null,
                    SO_FAR_SO_GOOD,
                    OK,
                    null,
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
                    Collections.emptySet(),
                    null,
                    Collections.emptySet(),
                    null,
                    null,
                    0);
        }
    }

    /**
     * A {@link AppKeyVerifier} that always returns {@link SignatureVerificationImpl} with a
     * passed verification.
     */
    public static class NoOpKeyVerifier implements AppKeyVerifier {
        private static final SignatureVerification PASSED_VERIFICATION =
                new SignatureVerificationImpl(Key.DEFAULT, Bytes.EMPTY, true);

        @NonNull
        @Override
        public SignatureVerification verificationFor(@NonNull final Key key) {
            return PASSED_VERIFICATION;
        }

        @NonNull
        @Override
        public SignatureVerification verificationFor(
                @NonNull final Key key, @NonNull final VerificationAssistant callback) {
            return PASSED_VERIFICATION;
        }

        @NonNull
        @Override
        public SignatureVerification verificationFor(@NonNull final Bytes evmAlias) {
            return PASSED_VERIFICATION;
        }

        @Override
        public int numSignaturesVerified() {
            return 0;
        }
    }

    /**
     * Returns a {@link AppKeyVerifier} based on the callback. If the callback is null, then it returns a
     * {@link NoOpKeyVerifier}. Otherwise, it returns a {@link DelegateKeyVerifier} with the callback.
     * The callback is null if the signature verification is not required. This is the case for hollow account
     * completion and auto account creation.
     *
     * @param callback the callback
     * @return the key verifier
     */
    public static AppKeyVerifier getKeyVerifier(@Nullable Predicate<Key> callback) {
        return callback == null
                ? NO_OP_KEY_VERIFIER
                : new AppKeyVerifier() {
                    private final AppKeyVerifier verifier = new DelegateKeyVerifier(callback);

                    @NonNull
                    @Override
                    public SignatureVerification verificationFor(@NonNull final Key key) {
                        return callback.test(key) ? NoOpKeyVerifier.PASSED_VERIFICATION : verifier.verificationFor(key);
                    }

                    @NonNull
                    @Override
                    public SignatureVerification verificationFor(
                            @NonNull final Key key, @NonNull final VerificationAssistant callback) {
                        throw new UnsupportedOperationException("Should never be called!");
                    }

                    @NonNull
                    @Override
                    public SignatureVerification verificationFor(@NonNull final Bytes evmAlias) {
                        throw new UnsupportedOperationException("Should never be called!");
                    }

                    @Override
                    public int numSignaturesVerified() {
                        return 0;
                    }
                };
    }

    /**
     * Provides the transaction information for the given dispatched transaction body.
     *
     * @param txBody the transaction body
     * @return the transaction information
     */
    private TransactionInfo getTxnInfoFrom(TransactionBody txBody) {
        final var bodyBytes = TransactionBody.PROTOBUF.toBytes(txBody);
        final var signedTransaction =
                SignedTransaction.newBuilder().bodyBytes(bodyBytes).build();
        final var signedTransactionBytes = SignedTransaction.PROTOBUF.toBytes(signedTransaction);
        final var transaction = Transaction.newBuilder()
                .signedTransactionBytes(signedTransactionBytes)
                .build();
        // Since in the current systems the synthetic transactions need not have a transaction ID
        // Payer will be injected as synthetic payer in dagger subcomponent, since the payer could be different
        // for schedule dispatches. Also, there will not be signature verifications for synthetic transactions.
        // So these fields are set to default values and will not be used.
        return new TransactionInfo(
                transaction,
                txBody,
                TransactionID.DEFAULT,
                AccountID.DEFAULT,
                SignatureMap.DEFAULT,
                signedTransactionBytes,
                functionOfTxn(txBody));
    }

    /**
     * Provides the functionality of the transaction body.
     *
     * @param txBody the transaction body
     * @return the functionality
     */
    private static HederaFunctionality functionOfTxn(final TransactionBody txBody) {
        try {
            return functionOf(txBody);
        } catch (final UnknownHederaFunctionality e) {
            throw new IllegalArgumentException("Unknown Hedera Functionality", e);
        }
    }
}
