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

package com.swirlds.platform.heartbeat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.gossip.sync.SyncInputStream;
import com.swirlds.platform.gossip.sync.SyncOutputStream;
import com.swirlds.platform.heartbeats.HeartbeatProtocol;
import com.swirlds.platform.network.ByteConstants;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.NetworkMetrics;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.HeartbeatProtocolFactory;
import com.swirlds.platform.network.protocol.Protocol;
import com.swirlds.platform.network.protocol.ProtocolFactory;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Tests for {@link HeartbeatProtocol}
 */
@DisplayName("Heartbeat Protocol Tests")
class HeartbeatProtocolTests {
    private NodeId peerId;
    private Duration heartbeatPeriod;
    private NetworkMetrics networkMetrics;
    private FakeTime time;
    private Connection heartbeatSendingConnection;
    private final int ackDelayMillis = 33;

    @BeforeEach
    void setup() {
        peerId = NodeId.of(1);
        heartbeatPeriod = Duration.ofMillis(1000);
        networkMetrics = mock(NetworkMetrics.class);
        time = new FakeTime();

        final SyncInputStream inputStream = mock(SyncInputStream.class);
        try {
            Mockito.when(inputStream.readByte())
                    .thenReturn(ByteConstants.HEARTBEAT)
                    .then(invocation -> {
                        // let time pass before sending the ACK
                        time.tick(Duration.ofMillis(ackDelayMillis));
                        return ByteConstants.HEARTBEAT_ACK;
                    });
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        heartbeatSendingConnection = mock(Connection.class);
        Mockito.when(heartbeatSendingConnection.getDis()).thenReturn(inputStream);
        Mockito.when(heartbeatSendingConnection.getDos()).thenReturn(mock(SyncOutputStream.class));
    }

    @Test
    @DisplayName("Protocol runs successfully")
    void successfulRun() {
        final ProtocolFactory heartbeatProtocolFactory =
                new HeartbeatProtocolFactory(heartbeatPeriod, networkMetrics, time);

        assertDoesNotThrow(() -> heartbeatProtocolFactory.build(peerId).runProtocol(heartbeatSendingConnection));

        // recorded roundtrip time should be the length of time the peer took to send an ACK
        Mockito.verify(networkMetrics)
                .recordPingTime(peerId, Duration.ofMillis(ackDelayMillis).toNanos());
    }

    @Test
    @DisplayName("shouldInitiate respects the heartbeat period")
    void shouldInitiate() {
        final ProtocolFactory heartbeatProtocolFactory =
                new HeartbeatProtocolFactory(heartbeatPeriod, networkMetrics, time);
        final Protocol heartbeatProtocol = heartbeatProtocolFactory.build(peerId);
        // first shouldInitiate is always true, since we haven't sent a heartbeat to start the timer yet
        assertTrue(heartbeatProtocol.shouldInitiate());

        // run the protocol to start the heartbeat timer
        assertDoesNotThrow(() -> heartbeatProtocol.runProtocol(heartbeatSendingConnection));

        assertFalse(heartbeatProtocol.shouldInitiate());

        // tick part way through the heartbeat period
        time.tick(Duration.ofMillis(555));

        assertFalse(heartbeatProtocol.shouldInitiate());

        // tick through the rest of the period
        time.tick(Duration.ofMillis(555));

        assertTrue(heartbeatProtocol.shouldInitiate());
    }

    @Test
    @DisplayName("shouldAccept always returns true")
    void shouldAccept() {
        final ProtocolFactory heartbeatProtocolFactory =
                new HeartbeatProtocolFactory(heartbeatPeriod, networkMetrics, time);
        final Protocol heartbeatProtocol = heartbeatProtocolFactory.build(peerId);

        assertTrue(heartbeatProtocol.shouldAccept());

        // additional calls to shouldAccept should always return true, without any cooldown being required
        assertTrue(heartbeatProtocol.shouldAccept());
    }

    @Test
    @DisplayName("Exception is thrown if the peer doesn't send a heartbeat byte")
    void peerSendsInvalidHeartbeat() {
        final ProtocolFactory heartbeatProtocolFactory =
                new HeartbeatProtocolFactory(heartbeatPeriod, networkMetrics, time);
        final Protocol heartbeatProtocol = heartbeatProtocolFactory.build(peerId);

        // reconfigure the heartbeatSendingConnection so that it sends an invalid byte at the beginning of the protocol
        final SyncInputStream badInputStream = mock(SyncInputStream.class);
        try {
            Mockito.when(badInputStream.readByte())
                    .thenReturn((byte) 0x22) // this should be a heartbeat byte
                    .thenReturn(ByteConstants.HEARTBEAT_ACK);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        Mockito.when(heartbeatSendingConnection.getDis()).thenReturn(badInputStream);

        assertThrows(NetworkProtocolException.class, () -> heartbeatProtocol.runProtocol(heartbeatSendingConnection));
    }

    @Test
    @DisplayName("Exception is thrown if the peer sends an invalid ack")
    void peerSendsInvalidAcknowledgement() {
        final ProtocolFactory heartbeatProtocolFactory =
                new HeartbeatProtocolFactory(heartbeatPeriod, networkMetrics, time);
        final Protocol heartbeatProtocol = heartbeatProtocolFactory.build(peerId);

        // reconfigure the heartbeatSendingConnection so that it sends an invalid byte instead of an ack
        final SyncInputStream badInputStream = mock(SyncInputStream.class);
        try {
            Mockito.when(badInputStream.readByte())
                    .thenReturn(ByteConstants.HEARTBEAT)
                    .thenReturn((byte) 0x22); // this should be a heartbeat ack byte
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        Mockito.when(heartbeatSendingConnection.getDis()).thenReturn(badInputStream);

        assertThrows(NetworkProtocolException.class, () -> heartbeatProtocol.runProtocol(heartbeatSendingConnection));
    }

    @Test
    @DisplayName("acceptOnSimultaneousInitiate should return true")
    void acceptOnSimultaneousInitiate() {
        final ProtocolFactory heartbeatProtocolFactory =
                new HeartbeatProtocolFactory(heartbeatPeriod, networkMetrics, time);
        final Protocol heartbeatProtocol = heartbeatProtocolFactory.build(peerId);

        assertTrue(heartbeatProtocol.acceptOnSimultaneousInitiate());
    }
}
