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
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for gossip.
 *
 * @param eventInput  the input wire for events received from peers during gossip
 * @param eventOutput the output wire for events received from peers during gossip
 */
public record GossipWiring(@NonNull InputWire<GossipEvent> eventInput, @NonNull OutputWire<GossipEvent> eventOutput) {

    // TODO delete

    /**
     * Create a new instance of {@link GossipWiring}.
     *
     * @param model the wiring model
     * @return the new instance
     */
    @NonNull
    public static GossipWiring create(@NonNull final WiringModel model) {
        final TaskScheduler<GossipEvent> scheduler = model.schedulerBuilder("gossip")
                .withType(TaskSchedulerType.DIRECT_THREADSAFE)
                .build()
                .cast();

        final BindableInputWire<GossipEvent, GossipEvent> inputWire = scheduler.buildInputWire("received events");
        inputWire.bind(x -> x);

        final OutputWire<GossipEvent> outputWire = scheduler.getOutputWire();

        return new GossipWiring(inputWire, outputWire);
    }
}
