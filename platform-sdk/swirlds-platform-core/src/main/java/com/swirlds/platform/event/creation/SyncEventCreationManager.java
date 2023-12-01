/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import static com.swirlds.platform.event.creation.EventCreationStatus.PIPELINE_INSERTION;
import static com.swirlds.platform.event.creation.EventCreationStatus.RATE_LIMITED;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.extensions.PhaseTimer;
import com.swirlds.common.metrics.extensions.PhaseTimerBuilder;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.rules.EventCreationRule;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Function;

/**
 * Wraps an {@link EventCreator} and provides additional functionality. Will sometimes decide not to create new events
 * based on external rules or based on paused status. Forwards created events to a consumer, and retries forwarding if
 * the consumer is not immediately able to accept the event.
 */
public class SyncEventCreationManager {

    /**
     * Creates events.
     */
    private final EventCreator creator;

    /**
     * Rules that say if event creation is permitted.
     */
    private final EventCreationRule eventCreationRules;

    /**
     * Created events are eventually passed here.
     */
    private final Function<GossipEvent, Boolean> eventConsumer;

    /**
     * If not null, contains an event that was created but was unable to be submitted right away.
     */
    private GossipEvent mostRecentlyCreatedEvent;

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
     * @param eventConsumer      events that are created are passed here, consumer returns true if the event was
     *                           accepted and false if it needs to be resubmitted later
     */
    public SyncEventCreationManager(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            @NonNull final EventCreator creator,
            @NonNull final EventCreationRule eventCreationRules,
            @NonNull final Function<GossipEvent, Boolean> eventConsumer) {

        this.creator = Objects.requireNonNull(creator);
        this.eventCreationRules = Objects.requireNonNull(eventCreationRules);
        this.eventConsumer = Objects.requireNonNull(eventConsumer);

        phase = new PhaseTimerBuilder<>(platformContext, time, "platform", EventCreationStatus.class)
                .enableFractionalMetrics()
                .setInitialPhase(PAUSED)
                .setMetricsNamePrefix("eventCreation")
                .build();
    }

    /**
     * Attempt to create an event. If successful, attempt to pass that event to the event consumer.
     */
    public void maybeCreateEvent() {
        if (paused) {
            phase.activatePhase(PAUSED);
            return;
        }

        if (!eventCreationRules.isEventCreationPermitted()) {
            phase.activatePhase(eventCreationRules.getEventCreationStatus());
            return;
        }

        tryToSubmitMostRecentEvent();
        if (mostRecentlyCreatedEvent != null) {
            // Don't create a new event until the previous one has been accepted.
            phase.activatePhase(PIPELINE_INSERTION);
            return;
        }

        phase.activatePhase(ATTEMPTING_CREATION);

        mostRecentlyCreatedEvent = creator.maybeCreateEvent();
        if (mostRecentlyCreatedEvent == null) {
            // The only reason why the event creator may choose not to create an event
            // is if there are no eligible parents.
            phase.activatePhase(NO_ELIGIBLE_PARENTS);
        } else {
            eventCreationRules.eventWasCreated();
            // We created an event, we won't be allowed to create another until some time has elapsed.
            phase.activatePhase(RATE_LIMITED);

            tryToSubmitMostRecentEvent();

            // We created an event, we won't be allowed to create another until some time has elapsed.
            phase.activatePhase(RATE_LIMITED);
        }
    }

    /**
     * If there is an unsubmitted self event then attempt to submit it.
     */
    private void tryToSubmitMostRecentEvent() {
        if (mostRecentlyCreatedEvent != null) {
            final boolean accepted = eventConsumer.apply(mostRecentlyCreatedEvent);
            if (accepted) {
                mostRecentlyCreatedEvent = null;
            } else {
                phase.activatePhase(PIPELINE_INSERTION);
            }
        }
    }

    /**
     * Pause event creation. If there is an unsubmitted event, this method will attempt to submit it and will block
     * until it has been accepted.
     */
    public void pauseEventCreation() {
        while (mostRecentlyCreatedEvent != null) {
            tryToSubmitMostRecentEvent();
        }
        paused = true;
    }

    /**
     * Resume event creation. Calling this method while event creation is already unpaused has no effect.
     */
    public void resumeEventCreation() {
        paused = false;
    }

    /**
     * Register a new event from event intake.
     *
     * @param event the event to add
     */
    public void registerEvent(@NonNull final EventImpl event) {
        creator.registerEvent(event);
    }

    /**
     * Update the minimum generation non-ancient.
     *
     * @param minimumGenerationNonAncient the new minimum generation non-ancient
     */
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        creator.setMinimumGenerationNonAncient(minimumGenerationNonAncient);
    }
}
