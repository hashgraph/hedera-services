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

import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.state.WrappedHederaState;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A save point that contains the current state and the record builders created in the current savepoint.
 * Currently, recordBuilders is not used in the codebase. It will be used in future PRs
 */
public class FirstSavePoint extends AbstractSavePoint {
    private final int maxPreceding;
    private int currentPrecedingChildren;

    public FirstSavePoint(@NonNull WrappedHederaState state, int maxPreceding) {
        super(state, null, 0);
        this.maxPreceding = maxPreceding;
    }

    @Override
    public SingleTransactionRecordBuilderImpl addRecord(
            @NonNull final SingleTransactionRecordBuilder.ReversingBehavior reversingBehavior,
            @NonNull final HandleContext.TransactionCategory txnCategory,
            @NonNull ExternalizedRecordCustomizer customizer) {
        final var record = super.addRecord(reversingBehavior, txnCategory, customizer);
        if (recordBuilders.getLast().isPreceding()) {
            this.currentPrecedingChildren++;
            if (SIMULATE_MONO) {
                totalPrecedingRecords++;
            }
        }
        return record;
    }

    @Override
    boolean canAddRecord(final SingleTransactionRecordBuilder recordBuilder) {
        if (SIMULATE_MONO) {
            if (recordBuilder.isPreceding()) {
                return totalPrecedingRecords < maxPreceding;
            } else {
                return numChildrenUsedSoFar() < maxRecords;
            }
        } else {
            if (recordBuilder.isPreceding()) {
                return currentPrecedingChildren < maxPreceding;
            } else {
                return numChildrenUsedSoFar() < maxRecords;
            }
        }
    }

    @Override
    public int numChildrenUsedSoFar() {
        return recordBuilders.size() - currentPrecedingChildren;
    }
}
