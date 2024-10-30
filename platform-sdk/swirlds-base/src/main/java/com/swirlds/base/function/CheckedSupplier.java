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

package com.swirlds.base.function;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Similar to {@link java.util.function.Supplier} but throws an exception.
 *
 * @param <V> the value type
 * @param <E> the type thrown by the supplier
 */
@FunctionalInterface
public interface CheckedSupplier<V, E extends Exception> {

    /**
     * Runs the runnable.
     *
     * @throws E the exception type thrown by the runnable
     */
    V get() throws E;

    /**
     * Convert a {@link Supplier} to a {@link CheckedSupplier}.
     *
     * @param supplier the supplier
     * @param <E>      the type thrown by the supplier
     * @return the {@link CheckedSupplier}
     */
    @NonNull
    static <V, E extends Exception> CheckedSupplier<V, E> of(@NonNull final Supplier<V> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return supplier::get;
    }
}
