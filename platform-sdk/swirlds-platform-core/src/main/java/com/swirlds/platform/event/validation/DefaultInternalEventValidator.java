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

import com.hedera.hapi.platform.event.EventCore;
import com.hedera.hapi.platform.event.EventDescriptor;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.hapi.platform.event.GossipEvent;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.metrics.api.LongAccumulator;
import com.swirlds.platform.config.TransactionConfig;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.system.events.EventConstants;
import com.swirlds.platform.system.events.EventDescriptorWrapper;
import com.swirlds.platform.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.Iterator;
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

    private final AncientMode ancientMode;

    private final RateLimitedLogger nullFieldLogger;
    private final RateLimitedLogger fieldLengthLogger;
    private final RateLimitedLogger tooManyTransactionBytesLogger;
    private final RateLimitedLogger inconsistentSelfParentLogger;
    private final RateLimitedLogger inconsistentOtherParentLogger;
    private final RateLimitedLogger identicalParentsLogger;
    private final RateLimitedLogger invalidGenerationLogger;
    private final RateLimitedLogger invalidBirthRoundLogger;

    private final LongAccumulator nullFieldAccumulator;
    private final LongAccumulator fieldLengthAccumulator;
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
     * @param singleNodeNetwork  true if this node is in a single-node network, otherwise false
     * @param intakeEventCounter keeps track of the number of events in the intake pipeline from each peer
     */
    public DefaultInternalEventValidator(
            @NonNull final PlatformContext platformContext,
            final boolean singleNodeNetwork,
            @NonNull final IntakeEventCounter intakeEventCounter) {

        this.singleNodeNetwork = singleNodeNetwork;
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);

        this.transactionConfig = platformContext.getConfiguration().getConfigData(TransactionConfig.class);
        this.ancientMode = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode();

        this.nullFieldLogger = new RateLimitedLogger(logger, platformContext.getTime(), MINIMUM_LOG_PERIOD);
        this.fieldLengthLogger = new RateLimitedLogger(logger, platformContext.getTime(), MINIMUM_LOG_PERIOD);
        this.tooManyTransactionBytesLogger =
                new RateLimitedLogger(logger, platformContext.getTime(), MINIMUM_LOG_PERIOD);
        this.inconsistentSelfParentLogger =
                new RateLimitedLogger(logger, platformContext.getTime(), MINIMUM_LOG_PERIOD);
        this.inconsistentOtherParentLogger =
                new RateLimitedLogger(logger, platformContext.getTime(), MINIMUM_LOG_PERIOD);
        this.identicalParentsLogger = new RateLimitedLogger(logger, platformContext.getTime(), MINIMUM_LOG_PERIOD);
        this.invalidGenerationLogger = new RateLimitedLogger(logger, platformContext.getTime(), MINIMUM_LOG_PERIOD);
        this.invalidBirthRoundLogger = new RateLimitedLogger(logger, platformContext.getTime(), MINIMUM_LOG_PERIOD);

        this.nullFieldAccumulator = platformContext
                .getMetrics()
                .getOrCreate(new LongAccumulator.Config(PLATFORM_CATEGORY, "eventsWithNullFields")
                        .withDescription("Events that had a null field")
                        .withUnit("events"));
        this.fieldLengthAccumulator = platformContext
                .getMetrics()
                .getOrCreate(new LongAccumulator.Config(PLATFORM_CATEGORY, "eventsWithInvalidFieldLength")
                        .withDescription("Events with an invalid field length")
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
    private boolean areRequiredFieldsNonNull(@NonNull final PlatformEvent event) {
        final GossipEvent gossipEvent = event.getGossipEvent();
        final EventCore eventCore = gossipEvent.eventCore();
        String nullField = null;
        if (eventCore == null) {
            nullField = "eventCore";
        } else if (eventCore.timeCreated() == null) {
            nullField = "timeCreated";
        } else if (eventCore.version() == null) {
            nullField = "version";
        } else if (eventCore.parents().stream().anyMatch(Objects::isNull)) {
            nullField = "parent";
        } else if (gossipEvent.eventTransaction().stream().anyMatch(DefaultInternalEventValidator::isTransactionNull)) {
            nullField = "transaction";
        }
        if (nullField != null) {
            nullFieldLogger.error(EXCEPTION.getMarker(), "Event has null field '{}' {}", nullField, gossipEvent);
            nullFieldAccumulator.update(1);
            return false;
        }

        return true;
    }

    /**
     * Checks whether the transaction is null.
     * @param transaction the transaction to check
     * @return true if the transaction is null, otherwise false
     */
    private static boolean isTransactionNull(@Nullable final EventTransaction transaction) {
        return transaction == null
                || transaction.transaction() == null
                || transaction.transaction().value() == null;
    }

    /**
     * Checks whether the {@link com.hedera.pbj.runtime.io.buffer.Bytes} fields of an event are the expected length.
     *
     * @param event the event to check
     * @return true if the byte fields of the event are the correct length, otherwise false
     */
    private boolean areByteFieldsCorrectLength(@NonNull final PlatformEvent event) {
        final GossipEvent gossipEvent = event.getGossipEvent();
        final EventCore eventCore = gossipEvent.eventCore();
        if (gossipEvent.signature().length() != SignatureType.RSA.signatureLength()) {
            fieldLengthLogger.error(EXCEPTION.getMarker(), "Event signature is the wrong length {}", gossipEvent);
            fieldLengthAccumulator.update(1);
            return false;
        }
        if (eventCore.parents().stream()
                .map(EventDescriptor::hash)
                .anyMatch(hash -> hash.length() != DigestType.SHA_384.digestLength())) {
            fieldLengthLogger.error(
                    EXCEPTION.getMarker(),
                    "Event parent descriptor has a hash that is the wrong length {}",
                    gossipEvent);
            fieldLengthAccumulator.update(1);
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
    private boolean isTransactionByteCountValid(@NonNull final PlatformEvent event) {
        int totalTransactionBytes = 0;
        final Iterator<Transaction> iterator = event.transactionIterator();
        while (iterator.hasNext()) {
            totalTransactionBytes += iterator.next().getSize();
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
    private boolean areParentsInternallyConsistent(@NonNull final PlatformEvent event) {
        // If a parent is not missing, then the generation and birth round must be valid.

        final EventDescriptorWrapper selfParent = event.getSelfParent();
        if (selfParent != null) {
            if (selfParent.eventDescriptor().generation() < FIRST_GENERATION) {
                inconsistentSelfParentLogger.error(
                        EXCEPTION.getMarker(),
                        "Event %s has self parent with generation less than the FIRST_GENERATION. self-parent generation: %s"
                                .formatted(event, selfParent.eventDescriptor().generation()));
                inconsistentSelfParentAccumulator.update(1);
                return false;
            }
        }

        for (final EventDescriptorWrapper otherParent : event.getOtherParents()) {
            if (otherParent.eventDescriptor().generation() < FIRST_GENERATION) {
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
            for (final EventDescriptorWrapper otherParent : event.getOtherParents()) {
                if (selfParent.hash().equals(otherParent.hash())) {
                    identicalParentsLogger.error(
                            EXCEPTION.getMarker(),
                            "Event %s has identical self-parent and other-parent hash: %s"
                                    .formatted(event, selfParent.hash()));
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
    private boolean isEventGenerationValid(@NonNull final PlatformEvent event) {
        final long eventGeneration = event.getGeneration();

        if (eventGeneration < FIRST_GENERATION) {
            invalidGenerationLogger.error(
                    EXCEPTION.getMarker(),
                    "Event %s has an invalid generation. Event generation: %s, the min generation is: %s"
                            .formatted(event, eventGeneration, FIRST_GENERATION));
            invalidGenerationAccumulator.update(1);
            return false;
        }

        long maxParentGeneration = EventConstants.GENERATION_UNDEFINED;
        for (final EventDescriptorWrapper parent : event.getAllParents()) {
            maxParentGeneration =
                    Math.max(maxParentGeneration, parent.eventDescriptor().generation());
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
    private boolean isEventBirthRoundValid(@NonNull final PlatformEvent event) {
        if (ancientMode == AncientMode.GENERATION_THRESHOLD) {
            // Don't validate birth rounds in generation mode.
            return true;
        }

        final long eventBirthRound = event.getDescriptor().eventDescriptor().birthRound();

        long maxParentBirthRound = ROUND_NEGATIVE_INFINITY;
        for (final EventDescriptorWrapper parent : event.getAllParents()) {
            maxParentBirthRound =
                    Math.max(maxParentBirthRound, parent.eventDescriptor().birthRound());
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
    public PlatformEvent validateEvent(@NonNull final PlatformEvent event) {
        if (areRequiredFieldsNonNull(event)
                && areByteFieldsCorrectLength(event)
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
