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

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleContext.SavepointStack;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.state.ReadonlyStatesWrapper;
import com.hedera.node.app.state.WrappedHederaState;
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
 * A stack of savepoints scoped to a dispatch. Each savepoint captures the state of the {@link HederaState} at the time
 * the savepoint was created and all the changes made to the state from the time savepoint is created. It also captures
 * all the stream item builders created in the savepoint.
 */
public class SavepointStackImpl implements SavepointStack, HederaState {
    private final HederaState root;
    private final Deque<Savepoint> stack = new ArrayDeque<>();
    private final Map<String, WritableStatesStack> writableStatesMap = new HashMap<>();
    private final SingleTransactionRecordBuilder baseRecordBuilder;
    // This is only set if this stack is root stack
    @Nullable
    private final BuilderSink builderSink;

    /**
     * Constructs the root {@link SavepointStackImpl} for the given state at the start of handling a user transaction.
     *
     * @param root                  the root state
     * @param maxBuildersBeforeUser the maximum number of preceding builders with available consensus times
     * @param maxBuildersAfterUser the maximum number of following builders with available consensus times
     * @return the root {@link SavepointStackImpl}
     */
    public static SavepointStackImpl newRootStack(
            @NonNull final HederaState root, final int maxBuildersBeforeUser, final int maxBuildersAfterUser) {
        return new SavepointStackImpl(
                root,
                maxBuildersBeforeUser,
                maxBuildersAfterUser,
                REVERSIBLE,
                USER,
                NOOP_EXTERNALIZED_RECORD_CUSTOMIZER);
    }

    /**
     * Constructs a new child {@link SavepointStackImpl} for the given state, where the child dispatch has the given
     * reversing behavior, transaction category, and record customizer.
     *
     * @param root the state on which the child dispatch i sbased
     * @param reversingBehavior the reversing behavior
     * @param category the transaction category
     * @param externalizedRecordCustomizer the record customizer
     * @return the child {@link SavepointStackImpl}
     */
    public static SavepointStackImpl newChildStack(
            @NonNull final SavepointStackImpl root,
            @NonNull final SingleTransactionRecordBuilder.ReversingBehavior reversingBehavior,
            @NonNull final HandleContext.TransactionCategory category,
            @NonNull final ExternalizedRecordCustomizer externalizedRecordCustomizer) {
        return new SavepointStackImpl(root, reversingBehavior, category, externalizedRecordCustomizer);
    }

    @VisibleForTesting
    public SavepointStackImpl(
            @NonNull final HederaState root, final int maxBuildersBeforeUser, final int maxBuildersAfterUser) {
        this(root, maxBuildersBeforeUser, maxBuildersAfterUser, REVERSIBLE, USER, NOOP_EXTERNALIZED_RECORD_CUSTOMIZER);
    }

    /**
     * Constructs a new {@link SavepointStackImpl} with the given root state.
     *
     * @param root                 the root state
     * @param maxBuildersAfterUser
     * @throws NullPointerException if {@code root} is {@code null}
     */
    private SavepointStackImpl(
            @NonNull final HederaState root,
            final int maxBuildersBeforeUser,
            final int maxBuildersAfterUser,
            final SingleTransactionRecordBuilder.ReversingBehavior reversingBehavior,
            final HandleContext.TransactionCategory category,
            final ExternalizedRecordCustomizer customizer) {
        this.root = requireNonNull(root, "root must not be null");
        builderSink = new BuilderSink(maxBuildersBeforeUser, maxBuildersAfterUser + 1);
        pushSavepoint(category);
        baseRecordBuilder = peek().createBuilder(reversingBehavior, category, customizer, true);
    }

    private SavepointStackImpl(
            @NonNull final SavepointStackImpl root,
            final SingleTransactionRecordBuilder.ReversingBehavior reversingBehavior,
            final HandleContext.TransactionCategory category,
            final ExternalizedRecordCustomizer customizer) {
        this.root = root;
        this.builderSink = null;
        pushSavepoint(category);
        baseRecordBuilder = peek().createBuilder(reversingBehavior, category, customizer, true);
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
        while (!stack.isEmpty()) {
            stack.pop().commit();
        }
        pushSavepoint(baseRecordBuilder.category());
    }

    /**
     * Rolls back all state changes captured in this stack.
     */
    public void rollbackFullStack() {
        while (!stack.isEmpty()) {
            stack.pop().rollback();
        }
        pushSavepoint(baseRecordBuilder.category());
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
    Savepoint peek() {
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
     * <p>
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
     * <p>
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

    private void pushSavepoint(final HandleContext.TransactionCategory category) {
        if (root instanceof SavepointStackImpl parentStack) {
            final var parentSavepoint = parentStack.peek();
            stack.push(new BaseSavepoint(new WrappedHederaState(root), parentSavepoint, category));
        } else {
            requireNonNull(builderSink);
            stack.push(new FirstSavepoint(
                    new WrappedHederaState(root),
                    builderSink.precedingCapacity(),
                    builderSink.followingCapacity(),
                    requireNonNull(builderSink)));
        }
    }

    public List<SingleTransactionRecordBuilder> recordBuilders() {
        return requireNonNull(builderSink).allBuilders();
    }

    public boolean hasMoreSystemRecords() {
        return requireNonNull(builderSink).precedingCapacity() > 0;
    }

    /**
     * Returns all following child records in the stack for use in end-of-EVM-transaction throttling.
     * <p>
     * To be removed on completion of HIP-993 and adoption of per-dispatch throttling.
     * @return the list of child records
     */
    @Deprecated
    public List<SingleTransactionRecordBuilder> getChildRecords() {
        final var childRecords = new ArrayList<SingleTransactionRecordBuilder>();
        for (var savePoint : stack) {
            for (var recordBuilder : ((AbstractSavepoint) savePoint).followingBuilders) {
                if (recordBuilder.category() == CHILD) {
                    childRecords.add(recordBuilder);
                }
            }
        }
        return childRecords;
    }

    public SingleTransactionRecordBuilder baseRecordBuilder() {
        return baseRecordBuilder;
    }

    public boolean hasChildOrPrecedingRecords() {
        if (builderSink != null && builderSink.hasOther(baseRecordBuilder)) {
            return true;
        }
        for (var savePoint : stack) {
            if (savePoint.hasOther(baseRecordBuilder)) {
                return true;
            }
        }
        return false;
    }

    public <T> void forEachChildRecord(@NonNull Class<T> recordBuilderClass, @NonNull Consumer<T> consumer) {
        if (builderSink != null) {
            builderSink.forEachOther(consumer, recordBuilderClass, baseRecordBuilder);
        }
        for (var savePoint : stack) {
            savePoint.forEachOther(consumer, recordBuilderClass, baseRecordBuilder);
        }
    }

    public HandleContext.TransactionCategory txnCategory() {
        return baseRecordBuilder.category();
    }

    public SingleTransactionRecordBuilder createBuilder(
            @NonNull SingleTransactionRecordBuilder.ReversingBehavior reversingBehavior,
            @NonNull HandleContext.TransactionCategory txnCategory,
            @NonNull ExternalizedRecordCustomizer customizer) {
        return peek().createBuilder(reversingBehavior, txnCategory, customizer, false);
    }
}
