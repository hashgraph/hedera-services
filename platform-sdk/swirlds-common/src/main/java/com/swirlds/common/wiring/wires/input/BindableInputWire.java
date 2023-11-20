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

package com.swirlds.common.wiring.wires.input;

import com.swirlds.common.wiring.TaskScheduler;
import com.swirlds.common.wiring.model.internal.StandardWiringModel;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An input wire that can be bound to an implementation.
 *
 * @param <IN>  the type of data that passes into the wire
 * @param <OUT> the type of the primary output wire for the scheduler that is associated with this object
 */
public class BindableInputWire<IN, OUT> extends InputWire<IN> {

    private final TaskSchedulerInput<OUT> taskSchedulerInput;
    private final String taskSchedulerName;
    private final StandardWiringModel model;

    /**
     * Constructor.
     *
     * @param model         the wiring model containing this input wire
     * @param taskScheduler the scheduler to insert data into
     * @param name          the name of the input wire
     */
    public BindableInputWire(
            @NonNull final StandardWiringModel model,
            @NonNull final TaskScheduler<OUT> taskScheduler,
            @NonNull final String name) {
        super(taskScheduler, name);
        this.model = Objects.requireNonNull(model);
        taskSchedulerInput = Objects.requireNonNull(taskScheduler);
        taskSchedulerName = taskScheduler.getName();

        model.registerInputWireCreation(taskSchedulerName, name);
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
    public final <A, B> BindableInputWire<A, B> cast() {
        return (BindableInputWire<A, B>) this;
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
    public <X> BindableInputWire<X, OUT> withInputType(@NonNull final Class<X> inputType) {
        return (BindableInputWire<X, OUT>) this;
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
    public BindableInputWire<IN, OUT> bind(@NonNull final Consumer<IN> handler) {
        Objects.requireNonNull(handler);
        setHandler((Consumer<Object>) handler);
        model.registerInputWireBinding(taskSchedulerName, getName());

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
    public BindableInputWire<IN, OUT> bind(@NonNull final Function<IN, OUT> handler) {
        Objects.requireNonNull(handler);
        setHandler(i -> {
            final OUT output = handler.apply((IN) i);
            if (output != null) {
                taskSchedulerInput.forward(output);
            }
        });

        return this;
    }
}
