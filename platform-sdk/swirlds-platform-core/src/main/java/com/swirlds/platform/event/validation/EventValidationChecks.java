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

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;
import static com.swirlds.platform.consensus.GraphGenerations.FIRST_GENERATION;
import static com.swirlds.platform.crypto.CryptoStatic.verifySignature;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.PublicKey;
import java.util.Objects;

/**
 * A collection of static methods for validating events
 */
public class EventValidationChecks {
    /**
     * Hidden constructor
     */
    private EventValidationChecks() {}

    /**
     * Determine whether a given event has a valid generation.
     *
     * @param event                       the event to be validated
     * @param minimumGenerationNonAncient the minimum generation for non-ancient events
     * @param logger                      a logger for validation errors
     * @return true if the event has a valid generation, otherwise false
     */
    public static boolean isGenerationValid(
            @NonNull final GossipEvent event,
            final long minimumGenerationNonAncient,
            @NonNull final RateLimitedLogger logger) {

        final boolean generationValid = event.getGeneration() >= minimumGenerationNonAncient;
        if (!generationValid) {
            logger.error(
                    INVALID_EVENT_ERROR.getMarker(),
                    "Event generation is invalid. Event: {}, Minimum Generation non ancient: {}",
                    event,
                    minimumGenerationNonAncient);
        }

        return generationValid;
    }

    /**
     * Determine whether a given event has a valid creation time.
     *
     * @param event  the event to be validated
     * @param logger a logger for validation errors
     * @return true if the creation time of the event is strictly after the creation time of its self-parent, otherwise false
     */
    public static boolean isValidTimeCreated(@NonNull final EventImpl event, @NonNull final RateLimitedLogger logger) {
        final EventImpl selfParent = event.getSelfParent();

        final boolean validTimeCreated =
                selfParent == null || event.getTimeCreated().isAfter(selfParent.getTimeCreated());

        if (!validTimeCreated) {
            logger.error(
                    INVALID_EVENT_ERROR.getMarker(),
                    "Event timeCreated is invalid. Event: {}, Time created: {}, Parent created: %s",
                    event.toMediumString(),
                    event.getTimeCreated().toString(),
                    selfParent.getTimeCreated().toString());
        }

        return validTimeCreated;
    }

    /**
     * Determine whether a given event has valid parents.
     *
     * @param event             the event to be validated
     * @param singleNodeNetwork true if the network is a single node network, otherwise false
     * @param logger            a logger for validation errors
     * @return true if the event has valid parents, otherwise false
     */
    public static boolean areParentsValid(
            @NonNull final EventImpl event, final boolean singleNodeNetwork, @NonNull final RateLimitedLogger logger) {

        final BaseEventHashedData hashedData = event.getHashedData();

        final Hash selfParentHash = hashedData.getSelfParentHash();
        final long selfParentGeneration = hashedData.getSelfParentGen();

        if (selfParentHash == null && selfParentGeneration >= FIRST_GENERATION) {
            logger.error(
                    INVALID_EVENT_ERROR.getMarker(), "Self parent hash is null, but generation is defined: {}", event);
            return false;
        }

        final Hash otherParentHash = hashedData.getOtherParentHash();
        final long otherParentGeneration = hashedData.getOtherParentGen();

        if (otherParentHash == null && otherParentGeneration >= FIRST_GENERATION) {
            logger.error(
                    INVALID_EVENT_ERROR.getMarker(), "Other parent hash is null, but generation is defined: {}", event);
            return false;
        }

        if (!singleNodeNetwork && Objects.equals(selfParentHash, otherParentHash)) {
            logger.error(INVALID_EVENT_ERROR.getMarker(), "Both parents have the same hash: {} ", event);
            return false;
        }

        return true;
    }

    /**
     * Determine whether the previous address book or the current address book should be used to verify an event's signature.
     * <p>
     * Logs an error and returns null if an applicable address book cannot be selected
     *
     * @param event                  the event to be validated
     * @param currentSoftwareVersion the current software version
     * @param previousAddressBook    the previous address book
     * @param currentAddressBook     the current address book
     * @param logger                 a logger for validation errors
     * @return the applicable address book, or null if an applicable address book cannot be selected
     */
    @Nullable
    private static AddressBook determineApplicableAddressBook(
            @NonNull final GossipEvent event,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @Nullable final AddressBook previousAddressBook,
            @NonNull final AddressBook currentAddressBook,
            @NonNull final RateLimitedLogger logger) {

        final int softwareComparison =
                currentSoftwareVersion.compareTo(event.getHashedData().getSoftwareVersion());
        if (softwareComparison < 0) {
            // current software version is less than event software version
            logger.error(
                    EXCEPTION.getMarker(),
                    "Cannot validate events for software version {} that is greater than the current software version {}",
                    event.getHashedData().getSoftwareVersion(),
                    currentSoftwareVersion);
            return null;
        } else if (softwareComparison > 0) {
            // current software version is greater than event software version
            if (previousAddressBook == null) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Cannot validate events for software version {} that is less than the current software version {} without a previous address book",
                        event.getHashedData().getSoftwareVersion(),
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
     * @param event                  the event to be validated
     * @param currentSoftwareVersion the current software version
     * @param previousAddressBook    the previous address book
     * @param currentAddressBook     the current address book
     * @param logger                 a logger for validation errors
     * @return true if the event has a valid signature, otherwise false
     */
    public static boolean isSignatureValid(
            @NonNull final GossipEvent event,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @Nullable final AddressBook previousAddressBook,
            @NonNull final AddressBook currentAddressBook,
            @NonNull final RateLimitedLogger logger) {

        final AddressBook applicableAddressBook = determineApplicableAddressBook(
                event, currentSoftwareVersion, previousAddressBook, currentAddressBook, logger);

        if (applicableAddressBook == null) {
            // this occurrence was already logged while attempting to determine the applicable address book
            return false;
        }

        final PublicKey publicKey = applicableAddressBook
                .getAddress(event.getHashedData().getCreatorId())
                .getSigPublicKey();

        if (publicKey == null) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Cannot find publicKey for creator with ID: {}",
                    event.getHashedData().getCreatorId());
            return false;
        }

        final boolean isSignatureValid = verifySignature(
                event.getHashedData().getHash().getValue(),
                event.getUnhashedData().getSignature(),
                publicKey);

        if (!isSignatureValid) {
            logger.error(
                    INVALID_EVENT_ERROR.getMarker(),
                    "Event failed signature check. Event: {}, Signature: {}, Hash: {}",
                    event,
                    CommonUtils.hex(event.getUnhashedData().getSignature()),
                    event.getHashedData().getHash());
        }

        return isSignatureValid;
    }
}
