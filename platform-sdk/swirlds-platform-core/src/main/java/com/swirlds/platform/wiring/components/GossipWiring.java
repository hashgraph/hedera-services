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
import com.swirlds.common.wiring.wires.output.StandardOutputWire;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.wiring.NoInput;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for gossip.
 */
public class GossipWiring {

    private final WiringModel model;
    private final TaskScheduler<Void> scheduler;
    private final BindableInputWire<GossipEvent, Void> eventInput;
    private final BindableInputWire<EventWindow, Void> eventWindowInput;
    private final BindableInputWire<NoInput, Void> startInput;
    private final BindableInputWire<NoInput, Void> stopInput;
    private final BindableInputWire<NoInput, Void> clearInput;
    private final BindableInputWire<NoInput, Void> resetFallenBehindInput;
    private final StandardOutputWire<GossipEvent> eventOutput;

    public GossipWiring(@NonNull final WiringModel model) {
        this.model = model;

        // TODO use configuration for this
        scheduler = model.schedulerBuilder("gossip")
                .withType(TaskSchedulerType.SEQUENTIAL)
                .withFlushingEnabled(true)
                .withUnhandledTaskCapacity(500)
                .build()
                .cast();

        eventInput = scheduler.buildInputWire("events to gossip");
        eventWindowInput = scheduler.buildInputWire("event window");
        startInput = scheduler.buildInputWire("start");
        stopInput = scheduler.buildInputWire("stop");
        clearInput = scheduler.buildInputWire("clear");
        resetFallenBehindInput = scheduler.buildInputWire("reset fallen behind");
        eventOutput = scheduler.buildSecondaryOutputWire();
    }

    /**
     * Bind the wiring to a gossip implementation.
     *
     * @param gossip the gossip implementation
     */
    public void bind(@NonNull final Gossip gossip) {
        gossip.bind(
                model,
                eventInput,
                eventWindowInput,
                startInput,
                stopInput,
                clearInput,
                resetFallenBehindInput,
                eventOutput);
    }

    /**
     * Get the input wire for events to be gossiped to the network.
     *
     * @return the input wire for events
     */
    @NonNull
    public InputWire<GossipEvent> getEventInput() {
        return eventInput;
    }

    /**
     * Get the input wire for the current event window.
     *
     * @return the input wire for the event window
     */
    @NonNull
    public InputWire<EventWindow> getEventWindowInput() {
        return eventWindowInput;
    }

    /**
     * Get the input wire to start gossip.
     *
     * @return the input wire to start gossip
     */
    @NonNull
    public InputWire<NoInput> getStartInput() {
        return startInput;
    }

    /**
     * Get the input wire to stop gossip.
     *
     * @return the input wire to stop gossip
     */
    @NonNull
    public InputWire<NoInput> getStopInput() {
        return stopInput;
    }

    /**
     * Get the input wire to clear the gossip state.
     */
    @NonNull
    public InputWire<NoInput> getClearInput() {
        return clearInput;
    }

    /**
     * Get the input wire to reset the fallen behind flag.
     *
     * @return the input wire to reset the fallen behind flag
     */
    @NonNull
    public InputWire<NoInput> getResetFallenBehindInput() {
        return resetFallenBehindInput;
    }

    /**
     * Get the output wire for events received from peers during gossip.
     *
     * @return the output wire for events
     */
    @NonNull
    public OutputWire<GossipEvent> getEventOutput() {
        return eventOutput;
    }

    /**
     * Flush the gossip scheduler.
     */
    public void flush() {
        scheduler.flush();
    }
}
