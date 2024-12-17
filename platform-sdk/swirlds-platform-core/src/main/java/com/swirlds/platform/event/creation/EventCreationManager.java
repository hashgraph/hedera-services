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
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.system.events.UnsignedEvent;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;

/**
 * Wraps an {@link EventCreator} and provides additional functionality. Will sometimes decide not to create new events
 * based on external rules. Forwards created events to a consumer, and retries forwarding if the consumer is not
 * immediately able to accept the event.
 */
public interface EventCreationManager {
    /**
     * Attempt to create an event.
     *
     * @return the created event, or null if no event was created
     */
    @InputWireLabel("heartbeat")
    @Nullable
    UnsignedEvent maybeCreateEvent();

    /**
     * Register a new event from event intake.
     *
     * @param event the event to add
     */
    @InputWireLabel("PlatformEvent")
    void registerEvent(@NonNull PlatformEvent event);

    /**
     * Update the event window, defining the minimum threshold for an event to be non-ancient.
     *
     * @param eventWindow the event window
     */
    @InputWireLabel("event window")
    void setEventWindow(@NonNull EventWindow eventWindow);

    /**
     * Update the platform status.
     *
     * @param platformStatus the new platform status
     */
    @InputWireLabel("PlatformStatus")
    void updatePlatformStatus(@NonNull PlatformStatus platformStatus);

    /**
     * Report the amount of time that the system has been in an unhealthy state. Will receive a report of
     * {@link Duration#ZERO} when the system enters a healthy state.
     *
     * @param duration the amount of time that the system has been in an unhealthy state
     */
    @InputWireLabel("health info")
    void reportUnhealthyDuration(@NonNull final Duration duration);

    /**
     * Clear the internal state of the event creation manager.
     */
    @InputWireLabel("clear")
    void clear();
}
