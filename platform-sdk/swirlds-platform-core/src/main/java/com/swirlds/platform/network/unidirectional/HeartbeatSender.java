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

package com.swirlds.platform.network.unidirectional;

import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.SocketConfig;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.threading.locks.locked.LockedResource;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.network.ByteConstants;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.ConnectionManager;
import com.swirlds.platform.network.NetworkMetrics;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.NetworkUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;

/**
 * Periodically send a heartbeat through the connection to keep it alive and measure response time
 */
public class HeartbeatSender implements InterruptableRunnable {
    // ID number for member to send heartbeats to
    private final NodeId otherId;
    private final SharedConnectionLocks sharedConnectionLocks;
    private final NetworkMetrics stats;
    private final Configuration configuration;

    public HeartbeatSender(
            @NonNull final NodeId otherId,
            @NonNull final SharedConnectionLocks sharedConnectionLocks,
            @NonNull final NetworkMetrics stats,
            @NonNull final Configuration configuration) {
        this.otherId = Objects.requireNonNull(otherId);
        this.sharedConnectionLocks = Objects.requireNonNull(sharedConnectionLocks);
        this.stats = Objects.requireNonNull(stats);
        this.configuration = Objects.requireNonNull(configuration);
    }

    /**
     * if connected, send a heartbeat, wait for a response, then sleep
     */
    @Override
    public void run() throws InterruptedException {
        Connection conn = null;
        try (LockedResource<ConnectionManager> lc = sharedConnectionLocks.lockConnectIfNeeded(otherId)) {
            conn = lc.getResource().waitForConnection();
            if (conn != null && conn.connected()) {
                doHeartbeat(conn);
            }
        } catch (final RuntimeException | IOException | NetworkProtocolException e) {
            NetworkUtils.handleNetworkException(e, conn);
        }
        final BasicConfig basicConfig = configuration.getConfigData(BasicConfig.class);
        Thread.sleep(basicConfig.sleepHeartbeat()); // Slow down heartbeats to match the configured interval
    }

    /**
     * Send out a heartbeat and wait to read the ACK response. The connection conn must be non-null, and
     * must have a valid connection. If the wait is too long, it will time out and disconnect.
     *
     * @param conn
     * 		the connection (which must already be connected)
     */
    void doHeartbeat(@NonNull final Connection conn) throws IOException, NetworkProtocolException {
        Objects.requireNonNull(conn);

        final long startTime = System.nanoTime();
        final SocketConfig socketConfig = configuration.getConfigData(SocketConfig.class);

        conn.getDos().write(UnidirectionalProtocols.HEARTBEAT.getInitialByte());
        conn.getDos().flush();
        conn.setTimeout(socketConfig.timeoutSyncClientSocket());
        final byte b = conn.getDis().readByte();
        if (b != ByteConstants.HEARTBEAT_ACK) {
            throw new NetworkProtocolException(
                    String.format("received %02x but expected %02x (heartbeatACK)", b, ByteConstants.HEARTBEAT_ACK));
        }
        stats.recordPingTime(otherId, System.nanoTime() - startTime);
    }
}
