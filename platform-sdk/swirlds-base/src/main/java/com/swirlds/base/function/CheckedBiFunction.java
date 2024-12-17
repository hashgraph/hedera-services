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
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Similar to {@link BiFunction} but throws an exception.
 *
 * @param <T> the type of the first arg accepted by the function
 * @param <U> the type of the second arg accepted by the function
 * @param <R> the return type of the function
 * @param <E> the type thrown by the function
 */
@FunctionalInterface
public interface CheckedBiFunction<T, U, R, E extends Exception> {

    /**
     * Apply the function.
     *
     * @param value1 the first input to the function
     * @param value2 the second input to the function
     * @return the value returned by the function
     * @throws E the exception type thrown by the function
     */
    @Nullable
    R apply(@Nullable T value1, @Nullable U value2) throws E;

    /**
     * Convert a {@link BiFunction} to a {@link CheckedBiFunction}.
     *
     * @param function the function
     * @param <T>      the type of the first arg accepted by the function
     * @param <U>      the type of the second arg accepted by the function
     * @param <R>      the return type of the function
     * @param <E>      the type thrown by the function
     * @return the {@link CheckedBiFunction}
     */
    @NonNull
    static <T, U, R, E extends Exception> CheckedBiFunction<T, U, R, E> of(
            @NonNull final BiFunction<T, U, R> function) {
        Objects.requireNonNull(function, "function must not be null");
        return function::apply;
    }
}
