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

package com.swirlds.platform.event.creation;

import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.wiring.ClearTrigger;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Wraps an {@link EventCreator} and provides additional functionality. Will sometimes decide not to create new events
 * based on external rules. Forwards created events to a consumer, and retries forwarding if the consumer is not
 * immediately able to accept the event.
 */
public interface EventCreationManager {
    /**
     * Attempt to create an event. If successful, attempt to pass that event to the event consumer.
     *
     * @return the created event, or null if no event was created
     */
    @InputWireLabel("heartbeat")
    @Nullable
    GossipEvent maybeCreateEvent();

    /**
     * Register a new event from event intake.
     *
     * @param event the event to add
     */
    @InputWireLabel("GossipEvent")
    void registerEvent(@NonNull GossipEvent event);

    /**
     * Update the non-ancient event window, defining the minimum threshold for an event to be non-ancient.
     *
     * @param nonAncientEventWindow the non-ancient event window
     */
    @InputWireLabel("non-ancient event window")
    void setNonAncientEventWindow(@NonNull NonAncientEventWindow nonAncientEventWindow);

    /**
     * Clear the internal state of the event creation manager.
     *
     * @param ignored the trigger on the wire that causes us to clear, ignored
     */
    @InputWireLabel("clear")
    void clear(@NonNull ClearTrigger ignored);
}
