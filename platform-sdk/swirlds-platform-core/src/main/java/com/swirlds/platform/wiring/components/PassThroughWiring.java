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
 * <p>
 * This pass through wiring object operating on a sequential scheduler is a workaround for the following problem that
 * concurrent schedulers currently have with pushing tasks directly to components that apply backpressure:
 * <p>
 * If the component immediately following a concurrent scheduler applies backpressure, the concurrent scheduler will
 * continue doing work and parking each result, unable to pass the unit of work along. This results in a proliferation
 * of parked threads.
 * <p>
 * One example usage is in collecting hashes from the concurrent execution of the
 * {@link com.swirlds.platform.event.hashing.EventHasher}. The "postHashCollectorWiring" in
 * {@link com.swirlds.platform.wiring.PlatformWiring} is a pass through wiring object with a sequential scheduler that
 * shares a combined object counter with the preceding concurrent scheduler. Since the pair of schedulers share a
 * counter, the sequential scheduler does *not* apply backpressure to the concurrent scheduler. Instead, "finished"
 * hashing tasks will wait in the queue of the sequential scheduler until the next component in the pipeline is ready to
 * receive them. The concurrent scheduler will refuse to accept additional work based on the number of tasks that are
 * waiting in the sequential scheduler's queue.
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
     * @param inputLabel    the label for the input wire
     * @param componentName the name of the component
     * @param schedulerType the type of scheduler to use
     */
    public PassThroughWiring(
            @NonNull final WiringModel model,
            @NonNull final String inputLabel,
            @NonNull final String componentName,
            @NonNull final TaskSchedulerType schedulerType) {
        this(
                model,
                inputLabel,
                model.schedulerBuilder(Objects.requireNonNull(componentName))
                        .withType(Objects.requireNonNull(schedulerType))
                        .build()
                        .cast());
    }

    /**
     * PassThrough Constructor for creating a no-op component with the given scheduler.
     *
     * @param model      the wiring model containing this input wire
     * @param inputLabel the label for the input wire
     * @param scheduler  the scheduler to use
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
