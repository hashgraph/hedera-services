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

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A replaceable reference to a dispatch's first savepoint.
 */
public class DispatchSavepoint {
    private final HandleContext.TransactionCategory category;

    @Nullable
    private Savepoint savepoint;

    public DispatchSavepoint(
            @NonNull final HandleContext.TransactionCategory category, @NonNull final Savepoint savepoint) {
        this.category = requireNonNull(category);
        this.savepoint = requireNonNull(savepoint);
    }

    public DispatchSavepoint() {
        this.category = HandleContext.TransactionCategory.USER;
        this.savepoint = null;
    }

    /**
     * Replaces the previous first savepoint with the given first savepoint.
     * @param savepoint the new savepoint
     */
    public void replace(@NonNull final Savepoint savepoint) {
        this.savepoint = requireNonNull(savepoint);
    }

    /**
     * Returns the first savepoint.
     * @return the first savepoint
     */
    public Savepoint current() {
        return savepoint;
    }

    public HandleContext.TransactionCategory category() {
        return category;
    }

    public SingleTransactionRecordBuilder createBaseBuilder(
            @NonNull SingleTransactionRecordBuilder.ReversingBehavior reversingBehavior,
            @NonNull HandleContext.TransactionCategory txnCategory,
            @NonNull ExternalizedRecordCustomizer customizer) {
        return savepoint.createBuilder(reversingBehavior, txnCategory, customizer, true);
    }
}
