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

package com.swirlds.platform.event.validation;

import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;
import static com.swirlds.logging.legacy.LogMarker.INVALID_EVENT_ERROR;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Performs validation that requires access to an event's parents, and therefore must occur after linking
 */
public class LinkedEventValidator {
    private static final Logger logger = LogManager.getLogger(LinkedEventValidator.class);

    /**
     * The minimum period between log messages
     */
    private static final Duration MINIMUM_LOG_PERIOD = Duration.ofMinutes(1);

    /**
     * Valid events are passed to this consumer.
     */
    private final Consumer<EventImpl> eventConsumer;

    /**
     * The current minimum generation required for an event to be non-ancient.
     */
    private long minimumGenerationNonAncient = 0;

    /**
     * Keeps track of the number of events in the intake pipeline from each peer
     */
    private final IntakeEventCounter intakeEventCounter;

    private final RateLimitedLogger rateLimitedLogger;

    private static final LongAccumulator.Config INVALID_TIME_CREATED_CONFIG = new LongAccumulator.Config(
                    PLATFORM_CATEGORY, "eventsWithInvalidTimeCreated")
            .withDescription("Events received with an invalid time created")
            .withUnit("events");
    private final LongAccumulator invalidTimeCreatedAccumulator;

    /**
     * Constructor
     *
     * @param platformContext    the platform context
     * @param time               a time object, for rate limiting logging
     * @param eventConsumer      validated events are passed to this consumer
     * @param intakeEventCounter keeps track of the number of events in the intake pipeline from each peer
     */
    public LinkedEventValidator(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            @NonNull final Consumer<EventImpl> eventConsumer,
            @NonNull final IntakeEventCounter intakeEventCounter) {

        Objects.requireNonNull(time);

        this.eventConsumer = Objects.requireNonNull(eventConsumer);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);

        this.rateLimitedLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.invalidTimeCreatedAccumulator = platformContext.getMetrics().getOrCreate(INVALID_TIME_CREATED_CONFIG);
    }

    /**
     * Validate an event.
     * <p>
     * If the event is determined to be valid, it is passed to the event consumer.
     *
     * @param event the event to validate
     */
    public void handleEvent(@NonNull final EventImpl event) {
        if (event.getGeneration() < minimumGenerationNonAncient) {
            // ancient events may be safely discarded
            intakeEventCounter.eventExitedIntakePipeline(event.getBaseEvent().getSenderId());
            event.clear();
            return;
        }

        final EventImpl selfParent = event.getSelfParent();

        final boolean validTimeCreated =
                selfParent == null || event.getTimeCreated().isAfter(selfParent.getTimeCreated());

        if (validTimeCreated) {
            eventConsumer.accept(event);
        } else {
            rateLimitedLogger.error(
                    INVALID_EVENT_ERROR.getMarker(),
                    "Event timeCreated is invalid. Event: {}, Time created: {}, Parent created: {}",
                    event.toMediumString(),
                    event.getTimeCreated(),
                    selfParent.getTimeCreated());
            invalidTimeCreatedAccumulator.update(1);
            intakeEventCounter.eventExitedIntakePipeline(event.getBaseEvent().getSenderId());
        }
    }

    /**
     * Set the minimum generation required for an event to be non-ancient.
     *
     * @param minimumGenerationNonAncient the minimum generation required for an event to be non-ancient
     */
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        this.minimumGenerationNonAncient = minimumGenerationNonAncient;
    }
}
