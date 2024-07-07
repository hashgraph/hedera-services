/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.stack;

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;
import static com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer.NOOP_EXTERNALIZED_RECORD_CUSTOMIZER;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.ReversingBehavior.REVERSIBLE;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleContext.SavepointStack;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.state.ReadonlyStatesWrapper;
import com.hedera.node.app.state.WrappedHederaState;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The default implementation of {@link SavepointStack}.
 * The {@link SavepointStackImpl} is a stack of {@link HederaState} instances that can be used to create savepoints.
 * Each savepoint captures the state of the {@link HederaState} at the time the savepoint was created and all the changes
 * made to the state from the time savepoint is created. It also captures all the records {@link SingleTransactionRecordBuilder}
 * created in the savepoint.
 * Currently, records are not used in the codebase. It will be used in future PRs.
 */
public class SavepointStackImpl implements SavepointStack, HederaState {
    private final HederaState root;
    private final Deque<AbstractSavePoint> stack = new ArrayDeque<>();
    private final Map<String, WritableStatesStack> writableStatesMap = new HashMap<>();
    private final int maxPreceding;
    private List<SingleTransactionRecordBuilder> recordBuilders;
    private final SingleTransactionRecordBuilderImpl baseRecordBuilder;

    /**
     * Constructs a new {@link SavepointStackImpl} with the given root state.
     *
     * @param root the root state
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public SavepointStackImpl(
            @NonNull final HederaState root,
            final int maxPreceding,
            final SingleTransactionRecordBuilder.ReversingBehavior reversingBehavior,
            final HandleContext.TransactionCategory category,
            final ExternalizedRecordCustomizer externalizedRecordCustomizer) {
        this.root = requireNonNull(root, "root must not be null");
        this.maxPreceding = maxPreceding;
        pushBaseSavepoint();
        baseRecordBuilder = recordBuilderFor(category, reversingBehavior, externalizedRecordCustomizer);
        baseRecordBuilder.setBaseRecordBuilder();
    }

    public SavepointStackImpl(@NonNull final HederaState root, final int maxPreceding) {
        this(root, maxPreceding, REVERSIBLE, USER, NOOP_EXTERNALIZED_RECORD_CUSTOMIZER);
    }

    @Override
    public void createSavepoint() {
        stack.push(peek().createFollowingSavePoint());
    }

    @Override
    public void commit() {
        if (stack.size() <= 1) {
            throw new IllegalStateException("The savepoint stack is empty");
        }
        stack.pop().commit();
    }

    @Override
    public void rollback() {
        if (stack.size() <= 1) {
            throw new IllegalStateException("The savepoint stack is empty");
        }
        stack.pop().rollback();
    }

    /**
     * Commits all state changes captured in this stack.
     */
    public void commitFullStack() {
        final var lastPopped = stack.peekLast();
        while (!stack.isEmpty()) {
            stack.pop().commit();
        }
        setRecordBuilders(lastPopped);
        pushBaseSavepoint();
    }

    /**
     * Rolls back all state changes captured in this stack.
     */
    public void rollbackFullStack() {
        final var lastPopped = stack.peekLast();
        while (!stack.isEmpty()) {
            stack.pop().rollback();
        }
        setRecordBuilders(lastPopped);
        pushBaseSavepoint();
    }

    @Override
    public int depth() {
        return stack.size();
    }

    /**
     * Returns the current {@link HederaState} without removing it from the stack.
     *
     * @return the current {@link HederaState}
     * @throws IllegalStateException if the stack has been committed already
     */
    @NonNull
    public AbstractSavePoint peek() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("The stack has already been committed");
        }
        return stack.peek();
    }

    /**
     * Returns the root {@link ReadableStates} for the given service name.
     *
     * @param serviceName the name of the service
     * @return the root {@link ReadableStates} for the given service name
     */
    @NonNull
    public ReadableStates rootStates(@NonNull final String serviceName) {
        return root.getReadableStates(serviceName);
    }

    /**
     * {@inheritDoc}
     *
     * The {@link ReadableStates} instances returned from this method are based on the {@link WritableStates} instances
     * for the same service name. This means that any modifications to the {@link WritableStates} will be reflected
     * in the {@link ReadableStates} instances returned from this method.
     * <p>
     * Unlike other {@link HederaState} implementations, the returned {@link ReadableStates} of this implementation
     * must only be used in the handle workflow.
     */
    @Override
    @NonNull
    public ReadableStates getReadableStates(@NonNull String serviceName) {
        return new ReadonlyStatesWrapper(getWritableStates(serviceName));
    }

    /**
     * {@inheritDoc}
     *
     * This method guarantees that the same {@link WritableStates} instance is returned for the same {@code serviceName}
     * to ensure all modifications to a {@link WritableStates} are kept together.
     */
    @Override
    @NonNull
    public WritableStates getWritableStates(@NonNull final String serviceName) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("The stack has already been committed");
        }
        return writableStatesMap.computeIfAbsent(serviceName, s -> new WritableStatesStack(this, s));
    }

    private void pushBaseSavepoint() {
        if (root instanceof SavepointStackImpl parentStack) {
            stack.push(new FollowingSavePoint(new WrappedHederaState(root), parentStack.peek()));
        } else {
            stack.push(new FirstSavePoint(new WrappedHederaState(root), maxPreceding));
        }
    }

    private void setRecordBuilders(final AbstractSavePoint lastPopped) {
        if (recordBuilders == null) {
            recordBuilders = requireNonNull(lastPopped).recordBuilders();
        } else {
            recordBuilders.addAll(requireNonNull(lastPopped).recordBuilders());
        }
    }

    public List<SingleTransactionRecordBuilder> recordBuilders() {
        return requireNonNull(recordBuilders);
    }

    public boolean hasMoreSystemRecords() {
        return recordBuilders == null || recordBuilders.size() < maxPreceding;
    }

    @Deprecated
    // TODO : Will be removed soon
    public List<SingleTransactionRecordBuilderImpl> getChildRecords() {
        final var childRecords = new ArrayList<SingleTransactionRecordBuilderImpl>();
        for (var savePoint : stack) {
            for (var recordBuilder : savePoint.recordBuilders()) {
                if (recordBuilder.category() == CHILD) {
                    childRecords.add((SingleTransactionRecordBuilderImpl) recordBuilder);
                }
            }
        }
        return childRecords;
    }

    public SingleTransactionRecordBuilderImpl baseRecordBuilder() {
        return baseRecordBuilder;
    }

    public boolean hasChildOrPrecedingRecords() {
        for (var savePoint : stack) {
            for (var recordBuilder : savePoint.recordBuilders()) {
                if (recordBuilder != baseRecordBuilder) {
                    return true;
                }
            }
        }
        return false;
    }

    public <T> void forEachChildRecord(@NonNull Class<T> recordBuilderClass, @NonNull Consumer<T> consumer) {
        for (var savePoint : stack) {
            for (var recordBuilder : savePoint.recordBuilders()) {
                if (recordBuilder != baseRecordBuilder) {
                    consumer.accept(recordBuilderClass.cast(recordBuilder));
                }
            }
        }
    }

    private SingleTransactionRecordBuilderImpl recordBuilderFor(
            @NonNull final HandleContext.TransactionCategory category,
            @NonNull final SingleTransactionRecordBuilderImpl.ReversingBehavior reversingBehavior,
            @Nullable ExternalizedRecordCustomizer customizer) {
        customizer = customizer == null ? NOOP_EXTERNALIZED_RECORD_CUSTOMIZER : customizer;
        return switch (category) {
            case PRECEDING -> switch (reversingBehavior) {
                case REMOVABLE, REVERSIBLE, IRREVERSIBLE -> peek().addRecord(reversingBehavior, category, customizer);
            };
            case CHILD -> switch (reversingBehavior) {
                case REMOVABLE, REVERSIBLE -> peek().addRecord(reversingBehavior, category, customizer);
                case IRREVERSIBLE -> throw new IllegalArgumentException("CHILD cannot be IRREVERSIBLE");
            };
            case SCHEDULED -> peek().addRecord(REVERSIBLE, category, customizer);
            case USER -> throw new IllegalArgumentException("USER not a valid child category");
        };
    }

    public HandleContext.TransactionCategory txnCategory() {
        return baseRecordBuilder.category();
    }
}
