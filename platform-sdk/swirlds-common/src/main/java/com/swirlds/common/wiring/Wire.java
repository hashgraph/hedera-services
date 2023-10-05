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
     * Add a task to the wire. Similar to {@link #acceptInterruptably(Object)} except that it cannot be interrupted and
     * can block forever if backpressure is enabled.
     *
     * @param data the data to be processed by the wire
     */
    @Override
    void accept(@NonNull T data);

    /**
     * Add a task to the wire. If backpressure is enabled and being applied, this method can be interrupted.
     *
     * @param data the data to be processed by the wire
     * @throws InterruptedException if the thread is interrupted while waiting for capacity to become available
     */
    void acceptInterruptably(@NonNull T data) throws InterruptedException;
}
