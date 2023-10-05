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

package com.swirlds.common.wiring;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;

/**
 * Wires two components together.
 *
 * @param <T> the type of object that is passed through the wire
 */
public interface Wire<T> extends Consumer<T> {

    /**
     * Get a new wire builder.
     *
     * @param consumer tasks are passed to this consumer
     * @param <T>      the type of object that is passed through the wire
     * @return a new wire builder
     */
    static <T> WireBuilder<T> builder(@NonNull final Consumer<T> consumer) {
        return new WireBuilder<>(consumer);
    }

    /**
     * Add a task to the wire.
     *
     * @param t the task
     */
    @Override
    void accept(@NonNull T t);
}
