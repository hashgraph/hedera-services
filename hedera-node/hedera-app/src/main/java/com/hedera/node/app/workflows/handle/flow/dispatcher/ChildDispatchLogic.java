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

package com.hedera.node.app.workflows.handle.flow.dispatcher;

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.signature.DelegateKeyVerifier;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.spi.workflows.ComputeDispatchFeesAsTopLevel;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.flow.dagger.components.ChildDispatchComponent;
import com.hedera.node.app.workflows.handle.flow.records.ChildRecordBuilderFactory;
import com.hedera.node.app.workflows.handle.flow.records.ChildTxnFactory;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class ChildDispatchLogic {
    private final ChildTxnFactory childTxnFactory;
    private final TransactionDispatcher dispatcher;
    private final NoOpKeyVerifier noOpKeyVerifier;
    private final ChildRecordBuilderFactory recordBuilderFactory;

    @Inject
    public ChildDispatchLogic(
            final ChildTxnFactory childTxnFactory,
            final TransactionDispatcher dispatcher,
            final NoOpKeyVerifier noOpKeyVerifier,
            final ChildRecordBuilderFactory recordBuilderFactory) {
        this.childTxnFactory = childTxnFactory;
        this.dispatcher = dispatcher;
        this.noOpKeyVerifier = noOpKeyVerifier;
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
        var result = ChildPreHandleResult.userError(checkUserError(txBody));

        if (result.userError() == OK) {
            result = dispatchPreHandle(parentDispatch, txBody, syntheticPayerId);
        }
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
                        result.requiredKeys(),
                        result.requiredHollowAccounts(),
                        result.userError(),
                        getKeyVerifier(callback));
    }

    private ChildPreHandleResult dispatchPreHandle(
            final @NonNull Dispatch parentDispatch,
            final @NonNull TransactionBody txBody,
            final @NonNull AccountID syntheticPayerId) {
        try {
            final var preHandleContext = new PreHandleContextImpl(
                    parentDispatch.readableStoreFactory(),
                    txBody,
                    syntheticPayerId,
                    parentDispatch.handleContext().configuration(),
                    dispatcher);
            dispatcher.dispatchPreHandle(preHandleContext);
            return new ChildPreHandleResult(
                    preHandleContext.requiredHollowAccounts(), preHandleContext.requiredNonPayerKeys(), OK);
        } catch (final PreCheckException e) {
            return new ChildPreHandleResult(Collections.emptySet(), Collections.emptySet(), e.responseCode());
        }
    }

    private KeyVerifier getKeyVerifier(@Nullable Predicate<Key> callback) {
        return callback == null ? noOpKeyVerifier : new DelegateKeyVerifier(callback);
    }

    @NonNull
    private static ComputeDispatchFeesAsTopLevel isScheduled(final HandleContext.TransactionCategory category) {
        return category == HandleContext.TransactionCategory.SCHEDULED
                ? ComputeDispatchFeesAsTopLevel.YES
                : ComputeDispatchFeesAsTopLevel.NO;
    }

    private ResponseCodeEnum checkUserError(TransactionBody txBody) {
        try {
            // Synthetic transaction bodies do not have transaction ids, node account
            // ids, and so on; hence we don't need to validate them with the checker
            dispatcher.dispatchPureChecks(txBody);
        } catch (final PreCheckException e) {
            return e.responseCode();
        }
        return OK;
    }

    private record ChildPreHandleResult(
            Set<Account> requiredHollowAccounts, Set<Key> requiredKeys, ResponseCodeEnum userError) {
        public static ChildPreHandleResult userError(ResponseCodeEnum userError) {
            return new ChildPreHandleResult(Collections.emptySet(), Collections.emptySet(), userError);
        }
    }
}
