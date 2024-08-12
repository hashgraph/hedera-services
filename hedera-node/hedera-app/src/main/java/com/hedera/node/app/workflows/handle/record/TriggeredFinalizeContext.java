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

package com.hedera.node.app.workflows.handle.record;

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.service.token.records.FinalizeContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.store.WritableStoreFactory;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * A tiny extension of {@link ChildFinalizeContextImpl} that allows us to re-use the
 * {@link com.hedera.node.app.service.token.records.ParentRecordFinalizer} for the
 * records of dispatched scheduled transactions.
 */
public class TriggeredFinalizeContext extends ChildFinalizeContextImpl implements FinalizeContext {
    private final Instant consensusNow;
    private final Configuration configuration;
    private final HederaFunctionality functionality;
    private final HandleContext.TransactionCategory category;
    private final RecordListBuilder recordListBuilder;

    public TriggeredFinalizeContext(
            @NonNull final ReadableStoreFactory readableStoreFactory,
            @NonNull final WritableStoreFactory writableStoreFactory,
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder,
            @NonNull final Instant consensusNow,
            @NonNull final Configuration configuration,
            @NonNull final HederaFunctionality functionality,
            @NonNull final HandleContext.TransactionCategory category,
            @NonNull final RecordListBuilder recordListBuilder) {
        super(configuration, readableStoreFactory, writableStoreFactory, recordBuilder);
        this.consensusNow = requireNonNull(consensusNow);
        this.configuration = requireNonNull(configuration);
        this.functionality = requireNonNull(functionality);
        this.category = requireNonNull(category);
        this.recordListBuilder = requireNonNull(recordListBuilder);
    }

    @NonNull
    @Override
    public Instant consensusTime() {
        return consensusNow;
    }

    @NonNull
    @Override
    public Configuration configuration() {
        return configuration;
    }

    @Override
    public boolean hasChildOrPrecedingRecords() {
        // There is a single case in 0.52 where a child dispatch can itself have a logical child with non-zero
        // balance adjustments---a scheduled crypto transfer that triggers an auto-account creation
        if (category == SCHEDULED && functionality == CRYPTO_TRANSFER) {
            final var precedingBuilders = recordListBuilder.precedingRecordBuilders();
            return precedingBuilders.stream()
                    .anyMatch(
                            builder -> !builder.transferList().accountAmounts().isEmpty());
        } else {
            return false;
        }
    }

    @Override
    public <T> void forEachChildRecord(
            @NonNull final Class<T> recordBuilderClass, @NonNull final Consumer<T> consumer) {
        requireNonNull(recordBuilderClass);
        requireNonNull(consumer);
        if (category == SCHEDULED && functionality == CRYPTO_TRANSFER) {
            final var precedingBuilders = recordListBuilder.precedingRecordBuilders();
            precedingBuilders.stream()
                    .filter(builder -> !builder.transferList().accountAmounts().isEmpty())
                    .map(recordBuilderClass::cast)
                    .forEach(consumer);
        }
    }

    @Override
    public boolean isScheduleDispatch() {
        return true;
    }
}
