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
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.platform.event.deduplication.EventDeduplicator;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
            @NonNull final byte[] signature) {

        final EventDescriptor descriptor = new EventDescriptor(hash, creatorId, generation);

        final BaseEventUnhashedData unhashedData = mock(BaseEventUnhashedData.class);
        when(unhashedData.getSignature()).thenReturn(signature);

        final GossipEvent event = mock(GossipEvent.class);
        when(event.getDescriptor()).thenReturn(descriptor);
        when(event.getGeneration()).thenReturn(generation);
        when(event.getUnhashedData()).thenReturn(unhashedData);

        return event;
    }

    @Test
    @DisplayName("Test standard event deduplicator operation")
    void standardOperation() {
        final AtomicLong minimumGenerationNonAncient = new AtomicLong(0);
        // events that have been emitted from the deduplicator
        final Set<GossipEvent> emittedEvents = new HashSet<>();

        // events that have been submitted to the deduplicator
        final List<GossipEvent> submittedEvents = new ArrayList<>();

        final Consumer<GossipEvent> eventConsumer = event -> {
            assertFalse(
                    event.getGeneration() < minimumGenerationNonAncient.get(), "Ancient events shouldn't be emitted");

            assertTrue(emittedEvents.add(event), "Event was emitted twice");
        };

        final AtomicLong eventsExitedIntakePipeline = new AtomicLong(0);
        final IntakeEventCounter intakeEventCounter = mock(IntakeEventCounter.class);
        doAnswer(invocation -> {
                    eventsExitedIntakePipeline.incrementAndGet();
                    return null;
                })
                .when(intakeEventCounter)
                .eventExitedIntakePipeline(any());

        final EventDeduplicator deduplicator =
                new EventDeduplicator(TestPlatformContextBuilder.create().build(), eventConsumer, intakeEventCounter);

        int duplicateEventCount = 0;
        int ancientEventCount = 0;

        for (int i = 0; i < TEST_EVENT_COUNT; i++) {
            if (submittedEvents.isEmpty() || random.nextBoolean()) {
                // submit a brand new event half the time
                final Hash eventHash = randomHash(random);
                final NodeId creatorId = new NodeId(random.nextInt(NODE_ID_COUNT));
                final long eventGeneration = Math.max(0, minimumGenerationNonAncient.get() + random.nextInt(-1, 10));

                if (eventGeneration < minimumGenerationNonAncient.get()) {
                    ancientEventCount++;
                }

                final GossipEvent newEvent = createGossipEvent(
                        eventHash,
                        creatorId,
                        eventGeneration,
                        randomSignature(random).getSignatureBytes());

                deduplicator.handleEvent(newEvent);
                submittedEvents.add(newEvent);
            } else if (random.nextBoolean()) {
                // submit a duplicate event 25% of the time
                duplicateEventCount++;

                deduplicator.handleEvent(submittedEvents.get(random.nextInt(submittedEvents.size())));
            } else {
                // submit a duplicate event with a different signature 25% of the time
                final GossipEvent duplicateEvent = submittedEvents.get(random.nextInt(submittedEvents.size()));
                final GossipEvent eventWithDisparateSignature = createGossipEvent(
                        duplicateEvent.getDescriptor().getHash(),
                        duplicateEvent.getDescriptor().getCreator(),
                        duplicateEvent.getDescriptor().getGeneration(),
                        randomSignature(random).getSignatureBytes());

                if (duplicateEvent.getDescriptor().getGeneration() < minimumGenerationNonAncient.get()) {
                    ancientEventCount++;
                }

                deduplicator.handleEvent(eventWithDisparateSignature);
            }

            if (random.nextBoolean()) {
                deduplicator.setMinimumGenerationNonAncient(minimumGenerationNonAncient.addAndGet(1));
            }
        }

        assertEquals(TEST_EVENT_COUNT, eventsExitedIntakePipeline.get() + emittedEvents.size());
        assertEquals(TEST_EVENT_COUNT, emittedEvents.size() + ancientEventCount + duplicateEventCount);
    }
}
