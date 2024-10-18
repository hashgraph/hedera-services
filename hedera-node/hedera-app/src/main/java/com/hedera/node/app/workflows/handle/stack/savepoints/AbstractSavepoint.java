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

package com.hedera.node.app.workflows.handle.stack.savepoints;

import static com.hedera.hapi.node.base.ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.PRECEDING;
import static com.hedera.node.app.spi.workflows.record.StreamBuilder.ReversingBehavior.REMOVABLE;
import static com.hedera.node.app.spi.workflows.record.StreamBuilder.ReversingBehavior.REVERSIBLE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.blocks.impl.BlockStreamBuilder;
import com.hedera.node.app.blocks.impl.PairedStreamBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.state.WrappedState;
import com.hedera.node.app.workflows.handle.record.RecordStreamBuilder;
import com.hedera.node.app.workflows.handle.stack.BuilderSink;
import com.hedera.node.app.workflows.handle.stack.Savepoint;
import com.hedera.node.config.types.StreamMode;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumSet;
import java.util.List;

/**
 * Implementation support for a {@link Savepoint}. The sole abstract method is {@link #commitBuilders()}, which
 * subclasses must override to choose the strategy used to flush any accumulated builders to the parent sink.
 * <p>
 * When adopting block streams we will add more extension points to this class in the form of abstract methods
 * that determine how each type of savepoint constructs state change block items.
 */
public abstract class AbstractSavepoint extends BuilderSinkImpl implements Savepoint {
    private static final EnumSet<ResponseCodeEnum> SUCCESSES =
            EnumSet.of(OK, SUCCESS, FEE_SCHEDULE_FILE_PART_UPLOADED, SUCCESS_BUT_MISSING_EXPECTED_OPERATION);

    protected final BuilderSink parentSink;
    protected final WrappedState state;
    private Status status = Status.PENDING;

    /**
     * Constructs a savepoint with limits that discriminate between the number of preceding and following builders.
     * @param state the current state
     * @param parentSink the parent sink
     * @param maxPreceding the maximum number of preceding builders
     * @param maxFollowing the maximum number of following builders
     */
    protected AbstractSavepoint(
            @NonNull final WrappedState state,
            @NonNull final BuilderSink parentSink,
            final int maxPreceding,
            final int maxFollowing) {
        super(maxPreceding, maxFollowing);
        this.state = requireNonNull(state);
        this.parentSink = requireNonNull(parentSink);
    }

    /**
     * Constructs a savepoint with a total limit on the number of builders that can be accumulated.
     * @param state the current state
     * @param parentSink the parent sink
     * @param maxTotal the maximum number of total builders
     */
    protected AbstractSavepoint(
            @NonNull final WrappedState state, @NonNull final BuilderSink parentSink, final int maxTotal) {
        super(maxTotal);
        this.state = requireNonNull(state);
        this.parentSink = requireNonNull(parentSink);
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public void commit() {
        assertNotFinished();

        commitBuilders();
        state.commit();
        status = Status.FINISHED;
    }

    @Override
    public void rollback() {
        assertNotFinished();

        rollback(precedingBuilders);
        rollback(followingBuilders);
        commitBuilders();
        status = Status.FINISHED;
    }

    @Override
    public StreamBuilder createBuilder(
            @NonNull final StreamBuilder.ReversingBehavior reversingBehavior,
            @NonNull final HandleContext.TransactionCategory txnCategory,
            @NonNull final ExternalizedRecordCustomizer customizer,
            @NonNull final StreamMode streamMode,
            final boolean isBaseBuilder) {
        requireNonNull(reversingBehavior);
        requireNonNull(txnCategory);
        requireNonNull(customizer);
        final var builder =
                switch (streamMode) {
                    case RECORDS -> new RecordStreamBuilder(reversingBehavior, customizer, txnCategory);
                    case BLOCKS -> new BlockStreamBuilder(reversingBehavior, customizer, txnCategory);
                    case BOTH -> new PairedStreamBuilder(reversingBehavior, customizer, txnCategory);
                };
        if (!customizer.shouldSuppressRecord()) {
            // Other code is a bit simpler when we always put the base builder for a stack in its
            // "following" list, even if the stack is child stack for a preceding child dispatch;
            // the base builder will still end up in the correct relative position in the parent
            // sink because of how FirstChildSavepoint implements #commitBuilders()
            if (txnCategory == PRECEDING && !isBaseBuilder) {
                addPrecedingOrThrow(builder);
            } else {
                addFollowingOrThrow(builder);
            }
        }
        return builder;
    }

    /**
     * Commits the builders accumulated in this savepoint to the parent sink.
     */
    abstract void commitBuilders();

    private void rollback(@NonNull final List<StreamBuilder> builders) {
        var iterator = builders.listIterator();
        while (iterator.hasNext()) {
            final var builder = iterator.next();
            if (builder.reversingBehavior() == REVERSIBLE) {
                builder.nullOutSideEffectFields();
                if (SUCCESSES.contains(builder.status())) {
                    builder.status(REVERTED_SUCCESS);
                }
            } else if (builder.reversingBehavior() == REMOVABLE) {
                iterator.remove();
            }
        }
    }

    private void assertNotFinished() {
        if (status == Status.FINISHED) {
            throw new IllegalStateException("Savepoint has already been committed or rolled back");
        }
    }

    private enum Status {
        PENDING,
        FINISHED
    }
}
