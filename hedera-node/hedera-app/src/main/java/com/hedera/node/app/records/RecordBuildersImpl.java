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

package com.hedera.node.app.records;

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer.NOOP_EXTERNALIZED_RECORD_CUSTOMIZER;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.ReversingBehavior.REMOVABLE;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.ReversingBehavior.REVERSIBLE;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.records.RecordBuilders;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

/**
 * Default implementation of {@link RecordBuilders}
 */
public class RecordBuildersImpl implements RecordBuilders {

    private final SingleTransactionRecordBuilderImpl recordBuilder;
    private final SavepointStackImpl stack;
    public RecordBuildersImpl(
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder,
            final SavepointStackImpl stack) {
        this.recordBuilder = requireNonNull(recordBuilder);
        this.stack = stack;
    }

    @NonNull
    @Override
    public <T> T getCurrent(@NonNull Class<T> recordBuilderClass) {
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        return castRecordBuilder(recordBuilder, recordBuilderClass);
    }

    @NonNull
    @Override
    public <T> T addChildRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        final var result = stack.peek().addRecord(REVERSIBLE, CHILD, NOOP_EXTERNALIZED_RECORD_CUSTOMIZER);
        return castRecordBuilder(result, recordBuilderClass);
    }

    @NonNull
    @Override
    public <T> T addRemovableChildRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        final var result = stack.peek().addRecord(REMOVABLE, CHILD, NOOP_EXTERNALIZED_RECORD_CUSTOMIZER);
        return castRecordBuilder(result, recordBuilderClass);
    }

    public static <T> T castRecordBuilder(
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder,
            @NonNull final Class<T> recordBuilderClass) {
        if (!recordBuilderClass.isInstance(recordBuilder)) {
            throw new IllegalArgumentException("Not a valid record builder class");
        }
        return recordBuilderClass.cast(recordBuilder);
    }
}
