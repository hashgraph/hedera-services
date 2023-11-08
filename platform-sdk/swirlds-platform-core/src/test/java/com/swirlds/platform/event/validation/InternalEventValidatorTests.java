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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InternalEventValidator}
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

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final Time time = new FakeTime();

        multinodeValidator = new InternalEventValidator(platformContext, time, false, intakeEventCounter);
        singleNodeValidator = new InternalEventValidator(platformContext, time, true, intakeEventCounter);
    }

    private static GossipEvent generateEvent(
            @Nullable final Hash selfParentHash,
            @Nullable final Hash otherParentHash,
            final long eventGeneration,
            final long selfParentGeneration,
            final long otherParentGeneration,
            final int totalTransactionBytes) {

        final ConsensusTransactionImpl[] transactions = new ConsensusTransactionImpl[100];
        for (int index = 0; index < transactions.length; index++) {
            transactions[index] = mock(ConsensusTransactionImpl.class);
            when(transactions[index].getSerializedLength()).thenReturn(totalTransactionBytes / transactions.length);
        }

        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
        when(hashedData.getSelfParentHash()).thenReturn(selfParentHash);
        when(hashedData.getOtherParentHash()).thenReturn(otherParentHash);
        when(hashedData.getSelfParentGen()).thenReturn(selfParentGeneration);
        when(hashedData.getOtherParentGen()).thenReturn(otherParentGeneration);
        when(hashedData.getTransactions()).thenReturn(transactions);

        final BaseEventUnhashedData unhashedData = mock(BaseEventUnhashedData.class);

        final GossipEvent event = mock(GossipEvent.class);
        when(event.getHashedData()).thenReturn(hashedData);
        when(event.getUnhashedData()).thenReturn(unhashedData);
        when(event.getGeneration()).thenReturn(eventGeneration);

        return event;
    }

    @Test
    @DisplayName("An event with null hashed data is invalid")
    void nullHashedData() {
        final GossipEvent event = generateEvent(randomHash(random), randomHash(random), 7, 5, 6, 1111);
        when(event.getHashedData()).thenReturn(null);

        assertNull(multinodeValidator.validateEvent(event));
        assertNull(singleNodeValidator.validateEvent(event));

        assertEquals(2, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event with null unhashed data is invalid")
    void nullUnhashedData() {
        final GossipEvent event = generateEvent(randomHash(random), randomHash(random), 7, 5, 6, 1111);
        when(event.getUnhashedData()).thenReturn(null);

        assertNull(multinodeValidator.validateEvent(event));
        assertNull(singleNodeValidator.validateEvent(event));

        assertEquals(2, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event with too many transaction bytes is invalid")
    void tooManyTransactionBytes() {
        // default max is 245_760 bytes
        final GossipEvent event = generateEvent(randomHash(random), randomHash(random), 7, 5, 6, 500_000);

        assertNull(multinodeValidator.validateEvent(event));
        assertNull(singleNodeValidator.validateEvent(event));

        assertEquals(2, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event with parent inconsistency is invalid")
    void inconsistentParents() {
        // has null self parent hash, but valid self parent generation
        final GossipEvent nullSelfParentHash = generateEvent(null, randomHash(random), 7, 5, 6, 1111);
        // has valid self parent hash, but invalid self parent generation
        final GossipEvent invalidSelfParentGeneration =
                generateEvent(randomHash(random), randomHash(random), -1, 7, 6, 1111);
        // has null other parent hash, but valid other parent generation
        final GossipEvent nullOtherParentHash = generateEvent(randomHash(random), null, 7, 5, 6, 1111);
        // has valid other parent hash, but invalid other parent generation
        final GossipEvent invalidOtherParentGeneration =
                generateEvent(randomHash(random), randomHash(random), 6, 5, -1, 1111);

        assertNull(multinodeValidator.validateEvent(nullSelfParentHash));
        assertNull(multinodeValidator.validateEvent(invalidSelfParentGeneration));
        assertNull(multinodeValidator.validateEvent(nullOtherParentHash));
        assertNull(multinodeValidator.validateEvent(invalidOtherParentGeneration));

        assertNull(singleNodeValidator.validateEvent(nullSelfParentHash));
        assertNull(singleNodeValidator.validateEvent(invalidSelfParentGeneration));
        assertNull(singleNodeValidator.validateEvent(nullOtherParentHash));
        assertNull(singleNodeValidator.validateEvent(invalidOtherParentGeneration));

        assertEquals(8, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event with identical parents is only valid in a single node network")
    void identicalParents() {
        final Hash sharedHash = randomHash(random);
        final GossipEvent event = generateEvent(sharedHash, sharedHash, 7, 5, 6, 1111);

        assertNull(multinodeValidator.validateEvent(event));
        assertNotEquals(null, singleNodeValidator.validateEvent(event));

        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event must have a generation of the max parent generation + 1")
    void invalidGeneration() {
        final GossipEvent highGeneration = generateEvent(randomHash(random), randomHash(random), 8, 5, 6, 1111);
        final GossipEvent lowGeneration = generateEvent(randomHash(random), randomHash(random), 4, 5, 6, 1111);

        assertNull(multinodeValidator.validateEvent(highGeneration));
        assertNull(multinodeValidator.validateEvent(lowGeneration));
        assertNull(singleNodeValidator.validateEvent(highGeneration));
        assertNull(singleNodeValidator.validateEvent(lowGeneration));

        assertEquals(4, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Test that an event with no issues passes validation")
    void successfulValidation() {
        final GossipEvent normalEvent = generateEvent(randomHash(random), randomHash(random), 7, 5, 6, 1111);
        final GossipEvent missingSelfParent = generateEvent(null, randomHash(random), 7, -1, 6, 1111);
        final GossipEvent missingOtherParent = generateEvent(randomHash(random), null, 6, 5, -1, 1111);

        assertNotEquals(null, multinodeValidator.validateEvent(normalEvent));
        assertNotEquals(null, multinodeValidator.validateEvent(missingSelfParent));
        assertNotEquals(null, multinodeValidator.validateEvent(missingOtherParent));

        assertNotEquals(null, singleNodeValidator.validateEvent(normalEvent));
        assertNotEquals(null, singleNodeValidator.validateEvent(missingSelfParent));
        assertNotEquals(null, singleNodeValidator.validateEvent(missingOtherParent));

        assertEquals(0, exitedIntakePipelineCount.get());
    }
}
