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

import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.ReversingBehavior.REMOVABLE;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.ReversingBehavior.REVERSIBLE;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.state.WrappedHederaState;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * An abstract base class for save point that contains the current state and the record builders created
 * in the current savepoint.
 */
public abstract class AbstractSavePoint extends RecordSink {
    private final WrappedHederaState state;

    @NonNull
    protected final RecordSink parentSink;

    // For simulating mono
    public static int maxBuildersAfterUserBuilder;
    public static int totalPrecedingRecords = 0;
    public static int legacyMaxPrecedingRecords;
    public static final boolean SIMULATE_MONO = true;

    public static final EnumSet<ResponseCodeEnum> SUCCESSES = EnumSet.of(
            ResponseCodeEnum.OK,
            ResponseCodeEnum.SUCCESS,
            ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED,
            ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION);

    protected AbstractSavePoint(@NonNull WrappedHederaState state, @NonNull final RecordSink parentSink) {
        this.state = state;
        this.parentSink = parentSink;
    }

    public WrappedHederaState state() {
        return state;
    }

    public SingleTransactionRecordBuilderImpl createRecord(
            @NonNull final SingleTransactionRecordBuilder.ReversingBehavior reversingBehavior,
            @NonNull final HandleContext.TransactionCategory txnCategory,
            @NonNull ExternalizedRecordCustomizer customizer) {
        final var recordBuilder = new SingleTransactionRecordBuilderImpl(reversingBehavior, customizer, txnCategory);
        if (!canAddRecord(recordBuilder)) {
            throw new HandleException(ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED);
        }
        if (!customizer.shouldSuppressRecord()) {
            if (txnCategory == HandleContext.TransactionCategory.PRECEDING) {
                precedingBuilders.add(recordBuilder);
            } else {
                followingBuilders.add(recordBuilder);
            }
        }
        return recordBuilder;
    }

    public FollowingSavePoint createFollowingSavePoint() {
        return new FollowingSavePoint(new WrappedHederaState(state), this);
    }

    public void commit() {
        state.commit();
        commitRecords();
    }

    public void rollback() {
        rollBackRecords(precedingBuilders);
        rollBackRecords(followingBuilders);
        commitRecords();
    }

    /**
     * {@inheritDoc}
     *
     * This method guarantees that the same {@link WritableStates} instance is returned for the same {@code serviceName}
     * to ensure all modifications to a {@link WritableStates} are kept together.
     */
    @NonNull
    @VisibleForTesting
    public WritableStates getWritableStates(@NonNull final String serviceName) {
        return state.getWritableStates(serviceName);
    }

    @NonNull
    @VisibleForTesting
    public ReadableStates getReadableStates(@NonNull String serviceName) {
        return state.getReadableStates(serviceName);
    }

    abstract void commitRecords();

    abstract boolean canAddRecord(SingleTransactionRecordBuilder recordBuilder);

    abstract int numBuildersAfterUserBuilder();

    private void rollBackRecords(final List<SingleTransactionRecordBuilder> recordBuilders) {
        boolean followingChildRemoved = false;
        for (int i = 0; i < recordBuilders.size(); i++) {
            final var recordBuilder = recordBuilders.get(i);
            if (recordBuilder.reversingBehavior() == REVERSIBLE) {
                recordBuilder.nullOutSideEffectFields();
                if (SUCCESSES.contains(recordBuilder.status())) {
                    recordBuilder.status(ResponseCodeEnum.REVERTED_SUCCESS);
                }
            } else if (recordBuilder.reversingBehavior() == REMOVABLE) {
                if (SIMULATE_MONO && recordBuilder.category() == HandleContext.TransactionCategory.PRECEDING) {
                    totalPrecedingRecords--;
                }
                // Remove it from the list by setting its location to null. Then, any subsequent children that are
                // kept will be moved into this position.
                recordBuilders.set(i, null);
                followingChildRemoved = true;
            }
        }
        if (followingChildRemoved) {
            recordBuilders.removeIf(Objects::isNull);
        }
    }
}
