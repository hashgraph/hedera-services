/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.swirlds.base.time.Time;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig_;
import com.swirlds.platform.gossip.chatter.protocol.ChatterCore;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ChatterCore}
 */
@Disabled
public class ChatterCoreTests {

    /**
     * Verifies that when a signed state is loaded from, all peer instance windows are shifted to the new generation
     * window.
     */
    @Test
    void loadFromSignedStateTest() {
        final Configuration configuration = new TestConfigBuilder()
                .withValue(ChatterConfig_.FUTURE_GENERATION_LIMIT, "100")
                .getOrCreateConfig();
        final ChatterConfig chatterConfig = configuration.getConfigData(ChatterConfig.class);

        final Random random = RandomUtils.getRandomPrintSeed();
        final ChatterCore<GossipEvent> chatterCore = new ChatterCore<>(
                Time.getCurrent(), GossipEvent.class, (m) -> {}, chatterConfig, (id, l) -> {}, new NoOpMetrics());

        chatterCore.newPeerInstance(new NodeId(0L), e -> {});
        chatterCore.newPeerInstance(new NodeId(1L), e -> {});

        final TestingEventBuilder builder = TestingEventBuilder.builder().setRandom(random);

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
        chatterCore.loadFromSignedState(signedState);
    }

    private List<GossipEvent> generateEventsBelow(final TestingEventBuilder builder, final long lowerBound) {
        final List<GossipEvent> events = new LinkedList<>();
        events.add(builder.setGeneration(lowerBound - 1).buildEvent());
        events.add(builder.setGeneration(lowerBound - 2).buildEvent());
        return events;
    }

    private List<GossipEvent> generateEventsAbove(final TestingEventBuilder builder, final long upperBound) {
        final List<GossipEvent> events = new LinkedList<>();
        events.add(builder.setGeneration(upperBound).buildEvent());
        events.add(builder.setGeneration(upperBound + 1).buildEvent());
        return events;
    }

    private List<GossipEvent> generateEventsInWindow(
            final TestingEventBuilder builder, final long lowerBound, final long upperBound) {
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
