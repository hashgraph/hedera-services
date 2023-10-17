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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.PublicKey;
import java.util.Map;
import java.util.Objects;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;
import static com.swirlds.platform.consensus.GraphGenerations.FIRST_GENERATION;
import static com.swirlds.platform.crypto.CryptoStatic.verifySignature;

/**
 * A collection of static methods for validating events
 */
public class EventValidationChecks {
    /**
     * Hidden constructor
     */
    private EventValidationChecks() {
    }

    private static final Logger logger = LogManager.getLogger(EventValidationChecks.class);

    /**
     * Determine whether a given event has a valid creation time.
     *
     * @param event the event to be validated
     * @return true if the creation time of the event is strictly after the creation time of its self-parent, otherwise false
     */
    public static boolean isValidTimeCreated(final EventImpl event) {
        final EventImpl selfParent = event.getSelfParent();
        if (selfParent == null || event.getTimeCreated().isAfter(selfParent.getTimeCreated())) {
            return true;
        }

        logger.error(
                INVALID_EVENT_ERROR.getMarker(),
                () -> String.format(
                        "Event timeCreated ERROR event %s created: %s, parent created: %s",
                        event.toMediumString(),
                        event.getTimeCreated().toString(),
                        selfParent.getTimeCreated().toString()));

        return false;
    }

    /**
     * Determine whether a given event has valid parents.
     *
     * @param event             the event to be validated
     * @param singleNodeNetwork true if the network is a single node network, otherwise false
     * @return true if the event has valid parents, otherwise false
     */
    public static boolean areParentsValid(@NonNull final EventImpl event, final boolean singleNodeNetwork) {
        final BaseEventHashedData hashedData = event.getHashedData();
        final Hash selfParentHash = hashedData.getSelfParentHash();
        final long selfParentGeneration = hashedData.getSelfParentGen();

        final Hash otherParentHash = hashedData.getOtherParentHash();
        final long otherParentGeneration = hashedData.getOtherParentGen();

        if (selfParentHash == null && selfParentGeneration >= FIRST_GENERATION) {
            logger.error(
                    INVALID_EVENT_ERROR.getMarker(),
                    "Self parent hash is null, but generation is defined: {}",
                    event::toString);
            return false;
        }

        if (otherParentHash == null && otherParentGeneration >= FIRST_GENERATION) {
            logger.error(
                    INVALID_EVENT_ERROR.getMarker(),
                    "Other parent hash is null, but generation is defined: {}",
                    event::toString);
            return false;
        }

        if (!singleNodeNetwork && Objects.equals(selfParentHash, otherParentHash)) {
            logger.error(INVALID_EVENT_ERROR.getMarker(), "Both parents have the same hash: {} ", event::toString);
            return false;
        }

        return true;
    }

    /**
     * Determine whether a given event has a valid signature.
     *
     * @param event                  the event to be validated
     * @param currentSoftwareVersion the current software version
     * @param currentKeyMap          map from node ID to public key, from the current address book
     * @param previousKeyMap         map from node ID to public key, from the previous address book
     * @return true if the event has a valid signature, otherwise false
     */
    public static boolean isSignatureValid(
            @NonNull final GossipEvent event,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @NonNull final Map<NodeId, PublicKey> currentKeyMap,
            @NonNull final Map<NodeId, PublicKey> previousKeyMap) {

        final int softwareComparison =
                currentSoftwareVersion.compareTo(event.getHashedData().getSoftwareVersion());
        final PublicKey publicKey;
        if (softwareComparison < 0) {
            // current software version is less than event software version
            logger.error(
                    EXCEPTION.getMarker(),
                    "Cannot validate events for software version {} that is greater than the current software version {}",
                    event.getHashedData().getSoftwareVersion(),
                    currentSoftwareVersion);

            return false;
        } else if (softwareComparison > 0) {
            // current software version is greater than event software version
            publicKey = previousKeyMap.get(event.getHashedData().getCreatorId());
        } else {
            // current software version is equal to event software version
            publicKey = currentKeyMap.get(event.getHashedData().getCreatorId());
        }

        if (publicKey == null) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Cannot find publicKey for creator with ID: {}",
                    event.getHashedData().getCreatorId());
            return false;
        }

        if (!verifySignature(
                event.getHashedData().getHash().getValue(),
                event.getUnhashedData().getSignature(),
                publicKey)) {

            logger.error(
                    INVALID_EVENT_ERROR.getMarker(),
                    "Event failed signature check. Event: {}, Signature: {}, Hash: {}",
                    () -> event,
                    () -> CommonUtils.hex(event.getUnhashedData().getSignature()),
                    () -> event.getHashedData().getHash());

            return false;
        }

        return true;
    }
}
