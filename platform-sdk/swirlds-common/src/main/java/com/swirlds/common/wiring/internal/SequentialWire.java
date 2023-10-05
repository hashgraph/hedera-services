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
import edu.umd.cs.findbugs.annotations.Nullable;
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
    private final AtomicReference<SequentialTask<T>> lastTask;
    private final AbstractObjectCounter counter;

    /**
     * Constructor.
     *
     * @param consumer data on the wire is passed to this consumer
     * @param counter  an object counter that is incremented when data is added to the wire and decremented when data
     *                 has been processed, ignored if null
     */
    public SequentialWire(@NonNull final Consumer<T> consumer, @Nullable final AbstractObjectCounter counter) {

        this.lastTask = new AtomicReference<>(new SequentialTask<>(1, consumer));
        this.counter = counter;

        Objects.requireNonNull(consumer);
        if (counter == null) {
            this.consumer = consumer;
        } else {
            this.consumer = data -> {
                counter.offRamp();
                consumer.accept(data);
            };
        }
    }

    /**
     * A task in a sequential wire.
     *
     * @param <T> the type of object that is passed through the wire
     */
    private static class SequentialTask<T> extends AbstractTask {
        private final Consumer<T> consumer;
        private T data;
        private SequentialTask<T> nextTask;

        /**
         * Constructor.
         *
         * @param dependencyCount the number of dependencies that must be satisfied before this task is eligible for
         *                        execution. The first task in a sequence has a dependency count of 1 (data must be
         *                        provided), and subsequent tasks have a dependency count of 2 (the previous task must
         *                        be executed and data must be provided).
         * @param consumer        data on the wire is passed to this consumer
         */
        SequentialTask(final int dependencyCount, @NonNull Consumer<T> consumer) {
            super(dependencyCount);
            this.consumer = consumer;
        }

        /**
         * Provide a reference to the next task and the data that will be processed during the handling of this task.
         *
         * @param nextTask the task that will execute after this task
         * @param data     the data to be passed to the consumer for this task
         */
        void send(@NonNull final SequentialTask<T> nextTask, @NonNull final T data) {
            this.nextTask = nextTask;
            this.data = data;

            // This method provides us with the data we intend to send to the consumer
            // when this task is executed, thus resolving one of the two dependencies
            // required for the task to be executed. This call will decrement the
            // dependency count. If this causes the dependency count to reach 0
            // (i.e. if the previous task has already been executed), then this call
            // will cause this task to be immediately eligible for execution.
            send();
        }

        /**
         * Execute this task.
         */
        @Override
        public boolean exec() {
            consumer.accept(data);

            // Reduce the dependency count of the next task. If the next task already has its data, then this
            // method will cause the next task to be immediately eligible for execution.
            nextTask.send();
            return true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void accept(@NonNull final T data) {
        if (counter != null) {
            counter.onRamp();
        }
        handle(data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void acceptInterruptably(@NonNull T data) throws InterruptedException {
        if (counter != null) {
            counter.interruptableOnRamp();
        }
        handle(data);
    }

    /**
     * Handle data passed to the wire.
     *
     * @param data the data to be handled
     */
    private void handle(@NonNull final T data) {
        // This wire may be called by may threads, but it must serialize the results a sequence of tasks that are
        // guaranteed to be executed one at a time on the target processor. We do this by forming a dependency graph
        // from task to task, such that each task depends on the previous task.

        final SequentialTask<T> nextTask = new SequentialTask<>(2, consumer);
        SequentialTask<T> currentTask;
        do {
            currentTask = lastTask.get();
        } while (!lastTask.compareAndSet(currentTask, nextTask));
        currentTask.send(nextTask, Objects.requireNonNull(data));
    }
}
