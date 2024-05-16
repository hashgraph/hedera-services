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
import static com.swirlds.platform.event.creation.EventCreationStatus.IDLE;
import static com.swirlds.platform.event.creation.EventCreationStatus.NO_ELIGIBLE_PARENTS;
import static com.swirlds.platform.event.creation.EventCreationStatus.RATE_LIMITED;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.extensions.PhaseTimer;
import com.swirlds.common.metrics.extensions.PhaseTimerBuilder;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.rules.AggregateEventCreationRules;
import com.swirlds.platform.event.creation.rules.BackpressureRule;
import com.swirlds.platform.event.creation.rules.EventCreationRule;
import com.swirlds.platform.event.creation.rules.MaximumRateRule;
import com.swirlds.platform.event.creation.rules.PlatformStatusRule;
import com.swirlds.platform.pool.TransactionPoolNexus;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Default implementation of the {@link EventCreationManager}.
 */
public class DefaultEventCreationManager implements EventCreationManager {

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

    private PlatformStatus platformStatus;

    /**
     * Constructor.
     *
     * @param platformContext      the platform context
     * @param transactionPoolNexus provides transactions to be added to new events
     * @param eventIntakeQueueSize supplies the size of the event intake queue
     * @param creator              creates events
     */
    public DefaultEventCreationManager(
            @NonNull final PlatformContext platformContext,
            @NonNull final TransactionPoolNexus transactionPoolNexus,
            @NonNull final LongSupplier eventIntakeQueueSize,
            @NonNull final EventCreator creator) {

        this.creator = Objects.requireNonNull(creator);

        this.eventCreationRules = AggregateEventCreationRules.of(
                new MaximumRateRule(platformContext),
                new BackpressureRule(platformContext, eventIntakeQueueSize),
                new PlatformStatusRule(this::getPlatformStatus, transactionPoolNexus));

        phase = new PhaseTimerBuilder<>(
                        platformContext, platformContext.getTime(), "platform", EventCreationStatus.class)
                .enableFractionalMetrics()
                .setInitialPhase(IDLE)
                .setMetricsNamePrefix("eventCreation")
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public BaseEventHashedData maybeCreateEvent() {
        if (!eventCreationRules.isEventCreationPermitted()) {
            phase.activatePhase(eventCreationRules.getEventCreationStatus());
            return null;
        }

        phase.activatePhase(ATTEMPTING_CREATION);

        final BaseEventHashedData newEvent = creator.maybeCreateEvent();
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
     * {@inheritDoc}
     */
    @Override
    public void registerEvent(@NonNull final GossipEvent event) {
        creator.registerEvent(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEventWindow(@NonNull final EventWindow eventWindow) {
        creator.setEventWindow(eventWindow);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        creator.clear();
        phase.activatePhase(IDLE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updatePlatformStatus(@NonNull final PlatformStatus platformStatus) {
        this.platformStatus = Objects.requireNonNull(platformStatus);
    }

    /**
     * Get the current platform status.
     *
     * @return the current platform status
     */
    @NonNull
    private PlatformStatus getPlatformStatus() {
        return platformStatus;
    }
}
