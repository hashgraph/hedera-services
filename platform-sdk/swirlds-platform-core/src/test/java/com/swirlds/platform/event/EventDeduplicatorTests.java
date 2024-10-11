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
import static com.swirlds.common.test.fixtures.RandomUtils.randomSignatureBytes;
import static com.swirlds.platform.test.fixtures.event.EventUtils.serializePlatformEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.platform.event.GossipEvent;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.deduplication.EventDeduplicator;
import com.swirlds.platform.event.deduplication.StandardEventDeduplicator;
import com.swirlds.platform.eventhandling.EventConfig_;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.system.events.EventConstants;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.ByteBuffer;
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
     * Create a test platform event
     *
     * @param creatorId  the creator of the event
     * @param generation the generation of the event
     * @param birthRound the birth round of the event
     * @return the mocked platform event
     */
    private PlatformEvent createPlatformEvent(
            @NonNull final NodeId creatorId, final long generation, final long birthRound) {

        final PlatformEvent selfParent = new TestingEventBuilder(random)
                .setCreatorId(creatorId)
                .setBirthRound(birthRound - 1)
                .build();

        final PlatformEvent event = new TestingEventBuilder(random)
                .setCreatorId(creatorId)
                .setBirthRound(birthRound)
                .setSelfParent(selfParent)
                .overrideSelfParentGeneration(generation - 1)
                .build();

        return event;
    }

    private static void validateEmittedEvent(
            @Nullable final PlatformEvent event,
            final long minimumGenerationNonAncient,
            final long minimumRoundNonAncient,
            @NonNull final AncientMode ancientMode,
            @NonNull final Set<ByteBuffer> emittedEvents) {
        if (event != null) {
            if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
                assertFalse(
                        event.getDescriptor().eventDescriptor().birthRound() < minimumRoundNonAncient,
                        "Ancient events shouldn't be emitted");
            } else {
                assertFalse(event.getGeneration() < minimumGenerationNonAncient, "Ancient events shouldn't be emitted");
            }
            assertTrue(emittedEvents.add(ByteBuffer.wrap(serializePlatformEvent(event))), "Event was emitted twice");
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
        // contents of the set are the serialized events
        final Set<ByteBuffer> emittedEvents = new HashSet<>();

        // events that have been submitted to the deduplicator
        final List<PlatformEvent> submittedEvents = new ArrayList<>();

        final AtomicLong eventsExitedIntakePipeline = new AtomicLong(0);
        final IntakeEventCounter intakeEventCounter = mock(IntakeEventCounter.class);
        doAnswer(invocation -> {
                    eventsExitedIntakePipeline.incrementAndGet();
                    return null;
                })
                .when(intakeEventCounter)
                .eventExitedIntakePipeline(any());

        final EventDeduplicator deduplicator = new StandardEventDeduplicator(
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

                final PlatformEvent newEvent = createPlatformEvent(creatorId, eventGeneration, eventBirthRound);

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
                final PlatformEvent platformEvent = submittedEvents.get(random.nextInt(submittedEvents.size()));
                final PlatformEvent duplicateEvent = new PlatformEvent(new GossipEvent.Builder()
                        .eventCore(platformEvent.getGossipEvent().eventCore())
                        .signature(randomSignatureBytes(random)) // randomize the signature
                        .eventTransaction(platformEvent.getGossipEvent().eventTransaction())
                        .build());
                duplicateEvent.setHash(platformEvent.getHash());

                if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
                    if (duplicateEvent.getDescriptor().eventDescriptor().birthRound() < minimumRoundNonAncient) {
                        ancientEventCount++;
                    }
                } else {
                    if (duplicateEvent.getDescriptor().eventDescriptor().generation() < minimumGenerationNonAncient) {
                        ancientEventCount++;
                    }
                }

                validateEmittedEvent(
                        deduplicator.handleEvent(duplicateEvent),
                        minimumGenerationNonAncient,
                        minimumRoundNonAncient,
                        ancientMode,
                        emittedEvents);
            }

            if (random.nextBoolean()) {
                minimumGenerationNonAncient++;
                minimumRoundNonAncient++;
                if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
                    deduplicator.setEventWindow(new EventWindow(
                            ConsensusConstants.ROUND_FIRST,
                            minimumRoundNonAncient,
                            ConsensusConstants.ROUND_FIRST /* ignored in this context */,
                            AncientMode.BIRTH_ROUND_THRESHOLD));
                } else {
                    deduplicator.setEventWindow(new EventWindow(
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
