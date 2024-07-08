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

package com.hedera.node.app.spi.store;

import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Definition of a writable store which is used to create instances of the store.
 *
 * @param storeInterface the class of the writable store
 * @param provider creates a new instance of the writable store
 * @param <T> the type of the writable store
 */
public record WritableStoreDefinition<T>(@NonNull Class<T> storeInterface, @NonNull WritableStoreProvider<T> provider) {

    /**
     * A provider for creating a writable store.
     *
     * @param <T> the type of the writable store
     */
    @FunctionalInterface
    public interface WritableStoreProvider<T> {
        /**
         * Creates a new instance of the writable store.
         *
         * @param configuration the node configuration
         * @param storeMetricsService Service that provides utilization metrics.
         * @param writableStates the writable state of the service
         * @return the new writable store
         */
        @NonNull
        T newInstance(
                @NonNull WritableStates writableStates,
                @NonNull Configuration configuration,
                @NonNull StoreMetricsService storeMetricsService);
    }
}
