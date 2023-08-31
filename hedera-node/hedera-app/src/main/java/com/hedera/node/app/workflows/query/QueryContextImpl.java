/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.query;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.records.impl.BlockRecordInfoImpl;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Simple implementation of {@link QueryContext}.
 */
public class QueryContextImpl implements QueryContext {

    private final ReadableStoreFactory storeFactory;
    private final Query query;
    private final Configuration configuration;
    private final RecordCache recordCache;
    private final HederaState state;
    private final AccountID payer;
    private BlockRecordInfo blockRecordInfo; // lazily created

    /**
     * Constructor of {@code QueryContextImpl}.
     *
     * @param storeFactory  the {@link ReadableStoreFactory} used to create the stores
     * @param query         the query that is currently being processed
     * @param configuration the current {@link Configuration}
     * @param payer         the {@link AccountID} of the payer, if present
     * @throws NullPointerException if {@code query} is {@code null}
     */
    public QueryContextImpl(
            @NonNull final HederaState state,
            @NonNull final ReadableStoreFactory storeFactory,
            @NonNull final Query query,
            @NonNull final Configuration configuration,
            @NonNull final RecordCache recordCache,
            @Nullable final AccountID payer) {
        this.storeFactory = requireNonNull(storeFactory, "The supplied argument 'storeFactory' cannot be null!");
        this.query = requireNonNull(query, "The supplied argument 'query' cannot be null!");
        this.configuration = requireNonNull(configuration, "The supplied argument 'configuration' cannot be null!");
        this.recordCache = requireNonNull(recordCache, "The supplied argument 'recordCache' cannot be null!");
        this.state = requireNonNull(state, "The supplied argument 'state' cannot be null!");
        this.payer = payer;
    }

    @Override
    @NonNull
    public Query query() {
        return query;
    }

    @Override
    public @Nullable AccountID payer() {
        return payer;
    }

    @Override
    @NonNull
    public <C> C createStore(@NonNull Class<C> storeInterface) {
        return storeFactory.getStore(storeInterface);
    }

    @NonNull
    @Override
    public Configuration configuration() {
        return configuration;
    }

    @NonNull
    @Override
    public RecordCache recordCache() {
        return recordCache;
    }

    @NonNull
    @Override
    public BlockRecordInfo blockRecordInfo() {
        if (blockRecordInfo == null) {
            final var states = state.createReadableStates(BlockRecordService.NAME);
            final var blockInfoState = states.<BlockInfo>getSingleton(BlockRecordService.BLOCK_INFO_STATE_KEY)
                    .get();
            final var runningHashState = states.<RunningHashes>getSingleton(BlockRecordService.RUNNING_HASHES_STATE_KEY)
                    .get();
            if (blockInfoState == null) throw new NullPointerException("state cannot be null!");
            if (runningHashState == null) throw new NullPointerException("state cannot be null!");
            blockRecordInfo = new BlockRecordInfoImpl(blockInfoState, runningHashState);
        }

        return blockRecordInfo;
    }
}
