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

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.platform.chatter.protocol.messages.ChatterEvent;
import com.swirlds.platform.test.chatter.simulator.SimulatedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

public class NetworkTestChatter implements SimulatedChatter {
    private final long selfId;
    private final int generateEveryMs;
    private final int payloadSizeInBytes;
    private Instant lastPayload = null;

    public NetworkTestChatter(final long selfId, final int bytesPerSecond, final int generateEveryMs) {
        this.selfId = selfId;
        this.generateEveryMs = generateEveryMs;
        System.out.println("bytesPerSecond " + bytesPerSecond);
        final int payloadsPerSec = 1000 / generateEveryMs;
        System.out.println("payloadsPerSec " + payloadsPerSec);
        payloadSizeInBytes = bytesPerSecond / payloadsPerSec;
        System.out.println("payloadSizeInBytes " + payloadSizeInBytes);
    }

    public static SimulatedChatterFactory factory(final int bytesPerSecond) {
        return (id, ids, e, n) -> new NetworkTestChatter(id, bytesPerSecond, 1);
    }

    @Override
    public void newEvent(final ChatterEvent event) {}

    @Override
    public void shiftWindow(final long firstSequenceNumberInWindow) {}

    @Override
    public void handlePayload(final SelfSerializable payload, final long sender) {}

    @Override
    public GossipPayload generatePayload(
            final Instant now, final boolean underutilizedNetwork, final long destination) {
        if (lastPayload == null || Duration.between(lastPayload, now).toMillis() >= generateEveryMs) {
            if (lastPayload != null && Duration.between(lastPayload, now).toMillis() > 1) {
                System.out.println(lastPayload + " to " + now);
            }
            lastPayload = now;
            return new GossipPayload(
                    new SimulatedEvent(new Random(), selfId, destination, payloadSizeInBytes), destination);
        }
        return null;
    }
}
