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

package com.swirlds.platform.gossip.chatter.protocol.messages;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.system.events.EventDescriptor;
import java.time.Instant;

/**
 * Describes an event, hiding interface details that are not relevant to a gossip algorithm.
 */
public interface ChatterEvent extends SelfSerializable {

    /**
     * Get the descriptor of the event.
     *
     * @return the descriptor
     */
    EventDescriptor getDescriptor();

    /**
     * @return the time at which the event has been received
     */
    Instant getTimeReceived();

    /**
     * Get the generation of the event
     *
     * @return the generation of the event
     */
    long getGeneration();
}
