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
 */
public class SequentialWire extends Wire {
    private final String name;
    private final AtomicReference<SequentialTask> lastTask;

    /**
     * Constructor.
     *
     * @param name the name of the wire
     */
    public SequentialWire(@NonNull final String name) {
        this.name = Objects.requireNonNull(name);
        this.lastTask = new AtomicReference<>(new SequentialTask(1));
    }

    /**
     * A task in a sequential wire.
     */
    private static class SequentialTask extends AbstractTask {
        private Consumer<Object> handler;
        private Object data;
        private SequentialTask nextTask;

        /**
         * Constructor.
         *
         * @param dependencyCount the number of dependencies that must be satisfied before this task is eligible for
         *                        execution. The first task in a sequence has a dependency count of 1 (data must be
         *                        provided), and subsequent tasks have a dependency count of 2 (the previous task must
         *                        be executed and data must be provided).
         */
        SequentialTask(final int dependencyCount) {
            super(dependencyCount);
        }

        /**
         * Provide a reference to the next task and the data that will be processed during the handling of this task.
         *
         * @param nextTask the task that will execute after this task
         * @param handler  the method that will be called when this task is executed
         * @param data     the data to be passed to the consumer for this task
         */
        void send(
                @NonNull final SequentialTask nextTask,
                @NonNull final Consumer<Object> handler,
                @NonNull final Object data) {
            this.nextTask = nextTask;
            this.handler = handler;
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
            try {
                handler.accept(data);
            } finally {
                // Reduce the dependency count of the next task. If the next task already has its data, then this
                // method will cause the next task to be immediately eligible for execution.
                nextTask.send();
            }

            return true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void put(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        // This wire may be called by may threads, but it must serialize the results a sequence of tasks that are
        // guaranteed to be executed one at a time on the target processor. We do this by forming a dependency graph
        // from task to task, such that each task depends on the previous task.

        final SequentialTask nextTask = new SequentialTask(2);
        SequentialTask currentTask;
        do {
            currentTask = lastTask.get();
        } while (!lastTask.compareAndSet(currentTask, nextTask));
        currentTask.send(nextTask, handler, data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void interruptablePut(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        // This wire may be called by may threads, but it must serialize the results a sequence of tasks that are
        // guaranteed to be executed one at a time on the target processor. We do this by forming a dependency graph
        // from task to task, such that each task depends on the previous task.

        final SequentialTask nextTask = new SequentialTask(2);
        SequentialTask currentTask;
        do {
            currentTask = lastTask.get();
        } while (!lastTask.compareAndSet(currentTask, nextTask));
        currentTask.send(nextTask, handler, data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean offer(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        // This wire may be called by may threads, but it must serialize the results a sequence of tasks that are
        // guaranteed to be executed one at a time on the target processor. We do this by forming a dependency graph
        // from task to task, such that each task depends on the previous task.

        final SequentialTask nextTask = new SequentialTask(2);
        SequentialTask currentTask;
        do {
            currentTask = lastTask.get();
        } while (!lastTask.compareAndSet(currentTask, nextTask));
        currentTask.send(nextTask, handler, data);

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void inject(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        // This wire may be called by may threads, but it must serialize the results a sequence of tasks that are
        // guaranteed to be executed one at a time on the target processor. We do this by forming a dependency graph
        // from task to task, such that each task depends on the previous task.

        final SequentialTask nextTask = new SequentialTask(2);
        SequentialTask currentTask;
        do {
            currentTask = lastTask.get();
        } while (!lastTask.compareAndSet(currentTask, nextTask));
        currentTask.send(nextTask, handler, data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getUnprocessedTaskCount() {
        return -1;
    }
}
