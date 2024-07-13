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
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.PRECEDING;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;
import static com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer.NOOP_EXTERNALIZED_RECORD_CUSTOMIZER;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.ReversingBehavior.IRREVERSIBLE;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.ReversingBehavior.REMOVABLE;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.ReversingBehavior.REVERSIBLE;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleContext.SavepointStack;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.state.ReadonlyStatesWrapper;
import com.hedera.node.app.state.WrappedHederaState;
import com.hedera.node.app.workflows.handle.stack.savepoints.FirstChildSavepoint;
import com.hedera.node.app.workflows.handle.stack.savepoints.FirstRootSavepoint;
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
 * the savepoint was created and all the changes made to the state from the time savepoint was created, along with all
 * the stream builders created in the savepoint.
 */
public class SavepointStackImpl implements SavepointStack, HederaState {
    private final HederaState root;
    private final Deque<Savepoint> stack = new ArrayDeque<>();
    private final Map<String, WritableStatesStack> writableStatesMap = new HashMap<>();
    private final SingleTransactionRecordBuilder baseStreamBuilder;
    // For the root stack of a user dispatch, the final sink of all created stream builders; otherwise null,
    // because child stacks flush their builders into the savepoint at the top of their parent stack
    @Nullable
    private final BuilderSink builderSink;

    /**
     * Constructs the root {@link SavepointStackImpl} for the given state at the start of handling a user transaction.
     *
     * @param root the root state
     * @param maxBuildersBeforeUser the maximum number of preceding builders with available consensus times
     * @param maxBuildersAfterUser the maximum number of following builders with available consensus times
     * @return the root {@link SavepointStackImpl}
     */
    public static SavepointStackImpl newRootStack(
            @NonNull final HederaState root, final int maxBuildersBeforeUser, final int maxBuildersAfterUser) {
        return new SavepointStackImpl(root, maxBuildersBeforeUser, maxBuildersAfterUser);
    }

    /**
     * Constructs a new child {@link SavepointStackImpl} for the given state, where the child dispatch has the given
     * reversing behavior, transaction category, and record customizer.
     *
     * @param root the state on which the child dispatch is based
     * @param reversingBehavior the reversing behavior for the initial dispatch
     * @param category the transaction category
     * @param customizer the record customizer
     * @return the child {@link SavepointStackImpl}
     */
    public static SavepointStackImpl newChildStack(
            @NonNull final SavepointStackImpl root,
            @NonNull final SingleTransactionRecordBuilder.ReversingBehavior reversingBehavior,
            @NonNull final HandleContext.TransactionCategory category,
            @NonNull final ExternalizedRecordCustomizer customizer) {
        return new SavepointStackImpl(root, reversingBehavior, category, customizer);
    }

    /**
     * Constructs a new root {@link SavepointStackImpl} with the given root state.
     *
     * @param root the root state
     * @param maxBuildersBeforeUser the maximum number of preceding builders to create
     * @param maxBuildersAfterUser the maximum number of following builders to create
     */
    private SavepointStackImpl(
            @NonNull final HederaState root, final int maxBuildersBeforeUser, final int maxBuildersAfterUser) {
        this.root = requireNonNull(root);
        builderSink = new BuilderSink(maxBuildersBeforeUser, maxBuildersAfterUser + 1);
        pushSavepoint(USER);
        baseStreamBuilder = peek().createBuilder(REVERSIBLE, USER, NOOP_EXTERNALIZED_RECORD_CUSTOMIZER, true);
    }

    /**
     * Constructs a new child {@link SavepointStackImpl} with the given parent stack and the provided
     * characteristics of the dispatch.
     * @param parent the parent stack
     * @param reversingBehavior the reversing behavior of the dispatch
     * @param category the category of the dispatch
     * @param customizer the record customizer for the dispatch
     */
    private SavepointStackImpl(
            @NonNull final SavepointStackImpl parent,
            @NonNull final SingleTransactionRecordBuilder.ReversingBehavior reversingBehavior,
            @NonNull final HandleContext.TransactionCategory category,
            @NonNull final ExternalizedRecordCustomizer customizer) {
        requireNonNull(reversingBehavior);
        requireNonNull(customizer);
        requireNonNull(category);
        this.root = requireNonNull(parent);
        this.builderSink = null;
        pushSavepoint(category);
        baseStreamBuilder = peek().createBuilder(reversingBehavior, category, customizer, true);
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

    @Override
    public int depth() {
        return stack.size();
    }

    /**
     * Commits all state changes captured in this stack.
     */
    public void commitFullStack() {
        while (!stack.isEmpty()) {
            stack.pop().commit();
        }
        pushSavepoint(baseStreamBuilder.category());
    }

    /**
     * Rolls back all state changes captured in this stack.
     */
    public void rollbackFullStack() {
        while (!stack.isEmpty()) {
            stack.pop().rollback();
        }
        pushSavepoint(baseStreamBuilder.category());
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

    /**
     * May only be called on the root stack to get the entire list of stream builders created in the course
     * of handling a user transaction.
     * @return all stream builders created when handling the user transaction
     * @throws NullPointerException if called on a non-root stack
     */
    public List<SingleTransactionRecordBuilder> streamBuilders() {
        return requireNonNull(builderSink).allBuilders();
    }

    /**
     * May only be called on the root stack to determine if this stack has capacity to create more system records to
     * as preceding dispatches.
     * @return whether there are more system records to be created
     * @throws NullPointerException if called on a non-root stack
     */
    public boolean hasMoreSystemRecords() {
        return requireNonNull(builderSink).precedingCapacity() > 0;
    }

    /**
     * Returns the base stream builder for this stack; i.e., the builder corresponding to the dispatch
     * that created the stack.
     * @return the base stream builder
     */
    public @NonNull SingleTransactionRecordBuilder baseStreamBuilder() {
        return baseStreamBuilder;
    }

    public boolean hasChildOrPrecedingBuilders() {
        if (builderSink != null && builderSink.hasBuilderOtherThan(baseStreamBuilder)) {
            return true;
        }
        for (var savepoint : stack) {
            if (savepoint.hasBuilderOtherThan(baseStreamBuilder)) {
                return true;
            }
        }
        return false;
    }

    public <T> void forEachChildRecord(@NonNull Class<T> recordBuilderClass, @NonNull Consumer<T> consumer) {
        if (builderSink != null) {
            builderSink.forEachOtherBuilder(consumer, recordBuilderClass, baseStreamBuilder);
        }
        for (var savepoint : stack) {
            savepoint.forEachOtherBuilder(consumer, recordBuilderClass, baseStreamBuilder);
        }
    }

    public HandleContext.TransactionCategory txnCategory() {
        return baseStreamBuilder.category();
    }

    public SingleTransactionRecordBuilder createRemovableChildBuilder() {
        return peek().createBuilder(REMOVABLE, CHILD, NOOP_EXTERNALIZED_RECORD_CUSTOMIZER, false);
    }

    public SingleTransactionRecordBuilder createChildBuilder() {
        return peek().createBuilder(REVERSIBLE, CHILD, NOOP_EXTERNALIZED_RECORD_CUSTOMIZER, false);
    }

    public SingleTransactionRecordBuilder createUserBuilder() {
        return peek().createBuilder(REVERSIBLE, USER, NOOP_EXTERNALIZED_RECORD_CUSTOMIZER, false);
    }

    public SingleTransactionRecordBuilder createPrecedingBuilder() {
        return peek().createBuilder(IRREVERSIBLE, PRECEDING, NOOP_EXTERNALIZED_RECORD_CUSTOMIZER, false);
    }

    /**
     * Returns all following child records in the stack for use in end-of-EVM-transaction throttling.
     * <p>
     * To be removed on completion of HIP-993 and adoption of per-dispatch throttling.
     *
     * @return the list of child records
     */
    @Deprecated
    public List<SingleTransactionRecordBuilder> getChildBuilders() {
        final var childRecords = new ArrayList<SingleTransactionRecordBuilder>();
        for (final var savepoint : stack) {
            for (final var builder : savepoint.followingBuilders()) {
                if (builder.category() == CHILD) {
                    childRecords.add(builder);
                }
            }
        }
        return childRecords;
    }

    /**
     * Returns the top savepoint without removing it from the stack. Used only by the {@link WritableStatesStack},
     * not part of the public API.
     *
     * @return the top savepoint
     * @throws IllegalStateException if the stack has been committed already
     */
    @NonNull
    Savepoint peek() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("The stack has already been committed");
        }
        return stack.peek();
    }

    private void pushSavepoint(final HandleContext.TransactionCategory category) {
        if (root instanceof SavepointStackImpl parent) {
            stack.push(new FirstChildSavepoint(
                    new WrappedHederaState(root), parent.peek().asSink(), category));
        } else {
            stack.push(new FirstRootSavepoint(new WrappedHederaState(root), requireNonNull(builderSink)));
        }
    }
}
