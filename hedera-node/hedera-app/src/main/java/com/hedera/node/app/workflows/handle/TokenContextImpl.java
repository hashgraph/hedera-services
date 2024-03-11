/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle;

import static com.hedera.node.app.workflows.handle.HandleContextImpl.PrecedingTransactionCategory.LIMITED_CHILD_RECORDS;
import static com.hedera.node.app.workflows.handle.HandleContextImpl.PrecedingTransactionCategory.UNLIMITED_CHILD_RECORDS;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.records.FinalizeContext;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.function.Consumer;

public class TokenContextImpl implements TokenContext, FinalizeContext {
    private final Configuration configuration;
    private final ReadableStoreFactory readableStoreFactory;
    private final WritableStoreFactory writableStoreFactory;
    private final RecordListBuilder recordListBuilder;
    private final BlockRecordManager blockRecordManager;
    private final boolean isFirstTransaction;

    public TokenContextImpl(
            @NonNull final Configuration configuration,
            @NonNull final SavepointStackImpl stack,
            @NonNull final RecordListBuilder recordListBuilder,
            @NonNull final BlockRecordManager blockRecordManager,
            final boolean isFirstTransaction) {
        requireNonNull(stack, "stack must not be null");
        this.configuration = requireNonNull(configuration, "configuration must not be null");
        this.recordListBuilder = requireNonNull(recordListBuilder, "recordListBuilder must not be null");
        this.blockRecordManager = requireNonNull(blockRecordManager, "blockRecordManager must not be null");
        this.isFirstTransaction = isFirstTransaction;

        this.readableStoreFactory = new ReadableStoreFactory(stack);
        this.writableStoreFactory = new WritableStoreFactory(stack, TokenService.NAME);
    }

    @NonNull
    @Override
    public Instant consensusTime() {
        return recordListBuilder.userTransactionRecordBuilder().consensusNow();
    }

    @NonNull
    @Override
    public Configuration configuration() {
        return configuration;
    }

    @NonNull
    @Override
    public <T> T readableStore(@NonNull Class<T> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        return readableStoreFactory.getStore(storeInterface);
    }

    @NonNull
    @Override
    public <T> T writableStore(@NonNull Class<T> storeInterface) {
        requireNonNull(storeInterface, "storeInterface must not be null");
        return writableStoreFactory.getStore(storeInterface);
    }

    @NonNull
    @Override
    public <T> T userTransactionRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        requireNonNull(recordBuilderClass, "recordBuilderClass must not be null");
        return castRecordBuilder(recordListBuilder.userTransactionRecordBuilder(), recordBuilderClass);
    }

    @Override
    public boolean hasChildRecords() {
        return !recordListBuilder.childRecordBuilders().isEmpty();
    }

    @Override
    public <T> void forEachChildRecord(@NonNull Class<T> recordBuilderClass, @NonNull Consumer<T> consumer) {
        requireNonNull(consumer, "consumer must not be null");
        final var childRecordBuilders = recordListBuilder.childRecordBuilders();
        childRecordBuilders.forEach(child -> consumer.accept(castRecordBuilder(child, recordBuilderClass)));
    }

    @NonNull
    @Override
    public <T> T addPrecedingChildRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        final var result = recordListBuilder.addPreceding(configuration(), LIMITED_CHILD_RECORDS);
        return castRecordBuilder(result, recordBuilderClass);
    }

    @NonNull
    @Override
    public <T> T addUncheckedPrecedingChildRecordBuilder(@NonNull Class<T> recordBuilderClass) {
        final var result = recordListBuilder.addPreceding(configuration(), UNLIMITED_CHILD_RECORDS);
        return castRecordBuilder(result, recordBuilderClass);
    }

    static <T> T castRecordBuilder(
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder,
            @NonNull final Class<T> recordBuilderClass) {
        if (!recordBuilderClass.isInstance(recordBuilder)) {
            throw new IllegalArgumentException("Not a valid record builder class");
        }
        return recordBuilderClass.cast(recordBuilder);
    }

    @Override
    public boolean isFirstTransaction() {
        return isFirstTransaction;
    }

    @Override
    public void markMigrationRecordsStreamed() {
        blockRecordManager.markMigrationRecordsStreamed();
    }
}
