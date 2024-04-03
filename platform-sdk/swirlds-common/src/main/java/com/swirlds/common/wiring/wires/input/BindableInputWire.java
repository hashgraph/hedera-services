/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.wiring.model.internal.StandardWiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An input wire that can be bound to an implementation.
 *
 * @param <IN>  the type of data that passes into the wire
 * @param <OUT> the type of the primary output wire for the scheduler that is associated with this object
 */
public class BindableInputWire<IN, OUT> extends InputWire<IN> implements Bindable<IN, OUT> {

    private final TaskSchedulerInput<OUT> taskSchedulerInput;
    private final String taskSchedulerName;
    private final StandardWiringModel model;

    /**
     * Supplier for whether the task scheduler is currently squelching.
     * <p>
     * As long as this supplier returns true, the handler will be executed as a no-op, and no data will be forwarded.
     */
    private final Supplier<Boolean> currentlySquelching;

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
        currentlySquelching = taskScheduler::currentlySquelching;

        model.registerInputWireCreation(taskSchedulerName, name);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public void bindConsumer(@NonNull final Consumer<IN> handler) {
        Objects.requireNonNull(handler);
        setHandler(i -> {
            if (currentlySquelching.get()) {
                return;
            }

            handler.accept((IN) i);
        });
        model.registerInputWireBinding(taskSchedulerName, getName());
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public void bind(@NonNull final Function<IN, OUT> handler) {
        Objects.requireNonNull(handler);
        setHandler(i -> {
            if (currentlySquelching.get()) {
                return;
            }

            final OUT output = handler.apply((IN) i);
            if (output != null) {
                taskSchedulerInput.forward(output);
            }
        });
        model.registerInputWireBinding(taskSchedulerName, getName());
    }
}
