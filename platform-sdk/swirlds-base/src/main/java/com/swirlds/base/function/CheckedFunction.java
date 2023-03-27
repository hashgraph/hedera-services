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

package com.swirlds.base.function;

import com.swirlds.base.ArgumentUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.Function;

/**
 * Similar to {@link java.util.function.Function} but throws an exception.
 *
 * @param <T>
 * 		the type accepted by the function
 * @param <R>
 * 		the return type of the function
 * @param <E>
 * 		the type thrown by the function
 */
@FunctionalInterface
public interface CheckedFunction<T, R, E extends Exception> {

    /**
     * Apply the function.
     *
     * @param t the input to the function
     * @return the value returned by the function
     * @throws E the exception type thrown by the function
     */
    @Nullable
    R apply(@Nullable T t) throws E;

    /**
     * Convert a {@link Function} to a {@link CheckedFunction}.
     *
     * @param function the function
     * @param <T>      the type accepted by the function
     * @param <R>      the return type of the function
     * @param <E>      the type thrown by the function
     * @return the {@link CheckedFunction}
     */
    static <T, R, E extends Exception> CheckedFunction<T, R, E> of(@NonNull final Function<T, R> function) {
        ArgumentUtils.throwArgNull(function, "function");
        return function::apply;
    }
}
