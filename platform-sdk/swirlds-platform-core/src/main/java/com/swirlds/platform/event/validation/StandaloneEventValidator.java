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
import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;
import static com.swirlds.platform.consensus.GraphGenerations.FIRST_GENERATION;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Validates events.
 * <p>
 * This validator is "standalone", because it is separate from the legacy intake monolith.
 */
public class StandaloneEventValidator {
    private static final Logger logger = LogManager.getLogger(StandaloneEventValidator.class);

    /**
     * The minimum period between log messages reporting a specific type of validation failure
     */
    private static final Duration MINIMUM_LOG_PERIOD = Duration.ofMinutes(1);

    /**
     * Valid events are passed to this consumer.
     */
    private final Consumer<GossipEvent> eventConsumer;

    /**
     * True if this node is in a single-node network, otherwise false
     */
    private final boolean singleNodeNetwork;

    /**
     * The current minimum generation required for an event to be non-ancient.
     */
    private long minimumGenerationNonAncient = 0;

    /**
     * Keeps track of the number of events in the intake pipeline from each peer
     */
    private final IntakeEventCounter intakeEventCounter;

    private final RateLimitedLogger timeCreatedLogger;
    private final RateLimitedLogger parentLogger;

    private static final LongAccumulator.Config INVALID_TIME_CREATED_CONFIG = new LongAccumulator.Config(
                    PLATFORM_CATEGORY, "eventsWithInvalidTimeCreated")
            .withDescription("Events received with an invalid time created")
            .withUnit("events");
    private final LongAccumulator invalidTimeCreatedAccumulator;

    private static final LongAccumulator.Config INVALID_PARENTS_CONFIG = new LongAccumulator.Config(
                    PLATFORM_CATEGORY, "eventsWithInvalidParents")
            .withDescription("Events received with invalid parents")
            .withUnit("events");
    private final LongAccumulator invalidParentsAccumulator;

    /**
     * Constructor
     *
     * @param platformContext    the platform context
     * @param time               a time object, for rate limiting loggers
     * @param singleNodeNetwork  true if this node is in a single-node network, otherwise false
     * @param eventConsumer      validated events are passed to this consumer
     * @param intakeEventCounter keeps track of the number of events in the intake pipeline from each peer
     */
    public StandaloneEventValidator(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            final boolean singleNodeNetwork,
            @NonNull final Consumer<GossipEvent> eventConsumer,
            @NonNull final IntakeEventCounter intakeEventCounter) {

        Objects.requireNonNull(time);

        this.singleNodeNetwork = singleNodeNetwork;
        this.eventConsumer = Objects.requireNonNull(eventConsumer);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);

        this.timeCreatedLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.parentLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);

        this.invalidTimeCreatedAccumulator = platformContext.getMetrics().getOrCreate(INVALID_TIME_CREATED_CONFIG);
        this.invalidParentsAccumulator = platformContext.getMetrics().getOrCreate(INVALID_PARENTS_CONFIG);
    }

    /**
     * Determine whether a given event has a valid creation time.
     *
     * @param event the event to be validated
     * @return true if the creation time of the event is strictly after the creation time of its self-parent, otherwise false
     */
    private boolean isValidTimeCreated(@NonNull final EventImpl event) {
        final EventImpl selfParent = event.getSelfParent();

        final boolean validTimeCreated =
                selfParent == null || event.getTimeCreated().isAfter(selfParent.getTimeCreated());

        if (!validTimeCreated) {
            timeCreatedLogger.error(
                    INVALID_EVENT_ERROR.getMarker(),
                    "Event timeCreated is invalid. Event: {}, Time created: {}, Parent created: {}",
                    event.toMediumString(),
                    event.getTimeCreated(),
                    selfParent.getTimeCreated());
            invalidTimeCreatedAccumulator.update(1);
        }

        return validTimeCreated;
    }

    /**
     * Determine whether a given event has valid parents.
     *
     * @param event the event to be validated
     * @return true if the event has valid parents, otherwise false
     */
    private boolean areParentsValid(@NonNull final EventImpl event) {
        final BaseEventHashedData hashedData = event.getHashedData();

        final Hash selfParentHash = hashedData.getSelfParentHash();
        final long selfParentGeneration = hashedData.getSelfParentGen();

        // If a parent hash is missing, then the generation must also be invalid.
        // If a parent hash is not missing, then the generation must be valid.
        if ((selfParentHash == null) != (selfParentGeneration < FIRST_GENERATION)) {
            parentLogger.error(INVALID_EVENT_ERROR.getMarker(), "Self parent hash / generation mismatch: {}", event);
            invalidParentsAccumulator.update(1);
            return false;
        }

        final Hash otherParentHash = hashedData.getOtherParentHash();
        final long otherParentGeneration = hashedData.getOtherParentGen();

        if ((otherParentHash == null) != (otherParentGeneration < FIRST_GENERATION)) {
            parentLogger.error(INVALID_EVENT_ERROR.getMarker(), "Other parent hash / generation mismatch: {}", event);
            invalidParentsAccumulator.update(1);
            return false;
        }

        if (!singleNodeNetwork && (selfParentHash != null) && selfParentHash.equals(otherParentHash)) {
            parentLogger.error(INVALID_EVENT_ERROR.getMarker(), "Both parents have the same hash: {} ", event);
            invalidParentsAccumulator.update(1);
            return false;
        }

        return true;
    }

    /**
     * Validate an event.
     * <p>
     * If the event is determined to be valid, it is passed to the event consumer.
     *
     * @param event the event to validate
     */
    public void handleEvent(@NonNull final EventImpl event) {
        if (event.getGeneration() >= minimumGenerationNonAncient
                && isValidTimeCreated(event)
                && areParentsValid(event)) {

            eventConsumer.accept(event.getBaseEvent());
        } else {
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
