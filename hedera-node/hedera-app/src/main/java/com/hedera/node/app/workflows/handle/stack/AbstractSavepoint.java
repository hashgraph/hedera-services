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

package com.hedera.node.app.workflows.handle.stack;

import static com.hedera.hapi.node.base.ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.PRECEDING;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.ReversingBehavior.REMOVABLE;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.ReversingBehavior.REVERSIBLE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.state.WrappedHederaState;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.swirlds.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * An abstract base class for save point that contains the current state and the record builders created
 * in the current savepoint.
 */
public abstract class AbstractSavepoint extends BuilderSink implements Savepoint {
    private static final EnumSet<ResponseCodeEnum> SUCCESSES =
            EnumSet.of(OK, SUCCESS, FEE_SCHEDULE_FILE_PART_UPLOADED, SUCCESS_BUT_MISSING_EXPECTED_OPERATION);

    protected final WrappedHederaState state;
    protected final BuilderSink parentSink;

    protected AbstractSavepoint(
            @NonNull final WrappedHederaState state,
            @NonNull final BuilderSink parentSink,
            int maxPreceding,
            int maxFollowing) {
        super(maxPreceding, maxFollowing);
        this.state = requireNonNull(state);
        this.parentSink = requireNonNull(parentSink);
    }

    protected AbstractSavepoint(
            @NonNull final WrappedHederaState state, @NonNull final BuilderSink parentSink, int maxTotal) {
        super(maxTotal);
        this.state = requireNonNull(state);
        this.parentSink = requireNonNull(parentSink);
    }

    @Override
    public HederaState state() {
        return state;
    }

    @Override
    public void commit() {
        state.commit();
        commitRecords();
    }

    @Override
    public void rollback() {
        rollBackRecords(precedingBuilders);
        rollBackRecords(followingBuilders);
        commitRecords();
    }

    @Override
    public SingleTransactionRecordBuilder createBuilder(
            @NonNull final SingleTransactionRecordBuilder.ReversingBehavior reversingBehavior,
            @NonNull final HandleContext.TransactionCategory txnCategory,
            @NonNull final ExternalizedRecordCustomizer customizer,
            final boolean isBaseBuilder) {
        requireNonNull(reversingBehavior);
        requireNonNull(txnCategory);
        requireNonNull(customizer);
        final var builder = new SingleTransactionRecordBuilderImpl(reversingBehavior, customizer, txnCategory);
        if (!customizer.shouldSuppressRecord()) {
            if (txnCategory == PRECEDING && !isBaseBuilder) {
                addPrecedingOrThrow(builder);
            } else {
                addFollowingOrThrow(builder);
            }
        }
        return builder;
    }

    abstract void commitRecords();

    private void rollBackRecords(final List<SingleTransactionRecordBuilder> recordBuilders) {
        boolean didRemoveBuilder = false;
        for (int i = 0; i < recordBuilders.size(); i++) {
            final var recordBuilder = recordBuilders.get(i);
            if (recordBuilder.reversingBehavior() == REVERSIBLE) {
                recordBuilder.nullOutSideEffectFields();
                if (SUCCESSES.contains(recordBuilder.status())) {
                    recordBuilder.status(ResponseCodeEnum.REVERTED_SUCCESS);
                }
            } else if (recordBuilder.reversingBehavior() == REMOVABLE) {
                // Remove it from the list by setting its location to null. Then, any subsequent children that are
                // kept will be moved into this position.
                recordBuilders.set(i, null);
                didRemoveBuilder = true;
            }
        }
        if (didRemoveBuilder) {
            recordBuilders.removeIf(Objects::isNull);
        }
    }

    @Override
    public Savepoint createFollowingSavePoint() {
        return new FollowingSavepoint(new WrappedHederaState(state), this, followingCapacity());
    }

    @Override
    public BuilderSink asSink() {
        return this;
    }
}
