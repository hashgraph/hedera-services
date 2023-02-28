/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter.network;

import com.swirlds.platform.chatter.protocol.messages.ChatterEvent;
import java.time.Instant;

/**
 * A simulated chatter event. Operationally, the time received is set when the event is received via gossip and
 * deserialized. This simulation does not use real events or serialization, so all events must have their time received
 * set by the simulation.
 */
public interface SimulatedChatterEvent extends ChatterEvent {

    /**
     * Sets the time this event was received by the node.
     *
     * @param timeReceived the time this event was received
     */
    void setTimeReceived(final Instant timeReceived);
}
