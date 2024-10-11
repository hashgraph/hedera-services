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
import static com.swirlds.platform.system.events.EventConstants.GENERATION_UNDEFINED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.platform.event.EventCore;
import com.hedera.hapi.platform.event.EventDescriptor;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.hapi.platform.event.EventTransaction.TransactionOneOfType;
import com.hedera.hapi.platform.event.GossipEvent;
import com.hedera.pbj.runtime.OneOf;
import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.eventhandling.EventConfig_;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Tests for {@link DefaultInternalEventValidator}
 */
class InternalEventValidatorTests {
    private AtomicLong exitedIntakePipelineCount;
    private Random random;
    private InternalEventValidator multinodeValidator;
    private InternalEventValidator singleNodeValidator;

    @BeforeEach
    void setup() {
        random = getRandomPrintSeed();

        exitedIntakePipelineCount = new AtomicLong(0);
        final IntakeEventCounter intakeEventCounter = mock(IntakeEventCounter.class);
        doAnswer(invocation -> {
                    exitedIntakePipelineCount.incrementAndGet();
                    return null;
                })
                .when(intakeEventCounter)
                .eventExitedIntakePipeline(any());

        final Time time = new FakeTime();

        // Adding the configuration to use the birth round as the ancient threshold for testing.
        // The conditions where it is false is covered by the case where it is set to true.
        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(new TestConfigBuilder()
                        .withValue(EventConfig_.USE_BIRTH_ROUND_ANCIENT_THRESHOLD, true)
                        .getOrCreateConfig())
                .withTime(time)
                .build();

        multinodeValidator = new DefaultInternalEventValidator(platformContext, false, intakeEventCounter);
        singleNodeValidator = new DefaultInternalEventValidator(platformContext, true, intakeEventCounter);
    }

    @Test
    @DisplayName("An event with null fields is invalid")
    void nullFields() {
        final PlatformEvent platformEvent = Mockito.mock(PlatformEvent.class);

        final Randotron r = Randotron.create();
        final GossipEvent wholeEvent = new TestingEventBuilder(r)
                .setSystemTransactionCount(1)
                .setAppTransactionCount(2)
                .setSelfParent(new TestingEventBuilder(r).build())
                .setOtherParent(new TestingEventBuilder(r).build())
                .build()
                .getGossipEvent();

        final GossipEvent noEventCore = GossipEvent.newBuilder()
                .eventCore((EventCore) null)
                .signature(wholeEvent.signature())
                .eventTransaction(wholeEvent.eventTransaction())
                .build();
        when(platformEvent.getGossipEvent()).thenReturn(noEventCore);
        assertNull(multinodeValidator.validateEvent(platformEvent));
        assertNull(singleNodeValidator.validateEvent(platformEvent));
        assertEquals(2, exitedIntakePipelineCount.get());

        final GossipEvent noTimeCreated = GossipEvent.newBuilder()
                .eventCore(EventCore.newBuilder()
                        .timeCreated((Timestamp) null)
                        .version(wholeEvent.eventCore().version())
                        .build())
                .signature(wholeEvent.signature())
                .eventTransaction(wholeEvent.eventTransaction())
                .build();
        when(platformEvent.getGossipEvent()).thenReturn(noTimeCreated);
        assertNull(multinodeValidator.validateEvent(platformEvent));
        assertNull(singleNodeValidator.validateEvent(platformEvent));
        assertEquals(4, exitedIntakePipelineCount.get());

        final GossipEvent noVersion = GossipEvent.newBuilder()
                .eventCore(EventCore.newBuilder()
                        .timeCreated(wholeEvent.eventCore().timeCreated())
                        .version((SemanticVersion) null)
                        .build())
                .signature(wholeEvent.signature())
                .eventTransaction(wholeEvent.eventTransaction())
                .build();
        when(platformEvent.getGossipEvent()).thenReturn(noVersion);
        assertNull(multinodeValidator.validateEvent(platformEvent));
        assertNull(singleNodeValidator.validateEvent(platformEvent));
        assertEquals(6, exitedIntakePipelineCount.get());

        final GossipEvent nullTransaction = GossipEvent.newBuilder()
                .eventCore(wholeEvent.eventCore())
                .signature(wholeEvent.signature())
                .eventTransaction(
                        List.of(new EventTransaction(new OneOf<>(TransactionOneOfType.APPLICATION_TRANSACTION, null))))
                .build();
        when(platformEvent.getGossipEvent()).thenReturn(nullTransaction);

        assertNull(multinodeValidator.validateEvent(platformEvent));
        assertNull(singleNodeValidator.validateEvent(platformEvent));
        assertEquals(8, exitedIntakePipelineCount.get());

        final ArrayList<EventDescriptor> parents = new ArrayList<>();
        parents.add(null);
        final GossipEvent nullParent = GossipEvent.newBuilder()
                .eventCore(EventCore.newBuilder()
                        .timeCreated(wholeEvent.eventCore().timeCreated())
                        .version(wholeEvent.eventCore().version())
                        .parents(parents)
                        .build())
                .signature(wholeEvent.signature())
                .eventTransaction(wholeEvent.eventTransaction())
                .build();
        when(platformEvent.getGossipEvent()).thenReturn(nullParent);
        assertNull(multinodeValidator.validateEvent(platformEvent));
        assertNull(singleNodeValidator.validateEvent(platformEvent));
        assertEquals(10, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event with a byte field length that is invalid")
    void byteFieldLength() {
        final PlatformEvent platformEvent = Mockito.mock(PlatformEvent.class);
        final Randotron r = Randotron.create();
        final GossipEvent validEvent = new TestingEventBuilder(r)
                .setSystemTransactionCount(1)
                .setAppTransactionCount(2)
                .setSelfParent(new TestingEventBuilder(r).build())
                .setOtherParent(new TestingEventBuilder(r).build())
                .build()
                .getGossipEvent();

        final GossipEvent shortSignature = GossipEvent.newBuilder()
                .eventCore(validEvent.eventCore())
                .signature(validEvent.signature().getBytes(1, SignatureType.RSA.signatureLength() - 2))
                .eventTransaction(validEvent.eventTransaction())
                .build();
        when(platformEvent.getGossipEvent()).thenReturn(shortSignature);
        assertNull(multinodeValidator.validateEvent(platformEvent));
        assertNull(singleNodeValidator.validateEvent(platformEvent));
        assertEquals(2, exitedIntakePipelineCount.get());

        final GossipEvent shortDescriptorHash = GossipEvent.newBuilder()
                .eventCore(EventCore.newBuilder()
                        .timeCreated(validEvent.eventCore().timeCreated())
                        .version(validEvent.eventCore().version())
                        .parents(EventDescriptor.newBuilder()
                                .hash(validEvent
                                        .eventCore()
                                        .parents()
                                        .getFirst()
                                        .hash()
                                        .getBytes(1, DigestType.SHA_384.digestLength() - 2))
                                .build())
                        .build())
                .signature(validEvent.signature())
                .eventTransaction(validEvent.eventTransaction())
                .build();
        when(platformEvent.getGossipEvent()).thenReturn(shortDescriptorHash);
        assertNull(multinodeValidator.validateEvent(platformEvent));
        assertNull(singleNodeValidator.validateEvent(platformEvent));
        assertEquals(4, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event with too many transaction bytes is invalid")
    void tooManyTransactionBytes() {
        // default max is 245_760 bytes
        final PlatformEvent event = new TestingEventBuilder(random)
                .setTransactionSize(100)
                .setAppTransactionCount(5000)
                .setSystemTransactionCount(0)
                .build();

        assertNull(multinodeValidator.validateEvent(event));
        assertNull(singleNodeValidator.validateEvent(event));

        assertEquals(2, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event with parent inconsistency is invalid")
    void inconsistentParents() {
        // self parent has invalid generation.
        final PlatformEvent invalidSelfParentGeneration = new TestingEventBuilder(random)
                .setSelfParent(new TestingEventBuilder(random).build())
                .overrideSelfParentGeneration(GENERATION_UNDEFINED)
                .build();

        // other parent has invalid generation.
        final PlatformEvent invalidOtherParentGeneration = new TestingEventBuilder(random)
                .setOtherParent(new TestingEventBuilder(random).build())
                .overrideOtherParentGeneration(GENERATION_UNDEFINED)
                .build();

        assertNull(multinodeValidator.validateEvent(invalidSelfParentGeneration));
        assertNull(multinodeValidator.validateEvent(invalidOtherParentGeneration));

        assertNull(singleNodeValidator.validateEvent(invalidSelfParentGeneration));
        assertNull(singleNodeValidator.validateEvent(invalidOtherParentGeneration));

        assertEquals(4, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event with identical parents is only valid in a single node network")
    void identicalParents() {
        final PlatformEvent parent = new TestingEventBuilder(random).build();
        final PlatformEvent event = new TestingEventBuilder(random)
                .setSelfParent(parent)
                .setOtherParent(parent)
                .build();

        assertNull(multinodeValidator.validateEvent(event));
        assertNotEquals(null, singleNodeValidator.validateEvent(event));

        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event must have a birth round greater than or equal to the max of all parent birth rounds.")
    void invalidBirthRound() {
        final PlatformEvent selfParent1 = new TestingEventBuilder(random)
                .setCreatorId(new NodeId(0))
                .setBirthRound(5)
                .build();
        final PlatformEvent otherParent1 = new TestingEventBuilder(random)
                .setCreatorId(new NodeId(1))
                .setBirthRound(7)
                .build();
        final PlatformEvent selfParent2 = new TestingEventBuilder(random)
                .setCreatorId(new NodeId(0))
                .setBirthRound(7)
                .build();
        final PlatformEvent otherParent2 = new TestingEventBuilder(random)
                .setCreatorId(new NodeId(1))
                .setBirthRound(5)
                .build();

        for (final InternalEventValidator validator : List.of(multinodeValidator, singleNodeValidator)) {
            assertNull(validator.validateEvent(new TestingEventBuilder(random)
                    .setCreatorId(new NodeId(0))
                    .setSelfParent(selfParent1)
                    .setOtherParent(otherParent1)
                    .setBirthRound(6)
                    .build()));
            assertNull(validator.validateEvent(new TestingEventBuilder(random)
                    .setCreatorId(new NodeId(0))
                    .setSelfParent(selfParent2)
                    .setOtherParent(otherParent2)
                    .setBirthRound(6)
                    .build()));
            assertNull(validator.validateEvent(new TestingEventBuilder(random)
                    .setCreatorId(new NodeId(0))
                    .setSelfParent(selfParent1)
                    .setOtherParent(otherParent1)
                    .setBirthRound(4)
                    .build()));
            assertNull(validator.validateEvent(new TestingEventBuilder(random)
                    .setCreatorId(new NodeId(0))
                    .setSelfParent(selfParent2)
                    .setOtherParent(otherParent2)
                    .setBirthRound(4)
                    .build()));
            assertNotNull(validator.validateEvent(new TestingEventBuilder(random)
                    .setCreatorId(new NodeId(0))
                    .setSelfParent(selfParent1)
                    .setOtherParent(otherParent1)
                    .setBirthRound(7)
                    .build()));
            assertNotNull(validator.validateEvent(new TestingEventBuilder(random)
                    .setCreatorId(new NodeId(0))
                    .setSelfParent(selfParent2)
                    .setOtherParent(otherParent2)
                    .setBirthRound(7)
                    .build()));
        }

        assertEquals(8, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Test that an event with no issues passes validation")
    void successfulValidation() {
        final PlatformEvent normalEvent = new TestingEventBuilder(random)
                .setSelfParent(new TestingEventBuilder(random).build())
                .setOtherParent(new TestingEventBuilder(random).build())
                .build();
        final PlatformEvent missingSelfParent = new TestingEventBuilder(random)
                .setSelfParent(null)
                .setOtherParent(new TestingEventBuilder(random).build())
                .build();

        final PlatformEvent missingOtherParent = new TestingEventBuilder(random)
                .setSelfParent(new TestingEventBuilder(random).build())
                .setOtherParent(null)
                .build();

        assertNotEquals(null, multinodeValidator.validateEvent(normalEvent));
        assertNotEquals(null, multinodeValidator.validateEvent(missingSelfParent));
        assertNotEquals(null, multinodeValidator.validateEvent(missingOtherParent));

        assertNotEquals(null, singleNodeValidator.validateEvent(normalEvent));
        assertNotEquals(null, singleNodeValidator.validateEvent(missingSelfParent));
        assertNotEquals(null, singleNodeValidator.validateEvent(missingOtherParent));

        assertEquals(0, exitedIntakePipelineCount.get());
    }
}
