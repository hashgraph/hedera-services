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

import static com.hedera.hapi.block.stream.output.StateChangesCause.STATE_CHANGE_CAUSE_SYSTEM;
import static com.hedera.hapi.block.stream.output.StateChangesCause.STATE_CHANGE_CAUSE_TRANSACTION;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.PRECEDING;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;
import static com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer.NOOP_RECORD_CUSTOMIZER;
import static com.hedera.node.app.spi.workflows.record.StreamBuilder.ReversingBehavior.IRREVERSIBLE;
import static com.hedera.node.app.spi.workflows.record.StreamBuilder.ReversingBehavior.REMOVABLE;
import static com.hedera.node.app.spi.workflows.record.StreamBuilder.ReversingBehavior.REVERSIBLE;
import static com.hedera.node.config.types.StreamMode.RECORDS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.StateChangesCause;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.node.app.blocks.impl.IoBlockItemsBuilder;
import com.hedera.node.app.blocks.impl.PairedStreamBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.state.ReadonlyStatesWrapper;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.state.WrappedHederaState;
import com.hedera.node.app.workflows.handle.HandleOutput;
import com.hedera.node.app.workflows.handle.HandleWorkflow;
import com.hedera.node.app.workflows.handle.record.RecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.savepoints.BuilderSinkImpl;
import com.hedera.node.app.workflows.handle.stack.savepoints.FirstChildSavepoint;
import com.hedera.node.app.workflows.handle.stack.savepoints.FirstRootSavepoint;
import com.hedera.node.app.workflows.handle.stack.savepoints.FollowingSavepoint;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A stack of savepoints scoped to a dispatch. Each savepoint captures the state of the {@link HederaState} at the time
 * the savepoint was created and all the changes made to the state from the time savepoint was created, along with all
 * the stream builders created in the savepoint.
 */
public class SavepointStackImpl implements HandleContext.SavepointStack, HederaState {
    private static final Logger log = LogManager.getLogger(SavepointStackImpl.class);

    private final HederaState root;
    private final Deque<Savepoint> stack = new ArrayDeque<>();
    private final Map<String, WritableStatesStack> writableStatesMap = new HashMap<>();
    /**
     * The stream builder for the transaction whose dispatch created this stack.
     */
    private final StreamBuilder baseBuilder;
    // For the root stack of a user dispatch, the final sink of all created stream builders; otherwise null,
    // because child stacks flush their builders into the savepoint at the top of their parent stack
    @Nullable
    private final BuilderSink builderSink;
    /**
     * Any system state changes made <b>before</b> all transaction-caused changes in
     * the {@link com.hedera.node.app.workflows.handle.HandleWorkflow}.
     */
    private final List<StateChange> preTxnSystemChanges = new ArrayList<>();
    /**
     * Any system state changes made <b>after</b> all transaction-caused changes.
     */
    private final List<StateChange> postTxnSystemChanges = new ArrayList<>();

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
            @NonNull final StreamBuilder.ReversingBehavior reversingBehavior,
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
        builderSink = new BuilderSinkImpl(maxBuildersBeforeUser, maxBuildersAfterUser + 1);
        setupFirstSavepoint(USER);
        baseBuilder = peek().createBuilder(REVERSIBLE, USER, NOOP_RECORD_CUSTOMIZER, true);
    }

    /**
     * Constructs a new child {@link SavepointStackImpl} with the given parent stack and the provided
     * characteristics of the dispatch.
     *
     * @param parent the parent stack
     * @param reversingBehavior the reversing behavior of the dispatch
     * @param category the category of the dispatch
     * @param customizer the record customizer for the dispatch
     */
    private SavepointStackImpl(
            @NonNull final SavepointStackImpl parent,
            @NonNull final StreamBuilder.ReversingBehavior reversingBehavior,
            @NonNull final HandleContext.TransactionCategory category,
            @NonNull final ExternalizedRecordCustomizer customizer) {
        requireNonNull(reversingBehavior);
        requireNonNull(customizer);
        requireNonNull(category);
        this.root = requireNonNull(parent);
        this.builderSink = null;
        setupFirstSavepoint(category);
        baseBuilder = peek().createBuilder(reversingBehavior, category, customizer, true);
    }

    @Override
    public void createSavepoint() {
        stack.push(new FollowingSavepoint(new WrappedHederaState(peek().state()), peek()));
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
     * Commits all state changes captured in this stack, without capturing the details
     * for the block stream.
     * @throws NullPointerException if called on the root stack
     */
    public void commitFullStack() {
        commitFullStack(null, null, null);
    }

    private enum SystemStateChangeOrder {
        PRE_TXNS,
        POST_TXNS
    }

    /**
     * Commits all state changes captured in this stack; and captures the details for
     * the block stream, correlated to the given builder.
     * @param builder the builder to correlate the state changes to
     */
    public void commitTransaction(@Nullable final StreamBuilder builder) {
        commitFullStack(STATE_CHANGE_CAUSE_TRANSACTION, null, builder);
    }

    /**
     * Commits all state changes captured in this stack; and captures the details for
     * the block stream, correlated to state changes preceding the first transaction.
     */
    public void commitPreTxnSystemChanges() {
        commitFullStack(STATE_CHANGE_CAUSE_SYSTEM, SystemStateChangeOrder.PRE_TXNS, null);
    }

    /**
     * Commits all state changes captured in this stack; and captures the details for
     * the block stream, correlated to state changes following the last transaction.
     */
    public void commitPostTxnSystemChanges() {
        commitFullStack(STATE_CHANGE_CAUSE_SYSTEM, SystemStateChangeOrder.POST_TXNS, null);
    }

    /**
     * Commits all state changes captured in this stack; if this is the root stack, also
     * captures those changes as builders with the given cause.
     */
    private void commitFullStack(
            @Nullable final StateChangesCause cause,
            @Nullable final SystemStateChangeOrder changeOrder,
            @Nullable final StreamBuilder causeBuilder) {
        if (cause != null || causeBuilder != null || changeOrder != null) {
            requireNonNull(builderSink, "Cause metadata provided to child stack");
        }
        final var isRoot = builderSink != null;
        if (isRoot) {
            requireNonNull(cause, "Committing root stack must have a cause");
        }
        while (!stack.isEmpty()) {
            // The root stack must capture its state changes before committing the first savepoint
            if (isRoot && HandleWorkflow.STREAM_MODE != RECORDS && stack.size() == 1) {
                final var stateChanges = ((WrappedHederaState) stack.peek().state()).pendingStateChanges();
                log.info("Capturing {} state changes {}", cause, stateChanges);
                switch (cause) {
                    case STATE_CHANGE_CAUSE_SYSTEM -> {
                        requireNonNull(changeOrder, "System cause given without order");
                        if (changeOrder == SystemStateChangeOrder.PRE_TXNS) {
                            preTxnSystemChanges.addAll(stateChanges);
                        } else {
                            postTxnSystemChanges.addAll(stateChanges);
                        }
                    }
                    case STATE_CHANGE_CAUSE_TRANSACTION -> {
                        requireNonNull(causeBuilder, "Transaction cause given without builder");
                        causeBuilder.stateChanges(stateChanges);
                    }
                }
            }
            stack.pop().commit();
        }
        setupFirstSavepoint(baseBuilder.category());
    }

    /**
     * Rolls back all state changes captured in this stack.
     */
    public void rollbackFullStack() {
        while (!stack.isEmpty()) {
            stack.pop().rollback();
        }
        setupFirstSavepoint(baseBuilder.category());
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

    @NonNull
    @Override
    public <T extends StreamBuilder> T getBaseBuilder(@NonNull Class<T> recordBuilderClass) {
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        return castBuilder(baseBuilder, recordBuilderClass);
    }

    @NonNull
    @Override
    public <T> T addChildRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        final var result = createReversibleChildBuilder();
        return castBuilder(result, recordBuilderClass);
    }

    @NonNull
    @Override
    public <T> T addRemovableChildRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        final var result = createRemovableChildBuilder();
        return castBuilder(result, recordBuilderClass);
    }

    public static <T> T castBuilder(@NonNull final StreamBuilder builder, @NonNull final Class<T> builderClass) {
        if (!builderClass.isInstance(builder)) {
            throw new IllegalArgumentException("Not a valid record builder class");
        }
        return builderClass.cast(builder);
    }

    /**
     * May only be called on the root stack to determine if this stack has capacity to create more system records to
     * as preceding dispatches.
     *
     * @return whether there are more system records to be created
     * @throws NullPointerException if called on a non-root stack
     */
    public boolean hasMoreSystemRecords() {
        return requireNonNull(builderSink).precedingCapacity() > 0;
    }

    /**
     * Whether this stack has accumulated any stream builders other than its base builder; important to know when
     * determining the record finalization work to be done.
     *
     * @return whether this stack has any stream builders other than the base builder
     */
    public boolean hasNonBaseStreamBuilder() {
        if (builderSink != null && builderSink.hasBuilderOtherThan(baseBuilder)) {
            return true;
        }
        for (final var savepoint : stack) {
            if (savepoint.hasBuilderOtherThan(baseBuilder)) {
                return true;
            }
        }
        return false;
    }

    /**
     * For each stream builder in this stack other than the designated base builder, invokes the given consumer
     * with the builder cast to the given type.
     *
     * @param builderClass the type to cast the builders to
     * @param consumer the consumer to invoke
     * @param <T> the type to cast the builders to
     */
    public <T> void forEachNonBaseBuilder(@NonNull final Class<T> builderClass, @NonNull final Consumer<T> consumer) {
        requireNonNull(builderClass);
        requireNonNull(consumer);
        if (builderSink != null) {
            builderSink.forEachOtherBuilder(consumer, builderClass, baseBuilder);
        }
        for (var savepoint : stack) {
            savepoint.forEachOtherBuilder(consumer, builderClass, baseBuilder);
        }
    }

    /**
     * Returns the {@link HandleContext.TransactionCategory} of the transaction that created this stack.
     *
     * @return the transaction category
     */
    public HandleContext.TransactionCategory txnCategory() {
        return baseBuilder.category();
    }

    /**
     * Creates a new stream builder for a removable child in the active savepoint.
     *
     * @return the new stream builder
     */
    public StreamBuilder createRemovableChildBuilder() {
        return peek().createBuilder(REMOVABLE, CHILD, NOOP_RECORD_CUSTOMIZER, false);
    }

    /**
     * Creates a new stream builder for a reversible child in the active savepoint.
     *
     * @return the new stream builder
     */
    public StreamBuilder createReversibleChildBuilder() {
        return peek().createBuilder(REVERSIBLE, CHILD, NOOP_RECORD_CUSTOMIZER, false);
    }

    /**
     * Creates a new stream builder for an irreversible preceding transaction in the active savepoint.
     *
     * @return the new stream builder
     */
    public StreamBuilder createIrreversiblePrecedingBuilder() {
        return peek().createBuilder(IRREVERSIBLE, PRECEDING, NOOP_RECORD_CUSTOMIZER, false);
    }

    /**
     * Returns all following child records in the stack for use in end-of-EVM-transaction throttling.
     * <p>
     * To be removed on completion of HIP-993 and adoption of per-dispatch throttling.
     *
     * @return the list of child records
     */
    @Deprecated
    public List<StreamBuilder> getChildBuilders() {
        final var childRecords = new ArrayList<StreamBuilder>();
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

    /**
     * Builds all the records for the user transaction.
     *
     * @param consensusTime consensus time of the transaction
     * @return the stream of records
     */
    public HandleOutput buildHandleOutput(@NonNull final Instant consensusTime) {
        final List<BlockItem> blockItems;
        if (HandleWorkflow.STREAM_MODE == RECORDS) {
            blockItems = null;
        } else {
            blockItems = new LinkedList<>();
        }
        final List<SingleTransactionRecord> records = new ArrayList<>();
        final var builders = requireNonNull(builderSink).allBuilders();
        TransactionID.Builder idBuilder = null;
        int indexOfUserRecord = 0;
        for (int i = 0; i < builders.size(); i++) {
            if (builders.get(i).category() == USER) {
                indexOfUserRecord = i;
                idBuilder = builders.get(i).transactionID().copyBuilder();
                break;
            }
        }
        int nextNonce = 1;
        for (int i = 0; i < builders.size(); i++) {
            final var builder = builders.get(i);
            final var nonce =
                    switch (builder.category()) {
                        case USER, SCHEDULED -> 0;
                        case PRECEDING, CHILD -> nextNonce++;
                    };
            // The schedule service specifies the transaction id to use for a triggered transaction
            if (builder.transactionID() == null || TransactionID.DEFAULT.equals(builder.transactionID())) {
                builder.transactionID(requireNonNull(idBuilder).nonce(nonce).build())
                        .syncBodyIdFromRecordId();
            }
            final var consensusNow = consensusTime.plusNanos((long) i - indexOfUserRecord);
            builder.consensusTimestamp(consensusNow);
            if (i > indexOfUserRecord && builder.category() != SCHEDULED) {
                builder.parentConsensus(consensusTime);
            }
            switch (HandleWorkflow.STREAM_MODE) {
                case RECORDS -> records.add(((RecordBuilderImpl) builder).build());
                case BLOCKS -> requireNonNull(blockItems).addAll(((IoBlockItemsBuilder) builder).build());
                case BOTH -> {
                    final var pairedBuilder = (PairedStreamBuilder) builder;
                    records.add(pairedBuilder.recordBuilder().build());
                    requireNonNull(blockItems)
                            .addAll(pairedBuilder.ioBlockItemsBuilder().build());
                }
            }
        }
        if (HandleWorkflow.STREAM_MODE != RECORDS && !preTxnSystemChanges.isEmpty()) {
            final var preTxnConsensusNow = consensusTime.minusNanos(indexOfUserRecord + 1);
            requireNonNull(blockItems)
                    .addFirst(BlockItem.newBuilder()
                            .stateChanges(StateChanges.newBuilder()
                                    .cause(STATE_CHANGE_CAUSE_SYSTEM)
                                    .consensusTimestamp(new Timestamp(
                                            preTxnConsensusNow.getEpochSecond(), preTxnConsensusNow.getNano()))
                                    .stateChanges(preTxnSystemChanges))
                            .build());
        }
        return new HandleOutput(blockItems, records);
    }

    private void setupFirstSavepoint(@NonNull final HandleContext.TransactionCategory category) {
        if (root instanceof SavepointStackImpl parent) {
            stack.push(new FirstChildSavepoint(new WrappedHederaState(root), parent.peek(), category));
        } else {
            stack.push(new FirstRootSavepoint(new WrappedHederaState(root), requireNonNull(builderSink)));
        }
    }
}
