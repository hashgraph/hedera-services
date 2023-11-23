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

package com.swirlds.platform.event.linking;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.platform.event.EventConstants.GENERATION_UNDEFINED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@link InOrderLinker} class
 */
class InOrderLinkerTests {
    private AtomicLong exitedIntakePipelineCount;
    private Random random;

    private InOrderLinker inOrderLinker;

    private GossipEvent genesisSelfParent;
    private GossipEvent genesisOtherParent;

    /**
     * Generates a mock event with the given parameters
     *
     * @param selfHash              the hash of the event
     * @param selfGeneration        the generation of the event
     * @param selfParentHash        the hash of the self parent of the event
     * @param selfParentGeneration  the generation of the self parent of the event
     * @param otherParentHash       the hash of the other parent of the event
     * @param otherParentGeneration the generation of the other parent of the event
     * @return the mock event
     */
    private static GossipEvent generateMockEvent(
            @NonNull final Hash selfHash,
            final long selfGeneration,
            @Nullable final Hash selfParentHash,
            final long selfParentGeneration,
            @Nullable final Hash otherParentHash,
            final long otherParentGeneration) {

        final EventDescriptor descriptor = mock(EventDescriptor.class);
        when(descriptor.getHash()).thenReturn(selfHash);
        when(descriptor.getGeneration()).thenReturn(selfGeneration);

        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
        when(hashedData.getHash()).thenReturn(selfHash);
        when(hashedData.getGeneration()).thenReturn(selfGeneration);
        when(hashedData.getSelfParentHash()).thenReturn(selfParentHash);
        when(hashedData.getSelfParentGen()).thenReturn(selfParentGeneration);
        when(hashedData.getOtherParentHash()).thenReturn(otherParentHash);
        when(hashedData.getOtherParentGen()).thenReturn(otherParentGeneration);

        final BaseEventUnhashedData unhashedData = mock(BaseEventUnhashedData.class);

        final GossipEvent event = mock(GossipEvent.class);
        when(event.getGeneration()).thenReturn(selfGeneration);
        when(event.getDescriptor()).thenReturn(descriptor);
        when(event.getHashedData()).thenReturn(hashedData);
        when(event.getUnhashedData()).thenReturn(unhashedData);

        return event;
    }

    /**
     * Set up the in order linker for testing
     * <p>
     * This method creates 2 genesis events and submits them to the linker, as a foundation for the tests.
     */
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

        inOrderLinker = new InOrderLinker(intakeEventCounter);

        genesisSelfParent =
                generateMockEvent(randomHash(random), 0, null, GENERATION_UNDEFINED, null, GENERATION_UNDEFINED);
        inOrderLinker.linkEvent(genesisSelfParent);

        genesisOtherParent =
                generateMockEvent(randomHash(random), 0, null, GENERATION_UNDEFINED, null, GENERATION_UNDEFINED);
        inOrderLinker.linkEvent(genesisOtherParent);
    }

    @Test
    @DisplayName("Test standard operation of the in order linker")
    void standardOperation() {
        final Hash child1Hash = randomHash(random);
        final long child1Generation = 1;
        final GossipEvent child1 = generateMockEvent(
                child1Hash,
                child1Generation,
                genesisSelfParent.getHashedData().getHash(),
                genesisSelfParent.getGeneration(),
                genesisOtherParent.getHashedData().getHash(),
                genesisOtherParent.getGeneration());
        assertNotEquals(null, inOrderLinker.linkEvent(child1));

        inOrderLinker.setMinimumGenerationNonAncient(2);

        // almost ancient
        final Hash child2Hash = randomHash(random);
        final long child2Generation = 2;
        final GossipEvent child2 = generateMockEvent(
                child2Hash,
                child2Generation,
                child1Hash,
                child1Generation,
                genesisOtherParent.getHashedData().getHash(),
                genesisOtherParent.getGeneration());

        assertNotEquals(null, inOrderLinker.linkEvent(child2));
        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Events with ancient parents should still be linkable")
    void parentBecomesAncient() {
        // this will cause the genesis parents to be purged, since they are now ancient
        inOrderLinker.setMinimumGenerationNonAncient(3);

        final GossipEvent child = generateMockEvent(
                randomHash(random),
                4,
                genesisSelfParent.getHashedData().getHash(),
                genesisSelfParent.getGeneration(),
                genesisOtherParent.getHashedData().getHash(),
                genesisOtherParent.getGeneration());

        assertNotEquals(null, inOrderLinker.linkEvent(child));
        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Events with a missing self parent should exit the intake pipeline")
    void missingSelfParent() {
        final GossipEvent child = generateMockEvent(
                randomHash(random),
                4,
                null,
                genesisSelfParent.getGeneration(),
                genesisOtherParent.getHashedData().getHash(),
                genesisOtherParent.getGeneration());

        assertNull(inOrderLinker.linkEvent(child));
        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Events with a missing other parent should exit the intake pipeline")
    void missingOtherParent() {
        final GossipEvent child = generateMockEvent(
                randomHash(random),
                4,
                genesisSelfParent.getHashedData().getHash(),
                genesisSelfParent.getGeneration(),
                null,
                genesisOtherParent.getGeneration());

        assertNull(inOrderLinker.linkEvent(child));
        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Ancient events should immediately exit the intake pipeline")
    void ancientEvent() {
        inOrderLinker.setMinimumGenerationNonAncient(3);

        final GossipEvent child1 = generateMockEvent(
                randomHash(random),
                1,
                genesisSelfParent.getHashedData().getHash(),
                genesisSelfParent.getGeneration(),
                genesisOtherParent.getHashedData().getHash(),
                genesisOtherParent.getGeneration());

        assertNull(inOrderLinker.linkEvent(child1));

        // barely ancient
        final GossipEvent child2 = generateMockEvent(
                randomHash(random),
                2,
                genesisSelfParent.getHashedData().getHash(),
                genesisSelfParent.getGeneration(),
                genesisOtherParent.getHashedData().getHash(),
                genesisOtherParent.getGeneration());

        assertNull(inOrderLinker.linkEvent(child2));
        assertEquals(2, exitedIntakePipelineCount.get());
    }
}
