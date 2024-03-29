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
import static com.swirlds.platform.system.events.EventConstants.GENERATION_UNDEFINED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.eventhandling.EventConfig_;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import com.swirlds.platform.system.events.EventDescriptor;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

        // Adding the configuration to use the birth round as the ancient threshold for testing.
        // The conditions where it is false is covered by the case where it is set to true.
        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(new TestConfigBuilder()
                        .withValue(EventConfig_.USE_BIRTH_ROUND_ANCIENT_THRESHOLD, true)
                        .getOrCreateConfig())
                .build();

        final Time time = new FakeTime();

        multinodeValidator = new DefaultInternalEventValidator(platformContext, time, false, intakeEventCounter);
        singleNodeValidator = new DefaultInternalEventValidator(platformContext, time, true, intakeEventCounter);
    }

    private static GossipEvent generateEvent(
            @NonNull final EventDescriptor self,
            @Nullable final EventDescriptor selfParent,
            @Nullable final EventDescriptor otherParent,
            final int totalTransactionBytes) {

        final ConsensusTransactionImpl[] transactions = new ConsensusTransactionImpl[100];
        for (int index = 0; index < transactions.length; index++) {
            transactions[index] = mock(ConsensusTransactionImpl.class);
            when(transactions[index].getSerializedLength()).thenReturn(totalTransactionBytes / transactions.length);
        }

        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
        when(hashedData.getSelfParentHash()).thenReturn(selfParent == null ? null : selfParent.getHash());
        when(hashedData.getOtherParentHash()).thenReturn(otherParent == null ? null : otherParent.getHash());
        when(hashedData.getSelfParentGen())
                .thenReturn(selfParent == null ? GENERATION_UNDEFINED : selfParent.getGeneration());
        when(hashedData.getOtherParentGen())
                .thenReturn(otherParent == null ? GENERATION_UNDEFINED : otherParent.getGeneration());
        when(hashedData.getTransactions()).thenReturn(transactions);
        when(hashedData.getBirthRound()).thenReturn(self.getBirthRound());
        when(hashedData.getGeneration()).thenReturn(self.getGeneration());
        when(hashedData.getSelfParent()).thenReturn(selfParent);
        // FUTURE WORK: Extend to support multiple other parents.
        when(hashedData.getOtherParents())
                .thenReturn(otherParent == null ? Collections.EMPTY_LIST : Collections.singletonList(otherParent));

        final BaseEventUnhashedData unhashedData = mock(BaseEventUnhashedData.class);

        final GossipEvent event = mock(GossipEvent.class);
        when(event.getHashedData()).thenReturn(hashedData);
        when(event.getUnhashedData()).thenReturn(unhashedData);
        when(event.getGeneration()).thenReturn(self.getGeneration());
        when(event.getDescriptor()).thenReturn(self);

        return event;
    }

    private static GossipEvent generateGoodEvent(@NonNull final Random random, final int totalTransactionBytes) {
        return generateEvent(
                new EventDescriptor(randomHash(random), new NodeId(0), 7, 1),
                new EventDescriptor(randomHash(random), new NodeId(0), 5, 1),
                new EventDescriptor(randomHash(random), new NodeId(1), 6, 1),
                totalTransactionBytes);
    }

    @Test
    @DisplayName("An event with null hashed data is invalid")
    void nullHashedData() {
        final GossipEvent event = generateGoodEvent(random, 1111);
        when(event.getHashedData()).thenReturn(null);

        assertNull(multinodeValidator.validateEvent(event));
        assertNull(singleNodeValidator.validateEvent(event));

        assertEquals(2, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event with null unhashed data is invalid")
    void nullUnhashedData() {
        final GossipEvent event = generateGoodEvent(random, 1111);
        when(event.getUnhashedData()).thenReturn(null);

        assertNull(multinodeValidator.validateEvent(event));
        assertNull(singleNodeValidator.validateEvent(event));

        assertEquals(2, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event with too many transaction bytes is invalid")
    void tooManyTransactionBytes() {
        // default max is 245_760 bytes
        final GossipEvent event = generateGoodEvent(random, 500_000);

        assertNull(multinodeValidator.validateEvent(event));
        assertNull(singleNodeValidator.validateEvent(event));

        assertEquals(2, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event with parent inconsistency is invalid")
    void inconsistentParents() {
        // self parent has invalid generation.
        final GossipEvent invalidSelfParentGeneration = generateEvent(
                new EventDescriptor(randomHash(random), new NodeId(0), 7, 1),
                new EventDescriptor(randomHash(random), new NodeId(0), GENERATION_UNDEFINED, 1),
                new EventDescriptor(randomHash(random), new NodeId(1), 6, 1),
                1111);

        // other parent has invalid generation.
        final GossipEvent invalidOtherParentGeneration = generateEvent(
                new EventDescriptor(randomHash(random), new NodeId(0), 7, 1),
                new EventDescriptor(randomHash(random), new NodeId(0), 5, 1),
                new EventDescriptor(randomHash(random), new NodeId(1), GENERATION_UNDEFINED, 1),
                1111);

        assertNull(multinodeValidator.validateEvent(invalidSelfParentGeneration));
        assertNull(multinodeValidator.validateEvent(invalidOtherParentGeneration));

        assertNull(singleNodeValidator.validateEvent(invalidSelfParentGeneration));
        assertNull(singleNodeValidator.validateEvent(invalidOtherParentGeneration));

        assertEquals(4, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event with identical parents is only valid in a single node network")
    void identicalParents() {
        final Hash sharedHash = randomHash(random);
        final EventDescriptor sharedDescriptor = new EventDescriptor(sharedHash, new NodeId(0), 5, 1);
        final GossipEvent event = generateEvent(
                new EventDescriptor(randomHash(random), new NodeId(0), 6, 1), sharedDescriptor, sharedDescriptor, 1111);

        assertNull(multinodeValidator.validateEvent(event));
        assertNotEquals(null, singleNodeValidator.validateEvent(event));

        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event must have a generation of the max parent generation + 1")
    void invalidGeneration() {
        final EventDescriptor selfParent1 = new EventDescriptor(randomHash(random), new NodeId(0), 5, 1);
        final EventDescriptor otherParent1 = new EventDescriptor(randomHash(random), new NodeId(1), 7, 1);
        final EventDescriptor selfParent2 = new EventDescriptor(randomHash(random), new NodeId(0), 7, 1);
        final EventDescriptor otherParent2 = new EventDescriptor(randomHash(random), new NodeId(1), 5, 1);
        final EventDescriptor selfMiddle = new EventDescriptor(randomHash(random), new NodeId(0), 6, 1);
        final EventDescriptor selfHigh = new EventDescriptor(randomHash(random), new NodeId(0), 9, 1);
        final EventDescriptor selfLow = new EventDescriptor(randomHash(random), new NodeId(0), 3, 1);
        final EventDescriptor selfGood = new EventDescriptor(randomHash(random), new NodeId(0), 8, 1);

        assertNull(multinodeValidator.validateEvent(generateEvent(selfHigh, selfParent1, otherParent1, 1111)));
        assertNull(multinodeValidator.validateEvent(generateEvent(selfHigh, selfParent2, otherParent2, 1111)));
        assertNull(multinodeValidator.validateEvent(generateEvent(selfMiddle, selfParent1, otherParent1, 1111)));
        assertNull(multinodeValidator.validateEvent(generateEvent(selfMiddle, selfParent2, otherParent2, 1111)));
        assertNull(multinodeValidator.validateEvent(generateEvent(selfLow, selfParent1, otherParent1, 1111)));
        assertNull(multinodeValidator.validateEvent(generateEvent(selfLow, selfParent2, otherParent2, 1111)));
        assertNotNull(multinodeValidator.validateEvent(generateEvent(selfGood, selfParent1, otherParent1, 1111)));
        assertNotNull(multinodeValidator.validateEvent(generateEvent(selfGood, selfParent2, otherParent2, 1111)));

        assertNull(singleNodeValidator.validateEvent(generateEvent(selfHigh, selfParent1, otherParent1, 1111)));
        assertNull(singleNodeValidator.validateEvent(generateEvent(selfHigh, selfParent2, otherParent2, 1111)));
        assertNull(singleNodeValidator.validateEvent(generateEvent(selfMiddle, selfParent1, otherParent1, 1111)));
        assertNull(singleNodeValidator.validateEvent(generateEvent(selfMiddle, selfParent2, otherParent2, 1111)));
        assertNull(singleNodeValidator.validateEvent(generateEvent(selfLow, selfParent1, otherParent1, 1111)));
        assertNull(singleNodeValidator.validateEvent(generateEvent(selfLow, selfParent2, otherParent2, 1111)));
        assertNotNull(singleNodeValidator.validateEvent(generateEvent(selfGood, selfParent1, otherParent1, 1111)));
        assertNotNull(singleNodeValidator.validateEvent(generateEvent(selfGood, selfParent2, otherParent2, 1111)));

        assertEquals(12, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event must have a birth round greater than or equal to the max of all parent birth rounds.")
    void invalidBirthRound() {
        final EventDescriptor selfParent1 = new EventDescriptor(randomHash(random), new NodeId(0), 5, 5);
        final EventDescriptor otherParent1 = new EventDescriptor(randomHash(random), new NodeId(1), 7, 7);
        final EventDescriptor selfParent2 = new EventDescriptor(randomHash(random), new NodeId(0), 7, 7);
        final EventDescriptor otherParent2 = new EventDescriptor(randomHash(random), new NodeId(1), 5, 5);
        final EventDescriptor selfMiddle = new EventDescriptor(randomHash(random), new NodeId(0), 8, 6);
        final EventDescriptor selfLow = new EventDescriptor(randomHash(random), new NodeId(0), 8, 4);
        final EventDescriptor selfGood = new EventDescriptor(randomHash(random), new NodeId(0), 8, 7);

        assertNull(multinodeValidator.validateEvent(generateEvent(selfMiddle, selfParent1, otherParent1, 1111)));
        assertNull(multinodeValidator.validateEvent(generateEvent(selfMiddle, selfParent2, otherParent2, 1111)));
        assertNull(multinodeValidator.validateEvent(generateEvent(selfLow, selfParent1, otherParent1, 1111)));
        assertNull(multinodeValidator.validateEvent(generateEvent(selfLow, selfParent2, otherParent2, 1111)));
        assertNotNull(multinodeValidator.validateEvent(generateEvent(selfGood, selfParent1, otherParent1, 1111)));
        assertNotNull(multinodeValidator.validateEvent(generateEvent(selfGood, selfParent2, otherParent2, 1111)));

        assertNull(singleNodeValidator.validateEvent(generateEvent(selfMiddle, selfParent1, otherParent1, 1111)));
        assertNull(singleNodeValidator.validateEvent(generateEvent(selfMiddle, selfParent2, otherParent2, 1111)));
        assertNull(singleNodeValidator.validateEvent(generateEvent(selfLow, selfParent1, otherParent1, 1111)));
        assertNull(singleNodeValidator.validateEvent(generateEvent(selfLow, selfParent2, otherParent2, 1111)));
        assertNotNull(singleNodeValidator.validateEvent(generateEvent(selfGood, selfParent1, otherParent1, 1111)));
        assertNotNull(singleNodeValidator.validateEvent(generateEvent(selfGood, selfParent2, otherParent2, 1111)));

        assertEquals(8, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Test that an event with no issues passes validation")
    void successfulValidation() {
        final GossipEvent normalEvent = generateGoodEvent(random, 1111);
        final GossipEvent missingSelfParent = generateEvent(
                new EventDescriptor(randomHash(random), new NodeId(0), 7, 1),
                null,
                new EventDescriptor(randomHash(random), new NodeId(1), 6, 1),
                1111);

        final GossipEvent missingOtherParent = generateEvent(
                new EventDescriptor(randomHash(random), new NodeId(0), 6, 1),
                new EventDescriptor(randomHash(random), new NodeId(0), 5, 1),
                null,
                1111);

        assertNotEquals(null, multinodeValidator.validateEvent(normalEvent));
        assertNotEquals(null, multinodeValidator.validateEvent(missingSelfParent));
        assertNotEquals(null, multinodeValidator.validateEvent(missingOtherParent));

        assertNotEquals(null, singleNodeValidator.validateEvent(normalEvent));
        assertNotEquals(null, singleNodeValidator.validateEvent(missingSelfParent));
        assertNotEquals(null, singleNodeValidator.validateEvent(missingOtherParent));

        assertEquals(0, exitedIntakePipelineCount.get());
    }
}
