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
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.records.RecordBuilders;
import com.hedera.node.app.spi.workflows.record.RecordListCheckPoint;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
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
    private final RecordListBuilder recordListBuilder;
    private final Configuration configuration;
    private final SavepointStackImpl stack;

    @Inject
    public RecordBuildersImpl(
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder,
            @NonNull final RecordListBuilder recordListBuilder,
            @NonNull final Configuration configuration,
            final SavepointStackImpl stack) {
        this.recordBuilder = requireNonNull(recordBuilder);
        this.recordListBuilder = requireNonNull(recordListBuilder);
        this.configuration = requireNonNull(configuration);
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
        final var result = recordListBuilder.addChild(configuration, CHILD);
        return castRecordBuilder(result, recordBuilderClass);
    }

    @NonNull
    @Override
    public <T> T addRemovableChildRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        final var result = recordListBuilder.addRemovableChild(configuration);
        return castRecordBuilder(result, recordBuilderClass);
    }

    @Override
    public void revertRecordsFrom(@NonNull RecordListCheckPoint recordListCheckPoint) {
        recordListBuilder.revertChildrenFrom(recordListCheckPoint);
    }

    @NonNull
    @Override
    public RecordListCheckPoint createRecordListCheckPoint() {
        final var precedingRecordBuilders = recordListBuilder.precedingRecordBuilders();
        final var childRecordBuilders = recordListBuilder.childRecordBuilders();

        SingleTransactionRecordBuilder lastFollowing = null;
        SingleTransactionRecordBuilder firstPreceding = null;

        if (!precedingRecordBuilders.isEmpty()) {
            firstPreceding = precedingRecordBuilders.get(precedingRecordBuilders.size() - 1);
        }
        if (!childRecordBuilders.isEmpty()) {
            lastFollowing = childRecordBuilders.get(childRecordBuilders.size() - 1);
        }

        return new RecordListCheckPoint(firstPreceding, lastFollowing);
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
