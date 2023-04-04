/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter;

import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.time.TimeFactory;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.test.metrics.NoOpMetrics;
import com.swirlds.platform.chatter.ChatterSubSetting;
import com.swirlds.platform.chatter.protocol.ChatterCore;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.test.event.GossipEventBuilder;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ChatterCore}
 */
public class ChatterCoreTests {

    /**
     * Verifies that when a signed state is loaded from, all peer instance windows are shifted to the new generation
     * window.
     */
    @Test
    @Tag(TIME_CONSUMING)
    void loadFromSignedStateTest() {
        final ChatterSubSetting chatterSettings = new ChatterSubSetting();
        chatterSettings.futureGenerationLimit = 100;

        final Random random = RandomUtils.getRandomPrintSeed();
        final ChatterCore<GossipEvent> chatterCore = new ChatterCore<>(
                TimeFactory.getOsTime(),
                GossipEvent.class,
                (m) -> {},
                chatterSettings,
                (id, l) -> {},
                new NoOpMetrics());

        chatterCore.newPeerInstance(0L, e -> {});
        chatterCore.newPeerInstance(1L, e -> {});

        final GossipEventBuilder builder = GossipEventBuilder.builder().setRandom(random);

        long minGen = 100;
        final long windowSize = 100;
        long maxGen = minGen + windowSize;

        // Set the initial window of acceptable generations
        shiftWindow(chatterCore, minGen);

        // Generate events below, in, and above the window
        List<GossipEvent> eventsInWindow = generateEventsInWindow(builder, minGen, maxGen);
        List<GossipEvent> eventsBelowWindow = generateEventsBelow(builder, minGen);
        List<GossipEvent> eventsAboveWindow = generateEventsAbove(builder, maxGen);

        // Send the events to the peers
        setPeersKnow(chatterCore, eventsInWindow);
        setPeersKnow(chatterCore, eventsBelowWindow);
        setPeersKnow(chatterCore, eventsAboveWindow);

        // Verify only the events in the window are known
        assertPeersKnow(chatterCore, eventsInWindow);
        assertPeersDontKnow(chatterCore, eventsBelowWindow);
        assertPeersDontKnow(chatterCore, eventsAboveWindow);

        minGen = 1_000;
        maxGen = minGen + windowSize;

        // Load a saved state that has no generation overlap
        loadSavedState(chatterCore, minGen);

        // Assert the peer instance known events are cleared
        assertPeersDontKnow(chatterCore, eventsInWindow);

        // Generate events below, in, and above the new window
        eventsInWindow = generateEventsInWindow(builder, minGen, maxGen);
        eventsBelowWindow = generateEventsBelow(builder, minGen);
        eventsAboveWindow = generateEventsAbove(builder, maxGen);

        // Send the new events to the peers
        setPeersKnow(chatterCore, eventsInWindow);
        setPeersKnow(chatterCore, eventsBelowWindow);
        setPeersKnow(chatterCore, eventsAboveWindow);

        // Verify only the events in the new window are known
        assertPeersKnow(chatterCore, eventsInWindow);
        assertPeersDontKnow(chatterCore, eventsBelowWindow);
        assertPeersDontKnow(chatterCore, eventsAboveWindow);
    }

    private void loadSavedState(final ChatterCore<GossipEvent> chatterCore, final long stateMinGen) {
        final SignedState signedState = mock(SignedState.class);
        when(signedState.getMinRoundGeneration()).thenReturn(stateMinGen);
        chatterCore.loadFromSignedState(signedState);
    }

    private List<GossipEvent> generateEventsBelow(final GossipEventBuilder builder, final long lowerBound) {
        final List<GossipEvent> events = new LinkedList<>();
        events.add(builder.setGeneration(lowerBound - 1).buildEvent());
        events.add(builder.setGeneration(lowerBound - 2).buildEvent());
        return events;
    }

    private List<GossipEvent> generateEventsAbove(final GossipEventBuilder builder, final long upperBound) {
        final List<GossipEvent> events = new LinkedList<>();
        events.add(builder.setGeneration(upperBound).buildEvent());
        events.add(builder.setGeneration(upperBound + 1).buildEvent());
        return events;
    }

    private List<GossipEvent> generateEventsInWindow(
            final GossipEventBuilder builder, final long lowerBound, final long upperBound) {
        final List<GossipEvent> events = new LinkedList<>();
        events.add(builder.setGeneration(lowerBound).buildEvent());
        events.add(builder.setGeneration((upperBound + lowerBound) / 2).buildEvent());
        events.add(builder.setGeneration(upperBound - 1).buildEvent());
        return events;
    }

    private void shiftWindow(final ChatterCore<GossipEvent> chatterCore, final long newLowestGeneration) {
        chatterCore.getPeerInstances().forEach(p -> p.state().shiftWindow(newLowestGeneration));
    }

    private void setPeersKnow(final ChatterCore<GossipEvent> chatterCore, final List<GossipEvent> events) {
        events.forEach(e -> setPeersKnow(chatterCore, e));
    }

    private void setPeersKnow(final ChatterCore<GossipEvent> chatterCore, final GossipEvent event) {
        chatterCore.getPeerInstances().forEach(p -> p.state().setPeerKnows(event.getDescriptor()));
    }

    private void assertPeersKnow(final ChatterCore<GossipEvent> chatterCore, final List<GossipEvent> events) {
        events.forEach(e -> assertPeersKnow(chatterCore, e));
    }

    private void assertPeersKnow(final ChatterCore<GossipEvent> chatterCore, final GossipEvent event) {
        chatterCore
                .getPeerInstances()
                .forEach(p -> assertTrue(
                        p.state().getPeerKnows(event.getDescriptor()),
                        "Peer " + p + " should know event " + event.getDescriptor()));
    }

    private void assertPeersDontKnow(final ChatterCore<GossipEvent> chatterCore, final List<GossipEvent> events) {
        events.forEach(e -> assertPeersDontKnow(chatterCore, e));
    }

    private void assertPeersDontKnow(final ChatterCore<GossipEvent> chatterCore, final GossipEvent event) {
        chatterCore
                .getPeerInstances()
                .forEach(p -> assertFalse(
                        p.state().getPeerKnows(event.getDescriptor()),
                        "Peer " + p + " should NOT know event " + event.getDescriptor()));
    }
}
