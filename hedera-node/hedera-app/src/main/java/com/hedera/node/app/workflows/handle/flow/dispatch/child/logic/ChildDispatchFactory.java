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

package com.hedera.node.app.workflows.handle.flow.dispatch.child.logic;

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.PRE_HANDLE_FAILURE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.SO_FAR_SO_GOOD;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.signature.DelegateKeyVerifier;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.signature.impl.SignatureVerificationImpl;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.hedera.node.app.spi.workflows.ComputeDispatchFeesAsTopLevel;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.flow.dispatch.Dispatch;
import com.hedera.node.app.workflows.handle.flow.dispatch.child.ChildDispatchComponent;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * A factory for constructing child dispatches.This also gets the pre-handle result for the child transaction,
 * and signature verifications for the child transaction.
 */
@Singleton
public class ChildDispatchFactory {
    private static final NoOpKeyVerifier NO_OP_KEY_VERIFIER = new NoOpKeyVerifier();

    private final ChildTxnInfoFactory childTxnInfoFactory;
    private final TransactionDispatcher dispatcher;
    private final ChildRecordBuilderFactory recordBuilderFactory;

    @Inject
    public ChildDispatchFactory(
            final ChildTxnInfoFactory childTxnInfoFactory,
            final TransactionDispatcher dispatcher,
            final ChildRecordBuilderFactory recordBuilderFactory) {
        this.childTxnInfoFactory = childTxnInfoFactory;
        this.dispatcher = dispatcher;
        this.recordBuilderFactory = recordBuilderFactory;
    }

    /**
     * Creates a child dispatch. This method computes the transaction info and initializes record builder for the child
     * transaction.
     * @param parentDispatch the parent dispatch
     * @param txBody the transaction body
     * @param callback the key verifier for child dispatch
     * @param syntheticPayerId  the synthetic payer id
     * @param category the transaction category
     * @param childDispatchFactory the child dispatch factory
     * @param customizer the externalized record customizer
     * @param reversingBehavior the reversing behavior
     * @return the child dispatch
     */
    public Dispatch createChildDispatch(
            @NonNull final Dispatch parentDispatch,
            @NonNull final TransactionBody txBody,
            @Nullable final Predicate<Key> callback,
            @NonNull final AccountID syntheticPayerId,
            @NonNull final HandleContext.TransactionCategory category,
            @NonNull final Provider<ChildDispatchComponent.Factory> childDispatchFactory,
            @NonNull final ExternalizedRecordCustomizer customizer,
            @NonNull final SingleTransactionRecordBuilderImpl.ReversingBehavior reversingBehavior) {
        final var preHandleResult = dispatchPreHandleForChildTxn(parentDispatch, txBody, syntheticPayerId);
        final var childTxnInfo = childTxnInfoFactory.getTxnInfoFrom(txBody);
        final var recordBuilder = recordBuilderFactory.recordBuilderFor(
                childTxnInfo,
                parentDispatch.recordListBuilder(),
                parentDispatch.handleContext().configuration(),
                category,
                reversingBehavior,
                customizer);
        final var childStack = new SavepointStackImpl(parentDispatch.stack().peek());
        return childDispatchFactory
                .get()
                .create(
                        recordBuilder,
                        childTxnInfo,
                        dispatchFeesModeFor(category),
                        syntheticPayerId,
                        category,
                        childStack,
                        preHandleResult,
                        getKeyVerifier(callback));
    }

    /**
     * Dispatches the pre-handle checks for the child transaction. This runs pureChecks and then dispatches pre-handle
     * for child transaction.
     * @param parentDispatch the parent dispatch
     * @param txBody the transaction body
     * @param syntheticPayerId the synthetic payer id
     * @return the pre-handle result
     */
    private PreHandleResult dispatchPreHandleForChildTxn(
            final @NonNull Dispatch parentDispatch,
            final @NonNull TransactionBody txBody,
            final @NonNull AccountID syntheticPayerId) {
        try {
            dispatcher.dispatchPureChecks(txBody);
            final var preHandleContext = new PreHandleContextImpl(
                    parentDispatch.readableStoreFactory(),
                    txBody,
                    syntheticPayerId,
                    parentDispatch.handleContext().configuration(),
                    dispatcher);
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
     * Returns whether the transaction is scheduled or not.
     * @param category the transaction category
     * @return the compute dispatch fees as top level
     */
    @NonNull
    private static ComputeDispatchFeesAsTopLevel dispatchFeesModeFor(
            @NonNull final HandleContext.TransactionCategory category) {
        return category == HandleContext.TransactionCategory.SCHEDULED
                ? ComputeDispatchFeesAsTopLevel.YES
                : ComputeDispatchFeesAsTopLevel.NO;
    }

    /**
     * A {@link KeyVerifier} that always returns {@link SignatureVerificationImpl} with a
     * passed verification.
     */
    static class NoOpKeyVerifier implements KeyVerifier {
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
     * Returns a {@link KeyVerifier} based on the callback. If the callback is null, then it returns a
     * {@link NoOpKeyVerifier}. Otherwise, it returns a {@link DelegateKeyVerifier} with the callback.
     * The callback is null if the signature verification is not required. This is the case for hollow account
     * completion and auto account creation.
     * @param callback the callback
     * @return the key verifier
     */
    static KeyVerifier getKeyVerifier(@Nullable Predicate<Key> callback) {
        return callback == null
                ? NO_OP_KEY_VERIFIER
                : new KeyVerifier() {
                    private final KeyVerifier verifier = new DelegateKeyVerifier(callback);

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
}
