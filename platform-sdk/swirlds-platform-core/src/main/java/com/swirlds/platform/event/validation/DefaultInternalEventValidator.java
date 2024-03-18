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

package com.swirlds.platform.event.validation;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.metrics.api.Metrics.PLATFORM_CATEGORY;
import static com.swirlds.platform.consensus.ConsensusConstants.ROUND_NEGATIVE_INFINITY;
import static com.swirlds.platform.system.events.EventConstants.FIRST_GENERATION;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.metrics.api.LongAccumulator;
import com.swirlds.platform.config.TransactionConfig;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.EventDescriptor;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A default implementation of the {@link InternalEventValidator} interface.
 */
public class DefaultInternalEventValidator implements InternalEventValidator {
    private static final Logger logger = LogManager.getLogger(DefaultInternalEventValidator.class);

    /**
     * The minimum period between log messages for a specific mode of failure.
     */
    private static final Duration MINIMUM_LOG_PERIOD = Duration.ofMinutes(1);

    /**
     * Whether this node is in a single-node network.
     */
    private final boolean singleNodeNetwork;

    /**
     * Keeps track of the number of events in the intake pipeline from each peer
     */
    private final IntakeEventCounter intakeEventCounter;

    private final TransactionConfig transactionConfig;

    private final RateLimitedLogger nullHashedDataLogger;
    private final RateLimitedLogger nullUnhashedDataLogger;
    private final RateLimitedLogger tooManyTransactionBytesLogger;
    private final RateLimitedLogger inconsistentSelfParentLogger;
    private final RateLimitedLogger inconsistentOtherParentLogger;
    private final RateLimitedLogger identicalParentsLogger;
    private final RateLimitedLogger invalidGenerationLogger;
    private final RateLimitedLogger invalidBirthRoundLogger;

    private final LongAccumulator nullHashedDataAccumulator;
    private final LongAccumulator nullUnhashedDataAccumulator;
    private final LongAccumulator tooManyTransactionBytesAccumulator;
    private final LongAccumulator inconsistentSelfParentAccumulator;
    private final LongAccumulator inconsistentOtherParentAccumulator;
    private final LongAccumulator identicalParentsAccumulator;
    private final LongAccumulator invalidGenerationAccumulator;
    private final LongAccumulator invalidBirthRoundAccumulator;

    /**
     * Constructor
     *
     * @param platformContext    the platform context
     * @param time               a time object, for rate limiting logging
     * @param singleNodeNetwork  true if this node is in a single-node network, otherwise false
     * @param intakeEventCounter keeps track of the number of events in the intake pipeline from each peer
     */
    public DefaultInternalEventValidator(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            final boolean singleNodeNetwork,
            @NonNull final IntakeEventCounter intakeEventCounter) {

        Objects.requireNonNull(time);

        this.singleNodeNetwork = singleNodeNetwork;
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);

        this.transactionConfig = platformContext.getConfiguration().getConfigData(TransactionConfig.class);

        this.nullHashedDataLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.nullUnhashedDataLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.tooManyTransactionBytesLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.inconsistentSelfParentLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.inconsistentOtherParentLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.identicalParentsLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.invalidGenerationLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);
        this.invalidBirthRoundLogger = new RateLimitedLogger(logger, time, MINIMUM_LOG_PERIOD);

        this.nullHashedDataAccumulator = platformContext
                .getMetrics()
                .getOrCreate(new LongAccumulator.Config(PLATFORM_CATEGORY, "eventsWithNullHashedData")
                        .withDescription("Events that had null hashed data")
                        .withUnit("events"));
        this.nullUnhashedDataAccumulator = platformContext
                .getMetrics()
                .getOrCreate(new LongAccumulator.Config(PLATFORM_CATEGORY, "eventsWithNullUnhashedData")
                        .withDescription("Events that had null unhashed data")
                        .withUnit("events"));
        this.tooManyTransactionBytesAccumulator = platformContext
                .getMetrics()
                .getOrCreate(new LongAccumulator.Config(PLATFORM_CATEGORY, "eventsWithTooManyTransactionBytes")
                        .withDescription("Events that had more transaction bytes than permitted")
                        .withUnit("events"));
        this.inconsistentSelfParentAccumulator = platformContext
                .getMetrics()
                .getOrCreate(new LongAccumulator.Config(PLATFORM_CATEGORY, "eventsWithInconsistentSelfParent")
                        .withDescription("Events that had an internal self-parent inconsistency")
                        .withUnit("events"));
        this.inconsistentOtherParentAccumulator = platformContext
                .getMetrics()
                .getOrCreate(new LongAccumulator.Config(PLATFORM_CATEGORY, "eventsWithInconsistentOtherParent")
                        .withDescription("Events that had an internal other-parent inconsistency")
                        .withUnit("events"));
        this.identicalParentsAccumulator = platformContext
                .getMetrics()
                .getOrCreate(new LongAccumulator.Config(PLATFORM_CATEGORY, "eventsWithIdenticalParents")
                        .withDescription("Events with identical self-parent and other-parent hash")
                        .withUnit("events"));
        this.invalidGenerationAccumulator = platformContext
                .getMetrics()
                .getOrCreate(new LongAccumulator.Config(PLATFORM_CATEGORY, "eventsWithInvalidGeneration")
                        .withDescription("Events with an invalid generation")
                        .withUnit("events"));
        this.invalidBirthRoundAccumulator = platformContext
                .getMetrics()
                .getOrCreate(new LongAccumulator.Config(PLATFORM_CATEGORY, "eventsWithInvalidBirthRound")
                        .withDescription("Events with an invalid birth round")
                        .withUnit("events"));
    }

    /**
     * Checks whether the required fields of an event are non-null.
     *
     * @param event the event to check
     * @return true if the required fields of the event are non-null, otherwise false
     */
    private boolean areRequiredFieldsNonNull(@NonNull final GossipEvent event) {
        if (event.getHashedData() == null) {
            // do not log the event itself, since toString would throw a NullPointerException
            nullHashedDataLogger.error(EXCEPTION.getMarker(), "Event has null hashed data");
            nullHashedDataAccumulator.update(1);
            return false;
        }

        if (event.getUnhashedData() == null) {
            // do not log the event itself, since toString would throw a NullPointerException
            nullUnhashedDataLogger.error(EXCEPTION.getMarker(), "Event has null unhashed data");
            nullUnhashedDataAccumulator.update(1);
            return false;
        }

        return true;
    }

    /**
     * Checks whether the total byte count of all transactions in an event is less than the maximum.
     *
     * @param event the event to check
     * @return true if the total byte count of transactions in the event is less than the maximum, otherwise false
     */
    private boolean isTransactionByteCountValid(@NonNull final GossipEvent event) {
        int totalTransactionBytes = 0;
        for (final ConsensusTransaction transaction : event.getHashedData().getTransactions()) {
            totalTransactionBytes += transaction.getSerializedLength();
        }

        if (totalTransactionBytes > transactionConfig.maxTransactionBytesPerEvent()) {
            tooManyTransactionBytesLogger.error(
                    EXCEPTION.getMarker(),
                    "Event %s has %s transaction bytes, which is more than permitted"
                            .formatted(event, totalTransactionBytes));
            tooManyTransactionBytesAccumulator.update(1);
            return false;
        }

        return true;
    }

    /**
     * Checks that if parents are present, then the generation and birth round of the parents are internally
     * consistent.
     *
     * @param event the event to check
     * @return true if the parent hashes and generations of the event are internally consistent, otherwise false
     */
    private boolean areParentsInternallyConsistent(@NonNull final GossipEvent event) {
        final BaseEventHashedData hashedData = event.getHashedData();

        // If a parent is not missing, then the generation and birth round must be valid.

        final EventDescriptor selfParent = event.getHashedData().getSelfParent();
        if (selfParent != null) {
            if (selfParent.getGeneration() < FIRST_GENERATION) {
                inconsistentSelfParentLogger.error(
                        EXCEPTION.getMarker(),
                        "Event %s has self parent with generation less than the FIRST_GENERATION. self-parent generation: %s"
                                .formatted(event, selfParent.getGeneration()));
                inconsistentSelfParentAccumulator.update(1);
                return false;
            }
        }

        for (final EventDescriptor otherParent : hashedData.getOtherParents()) {
            if (otherParent.getGeneration() < FIRST_GENERATION) {
                inconsistentOtherParentLogger.error(
                        EXCEPTION.getMarker(),
                        "Event %s has other parent with generation less than the FIRST_GENERATION. other-parent: %s"
                                .formatted(event, otherParent));
                inconsistentOtherParentAccumulator.update(1);
                return false;
            }
        }

        // only single node networks are allowed to have identical self-parent and other-parent hashes
        if (!singleNodeNetwork && selfParent != null) {
            for (final EventDescriptor otherParent : hashedData.getOtherParents()) {
                if (selfParent.getHash().equals(otherParent.getHash())) {
                    identicalParentsLogger.error(
                            EXCEPTION.getMarker(),
                            "Event %s has identical self-parent and other-parent hash: %s"
                                    .formatted(event, selfParent.getHash()));
                    identicalParentsAccumulator.update(1);
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Checks whether the generation of an event is valid. A valid generation is one greater than the maximum generation
     * of the event's parents.
     *
     * @param event the event to check
     * @return true if the generation of the event is valid, otherwise false
     */
    private boolean isEventGenerationValid(@NonNull final GossipEvent event) {
        final long eventGeneration = event.getGeneration();

        if (eventGeneration < FIRST_GENERATION) {
            invalidGenerationLogger.error(
                    EXCEPTION.getMarker(),
                    "Event %s has an invalid generation. Event generation: %s, the min generation is: %s"
                            .formatted(event, eventGeneration, FIRST_GENERATION));
            invalidGenerationAccumulator.update(1);
            return false;
        }

        long maxParentGeneration = event.getHashedData().getSelfParentGen();
        for (final EventDescriptor otherParent : event.getHashedData().getOtherParents()) {
            maxParentGeneration = Math.max(maxParentGeneration, otherParent.getGeneration());
        }

        if (eventGeneration != maxParentGeneration + 1) {
            invalidGenerationLogger.error(
                    EXCEPTION.getMarker(),
                    "Event %s has an invalid generation. Event generation: %s, the max of all parent generations is: %s"
                            .formatted(event, eventGeneration, maxParentGeneration));
            invalidGenerationAccumulator.update(1);
            return false;
        }

        return true;
    }

    /**
     * Checks whether the birth round of an event is valid. A child cannot have a birth round prior to the birth round
     * of its parents.
     *
     * @param event the event to check
     * @return true if the birth round of the event is valid, otherwise false
     */
    private boolean isEventBirthRoundValid(@NonNull final GossipEvent event) {
        final long eventBirthRound = event.getDescriptor().getBirthRound();

        long maxParentBirthRound = ROUND_NEGATIVE_INFINITY;
        final EventDescriptor parent = event.getHashedData().getSelfParent();
        if (parent != null) {
            maxParentBirthRound = parent.getBirthRound();
        }
        for (final EventDescriptor otherParent : event.getHashedData().getOtherParents()) {
            maxParentBirthRound = Math.max(maxParentBirthRound, otherParent.getBirthRound());
        }

        if (eventBirthRound < maxParentBirthRound) {
            invalidBirthRoundLogger.error(
                    EXCEPTION.getMarker(),
                    ("Event %s has an invalid birth round that is less than the max of its parents. Event birth round: "
                                    + "%s, the max of all parent birth rounds is: %s")
                            .formatted(event, eventBirthRound, maxParentBirthRound));
            invalidBirthRoundAccumulator.update(1);
            return false;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public GossipEvent validateEvent(@NonNull final GossipEvent event) {
        if (areRequiredFieldsNonNull(event)
                && isTransactionByteCountValid(event)
                && areParentsInternallyConsistent(event)
                && isEventGenerationValid(event)
                && isEventBirthRoundValid(event)) {
            return event;
        } else {
            intakeEventCounter.eventExitedIntakePipeline(event.getSenderId());

            return null;
        }
    }
}
