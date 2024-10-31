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

package com.swirlds.base.utility;

/**
 * Functional interface that represents a function that accepts one argument and produces a result. This variant is
 * capable of throwing a checked exception.
 *
 * @param <T> the type of the input to the function.
 * @param <R> the type of the result of the function.
 */
@FunctionalInterface
public interface ThrowableFunction<T, R> {
    /**
     * Applies this function to the given argument.
     *
     * @param t the function argument.
     * @return the function result.
     * @throws Throwable if an error occurs.
     */
    R apply(T t) throws Throwable;
}
