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
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.gossip.chatter.protocol.messages.ChatterEvent;
import com.swirlds.platform.test.chatter.simulator.SimulatedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Random;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class NetworkTestChatter implements SimulatedChatter {
    private final NodeId selfId;
    private final int generateEveryMs;
    private final int payloadSizeInBytes;
    private Instant lastPayload = null;

    public NetworkTestChatter(@NonNull final NodeId selfId, final int bytesPerSecond, final int generateEveryMs) {
        this.selfId = Objects.requireNonNull(selfId, "selfId must not be null");
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
    public void handlePayload(@Nullable final SelfSerializable payload, @Nullable final NodeId sender) {}

    @Override
    @Nullable
    public GossipPayload generatePayload(
            @NonNull final Instant now, final boolean underutilizedNetwork, @NonNull final NodeId destination) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        if (lastPayload == null || Duration.between(lastPayload, now).toMillis() >= generateEveryMs) {
            if (lastPayload != null && Duration.between(lastPayload, now).toMillis() > 1) {
                System.out.println(lastPayload + " to " + now);
            }
            lastPayload = now;
            return new GossipPayload(
                    // The round value doesn't seem to matter, setting to -1.
                    new SimulatedEvent(new Random(), selfId, -1, payloadSizeInBytes), destination);
        }
        return null;
    }
}
