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
import java.util.function.Consumer;

/**
 * Similar to {@link java.util.function.Consumer} but throws an exception.
 *
 * @param <T> the type accepted by the consumer
 * @param <E> the type thrown by the consumer
 */
@FunctionalInterface
public interface CheckedConsumer<T, E extends Exception> {

    /**
     * Accept the value.
     *
     * @param t the value to accept
     * @throws E the exception type thrown by the consumer
     */
    void accept(T t) throws E;

    /**
     * Convert a {@link Consumer} to a {@link CheckedConsumer}.
     *
     * @param consumer the consumer
     * @param <T>      the type accepted by the consumer
     * @param <E>      the type thrown by the consumer
     * @return the {@link CheckedConsumer}
     */
    static <T, E extends Exception> CheckedConsumer<T, E> of(@NonNull final Consumer<T> consumer) {
        ArgumentUtils.throwArgNull(consumer, "consumer");
        return consumer::accept;
    }
}
