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

import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.service.admin.FreezeService;
import com.hedera.node.app.service.admin.impl.ReadableSpecialFileStore;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.app.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Simple implementation of {@link QueryContext}.
 */
public class QueryContextImpl implements QueryContext {

    // TODO: Replace this once the lookup of stores is implemented
    private static final Map<Class<?>, Function<HederaState, ?>> STORE_FACTORY = Map.of(
            ReadableAccountStore.class, s -> new ReadableAccountStore(s.createReadableStates(TokenService.NAME)),
            ReadableTokenStore.class, s -> new ReadableTokenStore(s.createReadableStates(TokenService.NAME)),
            ReadableTopicStore.class, s -> new ReadableTopicStore(s.createReadableStates(ConsensusService.NAME)),
            ReadableScheduleStore.class, s -> new ReadableScheduleStore(s.createReadableStates(ScheduleService.NAME)),
            ReadableSpecialFileStore.class,
                    s -> new ReadableSpecialFileStore(s.createReadableStates(FreezeService.NAME)));

    private final HederaState state;
    private final Query query;

    /**
     * Constructor of {@code QueryContextImpl}.
     *
     * @param state the state that is valid for the query
     * @param query the query that is currently being processed
     * @throws NullPointerException if {@code query} is {@code null}
     */
    public QueryContextImpl(@NonNull final HederaState state, @NonNull final Query query) {
        this.state = Objects.requireNonNull(state);
        this.query = Objects.requireNonNull(query);
    }

    @Override
    @NonNull
    public Query query() {
        return query;
    }

    @Override
    @NonNull
    public <T> T createStore(@NonNull Class<T> storeInterface) {
        final var result = STORE_FACTORY.get(storeInterface);
        if (result != null) {
            return storeInterface.cast(result.apply(state));
        }
        throw new IllegalArgumentException("No store of the given class is available");
    }
}
