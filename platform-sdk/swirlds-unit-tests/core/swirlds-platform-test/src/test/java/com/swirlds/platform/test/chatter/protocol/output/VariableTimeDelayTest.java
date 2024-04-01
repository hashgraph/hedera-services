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

package com.swirlds.platform.test.chatter.protocol.output;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.chatter.protocol.output.SendAction;
import com.swirlds.platform.gossip.chatter.protocol.output.VariableTimeDelay;
import com.swirlds.platform.gossip.chatter.protocol.peer.PeerGossipState;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class VariableTimeDelayTest {
    private final PeerGossipState state = new PeerGossipState(1_000);
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());

    @Test
    void testPeerKnows() {
        final Random random = RandomUtils.getRandomPrintSeed();

        final GossipEvent event = TestingEventBuilder.builder(random).build();
        state.setPeerKnows(event.getDescriptor());
        final VariableTimeDelay<GossipEvent> eventTimeDelay =
                new VariableTimeDelay<>(() -> Duration.ofMillis(100), state, now::get);
        assertEquals(SendAction.DISCARD, eventTimeDelay.shouldSend(event));
    }

    @Test
    void testVariableDelay() {
        final Random random = RandomUtils.getRandomPrintSeed();

        final GossipEvent event = TestingEventBuilder.builder(random).build();

        final AtomicInteger numCalls = new AtomicInteger(0);
        final VariableTimeDelay<GossipEvent> eventTimeDelay = new VariableTimeDelay<>(
                () -> {
                    numCalls.incrementAndGet();
                    if (numCalls.get() == 1) {
                        return Duration.ofMillis(50);
                    } else if (numCalls.get() == 2) {
                        return Duration.ofMillis(100);
                    } else {
                        return Duration.ofMillis(150);
                    }
                },
                state,
                now::get);

        now.set(event.getTimeReceived().plus(Duration.ofMillis(40)));
        assertEquals(SendAction.WAIT, eventTimeDelay.shouldSend(event), "Event should not be sent yet");

        now.set(event.getTimeReceived().plus(Duration.ofMillis(90)));
        assertEquals(SendAction.WAIT, eventTimeDelay.shouldSend(event), "Event should still not be sent yet");

        now.set(event.getTimeReceived().plus(Duration.ofMillis(140)));
        assertEquals(SendAction.WAIT, eventTimeDelay.shouldSend(event), "Event should still not be sent yet");

        now.set(event.getTimeReceived().plus(Duration.ofMillis(160)));
        assertEquals(SendAction.SEND, eventTimeDelay.shouldSend(event), "Event should be sent yet now");
    }
}
