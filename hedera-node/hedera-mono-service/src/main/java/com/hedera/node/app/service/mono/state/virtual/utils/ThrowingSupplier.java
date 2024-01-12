/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual.utils;

/**
 * A functional interface that represents a supplier of results. It is similar to
 * {@link java.util.function.Supplier}, but may throw checked exceptions of the
 * specified type.
 *
 * @param <T> type of results provided by this supplier
 * @param <E> checked exception type
 */
@FunctionalInterface
public interface ThrowingSupplier<T, E extends Exception> {

    /**
     * Provides a result.
     *
     * @return a result
     * @throws E if an error occurred
     */
    T get() throws E;
}
