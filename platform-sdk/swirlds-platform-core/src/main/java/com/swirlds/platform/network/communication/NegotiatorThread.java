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

package com.swirlds.platform.network.communication;

import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.platform.Connection;
import com.swirlds.platform.network.ConnectionManager;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.network.protocol.ProtocolRunnable;
import java.io.IOException;
import java.util.List;

/**
 * Continuously runs protocol negotiation and protocols over connections supplied by the connection manager
 */
public class NegotiatorThread implements InterruptableRunnable {
    private static final int SLEEP_MS = 100;
    private final ConnectionManager connectionManager;
    private final List<ProtocolRunnable> handshakeProtocols;
    private final NegotiationProtocols protocols;

    /**
     * @param connectionManager
     * 		supplies network connections
     * @param handshakeProtocols
     * 		the list of protocols to execute when a new connection is established
     * @param protocols
     * 		the protocols to negotiate and run
     */
    public NegotiatorThread(
            final ConnectionManager connectionManager,
            final List<ProtocolRunnable> handshakeProtocols,
            final NegotiationProtocols protocols) {
        this.connectionManager = connectionManager;
        this.handshakeProtocols = handshakeProtocols;
        this.protocols = protocols;
    }

    @Override
    public void run() throws InterruptedException {
        final Connection currentConn = connectionManager.waitForConnection();
        final Negotiator negotiator = new Negotiator(protocols, currentConn, SLEEP_MS);
        try {
            // run the handshake protocols on every new connection
            for (final ProtocolRunnable handshakeProtocol : handshakeProtocols) {
                handshakeProtocol.runProtocol(currentConn);
            }

            while (currentConn.connected()) {
                negotiator.execute();
            }
        } catch (final RuntimeException | IOException | NetworkProtocolException | NegotiationException e) {
            NetworkUtils.handleNetworkException(e, currentConn);
        }
    }
}
