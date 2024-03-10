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

package com.hedera.node.app.workflows.handle;

import com.hedera.node.app.service.token.records.FinalizeContext;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A tiny extension of {@link ChildFinalizeContextImpl} that allows us to re-use the
 * {@link com.hedera.node.app.service.token.records.ParentRecordFinalizer} for the
 * records of dispatched scheduled transactions.
 */
public class TriggeredFinalizeContext extends ChildFinalizeContextImpl implements FinalizeContext {
    private final Instant consensusNow;
    private final Configuration configuration;

    public TriggeredFinalizeContext(
            @NonNull final ReadableStoreFactory readableStoreFactory,
            @NonNull final WritableStoreFactory writableStoreFactory,
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder,
            @NonNull final Instant consensusNow,
            @NonNull final Configuration configuration) {
        super(readableStoreFactory, writableStoreFactory, recordBuilder);
        this.consensusNow = Objects.requireNonNull(consensusNow);
        this.configuration = Objects.requireNonNull(configuration);
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
    public boolean hasChildRecords() {
        return false;
    }

    @Override
    public <T> void forEachChildRecord(@NonNull Class<T> recordBuilderClass, @NonNull Consumer<T> consumer) {
        // No-op, as contract operations cannot be scheduled at this time
    }
}
