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
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.eventhandling.EventConfig_;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import com.swirlds.platform.system.events.EventConstants;
import com.swirlds.platform.system.events.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the {@link InOrderLinker} class. For historical reasons, the linker implementation used in this test is
 * {@link GossipLinker}, and so we also test that the intake event counter is updated correctly (this is a feature
 * specific to {@link GossipLinker}).
 */
class InOrderLinkerTests {
    private AtomicLong exitedIntakePipelineCount;
    private Random random;

    private InOrderLinker inOrderLinker;

    private GossipEvent genesisSelfParent;
    private GossipEvent genesisOtherParent;

    private FakeTime time;

    private NodeId selfId = new NodeId(0);
    private NodeId otherId = new NodeId(1);

    /**
     * Generates a mock event with the given parameters
     *
     * @param selfId          the id of the event creator
     * @param selfHash        the hash of the event
     * @param selfGeneration  the generation of the event
     * @param selfBirthRound  the birthRound of the event
     * @param selfParent      the self parent event descriptor
     * @param otherParent     the other parent event descriptor
     * @param selfTimeCreated the time created of the event
     * @return the mock event
     */
    private static GossipEvent generateMockEvent(
            @NonNull final NodeId selfId,
            @NonNull final Hash selfHash,
            final long selfGeneration,
            final long selfBirthRound,
            @Nullable final EventDescriptor selfParent,
            @Nullable final EventDescriptor otherParent,
            @NonNull final Instant selfTimeCreated) {

        final EventDescriptor selfDescriptor = new EventDescriptor(selfHash, selfId, selfGeneration, selfBirthRound);

        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
        when(hashedData.getHash()).thenReturn(selfHash);
        when(hashedData.getGeneration()).thenReturn(selfGeneration);
        when(hashedData.getBirthRound()).thenReturn(selfBirthRound);
        if (selfParent != null) {
            when(hashedData.getSelfParentHash()).thenReturn(selfParent.getHash());
            when(hashedData.getSelfParentGen()).thenReturn(selfParent.getGeneration());
        } else {
            when(hashedData.getSelfParentHash()).thenReturn(null);
            when(hashedData.getSelfParentGen()).thenReturn(GENERATION_UNDEFINED);
        }
        if (otherParent != null) {
            when(hashedData.getOtherParentHash()).thenReturn(otherParent.getHash());
            when(hashedData.getOtherParentGen()).thenReturn(otherParent.getGeneration());
        } else {
            when(hashedData.getOtherParentHash()).thenReturn(null);
            when(hashedData.getOtherParentGen()).thenReturn(GENERATION_UNDEFINED);
        }
        when(hashedData.getTimeCreated()).thenReturn(selfTimeCreated);
        when(hashedData.getSelfParent()).thenReturn(selfParent);
        when(hashedData.getOtherParents()).thenReturn(Collections.singletonList(otherParent));

        final BaseEventUnhashedData unhashedData = mock(BaseEventUnhashedData.class);

        final GossipEvent event = mock(GossipEvent.class);
        when(event.getGeneration()).thenReturn(selfGeneration);
        when(event.getDescriptor()).thenReturn(selfDescriptor);
        when(event.getHashedData()).thenReturn(hashedData);
        when(event.getUnhashedData()).thenReturn(unhashedData);
        when(event.getSenderId()).thenReturn(selfId);

        when(event.getAncientIndicator(any()))
                .thenAnswer(args -> selfDescriptor.getAncientIndicator((AncientMode) args.getArguments()[0]));

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

        time = new FakeTime();
    }

    private void inOrderLinkerSetup(@NonNull final AncientMode ancientMode) {
        final IntakeEventCounter intakeEventCounter = mock(IntakeEventCounter.class);
        doAnswer(invocation -> {
                    exitedIntakePipelineCount.incrementAndGet();
                    return null;
                })
                .when(intakeEventCounter)
                .eventExitedIntakePipeline(any());

        inOrderLinker = new GossipLinker(
                TestPlatformContextBuilder.create()
                        .withConfiguration(new TestConfigBuilder()
                                .withValue(
                                        EventConfig_.USE_BIRTH_ROUND_ANCIENT_THRESHOLD,
                                        (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD))
                                .getOrCreateConfig())
                        .withTime(time)
                        .build(),
                intakeEventCounter);

        time.tick(Duration.ofSeconds(1));
        genesisSelfParent = generateMockEvent(
                selfId,
                randomHash(random),
                EventConstants.FIRST_GENERATION,
                ConsensusConstants.ROUND_FIRST,
                null,
                null,
                time.now());
        inOrderLinker.linkEvent(genesisSelfParent);

        time.tick(Duration.ofSeconds(1));
        genesisOtherParent = generateMockEvent(
                otherId,
                randomHash(random),
                EventConstants.FIRST_GENERATION,
                ConsensusConstants.ROUND_FIRST,
                null,
                null,
                time.now());
        inOrderLinker.linkEvent(genesisOtherParent);

        time.tick(Duration.ofSeconds(1));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Test standard operation of the in order linker")
    void standardOperation(final boolean useBirthRoundForAncient) {
        final AncientMode ancientMode =
                useBirthRoundForAncient ? AncientMode.BIRTH_ROUND_THRESHOLD : AncientMode.GENERATION_THRESHOLD;
        inOrderLinkerSetup(ancientMode);

        // In the following test events are created with increasing generation and birth round numbers.
        // The linking should fail to occur based on the advancing event window.
        // The values used for birthRound and generation are just for this test and do not reflect real world values.
        // Each event lives for 2 rounds or generations before becoming ancient.

        long latestConsensusRound = ConsensusConstants.ROUND_FIRST;
        long minRoundNonAncient = ConsensusConstants.ROUND_FIRST;
        long minGenNonAncient = EventConstants.FIRST_GENERATION;

        final Hash child1Hash = randomHash(random);
        final GossipEvent child1 = generateMockEvent(
                selfId,
                child1Hash,
                minGenNonAncient + 1,
                minRoundNonAncient + 1,
                genesisSelfParent.getDescriptor(),
                genesisOtherParent.getDescriptor(),
                time.now());

        final EventImpl linkedEvent1 = inOrderLinker.linkEvent(child1);
        assertNotEquals(null, linkedEvent1);
        assertNotEquals(null, linkedEvent1.getSelfParent(), "Self parent is non-ancient, and should not be null");
        assertNotEquals(null, linkedEvent1.getOtherParent(), "Other parent is non-ancient, and should not be null");
        assertEquals(0, exitedIntakePipelineCount.get());

        time.tick(Duration.ofSeconds(1));
        latestConsensusRound += 1;
        minRoundNonAncient += 1;
        minGenNonAncient += 1;
        if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
            inOrderLinker.setEventWindow(new EventWindow(
                    latestConsensusRound,
                    minRoundNonAncient,
                    ConsensusConstants.ROUND_FIRST /* ignored in this context */,
                    ancientMode));
        } else {
            inOrderLinker.setEventWindow(new EventWindow(
                    latestConsensusRound,
                    minGenNonAncient,
                    ConsensusConstants.ROUND_FIRST /* ignored in this context */,
                    ancientMode));
        }

        final Hash child2Hash = randomHash(random);
        final GossipEvent child2 = generateMockEvent(
                selfId,
                child2Hash,
                minGenNonAncient + 1,
                minRoundNonAncient + 1,
                child1.getDescriptor(),
                genesisOtherParent.getDescriptor(),
                time.now());

        final EventImpl linkedEvent2 = inOrderLinker.linkEvent(child2);
        assertNotEquals(null, linkedEvent2);
        assertNotEquals(null, linkedEvent2.getSelfParent(), "Self parent is non-ancient, and should not be null");
        assertNull(linkedEvent2.getOtherParent(), "Other parent is ancient, and should be null");
        assertEquals(0, exitedIntakePipelineCount.get());

        time.tick(Duration.ofSeconds(1));
        latestConsensusRound += 1;
        minRoundNonAncient += 1;
        minGenNonAncient += 1;
        if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
            inOrderLinker.setEventWindow(new EventWindow(
                    latestConsensusRound,
                    minRoundNonAncient,
                    ConsensusConstants.ROUND_FIRST /* ignored in this context */,
                    ancientMode));
        } else {
            inOrderLinker.setEventWindow(new EventWindow(
                    latestConsensusRound,
                    minGenNonAncient,
                    ConsensusConstants.ROUND_FIRST /* ignored in this context */,
                    ancientMode));
        }

        final Hash child3Hash = randomHash(random);
        final GossipEvent child3 = generateMockEvent(
                selfId,
                child3Hash,
                minGenNonAncient + 1,
                minRoundNonAncient + 1,
                child1.getDescriptor(),
                child2.getDescriptor(),
                time.now());

        final EventImpl linkedEvent3 = inOrderLinker.linkEvent(child3);
        assertNotEquals(null, linkedEvent3);
        assertNull(linkedEvent3.getSelfParent(), "Self parent is ancient, and should be null");
        assertNotEquals(null, linkedEvent3.getOtherParent(), "Other parent is non-ancient, and should not be null");
        assertEquals(0, exitedIntakePipelineCount.get());

        time.tick(Duration.ofSeconds(1));
        latestConsensusRound += 1;
        // make both parents ancient.
        minRoundNonAncient += 2;
        minGenNonAncient += 2;
        if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
            inOrderLinker.setEventWindow(new EventWindow(
                    latestConsensusRound,
                    minRoundNonAncient,
                    ConsensusConstants.ROUND_FIRST /* ignored in this context */,
                    ancientMode));
        } else {
            inOrderLinker.setEventWindow(new EventWindow(
                    latestConsensusRound,
                    minGenNonAncient,
                    ConsensusConstants.ROUND_FIRST /* ignored in this context */,
                    ancientMode));
        }

        final Hash child4Hash = randomHash(random);
        final GossipEvent child4 = generateMockEvent(
                selfId,
                child4Hash,
                minGenNonAncient + 1,
                minRoundNonAncient + 1,
                child2.getDescriptor(),
                child3.getDescriptor(),
                time.now());

        final EventImpl linkedEvent4 = inOrderLinker.linkEvent(child4);
        assertNotEquals(null, linkedEvent4);
        assertNull(linkedEvent4.getSelfParent(), "Self parent is ancient, and should be null");
        assertNull(linkedEvent4.getOtherParent(), "Other parent is ancient, and should be null");
        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Missing self parent should not be linked")
    void missingSelfParent(final boolean useBirthRoundForAncient) {
        final AncientMode ancientMode =
                useBirthRoundForAncient ? AncientMode.BIRTH_ROUND_THRESHOLD : AncientMode.GENERATION_THRESHOLD;
        inOrderLinkerSetup(ancientMode);
        final GossipEvent child = generateMockEvent(
                selfId, randomHash(random), 4, 1, null, genesisOtherParent.getDescriptor(), time.now());

        final EventImpl linkedEvent = inOrderLinker.linkEvent(child);
        assertNotEquals(null, linkedEvent);
        assertNull(linkedEvent.getSelfParent(), "Self parent is missing, and should be null");
        assertNotEquals(null, linkedEvent.getOtherParent(), "Other parent is not missing, and should not be null");

        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Missing other parent should not be linked")
    void missingOtherParent(final boolean useBirthRoundForAncient) {
        final AncientMode ancientMode =
                useBirthRoundForAncient ? AncientMode.BIRTH_ROUND_THRESHOLD : AncientMode.GENERATION_THRESHOLD;
        inOrderLinkerSetup(ancientMode);
        final GossipEvent child = generateMockEvent(
                selfId, randomHash(random), 4, 1, genesisSelfParent.getDescriptor(), null, time.now());

        final EventImpl linkedEvent = inOrderLinker.linkEvent(child);
        assertNotEquals(null, linkedEvent);
        assertNotEquals(null, linkedEvent.getSelfParent(), "Self parent is not missing, and should not be null");
        assertNull(linkedEvent.getOtherParent(), "Other parent is missing, and should be null");

        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Ancient events should immediately exit the intake pipeline")
    void ancientEvent(final boolean useBirthRoundForAncient) {
        final AncientMode ancientMode =
                useBirthRoundForAncient ? AncientMode.BIRTH_ROUND_THRESHOLD : AncientMode.GENERATION_THRESHOLD;
        inOrderLinkerSetup(ancientMode);
        final long minRoundNonAncient = 3;
        final long minGenNonAncient = 3;
        if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
            inOrderLinker.setEventWindow(new EventWindow(
                    ConsensusConstants.ROUND_FIRST /* not consequential for this test */,
                    minRoundNonAncient,
                    ConsensusConstants.ROUND_FIRST /* ignored in this context */,
                    ancientMode));
        } else {
            inOrderLinker.setEventWindow(new EventWindow(
                    ConsensusConstants.ROUND_FIRST /* not consequential for this test */,
                    minGenNonAncient,
                    ConsensusConstants.ROUND_FIRST /* ignored in this context */,
                    ancientMode));
        }

        final GossipEvent child1 = generateMockEvent(
                selfId,
                randomHash(random),
                1,
                1,
                genesisSelfParent.getDescriptor(),
                genesisOtherParent.getDescriptor(),
                time.now());

        time.tick(Duration.ofSeconds(1));

        assertNull(inOrderLinker.linkEvent(child1));

        // barely ancient
        final GossipEvent child2 = generateMockEvent(
                selfId,
                randomHash(random),
                2,
                2,
                genesisSelfParent.getDescriptor(),
                genesisOtherParent.getDescriptor(),
                time.now());

        assertNull(inOrderLinker.linkEvent(child2));
        assertEquals(2, exitedIntakePipelineCount.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Self parent with mismatched generation should not be linked")
    void selfParentGenerationMismatch(final boolean useBirthRoundForAncient) {
        final AncientMode ancientMode =
                useBirthRoundForAncient ? AncientMode.BIRTH_ROUND_THRESHOLD : AncientMode.GENERATION_THRESHOLD;
        inOrderLinkerSetup(ancientMode);
        final EventDescriptor mismatchedSelfParent = new EventDescriptor(
                genesisSelfParent.getDescriptor().getHash(),
                genesisSelfParent.getSenderId(),
                genesisSelfParent.getGeneration() + 1,
                genesisSelfParent.getHashedData().getBirthRound());
        final GossipEvent child = generateMockEvent(
                selfId, randomHash(random), 2, 1, mismatchedSelfParent, genesisOtherParent.getDescriptor(), time.now());

        final EventImpl linkedEvent = inOrderLinker.linkEvent(child);
        assertNotEquals(null, linkedEvent);
        assertNull(linkedEvent.getSelfParent(), "Self parent has mismatched generation, and should be null");
        assertNotEquals(null, linkedEvent.getOtherParent(), "Other parent should not be null");
        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Self parent with mismatched birth round should not be linked")
    void selfParentBirthRoundMismatch(final boolean useBirthRoundForAncient) {
        final AncientMode ancientMode =
                useBirthRoundForAncient ? AncientMode.BIRTH_ROUND_THRESHOLD : AncientMode.GENERATION_THRESHOLD;
        inOrderLinkerSetup(ancientMode);
        final EventDescriptor mismatchedSelfParent = new EventDescriptor(
                genesisSelfParent.getDescriptor().getHash(),
                genesisSelfParent.getSenderId(),
                genesisSelfParent.getGeneration(),
                genesisSelfParent.getHashedData().getBirthRound() + 1);
        final GossipEvent child = generateMockEvent(
                selfId, randomHash(random), 2, 1, mismatchedSelfParent, genesisOtherParent.getDescriptor(), time.now());

        final EventImpl linkedEvent = inOrderLinker.linkEvent(child);
        assertNotEquals(null, linkedEvent);
        assertNull(linkedEvent.getSelfParent(), "Self parent has mismatched generation, and should be null");
        assertNotEquals(null, linkedEvent.getOtherParent(), "Other parent should not be null");
        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Other parent with mismatched generation should not be linked")
    void otherParentGenerationMismatch(final boolean useBirthRoundForAncient) {
        final AncientMode ancientMode =
                useBirthRoundForAncient ? AncientMode.BIRTH_ROUND_THRESHOLD : AncientMode.GENERATION_THRESHOLD;
        inOrderLinkerSetup(ancientMode);
        final EventDescriptor mismatchedOtherParent = new EventDescriptor(
                genesisOtherParent.getDescriptor().getHash(),
                genesisOtherParent.getSenderId(),
                genesisOtherParent.getGeneration() + 1,
                genesisOtherParent.getHashedData().getBirthRound());
        final GossipEvent child = generateMockEvent(
                selfId, randomHash(random), 2, 1, genesisSelfParent.getDescriptor(), mismatchedOtherParent, time.now());

        final EventImpl linkedEvent = inOrderLinker.linkEvent(child);
        assertNotEquals(null, linkedEvent);
        assertNotEquals(null, linkedEvent.getSelfParent(), "Self parent should not be null");
        assertNull(linkedEvent.getOtherParent(), "Other parent has mismatched generation, and should be null");
        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Other parent with mismatched birth round should not be linked")
    void otherParentBirthRoundMismatch(final boolean useBirthRoundForAncient) {
        final AncientMode ancientMode =
                useBirthRoundForAncient ? AncientMode.BIRTH_ROUND_THRESHOLD : AncientMode.GENERATION_THRESHOLD;
        inOrderLinkerSetup(ancientMode);
        final EventDescriptor mismatchedOtherParent = new EventDescriptor(
                genesisOtherParent.getDescriptor().getHash(),
                genesisOtherParent.getSenderId(),
                genesisOtherParent.getGeneration(),
                genesisOtherParent.getHashedData().getBirthRound() + 1);
        final GossipEvent child = generateMockEvent(
                selfId, randomHash(random), 2, 1, genesisSelfParent.getDescriptor(), mismatchedOtherParent, time.now());

        final EventImpl linkedEvent = inOrderLinker.linkEvent(child);
        assertNotEquals(null, linkedEvent);
        assertNotEquals(null, linkedEvent.getSelfParent(), "Self parent should not be null");
        assertNull(linkedEvent.getOtherParent(), "Other parent has mismatched generation, and should be null");
        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Self parent with mismatched time created should not be linked")
    void selfParentTimeCreatedMismatch(final boolean useBirthRoundForAncient) {
        final AncientMode ancientMode =
                useBirthRoundForAncient ? AncientMode.BIRTH_ROUND_THRESHOLD : AncientMode.GENERATION_THRESHOLD;
        inOrderLinkerSetup(ancientMode);
        final Hash lateParentHash = randomHash(random);
        final long lateParentGeneration = 1;
        final GossipEvent lateParent = generateMockEvent(
                selfId,
                lateParentHash,
                lateParentGeneration,
                1,
                genesisSelfParent.getDescriptor(),
                genesisOtherParent.getDescriptor(),
                time.now().plus(Duration.ofSeconds(10)));
        inOrderLinker.linkEvent(lateParent);

        final GossipEvent child = generateMockEvent(
                selfId,
                randomHash(random),
                2,
                1,
                lateParent.getDescriptor(),
                genesisOtherParent.getDescriptor(),
                time.now());

        final EventImpl linkedEvent = inOrderLinker.linkEvent(child);
        assertNotEquals(null, linkedEvent);
        assertNull(linkedEvent.getSelfParent(), "Self parent has mismatched time created, and should be null");
        assertNotEquals(null, linkedEvent.getOtherParent(), "Other parent should not be null");
        assertEquals(0, exitedIntakePipelineCount.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Other parent with mismatched time created should be linked")
    void otherParentTimeCreatedMismatch(final boolean useBirthRoundForAncient) {
        final AncientMode ancientMode =
                useBirthRoundForAncient ? AncientMode.BIRTH_ROUND_THRESHOLD : AncientMode.GENERATION_THRESHOLD;
        inOrderLinkerSetup(ancientMode);
        final Hash lateParentHash = randomHash(random);
        final long lateParentGeneration = 1;
        final GossipEvent lateParent = generateMockEvent(
                otherId,
                lateParentHash,
                lateParentGeneration,
                1,
                genesisSelfParent.getDescriptor(),
                genesisOtherParent.getDescriptor(),
                time.now().plus(Duration.ofSeconds(10)));
        inOrderLinker.linkEvent(lateParent);

        final GossipEvent child = generateMockEvent(
                selfId,
                randomHash(random),
                2,
                2,
                genesisSelfParent.getDescriptor(),
                lateParent.getDescriptor(),
                time.now());

        final EventImpl linkedEvent = inOrderLinker.linkEvent(child);
        assertNotEquals(null, linkedEvent);
        assertNotEquals(null, linkedEvent.getSelfParent(), "Self parent should not be null");
        assertNotEquals(null, linkedEvent.getOtherParent(), "Other parent should not be null");
        assertEquals(0, exitedIntakePipelineCount.get());
    }
}
