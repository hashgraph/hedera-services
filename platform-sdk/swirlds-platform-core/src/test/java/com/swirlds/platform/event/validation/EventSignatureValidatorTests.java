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

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.crypto.SignatureVerifier;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.eventhandling.EventConfig_;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import com.swirlds.platform.system.events.EventConstants;
import com.swirlds.platform.test.fixtures.crypto.PreGeneratedX509Certs;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EventSignatureValidatorTests {
    private Random random;
    private PlatformContext platformContext;
    private FakeTime time;
    private AtomicLong exitedIntakePipelineCount;
    private IntakeEventCounter intakeEventCounter;

    private AddressBook currentAddressBook;

    /**
     * A verifier that always returns true.
     */
    private final SignatureVerifier trueVerifier = (data, signature, publicKey) -> true;

    /**
     * A verifier that always returns false.
     */
    private final SignatureVerifier falseVerifier = (data, signature, publicKey) -> false;

    private EventSignatureValidator validatorWithTrueVerifier;
    private EventSignatureValidator validatorWithFalseVerifier;

    private SoftwareVersion defaultVersion;

    /**
     * This address belongs to a node that is placed in the previous address book.
     */
    private Address previousNodeAddress;

    /**
     * This address belongs to a node that is placed in the current address book.
     */
    private Address currentNodeAddress;

    /**
     * Generate a mock address, with enough elements mocked to support the signature validation.
     *
     * @param nodeId the node id to use for the address
     * @return a mock address
     */
    private static Address generateMockAddress(final @NonNull NodeId nodeId) {
        return new Address(
                nodeId, "", "", 10, null, 77, null, 88, PreGeneratedX509Certs.getSigCert(nodeId.id()), null, "");
    }

    /**
     * Generate a mock event, with enough elements mocked to support the signature validation.
     *
     * @param version   the software version to use for the event
     * @param hash      the hash to use for the event
     * @param creatorId the creator id to use for the event
     * @return a mock event
     */
    private static GossipEvent generateMockEvent(
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
        random = getRandomPrintSeed();
        platformContext = TestPlatformContextBuilder.create().build();
        time = new FakeTime();

        exitedIntakePipelineCount = new AtomicLong(0);
        intakeEventCounter = mock(IntakeEventCounter.class);
        doAnswer(invocation -> {
                    exitedIntakePipelineCount.incrementAndGet();
                    return null;
                })
                .when(intakeEventCounter)
                .eventExitedIntakePipeline(any());

        // create two addresses, one for the previous address book and one for the current address book
        previousNodeAddress = generateMockAddress(new NodeId(66));
        currentNodeAddress = generateMockAddress(new NodeId(77));

        final AddressBook previousAddressBook = new AddressBook(List.of(previousNodeAddress));
        currentAddressBook = new AddressBook(List.of(currentNodeAddress));

        defaultVersion = new BasicSoftwareVersion(2);

        validatorWithTrueVerifier = new DefaultEventSignatureValidator(
                platformContext,
                time,
                trueVerifier,
                defaultVersion,
                previousAddressBook,
                currentAddressBook,
                intakeEventCounter);

        validatorWithFalseVerifier = new DefaultEventSignatureValidator(
                platformContext,
                time,
                falseVerifier,
                defaultVersion,
                previousAddressBook,
                currentAddressBook,
                intakeEventCounter);
    }

    @Test
    @DisplayName("Events with higher version than the app should always fail validation")
    void irreconcilableVersions() {
        final GossipEvent event =
                generateMockEvent(new BasicSoftwareVersion(3), randomHash(random), currentNodeAddress.getNodeId());

        assertNull(validatorWithTrueVerifier.validateSignature(event));
        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Lower version event with missing previous address book")
    void versionMismatchWithNullPreviousAddressBook() {
        final EventSignatureValidator signatureValidator = new DefaultEventSignatureValidator(
                platformContext, time, trueVerifier, defaultVersion, null, currentAddressBook, intakeEventCounter);

        final GossipEvent event =
                generateMockEvent(new BasicSoftwareVersion(1), randomHash(random), previousNodeAddress.getNodeId());

        assertNull(signatureValidator.validateSignature(event));
        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Node is missing from the applicable address book")
    void applicableAddressBookMissingNode() {
        // this creator isn't in the current address book, so verification will fail
        final GossipEvent event =
                generateMockEvent(defaultVersion, randomHash(random), previousNodeAddress.getNodeId());

        assertNull(validatorWithTrueVerifier.validateSignature(event));
        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Node has a null public key")
    void missingPublicKey() {
        final NodeId nodeId = new NodeId(88);
        final Address nodeAddress = new Address(nodeId, "", "", 10, null, 77, null, 88, null, null, "");

        currentAddressBook.add(nodeAddress);

        final GossipEvent event = generateMockEvent(defaultVersion, randomHash(random), nodeId);

        assertNull(validatorWithTrueVerifier.validateSignature(event));
        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Event passes validation if the signature verifies")
    void validSignature() {
        // both the event and the app have the same version, so the currentAddressBook will be selected
        final GossipEvent event1 =
                generateMockEvent(defaultVersion, randomHash(random), currentNodeAddress.getNodeId());

        assertNotEquals(null, validatorWithTrueVerifier.validateSignature(event1));
        assertEquals(0, exitedIntakePipelineCount.get());

        // event2 is from a previous version, so the previous address book will be selected
        final GossipEvent event2 =
                generateMockEvent(new BasicSoftwareVersion(1), randomHash(random), previousNodeAddress.getNodeId());

        assertNotEquals(null, validatorWithTrueVerifier.validateSignature(event2));
        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Event fails validation if the signature does not verify")
    void verificationFails() {
        final GossipEvent event = generateMockEvent(defaultVersion, randomHash(random), currentNodeAddress.getNodeId());

        assertNotEquals(null, validatorWithTrueVerifier.validateSignature(event));
        assertEquals(0, exitedIntakePipelineCount.get());

        assertNull(validatorWithFalseVerifier.validateSignature(event));
        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Ancient events are discarded")
    void ancientEvent(final boolean useBirthRoundForAncientThreshold) {
        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(new TestConfigBuilder()
                        .withValue(EventConfig_.USE_BIRTH_ROUND_ANCIENT_THRESHOLD, useBirthRoundForAncientThreshold)
                        .getOrCreateConfig())
                .build();
        final AddressBook previousAddressBook = new AddressBook(List.of(previousNodeAddress));

        final EventSignatureValidator validator = new DefaultEventSignatureValidator(
                platformContext,
                time,
                trueVerifier,
                defaultVersion,
                previousAddressBook,
                currentAddressBook,
                intakeEventCounter);

        final GossipEvent event = generateMockEvent(defaultVersion, randomHash(random), currentNodeAddress.getNodeId());
        final BaseEventHashedData hData = event.getHashedData();
        when(hData.getBirthRound()).thenReturn(EventConstants.MINIMUM_ROUND_CREATED);
        when(hData.getGeneration()).thenReturn(EventConstants.FIRST_GENERATION);
        when(event.getAncientIndicator(any())).thenAnswer(invocation -> {
            final AncientMode mode = invocation.getArgument(0);
            return mode == AncientMode.GENERATION_THRESHOLD
                    ? EventConstants.FIRST_GENERATION
                    : EventConstants.MINIMUM_ROUND_CREATED;
        });

        assertNotEquals(null, validator.validateSignature(event));
        assertEquals(0, exitedIntakePipelineCount.get());

        validatorWithTrueVerifier.setNonAncientEventWindow(new NonAncientEventWindow(
                ConsensusConstants.ROUND_FIRST,
                100L,
                ConsensusConstants.ROUND_FIRST /* ignored in this context */,
                platformContext
                        .getConfiguration()
                        .getConfigData(EventConfig.class)
                        .getAncientMode()));

        assertNull(validatorWithTrueVerifier.validateSignature(event));
        assertEquals(1, exitedIntakePipelineCount.get());
    }
}
