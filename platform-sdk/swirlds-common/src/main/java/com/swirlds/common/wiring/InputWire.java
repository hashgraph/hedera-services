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
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An object that can insert work to be handled by a {@link TaskScheduler}.
 *
 * @param <IN> the type of data that passes into the wire
 * @param <OUT> the type of the data that comes out of the parent {@link TaskScheduler}'s primary output wire
 */
public class InputWire<IN, OUT> {

    private final TaskScheduler<OUT> taskScheduler;
    private Consumer<Object> handler;
    private final String name;

    /**
     * Constructor.
     *
     * @param taskScheduler the scheduler to insert data into
     * @param name          the name of the input wire
     */
    InputWire(@NonNull final TaskScheduler<OUT> taskScheduler, @NonNull final String name) {
        this.taskScheduler = Objects.requireNonNull(taskScheduler);
        this.name = Objects.requireNonNull(name);
    }

    /**
     * Get the name of this input wire.
     *
     * @return the name of this input wire
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Get the name of the task scheduler this input channel is bound to.
     *
     * @return the name of the wire this input channel is bound to
     */
    @NonNull
    public String getTaskSchedulerName() {
        return taskScheduler.getName();
    }

    /**
     * Cast this input wire into whatever a variable is expecting. Sometimes the compiler gets confused with generics,
     * and path of least resistance is to just cast to the proper data type.
     *
     * <p>
     * Warning: this will appease the compiler, but it is possible to cast a wire into a data type that will cause
     * runtime exceptions. Use with appropriate caution.
     *
     * @param <A> the input type to cast to
     * @param <B> the output type to cast to
     * @return this, cast into whatever type is requested
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public final <A, B> InputWire<A, B> cast() {
        return (InputWire<A, B>) this;
    }

    /**
     * Convenience method for creating an input wire with a specific input type. This method is useful for when the
     * compiler can't figure out the generic type of the input wire. This method is a no op.
     *
     * @param inputType the input type of the input wire
     * @param <X>       the input type of the input wire
     * @return this
     */
    @SuppressWarnings("unchecked")
    public <X> InputWire<X, OUT> withInputType(@NonNull final Class<X> inputType) {
        return (InputWire<X, OUT>) this;
    }

    /**
     * Bind this input wire to a handler. A handler must be bound to this input wire prior to inserting data via any
     * method.
     *
     * @param handler the handler to bind to this input wire
     * @return this
     * @throws IllegalStateException if a handler is already bound and this method is called a second time
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public InputWire<IN, OUT> bind(@NonNull final Consumer<IN> handler) {
        if (this.handler != null) {
            throw new IllegalStateException("Input wire \"" + name + "\" already bound");
        }
        this.handler = (Consumer<Object>) Objects.requireNonNull(handler);

        return this;
    }

    /**
     * Bind this input wire to a handler. A handler must be bound to this inserter prior to inserting data via any
     * method.
     *
     * @param handler the handler to bind to this input task scheduler
     * @return this
     * @throws IllegalStateException if a handler is already bound and this method is called a second time
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public InputWire<IN, OUT> bind(@NonNull final Function<IN, OUT> handler) {
        if (this.handler != null) {
            throw new IllegalStateException("Handler already bound");
        }
        this.handler = i -> {
            final OUT output = handler.apply((IN) i);
            if (output != null) {
                taskScheduler.forward(output);
            }
        };

        return this;
    }

    /**
     * Add a task to the task scheduler. May block if back pressure is enabled.
     *
     * @param data the data to be processed by the task scheduler
     */
    public void put(@Nullable final IN data) {
        taskScheduler.put(handler, data);
    }

    /**
     * Add a task to the task scheduler. If backpressure is enabled and there is not immediately capacity available,
     * this method will not accept the data.
     *
     * @param data the data to be processed by the task scheduler
     * @return true if the data was accepted, false otherwise
     */
    public boolean offer(@Nullable final IN data) {
        return taskScheduler.offer(handler, data);
    }

    /**
     * Inject data into the task scheduler, doing so even if it causes the number of unprocessed tasks to exceed the
     * capacity specified by configured back pressure. If backpressure is disabled, this operation is logically
     * equivalent to {@link #put(Object)}.
     *
     * @param data the data to be processed by the task scheduler
     */
    public void inject(@Nullable final IN data) {
        taskScheduler.inject(handler, data);
    }
}
