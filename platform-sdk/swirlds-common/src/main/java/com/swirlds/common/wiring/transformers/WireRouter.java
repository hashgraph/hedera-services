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
 * Create a wire router. A wire router takes a single input and splits data into multiple outputs with different
 * addresses. When data is sent to a router, the address is also sent. The router then sends the data to the output wire
 * at the specified address.
 *
 * @param <ROUTER_TYPE> an enum that describes the addresses where data can be routed. Each enum value corresponds to a
 *                      different address where data can be routed.
 */
public class WireRouter<ROUTER_TYPE extends Enum<ROUTER_TYPE>> {

    private final BindableInputWire<RoutableData<ROUTER_TYPE>, Void> inputWire;
    private final List<StandardOutputWire<Object>> outputWires;
    private final Class<ROUTER_TYPE> clazz;

    /**
     * Constructor.
     *
     * @param model           the wiring model containing this router
     * @param routerName      the name of the router
     * @param routerInputName the label for the input wire going into the router
     * @param clazz           the class of the enum that describes the different addresses that data can be routed to.
     */
    public WireRouter(
            @NonNull final WiringModel model,
            @NonNull final String routerName,
            @NonNull final String routerInputName,
            @NonNull final Class<ROUTER_TYPE> clazz) {
        final TaskScheduler<Void> scheduler = model.schedulerBuilder(routerName)
                .withType(DIRECT_THREADSAFE)
                .build()
                .cast();

        outputWires = new ArrayList<>(clazz.getEnumConstants().length);
        for (int index = 0; index < clazz.getEnumConstants().length; index++) {
            final ROUTER_TYPE dataType = clazz.getEnumConstants()[index];
            if (dataType.ordinal() != index) {
                throw new IllegalArgumentException("Enum values must be in order");
            }

            final StandardOutputWire<Object> outputWire = scheduler.buildSecondaryOutputWire();
            outputWires.add(outputWire);
        }

        inputWire = scheduler.buildInputWire(routerInputName);
        inputWire.bindConsumer(this::routeData);
        this.clazz = clazz;
    }

    /**
     * Get the input wire.
     *
     * @return the input wire
     */
    @NonNull
    public InputWire<RoutableData<ROUTER_TYPE>> getInput() {
        return inputWire;
    }

    /**
     * Get the output wire for a specific address.
     *
     * @param address       the data type
     * @param <OUTPUT_TYPE> the type of data that the output wire carries
     * @return the output wire
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public <OUTPUT_TYPE> OutputWire<OUTPUT_TYPE> getOutput(@NonNull final ROUTER_TYPE address) {
        return (OutputWire<OUTPUT_TYPE>) outputWires.get(address.ordinal());
    }

    /**
     * Get the type of the enum that defines this router.
     *
     * @return the class of the enum
     */
    @NonNull
    public Class<ROUTER_TYPE> getRouterType() {
        return clazz;
    }

    /**
     * Route data to the appropriate output wire.
     *
     * @param routableData the data to route
     */
    private void routeData(@NonNull final RoutableData<ROUTER_TYPE> routableData) {
        final ROUTER_TYPE dataType = routableData.address();
        final StandardOutputWire<Object> outputWire = outputWires.get(dataType.ordinal());
        outputWire.forward(routableData.data());
    }
}
