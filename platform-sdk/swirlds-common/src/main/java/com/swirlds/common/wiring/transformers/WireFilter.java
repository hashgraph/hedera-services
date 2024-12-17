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

package com.swirlds.common.wiring.transformers;

import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Filters out data, allowing some objects to pass and blocking others.
 *
 * @param <T> the type of data being filtered
 */
public class WireFilter<T> {

    private final BindableInputWire<T, T> inputWire;
    private final OutputWire<T> outputWire;

    /**
     * Constructor. Immediately binds the transformation function to the input wire.
     *
     * @param model           the wiring model containing this output channel
     * @param filterName      the name of the filter
     * @param filterInputName the label for the input wire going into the filter
     * @param predicate       only data that causes this method to return true is forwarded. This method must be very
     *                        fast. Putting large amounts of work into this transformer violates the intended usage
     *                        pattern of the wiring framework and may result in very poor system performance.
     */
    public WireFilter(
            @NonNull final WiringModel model,
            @NonNull final String filterName,
            @NonNull final String filterInputName,
            @NonNull final Predicate<T> predicate) {

        Objects.requireNonNull(predicate);

        final TaskScheduler<T> taskScheduler = model.schedulerBuilder(filterName)
                .withType(TaskSchedulerType.DIRECT_THREADSAFE)
                .build()
                .cast();

        inputWire = taskScheduler.buildInputWire(filterInputName);
        inputWire.bind(t -> {
            if (predicate.test(t)) {
                return t;
            }
            return null;
        });
        outputWire = taskScheduler.getOutputWire();
    }

    /**
     * Constructor.
     *
     * @param model           the wiring model containing this output channel
     * @param filterName      the name of the filter
     * @param filterInputName the label for the input wire going into the filter
     */
    public WireFilter(
            @NonNull final WiringModel model, @NonNull final String filterName, @NonNull final String filterInputName) {

        final TaskScheduler<T> taskScheduler = model.schedulerBuilder(filterName)
                .withType(TaskSchedulerType.DIRECT_THREADSAFE)
                .build()
                .cast();

        inputWire = taskScheduler.buildInputWire(filterInputName);
        outputWire = taskScheduler.getOutputWire();
    }

    /**
     * Get the input wire for this filter.
     *
     * @return the input wire
     */
    @NonNull
    public InputWire<T> getInputWire() {
        return inputWire;
    }

    /**
     * Get the output wire for this filter.
     *
     * @return the output wire
     */
    @NonNull
    public OutputWire<T> getOutputWire() {
        return outputWire;
    }

    /**
     * Bind a predicate to this filter. Should not be called if this object was constructed using
     * {@link #WireFilter(WiringModel, String, String, Predicate)}. Must be called prior to use if this object was
     * constructed using {@link #WireFilter(WiringModel, String, String)}.
     *
     * @param predicate the predicate to bind
     */
    public void bind(@NonNull final Predicate<T> predicate) {
        inputWire.bind(t -> {
            if (predicate.test(t)) {
                return t;
            }
            return null;
        });
    }
}
