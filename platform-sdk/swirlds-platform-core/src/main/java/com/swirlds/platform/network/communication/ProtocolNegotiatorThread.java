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

package com.swirlds.platform.network.communication;

import com.swirlds.base.time.Time;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.utility.throttle.RateLimiter;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.ConnectionManager;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.network.protocol.ProtocolRunnable;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Continuously runs protocol negotiation and protocols over connections supplied by the connection manager
 */
public class ProtocolNegotiatorThread implements InterruptableRunnable {
    /** A duration between reporting full stack traces for socket exceptions. */
    private static final Duration SOCKET_EXCEPTION_DURATION = Duration.ofMinutes(1);

    /**
     * The number of milliseconds to sleep if a negotiation fails
     */
    private final int sleepMillis;

    private final ConnectionManager connectionManager;
    private final List<ProtocolRunnable> handshakeProtocols;
    private final NegotiationProtocols protocols;
    private final RateLimiter socketExceptionRateLimiter;

    /**
     * @param connectionManager
     * 		supplies network connections
     * @param sleepMillis
     *         the number of milliseconds to sleep if a negotiation fails
     * @param handshakeProtocols
     * 		the list of protocols to execute when a new connection is established
     * @param protocols
     * 		the protocols to negotiate and run
     * @param time
     *      the Time object
     */
    public ProtocolNegotiatorThread(
            final ConnectionManager connectionManager,
            final int sleepMillis,
            final List<ProtocolRunnable> handshakeProtocols,
            final NegotiationProtocols protocols,
            final Time time) {

        this.connectionManager = connectionManager;
        this.sleepMillis = sleepMillis;
        this.handshakeProtocols = handshakeProtocols;
        this.protocols = protocols;
        this.socketExceptionRateLimiter = new RateLimiter(time, SOCKET_EXCEPTION_DURATION);
    }

    @Override
    public void run() throws InterruptedException {
        final Connection currentConn = connectionManager.waitForConnection();
        final Negotiator negotiator = new Negotiator(protocols, currentConn, sleepMillis);
        try {
            // run the handshake protocols on every new connection
            for (final ProtocolRunnable handshakeProtocol : handshakeProtocols) {
                handshakeProtocol.runProtocol(currentConn);
            }

            while (currentConn.connected()) {
                negotiator.execute();
            }
        } catch (final RuntimeException | IOException | NetworkProtocolException | NegotiationException e) {
            NetworkUtils.handleNetworkException(e, currentConn, socketExceptionRateLimiter);
        }
    }
}
