/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import static org.hiero.wiring.framework.model.diagram.HyperlinkBuilder.platformCoreHyperlink;
import static org.hiero.wiring.framework.schedulers.builders.TaskSchedulerType.DIRECT_THREADSAFE;

import com.swirlds.common.stream.RunningEventHashOverride;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hiero.wiring.framework.model.WiringModel;
import org.hiero.wiring.framework.schedulers.TaskScheduler;
import org.hiero.wiring.framework.wires.input.BindableInputWire;
import org.hiero.wiring.framework.wires.input.InputWire;
import org.hiero.wiring.framework.wires.output.OutputWire;

/**
 * A wiring object for distributing {@link RunningEventHashOverride}s
 *
 * @param runningHashUpdateInput  the input wire for running hash updates to be distributed
 * @param runningHashUpdateOutput the output wire for running hash updates to be distributed
 */
public record RunningEventHashOverrideWiring(
        @NonNull InputWire<RunningEventHashOverride> runningHashUpdateInput,
        @NonNull OutputWire<RunningEventHashOverride> runningHashUpdateOutput) {

    /**
     * Create a new wiring object
     *
     * @param model the wiring model
     * @return the new wiring object
     */
    @NonNull
    public static RunningEventHashOverrideWiring create(@NonNull final WiringModel model) {

        final TaskScheduler<RunningEventHashOverride> taskScheduler = model.schedulerBuilder("RunningEventHashOverride")
                .withType(DIRECT_THREADSAFE)
                .withHyperlink(platformCoreHyperlink(RunningEventHashOverrideWiring.class))
                .build()
                .cast();

        final BindableInputWire<RunningEventHashOverride, RunningEventHashOverride> inputWire =
                taskScheduler.buildInputWire("hash override");
        final RunningEventHashOverrideWiring wiring =
                new RunningEventHashOverrideWiring(inputWire, taskScheduler.getOutputWire());

        // this is just a pass through method
        inputWire.bind(runningHashUpdate -> runningHashUpdate);

        return wiring;
    }
}
