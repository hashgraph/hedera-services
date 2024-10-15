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

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.service.gossip.IntakeEventCounter;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.metrics.api.LongAccumulator;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.crypto.SignatureVerifier;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.spi.HapiUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Default implementation for verifying event signatures
 */
public class DefaultEventSignatureValidator implements EventSignatureValidator {
    private static final Logger logger = LogManager.getLogger(DefaultEventSignatureValidator.class);

    /**
     * The minimum period between log messages reporting a specific type of validation failure
     */
    private static final Duration MINIMUM_LOG_PERIOD = Duration.ofMinutes(1);

    /**
     * A verifier for checking event signatures.
     */
    private final SignatureVerifier signatureVerifier;

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
    private final SemanticVersion currentSoftwareVersion;

    /**
     * The current event window.
     */
    private EventWindow eventWindow;

    /**
     * Keeps track of the number of events in the intake pipeline from each peer
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * A logger for validation errors
     */
    private final RateLimitedLogger rateLimitedLogger;

    private static final LongAccumulator.Config VALIDATION_FAILED_CONFIG = new LongAccumulator.Config(
                    PLATFORM_CATEGORY, "eventsFailedSignatureValidation")
            .withDescription("Events for which signature validation failed")
            .withUnit("events");
    private final LongAccumulator validationFailedAccumulator;

    /**
     * Constructor
     *
     * @param platformContext        the platform context
     * @param signatureVerifier      a verifier for checking event signatures
     * @param currentSoftwareVersion the current software version
     * @param previousAddressBook    the previous address book
     * @param currentAddressBook     the current address book
     * @param intakeEventCounter     keeps track of the number of events in the intake pipeline from each peer
     */
    public DefaultEventSignatureValidator(
            @NonNull final PlatformContext platformContext,
            @NonNull final SignatureVerifier signatureVerifier,
            @NonNull final SemanticVersion currentSoftwareVersion,
            @Nullable final AddressBook previousAddressBook,
            @NonNull final AddressBook currentAddressBook,
            @NonNull final IntakeEventCounter intakeEventCounter) {

        this.signatureVerifier = Objects.requireNonNull(signatureVerifier);
        this.currentSoftwareVersion = Objects.requireNonNull(currentSoftwareVersion);
        this.previousAddressBook = previousAddressBook;
        this.currentAddressBook = Objects.requireNonNull(currentAddressBook);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);

        this.rateLimitedLogger = new RateLimitedLogger(logger, platformContext.getTime(), MINIMUM_LOG_PERIOD);

        this.validationFailedAccumulator = platformContext.getMetrics().getOrCreate(VALIDATION_FAILED_CONFIG);

        eventWindow = EventWindow.getGenesisEventWindow(platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode());
    }

    /**
     * Determine whether the previous address book or the current address book should be used to verify an event's
     * signature.
     * <p>
     * Logs an error and returns null if an applicable address book cannot be selected
     *
     * @param event the event to be validated
     * @return the applicable address book, or null if an applicable address book cannot be selected
     */
    @Nullable
    private AddressBook determineApplicableAddressBook(@NonNull final PlatformEvent event) {
        final SemanticVersion eventVersion = event.getSoftwareVersion();

        final int softwareComparison =
                HapiUtils.SEMANTIC_VERSION_COMPARATOR.compare(currentSoftwareVersion, eventVersion);
        if (softwareComparison < 0) {
            // current software version is less than event software version
            rateLimitedLogger.error(
                    EXCEPTION.getMarker(),
                    "Cannot validate events for software version {} that is greater than the current software version {}",
                    eventVersion,
                    currentSoftwareVersion);
            return null;
        } else if (softwareComparison > 0) {
            // current software version is greater than event software version
            if (previousAddressBook == null) {
                rateLimitedLogger.error(
                        EXCEPTION.getMarker(),
                        "Cannot validate events for software version {} that is less than the current software version {} without a previous address book",
                        eventVersion,
                        currentSoftwareVersion);
                return null;
            }
            return previousAddressBook;
        } else {
            // current software version is equal to event software version
            return currentAddressBook;
        }
    }

    /**
     * Determine whether a given event has a valid signature.
     *
     * @param event the event to be validated
     * @return true if the event has a valid signature, otherwise false
     */
    private boolean isSignatureValid(@NonNull final PlatformEvent event) {
        final AddressBook applicableAddressBook = determineApplicableAddressBook(event);
        if (applicableAddressBook == null) {
            // this occurrence was already logged while attempting to determine the applicable address book
            return false;
        }

        final NodeId eventCreatorId = event.getCreatorId();

        if (!applicableAddressBook.contains(eventCreatorId)) {
            rateLimitedLogger.error(
                    EXCEPTION.getMarker(),
                    "Node {} doesn't exist in applicable address book. Event: {}",
                    eventCreatorId,
                    event);
            return false;
        }

        final PublicKey publicKey =
                applicableAddressBook.getAddress(eventCreatorId).getSigPublicKey();
        if (publicKey == null) {
            rateLimitedLogger.error(
                    EXCEPTION.getMarker(), "Cannot find publicKey for creator with ID: {}", eventCreatorId);
            return false;
        }

        final boolean isSignatureValid =
                signatureVerifier.verifySignature(event.getHash().getBytes(), event.getSignature(), publicKey);

        if (!isSignatureValid) {
            rateLimitedLogger.error(
                    EXCEPTION.getMarker(),
                    "Event failed signature check. Event: {}, Signature: {}, Hash: {}",
                    event,
                    event.getSignature().toHex(),
                    event.getHash());
        }

        return isSignatureValid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public PlatformEvent validateSignature(@NonNull final PlatformEvent event) {
        if (eventWindow.isAncient(event)) {
            // ancient events can be safely ignored
            intakeEventCounter.eventExitedIntakePipeline(event.getSenderId());
            return null;
        }

        if (isSignatureValid(event)) {
            return event;
        } else {
            intakeEventCounter.eventExitedIntakePipeline(event.getSenderId());
            validationFailedAccumulator.update(1);

            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEventWindow(@NonNull final EventWindow eventWindow) {
        this.eventWindow = Objects.requireNonNull(eventWindow);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateAddressBooks(@NonNull final AddressBookUpdate addressBookUpdate) {
        this.previousAddressBook = addressBookUpdate.previousAddressBook();
        this.currentAddressBook = addressBookUpdate.currentAddressBook();
    }
}
