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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * A unit of work that is handled by a {@link SequentialWire}.
 */
final class Task<T> extends ForkJoinTask<Void> {

    /**
     * The task that will run after this task.
     */
    private Task nextTask;

    /**
     * Used to decide when it is appropriate to execute this task. When this reaches zero, the task is eligible for
     * execution.
     * <p>
     * Most tasks start out with a count of 2. When the work is provided via
     * {@link #provideObjectForConsumer(Object, Task)}, the count is decremented by one. When the previous task has
     * been completed, the count is decremented by one again. It is possible for the work to be provided prior to the
     * previous task being executed. It is also possible for the previous task to be executed prior to the work being
     * provided. In either case, both must happen before this task can be executed.
     * <p>
     * A special case is the first task ever created. This starts with a count of 1, since there is no prior task. In
     * order for the first task to be executed, only work must be provided.
     */
    private final AtomicInteger count;

    private final Consumer<T> consumer;

    private T t;

    /**
     * Constructor.
     *
     * @param initial if true, this is the first task for this wire. False for all tasks after the first.
     */
    public Task(@NonNull final Consumer<T> consumer, final boolean initial) {
        this.consumer = Objects.requireNonNull(consumer);
        count = new AtomicInteger(initial ? 1 : 2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Void getRawResult() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setRawResult(Void value) {}

    /**
     * Provide the object to be sent to the consumer and set the next task to that will follow this task.
     *
     * @param nextTask the task that comes after this task
     * @param t        the data to send to the consumer
     */
    public void provideObjectForConsumer(@NonNull final T t, @NonNull final Task<T> nextTask) {

        this.nextTask = Objects.requireNonNull(nextTask);
        this.t = Objects.requireNonNull(t);
        decrementCountAndMaybeExecute();
    }

    /**
     * Decrement the count. If this action causes the count to reach zero then the task is executed.
     */
    private void decrementCountAndMaybeExecute() {
        if (count.decrementAndGet() == 0) {
            // This task is ready to execute
            fork();
        }
    }

    /**
     * Execute the task.
     */
    @Override
    public boolean exec() {
        // Delegate
        consumer.accept(t);
        nextTask.decrementCountAndMaybeExecute();
        return true;
    }
}
