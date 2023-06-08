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

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.PRECEDING;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.records.RecordListBuilder;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.stack.Savepoint;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

/**
 * The default implementation of {@link HandleContext}.
 */
public class HandleContextImpl implements HandleContext {

    private final TransactionBody txBody;
    private final TransactionCategory category;
    private final SingleTransactionRecordBuilder recordBuilder;
    private final SavepointStackImpl stack;
    private final HandleContextVerifier verifier;
    private final RecordListBuilder recordListBuilder;
    private final TransactionChecker checker;
    private final TransactionDispatcher dispatcher;
    private final ServiceScopeLookup serviceScopeLookup;
    private final WritableStoreFactory writableStoreFactory;

    private ReadableStoreFactory readableStoreFactory;

    /**
     * Constructs a {@link HandleContextImpl}.
     *
     * @param txBody The {@link TransactionBody} of the transaction
     * @param category The {@link TransactionCategory} of the transaction (either user, preceding, or child)
     * @param recordBuilder The main {@link SingleTransactionRecordBuilder}
     * @param stack The {@link SavepointStackImpl} used to manage savepoints
     * @param verifier The {@link HandleContextVerifier} used to verify signatures and hollow accounts
     * @param recordListBuilder The {@link RecordListBuilder} used to build the record stream
     * @param checker The {@link TransactionChecker} used to check dispatched transaction
     * @param dispatcher The {@link TransactionDispatcher} used to dispatch child transactions
     * @param serviceScopeLookup The {@link ServiceScopeLookup} used to look up the scope of a service
     */
    public HandleContextImpl(
            @NonNull final TransactionBody txBody,
            @NonNull final TransactionCategory category,
            @NonNull final SingleTransactionRecordBuilder recordBuilder,
            @NonNull final SavepointStackImpl stack,
            @NonNull final HandleContextVerifier verifier,
            @NonNull final RecordListBuilder recordListBuilder,
            @NonNull final TransactionChecker checker,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final ServiceScopeLookup serviceScopeLookup) {
        this.txBody = requireNonNull(txBody, "txBody must not be null");
        this.category = requireNonNull(category, "category must not be null");
        this.recordBuilder = requireNonNull(recordBuilder, "recordBuilder must not be null");
        this.stack = requireNonNull(stack, "stack must not be null");
        this.verifier = requireNonNull(verifier, "verifier must not be null");
        this.recordListBuilder = requireNonNull(recordListBuilder, "recordListBuilder must not be null");
        this.checker = requireNonNull(checker, "checker must not be null");
        this.dispatcher = requireNonNull(dispatcher, "dispatcher must not be null");
        this.serviceScopeLookup = requireNonNull(serviceScopeLookup, "serviceScopeLookup must not be null");

        final var serviceScope = serviceScopeLookup.getServiceName(txBody);
        this.writableStoreFactory = new WritableStoreFactory(stack, serviceScope);
    }

    private Savepoint current() {
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

    @Override
    @NonNull
    public Configuration configuration() {
        return current().configuration();
    }

    @Override
    public long newEntityNum() {
        return current().newEntityNum();
    }

    @Override
    @NonNull
    public AttributeValidator attributeValidator() {
        return current().attributeValidator();
    }

    @Override
    @NonNull
    public ExpiryValidator expiryValidator() {
        return current().expiryValidator();
    }

    @Override
    @Nullable
    public SignatureVerification verificationFor(@NonNull final Key key) {
        requireNonNull(key, "key must not be null");
        return verifier.verificationFor(key);
    }

    @Override
    @Nullable
    public SignatureVerification verificationFor(@NonNull final Bytes evmAlias) {
        requireNonNull(evmAlias, "evmAlias must not be null");
        return verifier.verificationFor(evmAlias);
    }

    @Override
    @NonNull
    public <C> C readableStore(@NonNull final Class<C> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        if (readableStoreFactory == null) {
            readableStoreFactory = new ReadableStoreFactory(stack);
        }
        return readableStoreFactory.getStore(storeInterface);
    }

    @Override
    @NonNull
    public <C> C writableStore(@NonNull final Class<C> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        return writableStoreFactory.getStore(storeInterface);
    }

    @Override
    @NonNull
    public <T> T recordBuilder(@NonNull final Class<T> recordBuilderClass) {
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        return castRecordBuilder(recordBuilder, recordBuilderClass);
    }

    private static <T> T castRecordBuilder(
            @NonNull final SingleTransactionRecordBuilder recordBuilder, @NonNull final Class<T> recordBuilderClass) {
        if (!recordBuilderClass.isInstance(recordBuilder)) {
            throw new IllegalArgumentException("Not a valid record builder class");
        }
        return recordBuilderClass.cast(recordBuilder);
    }

    @Override
    @NonNull
    public <T> T dispatchPrecedingTransaction(
            @NonNull final TransactionBody txBody, @NonNull final Class<T> recordBuilderClass) {
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");

        if (category != TransactionCategory.USER) {
            throw new IllegalArgumentException("Only user-transactions can dispatch preceding transactions");
        }
        if (stack.depth() > 1) {
            throw new IllegalStateException(
                    "Cannot dispatch a preceding transaction when a savepoint has been created");
        }

        if (current().state().isModified()) {
            throw new IllegalStateException("Cannot dispatch a preceding transaction when the state has been modified");
        }

        // run the transaction
        final var precedingRecordBuilder = recordListBuilder.addPreceding(configuration());
        dispatch(txBody, PRECEDING, precedingRecordBuilder);

        return castRecordBuilder(precedingRecordBuilder, recordBuilderClass);
    }

    @Override
    @NonNull
    public <T> T dispatchChildTransaction(
            @NonNull final TransactionBody txBody, @NonNull final Class<T> recordBuilderClass) {
        final var childRecordBuilder = recordListBuilder.addChild(configuration());
        return dispatchChildTransaction(txBody, childRecordBuilder, recordBuilderClass);
    }

    @Override
    @NonNull
    public <T> T dispatchRemovableChildTransaction(
            @NonNull final TransactionBody txBody, @NonNull final Class<T> recordBuilderClass) {
        final var childRecordBuilder = recordListBuilder.addRemovableChild(configuration());
        return dispatchChildTransaction(txBody, childRecordBuilder, recordBuilderClass);
    }

    @NonNull
    private <T> T dispatchChildTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final SingleTransactionRecordBuilder childRecordBuilder,
            @NonNull final Class<T> recordBuilderClass) {
        if (category == PRECEDING) {
            throw new IllegalArgumentException("A preceding transaction cannot have child transactions");
        }

        // create a savepoint
        stack.createSavepoint();

        // run the child-transaction
        dispatch(txBody, CHILD, childRecordBuilder);

        // rollback if the child-transaction failed
        if (childRecordBuilder.status() != ResponseCodeEnum.OK) {
            stack.rollback();
        }

        return castRecordBuilder(childRecordBuilder, recordBuilderClass);
    }

    private void dispatch(
            @NonNull final TransactionBody txBody,
            @NonNull final TransactionCategory childCategory,
            @NonNull final SingleTransactionRecordBuilder childRecordBuilder) {
        try {
            checker.checkTransactionBody(txBody);
            dispatcher.dispatchPureChecks(txBody);
        } catch (PreCheckException e) {
            childRecordBuilder.status(e.responseCode());
            return;
        }

        final var childStack = new SavepointStackImpl(current().state(), configuration());
        final var childContext = new HandleContextImpl(
                txBody,
                childCategory,
                childRecordBuilder,
                childStack,
                verifier,
                recordListBuilder,
                checker,
                dispatcher,
                serviceScopeLookup);

        try {
            dispatcher.dispatchHandle(childContext);
            stack.configuration(childContext.configuration());
            childStack.commit();
        } catch (HandleException e) {
            childRecordBuilder.status(e.getStatus());
            recordListBuilder.revertChildRecordBuilders(recordBuilder);
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
    public <T> T addRemovableChildRecordBuilder(@NonNull final Class<T> recordBuilderClass) {
        final var result = recordListBuilder.addRemovableChild(configuration());
        return castRecordBuilder(result, recordBuilderClass);
    }

    @Override
    @NonNull
    public SavepointStack savepointStack() {
        return stack;
    }
}
