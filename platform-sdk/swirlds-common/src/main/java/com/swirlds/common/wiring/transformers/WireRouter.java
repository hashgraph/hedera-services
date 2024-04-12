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

package com.swirlds.common.wiring.transformers;

import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.DIRECT_THREADSAFE;

import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.common.wiring.wires.output.StandardOutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Create a wire router. A wire router takes a single input and splits data into multiple outputs based on the data
 * type.
 *
 * @param <DATA_TYPE> an enum that describes the different types of data handled by this router. Each enum value
 *                    corresponds to a different output wire.
 */
public class WireRouter<DATA_TYPE extends Enum<DATA_TYPE> & RoutableDataType> {

    private final BindableInputWire<RoutableData<DATA_TYPE>, Void> inputWire;
    private final List<StandardOutputWire<Object>> outputWires;

    /**
     * Constructor.
     *
     * @param model the wiring model containing this router
     * @param clazz the class of the enum that describes the different types of data handled by this router
     */
    public WireRouter(@NonNull final WiringModel model, @NonNull final Class<DATA_TYPE> clazz) {
        final TaskScheduler<Void> scheduler = model.schedulerBuilder("TODO_name")
                .withType(DIRECT_THREADSAFE)
                .build()
                .cast();

        outputWires = new ArrayList<>(clazz.getEnumConstants().length);
        for (int index = 0; index < clazz.getEnumConstants().length; index++) {
            final DATA_TYPE dataType = clazz.getEnumConstants()[index];
            if (dataType.ordinal() != index) {
                throw new IllegalArgumentException("Enum values must be in order");
            }

            final StandardOutputWire<Object> outputWire = scheduler.buildSecondaryOutputWire();
            outputWires.add(outputWire);
        }

        inputWire = scheduler.buildInputWire("TODO input name");
        inputWire.bindConsumer(this::routeData);
    }

    /**
     * Get the input wire.
     *
     * @return the input wire
     */
    @NonNull
    public InputWire<RoutableData<DATA_TYPE>> getInput() {
        return inputWire;
    }

    /**
     * Get the output wire for a specific data type.
     *
     * @param dataType      the data type
     * @param <OUTPUT_TYPE> the type of data that the output wire carries
     * @return the output wire
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public <OUTPUT_TYPE> OutputWire<OUTPUT_TYPE> getOutput(@NonNull final DATA_TYPE dataType) {
        return (OutputWire<OUTPUT_TYPE>) outputWires.get(dataType.ordinal());
    }

    /**
     * Route data to the appropriate output wire.
     *
     * @param routableData the data to route
     */
    private void routeData(@NonNull final RoutableData<DATA_TYPE> routableData) {
        final DATA_TYPE dataType = routableData.type();
        final StandardOutputWire<Object> outputWire = outputWires.get(dataType.ordinal());
        outputWire.forward(routableData.data());
    }
}
