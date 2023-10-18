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
import static com.swirlds.platform.event.validation.EventValidationChecks.areParentsValid;
import static com.swirlds.platform.event.validation.EventValidationChecks.isGenerationValid;
import static com.swirlds.platform.event.validation.EventValidationChecks.isSignatureValid;
import static com.swirlds.platform.event.validation.EventValidationChecks.isValidTimeCreated;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Validates events.
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
     * The previous address book. May be null.
     */
    private AddressBook previousAddressBook;

    /**
     * The current address book.
     */
    private AddressBook currentAddressBook;

    /**
     * The current software version.
     */
    private final SoftwareVersion currentSoftwareVersion;

    /**
     * The current minimum generation required for an event to be non-ancient.
     */
    private long minimumGenerationNonAncient = 0;

    /**
     * Keeps track of the number of events in the intake pipeline from each peer
     */
    private final IntakeEventCounter intakeEventCounter;

    private final RateLimitedLogger generationLogger;
    private final RateLimitedLogger timeCreatedLogger;
    private final RateLimitedLogger parentLogger;
    private final RateLimitedLogger signatureLogger;

    private static final LongAccumulator.Config INVALID_EVENT_CONFIG = new LongAccumulator.Config(
                    PLATFORM_CATEGORY, "invalidEvents")
            .withDescription("Events received that were found to be invalid")
            .withUnit("events");
    private final LongAccumulator invalidEventAccumulator;

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

    private static final LongAccumulator.Config INVALID_SIGNATURE_CONFIG = new LongAccumulator.Config(
                    PLATFORM_CATEGORY, "eventsWithInvalidSignature")
            .withDescription("Events received with invalid signature")
            .withUnit("events");
    private final LongAccumulator invalidSignatureAccumulator;

    /**
     * Constructor
     *
     * @param platformContext        the platform context
     * @param time                   a time object, for rate limiting loggers
     * @param currentSoftwareVersion the current software version
     * @param intakeEventCounter     keeps track of the number of events in the intake pipeline from each peer
     * @param eventConsumer          deduplicated events are passed to this consumer
     * @param previousAddressBook    the previous address book
     * @param currentAddressBook     the current address book
     */
    public StandaloneEventValidator(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @NonNull final IntakeEventCounter intakeEventCounter,
            @NonNull final Consumer<GossipEvent> eventConsumer,
            @Nullable final AddressBook previousAddressBook,
            @NonNull final AddressBook currentAddressBook) {

        this.currentSoftwareVersion = Objects.requireNonNull(currentSoftwareVersion);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);
        this.eventConsumer = Objects.requireNonNull(eventConsumer);
        this.previousAddressBook = previousAddressBook;
        this.currentAddressBook = Objects.requireNonNull(currentAddressBook);

        this.generationLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.timeCreatedLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.parentLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.signatureLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);

        this.invalidEventAccumulator = platformContext.getMetrics().getOrCreate(INVALID_EVENT_CONFIG);
        this.invalidTimeCreatedAccumulator = platformContext.getMetrics().getOrCreate(INVALID_TIME_CREATED_CONFIG);
        this.invalidParentsAccumulator = platformContext.getMetrics().getOrCreate(INVALID_PARENTS_CONFIG);
        this.invalidSignatureAccumulator = platformContext.getMetrics().getOrCreate(INVALID_SIGNATURE_CONFIG);
    }

    /**
     * Validate an event.
     * <p>
     * If the event is determined to be valid, it is passed to the event consumer.
     * <p>
     * This method is threadsafe, and may be called concurrently from multiple threads.
     *
     * @param event the event to validate
     */
    public void handleEvent(@NonNull final EventImpl event) {
        if (isGenerationValid(event.getBaseEvent(), minimumGenerationNonAncient, generationLogger)
                && isValidTimeCreated(event, timeCreatedLogger, invalidTimeCreatedAccumulator)
                && areParentsValid(event, currentAddressBook.getSize() == 1, parentLogger, invalidParentsAccumulator)
                && isSignatureValid(
                        event.getBaseEvent(),
                        currentSoftwareVersion,
                        previousAddressBook,
                        currentAddressBook,
                        signatureLogger,
                        invalidSignatureAccumulator)) {

            eventConsumer.accept(event.getBaseEvent());
        } else {
            intakeEventCounter.eventExitedIntakePipeline(event.getBaseEvent().getSenderId());
            invalidEventAccumulator.update(1);
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

    /**
     * Set the previous address book.
     *
     * @param previousAddressBook the previous address book
     */
    public void setPreviousAddressBook(@NonNull final AddressBook previousAddressBook) {
        this.previousAddressBook = previousAddressBook;
    }

    /**
     * Set the current address book.
     *
     * @param currentAddressBook the current address book
     */
    public void setCurrentAddressBook(@NonNull final AddressBook currentAddressBook) {
        this.currentAddressBook = currentAddressBook;
    }
}
