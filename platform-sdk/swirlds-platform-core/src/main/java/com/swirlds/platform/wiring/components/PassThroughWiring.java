/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.wiring.components;

import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Function;

/**
 * PassThrough Component Wiring, useful for wiring no-op components with the given scheduler type.
 *
 * @param <DATA_TYPE> the type of input and output for this component wiring
 */
public class PassThroughWiring<DATA_TYPE> {

    private final BindableInputWire<DATA_TYPE, DATA_TYPE> input;
    private final OutputWire<DATA_TYPE> output;
    private final TaskScheduler<DATA_TYPE> scheduler;

    /**
     * PassThrough Constructor for creating a no-op component with the given scheduler type.
     *
     * @param model         the wiring model containing this input wire
     * @param componentName the name of the component
     * @param inputLabel    the label for the input wire
     * @param schedulerType the type of scheduler to use
     */
    public PassThroughWiring(
            @NonNull final WiringModel model,
            @NonNull final String componentName,
            @NonNull final String inputLabel,
            @NonNull final TaskSchedulerType schedulerType) {
        Objects.requireNonNull(model);
        Objects.requireNonNull(componentName);
        Objects.requireNonNull(inputLabel);
        Objects.requireNonNull(schedulerType);

        scheduler = model.schedulerBuilder(componentName)
                .withType(schedulerType)
                .build()
                .cast();

        input = scheduler.buildInputWire(inputLabel);
        input.bind(Function.identity());
        output = scheduler.getOutputWire();
    }

    /**
     * PassThrough Constructor for creating a no-op component with the given scheduler.
     *
     * @param model         the wiring model containing this input wire
     * @param inputLabel    the label for the input wire
     * @param scheduler     the scheduler to use
     */
    public PassThroughWiring(
            @NonNull final WiringModel model,
            @NonNull final String inputLabel,
            @NonNull final TaskScheduler<DATA_TYPE> scheduler) {
        Objects.requireNonNull(model);
        Objects.requireNonNull(inputLabel);
        this.scheduler = Objects.requireNonNull(scheduler);

        input = scheduler.buildInputWire(inputLabel);
        input.bind(Function.identity());
        output = scheduler.getOutputWire();
    }

    /**
     * Get the scheduler for this component.
     *
     * @return the scheduler
     */
    @NonNull
    public TaskScheduler<DATA_TYPE> getScheduler() {
        return scheduler;
    }

    /**
     * Get the input wire for this component.
     *
     * @return the input wire
     */
    @NonNull
    public InputWire<DATA_TYPE> getInputWire() {
        return input;
    }

    /**
     * Get the output wire for this component.
     *
     * @return the output wire
     */
    @NonNull
    public OutputWire<DATA_TYPE> getOutputWire() {
        return output;
    }
}
