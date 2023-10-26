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

package com.swirlds.platform.event.orphan;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OrphanBuffer}
 */
class OrphanBufferTests {
    /**
     * Events that will be "received" from intake
     */
    private List<GossipEvent> intakeEvents;

    /**
     * The maximum generation of any event that has been created
     */
    private long maxGeneration;

    private Random random;

    /**
     * The number of events to be created for testing
     */
    private static final long TEST_EVENT_COUNT = 10000;

    /**
     * Number of possible nodes in the universe
     */
    private static final int NODE_ID_COUNT = 100;

    /**
     * The number of most recently created events to consider when choosing an other parent
     */
    private static final int PARENT_SELECTION_WINDOW = 100;

    /**
     * The maximum amount to advance minimumGenerationNonAncient at a time. Average advancement will be half this.
     */
    private static final int MAX_GENERATION_STEP = 10;

    /**
     * Create a bootstrap event for a node. This is just a descriptor, and will never be received from intake.
     *
     * @param nodeId           the node to create the bootstrap event for
     * @param parentCandidates the list of events to choose from when selecting an other parent
     * @return the bootstrap event descriptor
     */
    private EventDescriptor createBootstrapEvent(
            @NonNull final NodeId nodeId, @NonNull final List<EventDescriptor> parentCandidates) {
        final EventDescriptor bootstrapEvent = new EventDescriptor(randomHash(random), nodeId, 0);

        parentCandidates.add(bootstrapEvent);

        return bootstrapEvent;
    }

    /**
     * Create a gossip event with the given parameters.
     *
     * @param eventHash       the hash of the event
     * @param eventCreator    the creator of the event
     * @param eventGeneration the generation of the event
     * @param selfParent      the self parent of the event
     * @param otherParent     the other parent of the event
     * @return the gossip event
     */
    private GossipEvent createGossipEvent(
            @NonNull final Hash eventHash,
            @NonNull final NodeId eventCreator,
            final long eventGeneration,
            @NonNull final EventDescriptor selfParent,
            @NonNull final EventDescriptor otherParent) {
        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
        when(hashedData.getHash()).thenReturn(eventHash);
        when(hashedData.getCreatorId()).thenReturn(eventCreator);
        when(hashedData.getGeneration()).thenReturn(eventGeneration);
        when(hashedData.getSelfParentGen()).thenReturn(selfParent.getGeneration());
        when(hashedData.getOtherParentGen()).thenReturn(otherParent.getGeneration());
        when(hashedData.getSelfParentHash()).thenReturn(selfParent.getHash());
        when(hashedData.getOtherParentHash()).thenReturn(otherParent.getHash());
        when(hashedData.getTimeCreated()).thenReturn(Instant.now());

        final BaseEventUnhashedData unhashedData = mock(BaseEventUnhashedData.class);
        when(unhashedData.getOtherId()).thenReturn(otherParent.getCreator());

        final GossipEvent event = mock(GossipEvent.class);
        when(event.getHashedData()).thenReturn(hashedData);
        when(event.getUnhashedData()).thenReturn(unhashedData);
        when(event.getDescriptor()).thenReturn(new EventDescriptor(eventHash, eventCreator, eventGeneration));
        when(event.getGeneration()).thenReturn(eventGeneration);
        when(event.getSenderId()).thenReturn(eventCreator);

        return event;
    }

    /**
     * Create a random event
     *
     * @param parentCandidates the list of events to choose from when selecting an other parent
     * @param tips             the most recent events from each node
     * @return the random event
     */
    private GossipEvent createRandomEvent(
            @NonNull final List<EventDescriptor> parentCandidates, @NonNull final Map<NodeId, EventDescriptor> tips) {

        final Hash eventHash = randomHash(random);
        final NodeId eventCreator = new NodeId(random.nextInt(NODE_ID_COUNT));

        final EventDescriptor selfParent =
                tips.computeIfAbsent(eventCreator, creator -> createBootstrapEvent(creator, parentCandidates));

        final EventDescriptor otherParent = chooseOtherParent(parentCandidates);

        final long maxParentGeneration = Math.max(selfParent.getGeneration(), otherParent.getGeneration());
        final long eventGeneration = maxParentGeneration + 1;
        maxGeneration = Math.max(maxGeneration, eventGeneration);

        return createGossipEvent(eventHash, eventCreator, eventGeneration, selfParent, otherParent);
    }

    /**
     * Check if an event has been emitted or is ancient
     *
     * @param eventHash                   the hash of the event
     * @param eventGeneration             the generation of the event
     * @param minimumGenerationNonAncient the minimum generation of non-ancient events
     * @return true if the event has been emitted or is ancient, false otherwise
     */
    private boolean eventEmittedOrAncient(
            @NonNull final Hash eventHash,
            final long eventGeneration,
            final long minimumGenerationNonAncient,
            @NonNull final Set<Hash> emittedEvents) {

        return emittedEvents.contains(eventHash) || eventGeneration < minimumGenerationNonAncient;
    }

    /**
     * Choose an other parent from the given list of candidates. This method chooses from the last PARENT_SELECTION_WINDOW
     * events in the list.
     *
     * @param parentCandidates the list of candidates
     * @return the chosen other parent
     */
    private EventDescriptor chooseOtherParent(@NonNull final List<EventDescriptor> parentCandidates) {
        final int startIndex = Math.max(0, parentCandidates.size() - PARENT_SELECTION_WINDOW);
        return parentCandidates.get(
                startIndex + random.nextInt(Math.min(PARENT_SELECTION_WINDOW, parentCandidates.size())));
    }

    @BeforeEach
    void setup() {
        random = getRandomPrintSeed();

        final ArrayList<EventDescriptor> parentCandidates = new ArrayList<>();
        final Map<NodeId, EventDescriptor> tips = new HashMap<>();

        intakeEvents = new ArrayList<>();

        for (long i = 0; i < TEST_EVENT_COUNT; i++) {
            final GossipEvent newEvent = createRandomEvent(parentCandidates, tips);

            parentCandidates.add(newEvent.getDescriptor());
            intakeEvents.add(newEvent);
        }
    }

    @Test
    @DisplayName("Test standard orphan buffer operation")
    void standardOperation() {
        final AtomicLong minimumGenerationNonAncient = new AtomicLong(0);

        // events that have been emitted from the orphan buffer
        final Set<Hash> emittedEvents = new HashSet<>();

        final Consumer<GossipEvent> eventConsumer = event -> {
            final BaseEventHashedData hashedData = event.getHashedData();
            assertFalse(
                    hashedData.getGeneration() < minimumGenerationNonAncient.get(),
                    "Ancient events shouldn't be emitted");
            assertTrue(
                    eventEmittedOrAncient(
                            hashedData.getSelfParentHash(),
                            hashedData.getSelfParentGen(),
                            minimumGenerationNonAncient.get(),
                            emittedEvents),
                    "Self parent was neither emitted nor ancient");
            assertTrue(
                    eventEmittedOrAncient(
                            hashedData.getOtherParentHash(),
                            hashedData.getOtherParentGen(),
                            minimumGenerationNonAncient.get(),
                            emittedEvents),
                    "Other parent was neither emitted nor ancient");
            assertTrue(emittedEvents.add(event.getDescriptor().getHash()), "Event was emitted twice");
        };

        final AtomicLong eventsExitedIntakePipeline = new AtomicLong(0);
        final IntakeEventCounter intakeEventCounter = mock(IntakeEventCounter.class);
        doAnswer(invocation -> {
                    eventsExitedIntakePipeline.incrementAndGet();
                    return null;
                })
                .when(intakeEventCounter)
                .eventExitedIntakePipeline(any());

        final OrphanBuffer orphanBuffer =
                new OrphanBuffer(TestPlatformContextBuilder.create().build(), eventConsumer, intakeEventCounter);

        // increase minimum generation non-ancient at the approximate rate that event generations are increasing
        // this means that roughly half of the events will be ancient before they are received from intake
        final float averageGenerationAdvancement = (float) maxGeneration / TEST_EVENT_COUNT;

        Collections.shuffle(intakeEvents, random);
        for (final GossipEvent intakeEvent : intakeEvents) {
            orphanBuffer.handleEvent(intakeEvent);

            // add some randomness to step size, so minimumGenerationNonAncient doesn't always just increase by 1
            final int stepRandomness = Math.round(random.nextFloat() * MAX_GENERATION_STEP);
            if (random.nextFloat() < averageGenerationAdvancement / stepRandomness) {
                minimumGenerationNonAncient.addAndGet(stepRandomness);
                orphanBuffer.setMinimumGenerationNonAncient(minimumGenerationNonAncient.get());
            }
        }

        // either events exit the pipeline in the orphan buffer and are never emitted, or they are emitted and exit
        // the pipeline at a later stage
        assertEquals(TEST_EVENT_COUNT, eventsExitedIntakePipeline.get() + emittedEvents.size());
        assertEquals(0, orphanBuffer.getCurrentOrphanCount());
    }
}
