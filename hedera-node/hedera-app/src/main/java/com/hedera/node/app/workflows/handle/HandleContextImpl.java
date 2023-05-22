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
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.RecordCache;
import com.hedera.node.app.state.WrappedHederaState;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.stack.ReadableStatesStack;
import com.hedera.node.app.workflows.handle.stack.Savepoint;
import com.hedera.node.app.workflows.handle.stack.WritableStatesStack;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * The default implementation of {@link HandleContext}.
 */
public class HandleContextImpl implements HandleContext {

    private final Instant consensusNow;
    private final TransactionBody txBody;
    private final TransactionCategory category;
    private final SingleTransactionRecordBuilder recordBuilder;
    private final SavepointStackImpl stack;
    private final WritableStoreFactory writableStoreFactory;
    private final Map<Key, SignatureVerification> keyVerifications;
    private final RecordListBuilder recordListBuilder;
    private final HandleContextService service;

    private ReadableStoreFactory readableStoreFactory;

    /**
     * Constructs a {@link HandleContextImpl}.
     *
     * @param rootState The {@link HederaState} that forms the root of the state changes
     * @param txBody The {@link TransactionBody} of the transaction
     * @param category The {@link TransactionCategory} of the transaction (either user, preceding, or child)
     * @param recordBuilder The main {@link SingleTransactionRecordBuilder}
     * @param keyVerifications The {@link SignatureVerification}s of the transaction
     * @param recordListBuilder The {@link RecordListBuilder} used to build the record stream
     * @param service A reference to {@link HandleContextService} for more complex operations
     */
    public HandleContextImpl(
            @NonNull final HederaState rootState,
            @NonNull final TransactionBody txBody,
            @NonNull final TransactionCategory category,
            @NonNull final SingleTransactionRecordBuilder recordBuilder,
            @NonNull final Map<Key, SignatureVerification> keyVerifications,
            @NonNull final RecordListBuilder recordListBuilder,
            @NonNull final HandleContextService service) {
        this.txBody = requireNonNull(txBody, "txBody must not be null");
        this.category = requireNonNull(category, "category must not be null");
        this.recordBuilder = requireNonNull(recordBuilder, "recordBuilder must not be null");
        this.keyVerifications = requireNonNull(keyVerifications, "keyVerifications must not be null");
        this.recordListBuilder = requireNonNull(recordListBuilder, "recordListBuilder must not be null");
        this.service = requireNonNull(service, "service must not be null");

        this.consensusNow = recordBuilder.consensusNow();
        this.stack = new SavepointStackImpl(rootState);
        final var serviceScope = service.getServiceScope(txBody);
        this.writableStoreFactory = new WritableStoreFactory(stack, serviceScope);
    }

    private Savepoint current() {
        return stack.peek();
    }

    @Override
    @NonNull
    public Instant consensusNow() {
        return consensusNow;
    }

    @Override
    @NonNull
    public TransactionBody body() {
        return txBody;
    }

    @Override
    @NonNull
    public TransactionCategory category() {
        return category;
    }

    @Override
    @NonNull
    public Configuration configuration() {
        return current().config();
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
        return keyVerifications.get(key);
    }

    @Override
    @Nullable
    public SignatureVerification verificationFor(@NonNull final Bytes evmAlias) {
        requireNonNull(evmAlias, "evmAlias must not be null");
        // TODO: This code is shared with PreHandleResult and should probably be moved to a shared place
        if (evmAlias.length() == 20) {
            for (final var result : keyVerifications.values()) {
                final var account = result.evmAlias();
                if (account != null && evmAlias.matchesPrefix(account)) {
                    return result;
                }
            }
        }
        return null;
    }

    @Override
    @NonNull
    public <C> C readableStore(@NonNull Class<C> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        if (readableStoreFactory == null) {
            readableStoreFactory = new ReadableStoreFactory(stack);
        }
        return readableStoreFactory.getStore(storeInterface);
    }

    @Override
    @NonNull
    public <C> C writableStore(@NonNull Class<C> storeInterface) {
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
                    "Cannot dispatch a preceding transaction when child transactions have been dispatched");
        }

        final var state = currentState();
        if (state.isModified()) {
            throw new IllegalStateException("Cannot dispatch a preceding transaction when the state has been modified");
        }

        // run the transaction
        final var precedingRecordBuilder = recordListBuilder.addPreceding(configuration());
        final var result =
                service.dispatch(txBody, PRECEDING, state, keyVerifications, recordListBuilder, precedingRecordBuilder);
        return castRecordBuilder(result, recordBuilderClass);
    }

    @Override
    @NonNull
    public <T> T dispatchChildTransaction(
            @NonNull final TransactionBody txBody, @NonNull final Class<T> recordBuilderClass) {
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");

        if (category == PRECEDING) {
            throw new IllegalArgumentException("A preceding transaction cannot have child transactions");
        }

        // create a savepoint
        stack.createSavepoint();

        // run the child-transaction
        final var childRecordBuilder = recordListBuilder.addChild(configuration());
        final var result = service.dispatch(
                txBody, CHILD, currentState(), keyVerifications, recordListBuilder, childRecordBuilder);

        // rollback if the child-transaction failed
        if (result.status() != ResponseCodeEnum.OK) {
            stack.rollback();
        }

        return castRecordBuilder(result, recordBuilderClass);
    }

    @Override
    @NonNull
    public <T> T dispatchRemovableChildTransaction(
            @NonNull TransactionBody txBody,
            @NonNull Class<T> recordBuilderClass) {
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");

        if (category == PRECEDING) {
            throw new IllegalArgumentException("A preceding transaction cannot have child transactions");
        }

        // create a savepoint
        stack.createSavepoint();

        // run the child-transaction
        final var childRecordBuilder = recordListBuilder.addRemovableChild(configuration());
        final var result = service.dispatch(
                txBody, CHILD, currentState(), keyVerifications, recordListBuilder, childRecordBuilder);

        // rollback if the child-transaction failed
        if (result.status() != ResponseCodeEnum.OK) {
            stack.rollback();
        }

        return castRecordBuilder(result, recordBuilderClass);
    }

    @NonNull
    @Override
    public <T> T addChildRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        final var result = recordListBuilder.addChild(configuration());
        return castRecordBuilder(result, recordBuilderClass);
    }

    @NonNull
    @Override
    public <T> T addRemovableChildRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        final var result = recordListBuilder.addRemovableChild(configuration());
        return castRecordBuilder(result, recordBuilderClass);
    }

    @NonNull
    @Override
    public SavepointStack savepointStack() {
        return stack;
    }

    public void commitStateChanges() {
        stack.commit();
    }

    public WrappedHederaState currentState() {
        return current().state();
    }

    public class SavepointStackImpl implements SavepointStack, HederaState {

        private final Deque<Savepoint> stack = new ArrayDeque<>();
        private final Map<String, WritableStatesStack> writableStatesMap = new HashMap<>();

        public SavepointStackImpl(@NonNull final HederaState root) {
            setupSavepoint(root);
        }

        private void setupSavepoint(@NonNull HederaState state) {
            final var newState = new WrappedHederaState(state);
            final var savepoint = new Savepoint(newState, configuration());
            stack.push(savepoint);
        }

        @Override
        public void createSavepoint() {
            setupSavepoint(peek().state());
        }

        @Override
        public void rollback(final int level) {
            if (stack.size() <= level) {
                throw new IllegalStateException("The transaction stack does not contain enough elements");
            }
            for (int i = 0; i < level; i++) {
                stack.pop();
            }
        }

        @Override
        public int depth() {
            return stack.size();
        }

        @NonNull
        public Savepoint peek() {
            if (stack.isEmpty()) {
                throw new IllegalStateException("The stack has already been committed");
            }
            return stack.peek();
        }

        public void commit() {
            while (!stack.isEmpty()) {
                stack.pop().state().commit();
            }
        }

        @Override
        @NonNull
        public ReadableStates createReadableStates(@NonNull final String serviceName) {
            return new ReadableStatesStack(this, serviceName);
        }

        @Override
        @NonNull
        public WritableStates createWritableStates(@NonNull String serviceName) {
            return writableStatesMap.computeIfAbsent(serviceName, s -> new WritableStatesStack(this, s));
        }

        @Override
        @NonNull
        public RecordCache getRecordCache() {
            throw new UnsupportedOperationException();
        }
    }

}
