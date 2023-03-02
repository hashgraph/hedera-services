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

import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.platform.Connection;
import com.swirlds.platform.network.ConnectionManager;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.NetworkUtils;
import java.io.IOException;
import java.util.Objects;

/**
 * Listen for incoming network protocol requests then calls the handler. If it does not have a connection, or the
 * connection is closed, it will block waiting for a new connection.
 */
public class Listener implements InterruptableRunnable {
    /** handles incoming network protocol requests */
    private final NetworkProtocolResponder protocolHandler;
    /** a blocking point to wait for a connection if the current one is invalid */
    private final ConnectionManager connectionManager;

    public Listener(final NetworkProtocolResponder protocolHandler, final ConnectionManager connectionManager) {
        Objects.requireNonNull(protocolHandler);
        Objects.requireNonNull(connectionManager);
        this.protocolHandler = protocolHandler;
        this.connectionManager = connectionManager;
    }

    @Override
    public void run() throws InterruptedException {
        final Connection currentConn = connectionManager.waitForConnection();
        try {
            // wait for a request to be received, and pass it on to the handler
            final byte b = currentConn.getDis().readByte();
            protocolHandler.protocolInitiated(b, currentConn);
        } catch (final RuntimeException | IOException | NetworkProtocolException e) {
            NetworkUtils.handleNetworkException(e, currentConn);
        }
    }
}
