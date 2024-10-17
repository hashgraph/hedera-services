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

import com.swirlds.common.PlatformStatus;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.output.StandardOutputWire;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.wiring.NoInput;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;

/**
 * Implements gossip with network peers.
 */
public interface Gossip {

    /**
     * Bind the input wires to the gossip implementation.
     *
     * @param model               the wiring model for this node
     * @param eventInput          the input wire for events, events sent here should be gossiped to the network
     * @param eventWindowInput    the input wire for the current event window
     * @param eventOutput         the output wire for events received from peers during gossip
     * @param startInput          used to tell gossip to start
     * @param stopInput           used to tell gossip to stop
     * @param clearInput          used to tell gossip to clear its internal state
     * @param systemHealthInput   used to tell gossip the health of the system, carries the duration that the system has
     *                            been in an unhealthy state
     * @param platformStatusInput used to tell gossip the status of the platform
     */
    void bind(
            @NonNull WiringModel model,
            @NonNull BindableInputWire<PlatformEvent, Void> eventInput,
            @NonNull BindableInputWire<EventWindow, Void> eventWindowInput,
            @NonNull StandardOutputWire<PlatformEvent> eventOutput,
            @NonNull BindableInputWire<NoInput, Void> startInput,
            @NonNull BindableInputWire<NoInput, Void> stopInput,
            @NonNull BindableInputWire<NoInput, Void> clearInput,
            @NonNull BindableInputWire<Duration, Void> systemHealthInput,
            @NonNull BindableInputWire<PlatformStatus, Void> platformStatusInput);
}
