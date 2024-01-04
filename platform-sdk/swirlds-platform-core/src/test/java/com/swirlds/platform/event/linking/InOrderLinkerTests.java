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

package com.swirlds.platform.event.linking;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.platform.system.events.EventConstants.GENERATION_UNDEFINED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import com.swirlds.platform.system.events.EventDescriptor;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
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

    private FakeTime time;

    /**
     * Generates a mock event with the given parameters
     *
     * @param selfHash              the hash of the event
     * @param selfGeneration        the generation of the event
     * @param selfParentHash        the hash of the self parent of the event
     * @param selfParentGeneration  the generation of the self parent of the event
     * @param otherParentHash       the hash of the other parent of the event
     * @param otherParentGeneration the generation of the other parent of the event
     * @param selfTimeCreated       the time created of the event
     * @return the mock event
     */
    private static GossipEvent generateMockEvent(
            @NonNull final Hash selfHash,
            final long selfGeneration,
            @Nullable final Hash selfParentHash,
            final long selfParentGeneration,
            @Nullable final Hash otherParentHash,
            final long otherParentGeneration,
            @NonNull final Instant selfTimeCreated) {

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
        when(hashedData.getTimeCreated()).thenReturn(selfTimeCreated);

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

        time = new FakeTime();

        inOrderLinker = new InOrderLinker(TestPlatformContextBuilder.create().build(), time, intakeEventCounter);

        time.tick(Duration.ofSeconds(1));
        genesisSelfParent = generateMockEvent(
                randomHash(random), 0, null, GENERATION_UNDEFINED, null, GENERATION_UNDEFINED, time.now());
        inOrderLinker.linkEvent(genesisSelfParent);

        time.tick(Duration.ofSeconds(1));
        genesisOtherParent = generateMockEvent(
                randomHash(random), 0, null, GENERATION_UNDEFINED, null, GENERATION_UNDEFINED, time.now());
        inOrderLinker.linkEvent(genesisOtherParent);

        time.tick(Duration.ofSeconds(1));
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
                genesisOtherParent.getGeneration(),
                time.now());

        final EventImpl linkedEvent1 = inOrderLinker.linkEvent(child1);
        assertNotEquals(null, linkedEvent1);
        assertNotEquals(null, linkedEvent1.getSelfParent(), "Self parent is non-ancient, and should not be null");
        assertNotEquals(null, linkedEvent1.getOtherParent(), "Other parent is non-ancient, and should not be null");
        assertEquals(0, exitedIntakePipelineCount.get());

        time.tick(Duration.ofSeconds(1));
        // FUTURE WORK: change from minGenNonAncient to minRoundNonAncient
        inOrderLinker.setNonAncientEventWindow(new NonAncientEventWindow(
                ConsensusConstants.ROUND_FIRST, ConsensusConstants.ROUND_NEGATIVE_INFINITY, 1, false));

        final Hash child2Hash = randomHash(random);
        final long child2Generation = 2;
        final GossipEvent child2 = generateMockEvent(
                child2Hash,
                child2Generation,
                child1Hash,
                child1Generation,
                genesisOtherParent.getHashedData().getHash(),
                genesisOtherParent.getGeneration(),
                time.now());

        final EventImpl linkedEvent2 = inOrderLinker.linkEvent(child2);
        assertNotEquals(null, linkedEvent2);
        assertNotEquals(null, linkedEvent2.getSelfParent(), "Self parent is non-ancient, and should not be null");
        assertNull(linkedEvent2.getOtherParent(), "Other parent is ancient, and should be null");
        assertEquals(0, exitedIntakePipelineCount.get());

        time.tick(Duration.ofSeconds(1));
        // FUTURE WORK: change from minGenNonAncient to minRoundNonAncient
        inOrderLinker.setNonAncientEventWindow(new NonAncientEventWindow(
                ConsensusConstants.ROUND_FIRST, ConsensusConstants.ROUND_NEGATIVE_INFINITY, 2, false));

        final Hash child3Hash = randomHash(random);
        final long child3Generation = 3;
        final GossipEvent child3 = generateMockEvent(
                child3Hash, child3Generation, child1Hash, child1Generation, child2Hash, child2Generation, time.now());

        final EventImpl linkedEvent3 = inOrderLinker.linkEvent(child3);
        assertNotEquals(null, linkedEvent3);
        assertNull(linkedEvent3.getSelfParent(), "Self parent is ancient, and should be null");
        assertNotEquals(null, linkedEvent3.getOtherParent(), "Other parent is non-ancient, and should not be null");
        assertEquals(0, exitedIntakePipelineCount.get());

        time.tick(Duration.ofSeconds(1));
        // FUTURE WORK: change from minGenNonAncient to minRoundNonAncient
        inOrderLinker.setNonAncientEventWindow(new NonAncientEventWindow(
                ConsensusConstants.ROUND_FIRST, ConsensusConstants.ROUND_NEGATIVE_INFINITY, 4, false));

        final Hash child4Hash = randomHash(random);
        final long child4Generation = 4;
        final GossipEvent child4 = generateMockEvent(
                child4Hash, child4Generation, child2Hash, child2Generation, child3Hash, child3Generation, time.now());

        final EventImpl linkedEvent4 = inOrderLinker.linkEvent(child4);
        assertNotEquals(null, linkedEvent4);
        assertNull(linkedEvent4.getSelfParent(), "Self parent is ancient, and should be null");
        assertNull(linkedEvent4.getOtherParent(), "Other parent is ancient, and should be null");
        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Missing self parent should not be linked")
    void missingSelfParent() {
        final GossipEvent child = generateMockEvent(
                randomHash(random),
                4,
                null,
                genesisSelfParent.getGeneration(),
                genesisOtherParent.getHashedData().getHash(),
                genesisOtherParent.getGeneration(),
                time.now());

        final EventImpl linkedEvent = inOrderLinker.linkEvent(child);
        assertNotEquals(null, linkedEvent);
        assertNull(linkedEvent.getSelfParent(), "Self parent is missing, and should be null");
        assertNotEquals(null, linkedEvent.getOtherParent(), "Other parent is not missing, and should not be null");

        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Missing other parent should not be linked")
    void missingOtherParent() {
        final GossipEvent child = generateMockEvent(
                randomHash(random),
                4,
                genesisSelfParent.getHashedData().getHash(),
                genesisSelfParent.getGeneration(),
                null,
                genesisOtherParent.getGeneration(),
                time.now());

        final EventImpl linkedEvent = inOrderLinker.linkEvent(child);
        assertNotEquals(null, linkedEvent);
        assertNotEquals(null, linkedEvent.getSelfParent(), "Self parent is not missing, and should not be null");
        assertNull(linkedEvent.getOtherParent(), "Other parent is missing, and should be null");

        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Ancient events should immediately exit the intake pipeline")
    void ancientEvent() {
        // FUTURE WORK: change from minGenNonAncient to minRoundNonAncient
        inOrderLinker.setNonAncientEventWindow(new NonAncientEventWindow(
                ConsensusConstants.ROUND_FIRST, ConsensusConstants.ROUND_NEGATIVE_INFINITY, 3, false));

        final GossipEvent child1 = generateMockEvent(
                randomHash(random),
                1,
                genesisSelfParent.getHashedData().getHash(),
                genesisSelfParent.getGeneration(),
                genesisOtherParent.getHashedData().getHash(),
                genesisOtherParent.getGeneration(),
                time.now());

        time.tick(Duration.ofSeconds(1));

        assertNull(inOrderLinker.linkEvent(child1));

        // barely ancient
        final GossipEvent child2 = generateMockEvent(
                randomHash(random),
                2,
                genesisSelfParent.getHashedData().getHash(),
                genesisSelfParent.getGeneration(),
                genesisOtherParent.getHashedData().getHash(),
                genesisOtherParent.getGeneration(),
                time.now());

        assertNull(inOrderLinker.linkEvent(child2));
        assertEquals(2, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Self parent with mismatched generation should not be linked")
    void selfParentGenerationMismatch() {
        final GossipEvent child = generateMockEvent(
                randomHash(random),
                2,
                genesisSelfParent.getHashedData().getHash(),
                genesisSelfParent.getGeneration() + 1,
                genesisOtherParent.getHashedData().getHash(),
                genesisOtherParent.getGeneration(),
                time.now());

        final EventImpl linkedEvent = inOrderLinker.linkEvent(child);
        assertNotEquals(null, linkedEvent);
        assertNull(linkedEvent.getSelfParent(), "Self parent has mismatched generation, and should be null");
        assertNotEquals(null, linkedEvent.getOtherParent(), "Other parent should not be null");
        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Other parent with mismatched generation should not be linked")
    void otherParentGenerationMismatch() {
        final GossipEvent child = generateMockEvent(
                randomHash(random),
                2,
                genesisSelfParent.getHashedData().getHash(),
                genesisSelfParent.getGeneration(),
                genesisOtherParent.getHashedData().getHash(),
                genesisOtherParent.getGeneration() + 1,
                time.now());

        final EventImpl linkedEvent = inOrderLinker.linkEvent(child);
        assertNotEquals(null, linkedEvent);
        assertNotEquals(null, linkedEvent.getSelfParent(), "Self parent should not be null");
        assertNull(linkedEvent.getOtherParent(), "Other parent has mismatched generation, and should be null");
        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Self parent with mismatched time created should not be linked")
    void selfParentTimeCreatedMismatch() {
        final Hash lateParentHash = randomHash(random);
        final long lateParentGeneration = 1;
        final GossipEvent lateParent = generateMockEvent(
                lateParentHash,
                lateParentGeneration,
                genesisSelfParent.getHashedData().getHash(),
                genesisSelfParent.getGeneration(),
                genesisOtherParent.getHashedData().getHash(),
                genesisOtherParent.getGeneration(),
                time.now().plus(Duration.ofSeconds(10)));
        inOrderLinker.linkEvent(lateParent);

        final GossipEvent child = generateMockEvent(
                randomHash(random),
                2,
                lateParentHash,
                lateParentGeneration,
                genesisOtherParent.getHashedData().getHash(),
                genesisOtherParent.getGeneration(),
                time.now());

        final EventImpl linkedEvent = inOrderLinker.linkEvent(child);
        assertNotEquals(null, linkedEvent);
        assertNull(linkedEvent.getSelfParent(), "Self parent has mismatched time created, and should be null");
        assertNotEquals(null, linkedEvent.getOtherParent(), "Other parent should not be null");
        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Other parent with mismatched time created should not be linked")
    void otherParentTimeCreatedMismatch() {
        final Hash lateParentHash = randomHash(random);
        final long lateParentGeneration = 1;
        final GossipEvent lateParent = generateMockEvent(
                lateParentHash,
                lateParentGeneration,
                genesisSelfParent.getHashedData().getHash(),
                genesisSelfParent.getGeneration(),
                genesisOtherParent.getHashedData().getHash(),
                genesisOtherParent.getGeneration(),
                time.now().plus(Duration.ofSeconds(10)));
        inOrderLinker.linkEvent(lateParent);

        final GossipEvent child = generateMockEvent(
                randomHash(random),
                2,
                genesisSelfParent.getHashedData().getHash(),
                genesisSelfParent.getGeneration(),
                lateParentHash,
                lateParentGeneration,
                time.now());

        final EventImpl linkedEvent = inOrderLinker.linkEvent(child);
        assertNotEquals(null, linkedEvent);
        assertNotEquals(null, linkedEvent.getSelfParent(), "Self parent should not be null");
        assertNull(linkedEvent.getOtherParent(), "Other parent has mismatched time created, and should be null");
        assertEquals(0, exitedIntakePipelineCount.get());
    }
}
