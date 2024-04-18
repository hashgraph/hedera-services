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
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.wiring.NoInput;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for gossip.
 */
public class NewGossipWiring {

    private final WiringModel model;
    private final BindableInputWire<GossipEvent, Void> eventInput;
    private final BindableInputWire<EventWindow, Void> eventWindowInput;
    private final BindableInputWire<NoInput, Void> startInput;
    private final BindableInputWire<NoInput, Void> stopInput;
    private final OutputWire<GossipEvent> eventOutput;

    public NewGossipWiring(@NonNull final WiringModel model) {
        this.model = model;
        final TaskScheduler<Void> scheduler = model.schedulerBuilder("gossip")
                .withType(TaskSchedulerType.DIRECT_THREADSAFE)
                .build()
                .cast();

        eventInput = scheduler.buildInputWire("received events");
        eventWindowInput = scheduler.buildInputWire("event window");
        startInput = scheduler.buildInputWire("start");
        stopInput = scheduler.buildInputWire("stop");
        eventOutput = scheduler.buildSecondaryOutputWire();
    }


    /**
     * Bind the wiring to a gossip implementation.
     *
     * @param gossip the gossip implementation
     */
    public void bind(@NonNull final Gossip gossip) {
        gossip.bind(model, eventInput, eventWindowInput, startInput, stopInput, eventOutput);
    }

}
