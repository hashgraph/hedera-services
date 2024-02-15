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

package com.swirlds.platform.event;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.common.test.fixtures.RandomUtils.randomSignature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.deduplication.EventDeduplicator;
import com.swirlds.platform.eventhandling.EventConfig_;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import com.swirlds.platform.system.events.EventConstants;
import com.swirlds.platform.system.events.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the {@link EventDeduplicator} class
 */
class EventDeduplicatorTests {
    private Random random;

    /**
     * Number of possible nodes in the universe
     */
    private static final int NODE_ID_COUNT = 100;

    /**
     * The number of events to be created for testing
     */
    private static final long TEST_EVENT_COUNT = 1000;

    @BeforeEach
    void setup() {
        random = getRandomPrintSeed();
    }

    /**
     * Mock a gossip event
     *
     * @param hash       the hash of the event
     * @param creatorId  the creator of the event
     * @param generation the generation of the event
     * @param signature  the signature of the event
     * @return the mocked gossip event
     */
    private GossipEvent createGossipEvent(
            @NonNull final Hash hash,
            @NonNull final NodeId creatorId,
            final long generation,
            final long birthRound,
            @NonNull final byte[] signature) {

        final EventDescriptor descriptor = new EventDescriptor(hash, creatorId, generation, birthRound);

        final BaseEventUnhashedData unhashedData = mock(BaseEventUnhashedData.class);
        when(unhashedData.getSignature()).thenReturn(signature);

        final GossipEvent event = mock(GossipEvent.class);
        when(event.getDescriptor()).thenReturn(descriptor);
        when(event.getGeneration()).thenReturn(generation);
        when(event.getUnhashedData()).thenReturn(unhashedData);
        when(event.getAncientIndicator(any()))
                .thenAnswer(
                        args -> args.getArguments()[0] == AncientMode.BIRTH_ROUND_THRESHOLD ? birthRound : generation);

        return event;
    }

    private static void validateEmittedEvent(
            @Nullable final GossipEvent event,
            final long minimumGenerationNonAncient,
            final long minimumRoundNonAncient,
            @NonNull final AncientMode ancientMode,
            @NonNull final Set<GossipEvent> emittedEvents) {
        if (event != null) {
            if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
                assertFalse(
                        event.getDescriptor().getBirthRound() < minimumRoundNonAncient,
                        "Ancient events shouldn't be emitted");
            } else {
                assertFalse(event.getGeneration() < minimumGenerationNonAncient, "Ancient events shouldn't be emitted");
            }
            assertTrue(emittedEvents.add(event), "Event was emitted twice");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Test standard event deduplicator operation")
    void standardOperation(final boolean useBirthRoundForAncientThreshold) {
        final AncientMode ancientMode =
                useBirthRoundForAncientThreshold ? AncientMode.BIRTH_ROUND_THRESHOLD : AncientMode.GENERATION_THRESHOLD;

        long minimumGenerationNonAncient = EventConstants.FIRST_GENERATION;
        long minimumRoundNonAncient = ConsensusConstants.ROUND_FIRST;

        // events that have been emitted from the deduplicator
        final Set<GossipEvent> emittedEvents = new HashSet<>();

        // events that have been submitted to the deduplicator
        final List<GossipEvent> submittedEvents = new ArrayList<>();

        final AtomicLong eventsExitedIntakePipeline = new AtomicLong(0);
        final IntakeEventCounter intakeEventCounter = mock(IntakeEventCounter.class);
        doAnswer(invocation -> {
                    eventsExitedIntakePipeline.incrementAndGet();
                    return null;
                })
                .when(intakeEventCounter)
                .eventExitedIntakePipeline(any());

        final EventDeduplicator deduplicator = new EventDeduplicator(
                TestPlatformContextBuilder.create()
                        .withConfiguration(new TestConfigBuilder()
                                .withValue(
                                        EventConfig_.USE_BIRTH_ROUND_ANCIENT_THRESHOLD,
                                        ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD)
                                .getOrCreateConfig())
                        .build(),
                intakeEventCounter);

        int duplicateEventCount = 0;
        int ancientEventCount = 0;

        for (int i = 0; i < TEST_EVENT_COUNT; i++) {
            if (submittedEvents.isEmpty() || random.nextBoolean()) {
                // submit a brand new event half the time
                final Hash eventHash = randomHash(random);
                final NodeId creatorId = new NodeId(random.nextInt(NODE_ID_COUNT));
                final long eventGeneration = Math.max(0, minimumGenerationNonAncient + random.nextInt(-1, 10));
                final long eventBirthRound =
                        Math.max(ConsensusConstants.ROUND_FIRST, minimumRoundNonAncient + random.nextLong(-1, 4));

                if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
                    if (eventBirthRound < minimumRoundNonAncient) {
                        ancientEventCount++;
                    }
                } else {
                    if (eventGeneration < minimumGenerationNonAncient) {
                        ancientEventCount++;
                    }
                }

                final GossipEvent newEvent = createGossipEvent(
                        eventHash,
                        creatorId,
                        eventGeneration,
                        eventBirthRound,
                        randomSignature(random).getSignatureBytes());

                validateEmittedEvent(
                        deduplicator.handleEvent(newEvent),
                        minimumGenerationNonAncient,
                        minimumRoundNonAncient,
                        ancientMode,
                        emittedEvents);

                submittedEvents.add(newEvent);
            } else if (random.nextBoolean()) {
                // submit a duplicate event 25% of the time
                duplicateEventCount++;

                validateEmittedEvent(
                        deduplicator.handleEvent(submittedEvents.get(random.nextInt(submittedEvents.size()))),
                        minimumGenerationNonAncient,
                        minimumRoundNonAncient,
                        ancientMode,
                        emittedEvents);
            } else {
                // submit a duplicate event with a different signature 25% of the time
                final GossipEvent duplicateEvent = submittedEvents.get(random.nextInt(submittedEvents.size()));
                final GossipEvent eventWithDisparateSignature = createGossipEvent(
                        duplicateEvent.getDescriptor().getHash(),
                        duplicateEvent.getDescriptor().getCreator(),
                        duplicateEvent.getDescriptor().getGeneration(),
                        duplicateEvent.getDescriptor().getBirthRound(),
                        randomSignature(random).getSignatureBytes());

                if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
                    if (duplicateEvent.getDescriptor().getBirthRound() < minimumRoundNonAncient) {
                        ancientEventCount++;
                    }
                } else {
                    if (duplicateEvent.getDescriptor().getGeneration() < minimumGenerationNonAncient) {
                        ancientEventCount++;
                    }
                }

                validateEmittedEvent(
                        deduplicator.handleEvent(eventWithDisparateSignature),
                        minimumGenerationNonAncient,
                        minimumRoundNonAncient,
                        ancientMode,
                        emittedEvents);
            }

            if (random.nextBoolean()) {
                minimumGenerationNonAncient++;
                minimumRoundNonAncient++;
                if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
                    deduplicator.setNonAncientEventWindow(new NonAncientEventWindow(
                            ConsensusConstants.ROUND_FIRST,
                            minimumRoundNonAncient,
                            ConsensusConstants.ROUND_FIRST /* ignored in this context */,
                            AncientMode.BIRTH_ROUND_THRESHOLD));
                } else {
                    deduplicator.setNonAncientEventWindow(new NonAncientEventWindow(
                            ConsensusConstants.ROUND_FIRST,
                            minimumGenerationNonAncient,
                            ConsensusConstants.ROUND_FIRST /* ignored in this context */,
                            AncientMode.GENERATION_THRESHOLD));
                }
            }
        }

        assertEquals(TEST_EVENT_COUNT, eventsExitedIntakePipeline.get() + emittedEvents.size());
        assertEquals(TEST_EVENT_COUNT, emittedEvents.size() + ancientEventCount + duplicateEventCount);
    }
}
