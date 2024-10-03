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

package com.hedera.node.app.service.consensus.impl;

import static com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl.TOPIC_ALLOWANCES_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TopicAllowanceId;
import com.hedera.hapi.node.base.TopicAllowanceValue;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.config.data.TopicsConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

public class WritableTopicAllowanceStore extends ReadableTopicAllowanceStoreImpl {
    /**
     * The underlying data storage class that holds the Topic Allowances data.
     */
    private final WritableKVState<TopicAllowanceId, TopicAllowanceValue> writableState;

    /**
     * Create a new {@link WritableTopicAllowanceStore} instance.
     *
     * @param states The state to use.
     * @param configuration The configuration used to read the maximum capacity.
     * @param storeMetricsService Service that provides utilization metrics.
     */
    public WritableTopicAllowanceStore(
            @NonNull final WritableStates states,
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService) {
        super(states);
        writableState = states.get(TOPIC_ALLOWANCES_KEY);

        final long maxCapacity = configuration.getConfigData(TopicsConfig.class).maxAllowances();
        final var storeMetrics = storeMetricsService.get(StoreMetricsService.StoreType.TOPIC_ALLOWANCES, maxCapacity);
        writableState.setMetrics(storeMetrics);
    }

    /**
     * Persists a new {@link TopicAllowanceId} with given {@link TopicAllowanceValue} into the state.
     * This always replaces the existing value.
     *
     * @param allowanceId - the allowanceId to be persisted.
     * @param allowanceValue - the allowance value for the given allowanceId to be persisted.
     */
    public void put(@NonNull final TopicAllowanceId allowanceId, @NonNull final TopicAllowanceValue allowanceValue) {
        requireNonNull(allowanceId);
        requireNonNull(allowanceValue);
        writableState.put(allowanceId, allowanceValue);
    }

    /**
     * Removes the {@link TopicAllowanceValue} associated with the given {@link TopicAllowanceId} from the state.
     *
     * @param allowanceId - the allowanceId to be removed.
     */
    public void remove(@NonNull final TopicAllowanceId allowanceId) {
        requireNonNull(allowanceId);
        writableState.remove(allowanceId);
    }

    /**
     * Returns the {@link TopicAllowanceValue} associated with the given {@link TopicAllowanceId} for modification.
     *
     * @param allowanceId - the allowanceId to be modified.
     * @return the {@link TopicAllowanceValue} associated with the given {@link TopicAllowanceId}.
     */
    public TopicAllowanceValue getForModify(@NonNull final TopicAllowanceId allowanceId) {
        requireNonNull(allowanceId);
        return writableState.getForModify(allowanceId);
    }
}
