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

import static com.hedera.node.app.workflows.handle.record.RecordListBuilder.SUCCESSES;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.state.WrappedHederaState;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An abstract base class for save point that contains the current state and the record builders created
 * in the current savepoint.
 */
public abstract class AbstractSavePoint {
    private final WrappedHederaState state;

    @Nullable
    private final AbstractSavePoint parent;

    protected final List<SingleTransactionRecordBuilder> recordBuilders;
    protected final int numPreviouslyUsedRecords;

    public static int maxRecords;
    public static int totalPrecedingRecords = 0;
    public static int legacyMaxPrecedingRecords = 3;
    public static final boolean SIMULATE_MONO = true;

    protected AbstractSavePoint(
            @NonNull WrappedHederaState state,
            @Nullable final AbstractSavePoint parent,
            final int numPreviouslyUsedRecords) {
        this.state = state;
        this.parent = parent;
        this.numPreviouslyUsedRecords = numPreviouslyUsedRecords;
        this.recordBuilders = new ArrayList<>();
    }

    public List<SingleTransactionRecordBuilder> recordBuilders() {
        return recordBuilders;
    }

    public WrappedHederaState state() {
        return state;
    }

    public SingleTransactionRecordBuilderImpl addRecord(
            @NonNull final SingleTransactionRecordBuilder.ReversingBehavior reversingBehavior,
            @NonNull final HandleContext.TransactionCategory txnCategory,
            @NonNull ExternalizedRecordCustomizer customizer) {
        final var recordBuilder = new SingleTransactionRecordBuilderImpl(reversingBehavior, customizer, txnCategory);
        if (!canAddRecord(recordBuilder)) {
            throw new HandleException(ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED);
        }
        this.recordBuilders().add(recordBuilder);
        return recordBuilder;
    }

    public FollowingSavePoint createFollowingSavePoint() {
        return new FollowingSavePoint(new WrappedHederaState(state), this);
    }

    public AbstractSavePoint commit() {
        state.commit();
        pushRecordsToParentStack();
        return this;
    }

    public AbstractSavePoint rollback() {
        boolean followingChildRemoved = false;
        for (int i = 0; i < recordBuilders.size(); i++) {
            final var recordBuilder = recordBuilders.get(i);
            if (recordBuilder.reversingBehavior() == SingleTransactionRecordBuilder.ReversingBehavior.REVERSIBLE) {
                recordBuilder.nullOutSideEffectFields();
                if (SUCCESSES.contains(recordBuilder.status())) {
                    recordBuilder.status(ResponseCodeEnum.REVERTED_SUCCESS);
                }
            } else if (recordBuilder.reversingBehavior()
                    == SingleTransactionRecordBuilder.ReversingBehavior.REMOVABLE) {
                // Remove it from the list by setting its location to null. Then, any subsequent children that are
                // kept will be moved into this position.
                recordBuilders.set(i, null);
                followingChildRemoved = true;
            }
        }
        if (followingChildRemoved) {
            recordBuilders.removeIf(Objects::isNull);
        }
        pushRecordsToParentStack();
        return this;
    }

    private void pushRecordsToParentStack() {
        if (parent != null) {
            parent.recordBuilders().addAll(recordBuilders);
        }
    }

    abstract boolean canAddRecord(SingleTransactionRecordBuilder recordBuilder);

    public abstract int numChildrenUsedSoFar();
}
