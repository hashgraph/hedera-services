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

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.records.RecordBuilders;
import com.hedera.node.app.spi.workflows.record.SingleTransactionBuilder;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Default implementation of {@link RecordBuilders}
 */
public class RecordBuildersImpl implements RecordBuilders {
    private final SavepointStackImpl stack;

    public RecordBuildersImpl(final SavepointStackImpl stack) {
        this.stack = stack;
    }

    @NonNull
    @Override
    public <T> T getOrCreate(@NonNull Class<T> recordBuilderClass) {
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        return castBuilder(stack.baseStreamBuilder(), recordBuilderClass);
    }

    @NonNull
    @Override
    public <T> T addChildRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        final var result = stack.createReversibleChildBuilder();
        return castBuilder(result, recordBuilderClass);
    }

    @NonNull
    @Override
    public <T> T addRemovableChildRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        final var result = stack.createRemovableChildBuilder();
        return castBuilder(result, recordBuilderClass);
    }

    public static <T> T castBuilder(
            @NonNull final SingleTransactionBuilder builder, @NonNull final Class<T> builderClass) {
        if (!builderClass.isInstance(builder)) {
            throw new IllegalArgumentException("Not a valid record builder class");
        }
        return builderClass.cast(builder);
    }
}
