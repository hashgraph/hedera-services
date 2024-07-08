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

import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Definition of a readable store which is used to create instances of the store.
 *
 * @param storeInterface the class of the readable store
 * @param provider creates a new instance of the readable store
 * @param <T> the type of the readable store
 */
public record ReadableStoreDefinition<T>(@NonNull Class<T> storeInterface, @NonNull ReadableStoreProvider<T> provider) {

    /**
     * A provider for creating a readable store.
     *
     * @param <T> the type of the readable store
     */
    @FunctionalInterface
    public interface ReadableStoreProvider<T> {
        /**
         * Creates a new instance of the readable store.
         *
         * @param readableStates the readable state of the service
         * @return the new readable store
         */
        @NonNull
        T newInstance(@NonNull ReadableStates readableStates);
    }
}
