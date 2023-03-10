/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.io.utility;

import java.io.IOException;
import java.util.function.Function;

/**
 * Similar to {@link Function} but throws an {@link IOException}.
 *
 * @param <T> the type of the function argument
 * @param <R> the return type of the function
 */
@FunctionalInterface
public interface IOFunction<T, R> {

    /**
     * Take a value and return another.
     *
     * @param t the argument
     * @return the result
     * @throws IOException if an I/O error occurs
     */
    R apply(T t) throws IOException;
}
