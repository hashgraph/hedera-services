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

package com.swirlds.platform.heartbeats;

import com.swirlds.base.ArgumentUtils;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.Connection;
import com.swirlds.platform.network.ByteConstants;
import com.swirlds.platform.network.NetworkMetrics;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;
import com.swirlds.platform.network.unidirectional.UnidirectionalProtocols;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Sends a heartbeat to the other node and measures the time it takes to receive a response.
 */
public class HeartbeatProtocol implements Protocol {
    /**
     * ID of the peer
     */
    private final NodeId otherId;

    /**
     * The last time a heartbeat protocol was executed
     */
    private Instant lastHeartbeatTime = Instant.MIN;

    /**
     * The period at which the heartbeat protocol should be executed
     */
    private final Duration heartbeatPeriod;

    /**
     * Network metrics, for recording roundtrip heartbeat time
     */
    private final NetworkMetrics networkMetrics;

    /**
     * Constructor
     *
     * @param otherId         ID of the peer
     * @param heartbeatPeriod The period (milliseconds) at which this protocol should execute
     * @param networkMetrics  Network metrics, for recording roundtrip heartbeat time
     */
    public HeartbeatProtocol(
            final @NonNull NodeId otherId,
            final @NonNull Duration heartbeatPeriod,
            final @NonNull NetworkMetrics networkMetrics) {

        this.otherId = ArgumentUtils.throwArgNull(otherId, "otherId");
        this.heartbeatPeriod = ArgumentUtils.throwArgNull(heartbeatPeriod, "heartbeatPeriod");
        this.networkMetrics = ArgumentUtils.throwArgNull(networkMetrics, "networkMetrics");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns true if the last heartbeat protocol was started more than {@link #heartbeatPeriod} ago
     */
    @Override
    public boolean shouldInitiate() {
        return Duration.between(lastHeartbeatTime, Instant.now()).compareTo(heartbeatPeriod) >= 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldAccept() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean acceptOnSimultaneousInitiate() {
        return true;
    }

    /**
     * Reads a byte from the socket
     *
     * @param connection the connection to read from
     * @param startTime  the start time measurement for determining roundtrip time
     * @throws IOException              if there is an error reading from the socket
     * @throws NetworkProtocolException if the byte read is not a heartbeat or heartbeat ack
     */
    private void readByte(final @NonNull Connection connection, final long startTime)
            throws IOException, NetworkProtocolException {

        final byte readByte = connection.getDis().readByte();

        if (readByte == ByteConstants.HEARTBEAT) {
            // if we received their heartbeat, immediately send an ack
            connection.getDos().write(ByteConstants.HEARTBEAT_ACK);
            connection.getDos().flush();
        } else if (readByte == ByteConstants.HEARTBEAT_ACK) {
            // the time from the measurement start to now represents the total roundtrip
            networkMetrics.recordPingTime(otherId, System.nanoTime() - startTime);
        } else {
            throw new NetworkProtocolException(String.format(
                    "received %02x but expected %02x or %02x (HEARTBEAT or HEARTBEAT_ACK)",
                    readByte, ByteConstants.HEARTBEAT, ByteConstants.HEARTBEAT_ACK));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void runProtocol(final @NonNull Connection connection)
            throws NetworkProtocolException, IOException, InterruptedException {

        // record the time prior to executing the protocol, so that heartbeatPeriod represents a true period, as
        // opposed to a sleep time
        lastHeartbeatTime = Instant.now();

        connection.getDos().write(UnidirectionalProtocols.HEARTBEAT.getInitialByte());
        connection.getDos().flush();

        // start time measurement after flushing the output stream, so that write time isn't included in the measurement
        final long startTime = System.nanoTime();

        // 2 bytes must be read from the socket: their initiation, and their ack
        readByte(connection, startTime);
        readByte(connection, startTime);
    }
}
