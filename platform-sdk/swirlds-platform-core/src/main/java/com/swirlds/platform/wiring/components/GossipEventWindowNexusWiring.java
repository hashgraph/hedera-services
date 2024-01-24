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

import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.DIRECT;

import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.gossip.GossipEventWindowNexus;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link com.swirlds.platform.gossip.GossipEventWindowNexus}.
 *
 * @param eventWindowInput the input wire for the event window
 */
public record GossipEventWindowNexusWiring(@NonNull InputWire<NonAncientEventWindow> eventWindowInput) {

    /**
     * Create a new wiring for the {@link com.swirlds.platform.gossip.GossipEventWindowNexus}.
     *
     * @param model the wiring model
     * @return the wiring
     */
    @NonNull
    public static GossipEventWindowNexusWiring create(@NonNull final WiringModel model) {
        final TaskScheduler<Void> scheduler = model.schedulerBuilder("gossipEventWindowNexus")
                .withType(DIRECT)
                .build()
                .cast();
        final InputWire<NonAncientEventWindow> eventWindowInput = scheduler.buildInputWire("non-ancient event window");
        return new GossipEventWindowNexusWiring(eventWindowInput);
    }

    /**
     * Bind the {@link com.swirlds.platform.gossip.GossipEventWindowNexus} to the input wires.
     *
     * @param gossipEventWindowNexus the nexus to bind
     */
    public void bind(@NonNull final GossipEventWindowNexus gossipEventWindowNexus) {
        ((BindableInputWire<NonAncientEventWindow, Void>) eventWindowInput)
                .bind(gossipEventWindowNexus::setEventWindow);
    }
}
