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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.crypto.SignatureVerifier;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.eventhandling.EventConfig_;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.EventConstants;
import com.swirlds.platform.test.fixtures.crypto.PreGeneratedX509Certs;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EventSignatureValidatorTests {
    private Randotron random;
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

    private SemanticVersion defaultVersion;

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

    @BeforeEach
    void setup() {
        random = Randotron.create();
        time = new FakeTime();
        platformContext = TestPlatformContextBuilder.create().withTime(time).build();

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

        defaultVersion = SemanticVersion.newBuilder().major(2).build();

        validatorWithTrueVerifier = new DefaultEventSignatureValidator(
                platformContext,
                trueVerifier,
                defaultVersion,
                previousAddressBook,
                currentAddressBook,
                intakeEventCounter);

        validatorWithFalseVerifier = new DefaultEventSignatureValidator(
                platformContext,
                falseVerifier,
                defaultVersion,
                previousAddressBook,
                currentAddressBook,
                intakeEventCounter);
    }

    @Test
    @DisplayName("Events with higher version than the app should always fail validation")
    void irreconcilableVersions() {
        final PlatformEvent event = new TestingEventBuilder(random)
                .setCreatorId(currentNodeAddress.getNodeId())
                .setSoftwareVersion(SemanticVersion.newBuilder().major(3).build())
                .build();

        assertNull(validatorWithTrueVerifier.validateSignature(event));
        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Lower version event with missing previous address book")
    void versionMismatchWithNullPreviousAddressBook() {
        final EventSignatureValidator signatureValidator = new DefaultEventSignatureValidator(
                platformContext, trueVerifier, defaultVersion, null, currentAddressBook, intakeEventCounter);

        final PlatformEvent event = new TestingEventBuilder(random)
                .setCreatorId(previousNodeAddress.getNodeId())
                .setSoftwareVersion(SemanticVersion.newBuilder().major(3).build())
                .build();

        assertNull(signatureValidator.validateSignature(event));
        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Node is missing from the applicable address book")
    void applicableAddressBookMissingNode() {
        // this creator isn't in the current address book, so verification will fail
        final PlatformEvent event = new TestingEventBuilder(random)
                .setCreatorId(previousNodeAddress.getNodeId())
                .setSoftwareVersion(defaultVersion)
                .build();

        assertNull(validatorWithTrueVerifier.validateSignature(event));
        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Node has a null public key")
    void missingPublicKey() {
        final NodeId nodeId = new NodeId(88);
        final Address nodeAddress = new Address(nodeId, "", "", 10, null, 77, null, 88, null, null, "");

        currentAddressBook.add(nodeAddress);

        final PlatformEvent event = new TestingEventBuilder(random)
                .setCreatorId(nodeId)
                .setSoftwareVersion(defaultVersion)
                .build();

        assertNull(validatorWithTrueVerifier.validateSignature(event));
        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Event passes validation if the signature verifies")
    void validSignature() {
        // both the event and the app have the same version, so the currentAddressBook will be selected
        final PlatformEvent event1 = new TestingEventBuilder(random)
                .setCreatorId(currentNodeAddress.getNodeId())
                .setSoftwareVersion(defaultVersion)
                .build();

        assertNotEquals(null, validatorWithTrueVerifier.validateSignature(event1));
        assertEquals(0, exitedIntakePipelineCount.get());

        // event2 is from a previous version, so the previous address book will be selected
        final PlatformEvent event2 = new TestingEventBuilder(random)
                .setCreatorId(previousNodeAddress.getNodeId())
                .setSoftwareVersion(SemanticVersion.newBuilder().major(1).build())
                .build();

        assertNotEquals(null, validatorWithTrueVerifier.validateSignature(event2));
        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Event fails validation if the signature does not verify")
    void verificationFails() {
        final PlatformEvent event = new TestingEventBuilder(random)
                .setCreatorId(currentNodeAddress.getNodeId())
                .setSoftwareVersion(defaultVersion)
                .build();

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
                trueVerifier,
                defaultVersion,
                previousAddressBook,
                currentAddressBook,
                intakeEventCounter);

        final PlatformEvent event = new TestingEventBuilder(random)
                .setCreatorId(currentNodeAddress.getNodeId())
                .setBirthRound(EventConstants.MINIMUM_ROUND_CREATED)
                .setSoftwareVersion(defaultVersion)
                .build();

        assertNotEquals(null, validator.validateSignature(event));
        assertEquals(0, exitedIntakePipelineCount.get());

        validatorWithTrueVerifier.setEventWindow(new EventWindow(
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
