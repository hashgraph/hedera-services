/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.logging.payloads;

import java.util.LinkedList;
import java.util.List;

/**
 * Provides information about peers we tried to reconnect with
 */
public class ReconnectPeerInfoPayload extends AbstractLogPayload {
    /** A list of peers we tried to reconnect with */
    private final List<PeerInfo> peerInfo;

    public ReconnectPeerInfoPayload() {
        super("");
        peerInfo = new LinkedList<>();
    }

    public void addPeerInfo(final long peerId, final String info) {
        peerInfo.add(new PeerInfo(peerId, info));
    }

    public List<PeerInfo> getPeerInfo() {
        return peerInfo;
    }

    public static class PeerInfo {
        private final long node;
        private final String message;

        public PeerInfo(long node, String message) {
            this.node = node;
            this.message = message;
        }

        public long getNode() {
            return node;
        }

        public String getMessage() {
            return message;
        }
    }
}
