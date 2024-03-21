/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.network.communication.handshake;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.platform.Utilities;
import com.swirlds.platform.network.PeerInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Listens for a handshake completion event
 * */
public class HandshakeCompletedListenerImpl implements HandshakeCompletedListener {

    private final List<PeerInfo> peerInfoList;
    private static final Logger logger = LogManager.getLogger(HandshakeCompletedListenerImpl.class);

    public HandshakeCompletedListenerImpl(@NonNull final List<PeerInfo> peerInfoList) {
        this.peerInfoList = Objects.requireNonNull(peerInfoList);
    }

    @Override
    public void handshakeCompleted(final HandshakeCompletedEvent event) {
        final PeerInfo peer = Utilities.validateTLSPeer(event.getSocket(), peerInfoList);
        if (peer == null) {
            try {
                event.getSocket().close();
            } catch (final IOException e) {
                logger.warn(
                        EXCEPTION.getMarker(),
                        "Attempt to close connection from {}:{} threw IO exception {}",
                        event.getSocket().getInetAddress(),
                        event.getSocket().getPort(),
                        e.getMessage());
            }
        }
    }
}
