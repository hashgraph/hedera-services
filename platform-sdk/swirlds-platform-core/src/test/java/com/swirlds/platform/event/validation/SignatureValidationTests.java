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

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.platform.event.validation.EventValidationChecks.isSignatureValid;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SerializablePublicKey;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.crypto.SignatureVerifier;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.PublicKey;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link EventValidationChecks#isSignatureValid} method
 */
class SignatureValidationTests {
    private RateLimitedLogger logger;
    private LongAccumulator metricAccumulator;
    private Random random;

    private AddressBook previousAddressBook;
    private AddressBook currentAddressBook;

    /**
     * This address belongs to a node that is placed in the previous address book.
     */
    private Address previousNodeAddress;

    /**
     * This address belongs to a node that is placed in the current address book.
     */
    private Address currentNodeAddress;

    /**
     * A verifier that always returns true.
     */
    private final SignatureVerifier trueVerifier = (data, signature, publicKey) -> true;

    /**
     * A verifier that always returns false.
     */
    private final SignatureVerifier falseVerifier = (data, signature, publicKey) -> false;

    /**
     * Generate a mock address, with enough elements mocked to support the signature validation.
     *
     * @param nodeId the node id to use for the address
     * @return a mock address
     */
    private Address generateMockAddress(final @NonNull NodeId nodeId) {
        final PublicKey publicKey = mock(PublicKey.class);
        final SerializablePublicKey serializablePublicKey = mock(SerializablePublicKey.class);
        when(serializablePublicKey.getPublicKey()).thenReturn(publicKey);

        return new Address(nodeId, "", "", 10, null, 77, null, 88, serializablePublicKey, null, null, "");
    }

    /**
     * Generate a mock event, with enough elements mocked to support the signature validation.
     *
     * @param version   the software version to use for the event
     * @param hash      the hash to use for the event
     * @param creatorId the creator id to use for the event
     * @return a mock event
     */
    final GossipEvent generateMockEvent(
            @NonNull final SoftwareVersion version, @NonNull final Hash hash, @NonNull final NodeId creatorId) {
        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
        when(hashedData.getSoftwareVersion()).thenReturn(version);
        when(hashedData.getHash()).thenReturn(hash);
        when(hashedData.getCreatorId()).thenReturn(creatorId);

        final GossipEvent event = mock(GossipEvent.class);
        when(event.getHashedData()).thenReturn(hashedData);
        when(event.getUnhashedData()).thenReturn(mock(BaseEventUnhashedData.class));

        return event;
    }

    @BeforeEach
    void setup() {
        logger = mock(RateLimitedLogger.class);
        metricAccumulator = mock(LongAccumulator.class);
        random = getRandomPrintSeed();

        // create two addresses, one for the previous address book and one for the current address book
        previousNodeAddress = generateMockAddress(new NodeId(66));
        currentNodeAddress = generateMockAddress(new NodeId(77));

        previousAddressBook = new AddressBook(List.of(previousNodeAddress));
        currentAddressBook = new AddressBook(List.of(currentNodeAddress));
    }

    @Test
    @DisplayName("Events with higher version than the app should always fail validation")
    void irreconcilableVersions() {
        final GossipEvent event =
                generateMockEvent(new BasicSoftwareVersion(2), randomHash(random), currentNodeAddress.getNodeId());

        assertFalse(isSignatureValid(
                event,
                trueVerifier,
                new BasicSoftwareVersion(1),
                previousAddressBook,
                currentAddressBook,
                logger,
                metricAccumulator));
    }

    @Test
    @DisplayName("Lower version event with missing previous address book")
    void versionMismatchWithNullPreviousAddressBook() {
        final GossipEvent event =
                generateMockEvent(new BasicSoftwareVersion(1), randomHash(random), currentNodeAddress.getNodeId());

        // previous address book will be selected based on the versions, but we're passing in null, so validation fails
        assertFalse(isSignatureValid(
                event, trueVerifier, new BasicSoftwareVersion(2), null, currentAddressBook, logger, metricAccumulator));
    }

    @Test
    @DisplayName("Node is missing from the applicable address book")
    void applicableAddressBookMissingNode() {
        // both the event and the app have the same version, so the currentAddressBook will be selected
        final SoftwareVersion sharedVersion = new BasicSoftwareVersion(1);

        // this creator isn't in the current address book, so verification will fail
        final GossipEvent event = generateMockEvent(sharedVersion, randomHash(random), previousNodeAddress.getNodeId());

        assertFalse(isSignatureValid(
                event,
                trueVerifier,
                sharedVersion,
                previousAddressBook,
                currentAddressBook,
                logger,
                metricAccumulator));
    }

    @Test
    @DisplayName("Node has a null public key")
    void missingPublicKey() {
        final NodeId nodeId = new NodeId(88);
        final SerializablePublicKey serializablePublicKey = mock(SerializablePublicKey.class);
        when(serializablePublicKey.getPublicKey()).thenReturn(null);

        final Address nodeAddress =
                new Address(nodeId, "", "", 10, null, 77, null, 88, serializablePublicKey, null, null, "");

        currentAddressBook.add(nodeAddress);

        // both the event and the app have the same version, so the currentAddressBook will be selected
        final SoftwareVersion sharedVersion = new BasicSoftwareVersion(1);

        final GossipEvent event = generateMockEvent(sharedVersion, randomHash(random), nodeId);

        assertFalse(isSignatureValid(
                event,
                trueVerifier,
                sharedVersion,
                previousAddressBook,
                currentAddressBook,
                logger,
                metricAccumulator));
    }

    @Test
    @DisplayName("Event passes validation if the signature verifies")
    void validSignature() {
        final SoftwareVersion appVersion = new BasicSoftwareVersion(2);

        // both the event and the app have the same version, so the currentAddressBook will be selected
        final GossipEvent event1 = generateMockEvent(appVersion, randomHash(random), currentNodeAddress.getNodeId());
        assertTrue(isSignatureValid(
                event1, trueVerifier, appVersion, previousAddressBook, currentAddressBook, logger, metricAccumulator));

        // event2 is from a previous version, so the previous address book will be selected
        final GossipEvent event2 =
                generateMockEvent(new BasicSoftwareVersion(1), randomHash(random), previousNodeAddress.getNodeId());
        assertTrue(isSignatureValid(
                event2, trueVerifier, appVersion, previousAddressBook, currentAddressBook, logger, metricAccumulator));
    }

    @Test
    @DisplayName("Event fails validation if the signature does not verify")
    void verificationFails() {
        // both the event and the app have the same version, so the currentAddressBook will be selected
        final SoftwareVersion sharedVersion = new BasicSoftwareVersion(1);

        final GossipEvent event = generateMockEvent(sharedVersion, randomHash(random), currentNodeAddress.getNodeId());

        assertFalse(isSignatureValid(
                event,
                falseVerifier,
                sharedVersion,
                previousAddressBook,
                currentAddressBook,
                logger,
                metricAccumulator));
    }
}
