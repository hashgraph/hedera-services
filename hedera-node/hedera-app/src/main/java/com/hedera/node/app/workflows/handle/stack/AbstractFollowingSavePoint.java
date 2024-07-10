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

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.PRECEDING;

import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.state.WrappedHederaState;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An abstract base class for save point that contains the current state and the record builders created
 * in the current savepoint.
 */
public abstract class AbstractFollowingSavePoint extends AbstractSavePoint {
    private static final long MIN_TRANS_TIMESTAMP_INCR_NANOS = 1_000;
    protected final int numPreviouslyUsedBuilders;

    protected AbstractFollowingSavePoint(
            @NonNull final WrappedHederaState state, @NonNull final AbstractSavePoint parent) {
        super(state, parent);
        this.numPreviouslyUsedBuilders = parent.numBuildersAfterUserBuilder();
    }

    @Override
    boolean canAddRecord(final SingleTransactionRecordBuilder recordBuilder) {
        if (SIMULATE_MONO) {
            if (recordBuilder.isPreceding()) {
                return totalPrecedingRecords < legacyMaxPrecedingRecords;
            } else {
                final var numBuildersAfterUserBuilder = numBuildersAfterUserBuilder();
                return numBuildersAfterUserBuilder < maxBuildersAfterUserBuilder;
            }
        } else {
            return numBuildersAfterUserBuilder() < MIN_TRANS_TIMESTAMP_INCR_NANOS - 1;
        }
    }

    @Override
    public SingleTransactionRecordBuilderImpl createRecord(
            @NonNull final SingleTransactionRecordBuilder.ReversingBehavior reversingBehavior,
            @NonNull final HandleContext.TransactionCategory txnCategory,
            @NonNull ExternalizedRecordCustomizer customizer) {
        final var record = super.createRecord(reversingBehavior, txnCategory, customizer);
        if (txnCategory == PRECEDING && SIMULATE_MONO) {
            totalPrecedingRecords++;
        }
        return record;
    }

    @Override
    int numBuildersAfterUserBuilder() {
        return numPreviouslyUsedBuilders + numBuilders();
    }
}
