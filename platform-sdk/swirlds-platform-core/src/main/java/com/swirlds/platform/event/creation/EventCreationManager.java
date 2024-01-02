/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.platform.event.creation.EventCreationStatus.ATTEMPTING_CREATION;
import static com.swirlds.platform.event.creation.EventCreationStatus.NO_ELIGIBLE_PARENTS;
import static com.swirlds.platform.event.creation.EventCreationStatus.PAUSED;
import static com.swirlds.platform.event.creation.EventCreationStatus.RATE_LIMITED;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.extensions.PhaseTimer;
import com.swirlds.common.metrics.extensions.PhaseTimerBuilder;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.rules.EventCreationRule;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * Wraps an {@link EventCreator} and provides additional functionality. Will sometimes decide not to create new events
 * based on external rules or based on paused status. Forwards created events to a consumer, and retries forwarding if
 * the consumer is not immediately able to accept the event.
 */
public class EventCreationManager {

    /**
     * Creates events.
     */
    private final EventCreator creator;

    /**
     * Rules that say if event creation is permitted.
     */
    private final EventCreationRule eventCreationRules;

    /**
     * Tracks the current phase of event creation.
     */
    private final PhaseTimer<EventCreationStatus> phase;

    /**
     * Whether or not event creation is paused.
     */
    private boolean paused = false;

    /**
     * Constructor.
     *
     * @param platformContext    the platform context
     * @param time               provides wall clock time
     * @param creator            creates events
     * @param eventCreationRules rules for deciding when it is permitted to create events
     */
    public EventCreationManager(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            @NonNull final EventCreator creator,
            @NonNull final EventCreationRule eventCreationRules) {

        this.creator = Objects.requireNonNull(creator);
        this.eventCreationRules = Objects.requireNonNull(eventCreationRules);

        phase = new PhaseTimerBuilder<>(platformContext, time, "platform", EventCreationStatus.class)
                .enableFractionalMetrics()
                .setInitialPhase(PAUSED)
                .setMetricsNamePrefix("eventCreation")
                .build();
    }

    /**
     * Attempt to create an event. If successful, attempt to pass that event to the event consumer.
     *
     * @return the created event, or null if no event was created
     */
    @Nullable
    public GossipEvent maybeCreateEvent() {
        if (paused) {
            phase.activatePhase(PAUSED);
            return null;
        }

        if (!eventCreationRules.isEventCreationPermitted()) {
            phase.activatePhase(eventCreationRules.getEventCreationStatus());
            return null;
        }

        phase.activatePhase(ATTEMPTING_CREATION);

        final GossipEvent newEvent = creator.maybeCreateEvent();
        if (newEvent == null) {
            // The only reason why the event creator may choose not to create an event
            // is if there are no eligible parents.
            phase.activatePhase(NO_ELIGIBLE_PARENTS);
        } else {
            eventCreationRules.eventWasCreated();
            // We created an event, we won't be allowed to create another until some time has elapsed.
            phase.activatePhase(RATE_LIMITED);
        }

        return newEvent;
    }

    /**
     * Pause or resume event creation.
     *
     * @param paused true to pause, false to resume
     */
    public void setPauseStatus(final boolean paused) {
        this.paused = paused;
    }

    /**
     * Register a new event from event intake.
     *
     * @param event the event to add
     */
    public void registerEvent(@NonNull final GossipEvent event) {
        creator.registerEvent(event);
    }

    /**
     * Update the non-ancient event window, defining the minimum threshold for an event to be non-ancient.
     *
     * @param nonAncientEventWindow the non-ancient event window
     */
    public void setNonAncientEventWindow(@NonNull final NonAncientEventWindow nonAncientEventWindow) {
        creator.setNonAncientEventWindow(nonAncientEventWindow);
    }
}
