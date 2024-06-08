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

package com.hedera.node.app.workflows.handle.flow.dispatch;

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
import com.hedera.node.app.workflows.handle.flow.dispatch.child.ChildDispatchComponent;
import com.hedera.node.app.workflows.handle.flow.records.ChildRecordBuilderFactory;
import com.hedera.node.app.workflows.handle.flow.records.ChildTxnFactory;
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

@Singleton
public class ChildDispatchLogic {
    private static final NoOpKeyVerifier NO_OP_KEY_VERIFIER = new NoOpKeyVerifier();

    private final ChildTxnFactory childTxnFactory;
    private final TransactionDispatcher dispatcher;
    private final ChildRecordBuilderFactory recordBuilderFactory;

    @Inject
    public ChildDispatchLogic(
            final ChildTxnFactory childTxnFactory,
            final TransactionDispatcher dispatcher,
            final ChildRecordBuilderFactory recordBuilderFactory) {
        this.childTxnFactory = childTxnFactory;
        this.dispatcher = dispatcher;
        this.recordBuilderFactory = recordBuilderFactory;
    }

    public Dispatch createChildDispatch(
            @NonNull final Dispatch parentDispatch,
            @NonNull final TransactionBody txBody,
            @Nullable final Predicate<Key> callback,
            @NonNull final AccountID syntheticPayerId,
            @NonNull final HandleContext.TransactionCategory category,
            @NonNull final Provider<ChildDispatchComponent.Factory> childDispatchFactory,
            @NonNull final ExternalizedRecordCustomizer customizer,
            @NonNull final SingleTransactionRecordBuilderImpl.ReversingBehavior reversingBehavior) {
        final var preHandleResult = dispatchPreHandle(parentDispatch, txBody, syntheticPayerId);
        final var childTxnInfo = childTxnFactory.getTxnInfoFrom(txBody);
        final var recordBuilder = recordBuilderFactory.recordBuilderFor(
                childTxnInfo,
                parentDispatch.recordListBuilder(),
                parentDispatch.handleContext().configuration(),
                category,
                reversingBehavior,
                customizer);

        return childDispatchFactory
                .get()
                .create(
                        recordBuilder,
                        childTxnInfo,
                        isScheduled(category),
                        syntheticPayerId,
                        category,
                        new SavepointStackImpl(parentDispatch.stack().peek()),
                        preHandleResult,
                        getKeyVerifier(callback));
    }

    private PreHandleResult dispatchPreHandle(
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

    private KeyVerifier getKeyVerifier(@Nullable Predicate<Key> callback) {
        return callback == null ? NO_OP_KEY_VERIFIER : new DelegateKeyVerifier(callback);
    }

    @NonNull
    private static ComputeDispatchFeesAsTopLevel isScheduled(final HandleContext.TransactionCategory category) {
        return category == HandleContext.TransactionCategory.SCHEDULED
                ? ComputeDispatchFeesAsTopLevel.YES
                : ComputeDispatchFeesAsTopLevel.NO;
    }

    private static class NoOpKeyVerifier implements KeyVerifier {
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
}
