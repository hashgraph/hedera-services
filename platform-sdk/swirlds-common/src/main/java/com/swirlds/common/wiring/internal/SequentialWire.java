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

package com.swirlds.common.wiring.internal;

import com.swirlds.common.wiring.Wire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A {@link Wire} that guarantees that tasks are executed sequentially in the order they are received.
 *
 * @param <T> the type of object that is passed through the wire
 */
public class SequentialWire<T> implements Wire<T> {
    private final Consumer<T> consumer;
    private final AtomicReference<Task<T>> lastTask;

    /**
     * Constructor.
     *
     * @param consumer all data on this wire is passed to this consumer in sequential order
     */
    public SequentialWire(@NonNull final Consumer<T> consumer) {
        this.consumer = Objects.requireNonNull(consumer);
        lastTask = new AtomicReference<>(new Task<>(consumer, true));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void accept(@NonNull final T t) {

        // This wire may be called by may threads, but it must serialize the results a sequence of tasks that are
        // guaranteed to be executed one at a time on the target processor. We do this by forming a dependency graph
        // from task to task, such that each task depends on the previous task.

        final Task<T> nextTask = new Task<>(consumer, false);
        Task<T> currentTask;
        do {
            currentTask = lastTask.get();
        } while (!lastTask.compareAndSet(currentTask, nextTask));
        currentTask.provideObjectForConsumer(t, nextTask);
    }
}
