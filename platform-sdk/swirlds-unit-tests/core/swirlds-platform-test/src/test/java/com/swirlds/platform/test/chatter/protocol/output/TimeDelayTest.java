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

import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.chatter.protocol.output.SendAction;
import com.swirlds.platform.gossip.chatter.protocol.output.TimeDelay;
import com.swirlds.platform.gossip.chatter.protocol.peer.PeerGossipState;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TimeDelayTest {
    private final Duration delayTime = Duration.ofMillis(100);
    private final PeerGossipState state = new PeerGossipState(100_000);
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());
    private final TimeDelay<GossipEvent> eventTimeDelay = new TimeDelay<>(delayTime, state, now::get);

    @Test
    void testPeerKnows() {
        final Random random = RandomUtils.getRandomPrintSeed();

        final GossipEvent event = TestingEventBuilder.builder(random).build();
        state.setPeerKnows(event.getDescriptor());
        Assertions.assertEquals(SendAction.DISCARD, eventTimeDelay.shouldSend(event));
    }

    @Test
    void testDelay() {
        final Random random = RandomUtils.getRandomPrintSeed();

        final GossipEvent event = TestingEventBuilder.builder(random).build();
        now.set(event.getTimeReceived().plus(Duration.ofMillis(50)));
        Assertions.assertEquals(SendAction.WAIT, eventTimeDelay.shouldSend(event));
    }

    @Test
    void testSend() {
        final Random random = RandomUtils.getRandomPrintSeed();

        final GossipEvent event = TestingEventBuilder.builder(random).build();
        now.set(event.getTimeReceived().plus(Duration.ofMillis(200)));
        Assertions.assertEquals(SendAction.SEND, eventTimeDelay.shouldSend(event));
    }
}
