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

package com.swirlds.platform.chatter.protocol.heartbeat;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.time.Time;
import com.swirlds.platform.chatter.protocol.MessageHandler;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Sends heartbeats, waits for a response, tracks the time between the request and the response
 */
public class HeartbeatSender implements MessageProvider, MessageHandler<HeartbeatMessage> {
    public static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(30);
    private final NodeId peerId;
    private final BiConsumer<NodeId, Long> pingConsumer;
    private final Duration heartbeatInterval;
    private final Time time;
    private final AtomicReference<HeartbeatSent> outbound = new AtomicReference<>();
    private final AtomicLong lastHeartbeatNanos = new AtomicLong();
    private final AtomicBoolean firstResponseReceived = new AtomicBoolean();

    /**
     * @param peerId
     * 		the ID of the peer we are pinging
     * @param pingConsumer
     * 		consumes the ping time the heartbeat measures. accepts the ID of the peer and the number of
     * 		nanoseconds it took for the peer to respond
     * @param heartbeatInterval
     * 		the interval at which to send heartbeats
     * @param time
     * 		provides a point in time in nanoseconds, should only be used to measure relative time (from one point to
     * 		another), not absolute time (wall clock time)
     */
    public HeartbeatSender(
            final long peerId,
            final BiConsumer<NodeId, Long> pingConsumer,
            final Duration heartbeatInterval,
            final Time time) {
        this.peerId = NodeId.createMain(peerId);
        this.pingConsumer = pingConsumer;
        this.heartbeatInterval = heartbeatInterval;
        this.time = time;
        clear();
    }

    @Override
    public SelfSerializable getMessage() {
        final long now = time.nanoTime();
        final HeartbeatSent lastHeartBeatSent = outbound.get();
        if (!lastHeartBeatSent.responded()) {
            // we are still waiting for a response
            if (now - lastHeartBeatSent.time() > HEARTBEAT_TIMEOUT.toNanos()) {
                // the request timed out, send a new one
                return nextHeartbeat(lastHeartBeatSent, now);
            }
        } else {
            // we have received the last response
            if (now - lastHeartBeatSent.time() > heartbeatInterval.toNanos()) {
                // time to send a new heartbeat
                return nextHeartbeat(lastHeartBeatSent, now);
            }
        }
        return null;
    }

    private HeartbeatMessage nextHeartbeat(final HeartbeatSent lastHeartBeatSent, final long now) {
        final long nextId = lastHeartBeatSent.id() + 1;
        outbound.set(new HeartbeatSent(nextId, now, false));
        return HeartbeatMessage.request(nextId);
    }

    @Override
    public void handleMessage(final HeartbeatMessage message) {
        final HeartbeatSent lastSent = outbound.get();
        if (lastSent.id() != message.getHeartbeatId()) {
            // this is not the last sequence we sent, we can ignore this response
            return;
        }
        // they are responding to our ping, so we capture the time difference
        final long roundTripNanos = time.nanoTime() - lastSent.time();
        lastHeartbeatNanos.set(roundTripNanos);
        firstResponseReceived.set(true);
        pingConsumer.accept(peerId, roundTripNanos);
        final HeartbeatSent respondedTrue = new HeartbeatSent(message.getHeartbeatId(), lastSent.time(), true);
        outbound.compareAndExchange(lastSent, respondedTrue);
    }

    @Override
    public void clear() {
        outbound.set(new HeartbeatSent(0, time.nanoTime(), true));
        firstResponseReceived.set(false);
        lastHeartbeatNanos.set(0);
    }

    /**
     * Calculates the last known round-trip time of a heartbeat with this peer, or the time since sending the last
     * heartbeat without a response, whichever is greater.
     *
     * @return latest round trip time in nanos, or null if no heartbeat response has been received
     */
    public Long getLastRoundTripNanos() {
        if (!firstResponseReceived.get()) {
            return null;
        }
        return Math.max(lastHeartbeatNanos.get(), timeSinceLastSentWithNoResponse());
    }

    private long timeSinceLastSentWithNoResponse() {
        final HeartbeatSent lastHeartBeatSent = outbound.get();
        if (lastHeartBeatSent.responded()) {
            // The last heartbeat was responded to, and we have not sent another yet
            return 0;
        } else {
            // we are still waiting for a response
            return time.nanoTime() - lastHeartBeatSent.time();
        }
    }
}
