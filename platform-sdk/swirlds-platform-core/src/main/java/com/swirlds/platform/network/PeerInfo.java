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

package com.swirlds.platform.network;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.roster.RosterUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.cert.Certificate;

/**
 * A record representing a peer's network information.  If the certificate is not null, it must be encodable as a valid
 * X509Certificate.  It is assumed the calling code has already validated the certificate.
 *
 * @param nodeId             the ID of the peer
 * @param hostname           the hostname (or IP address) of the peer
 * @param signingCertificate the certificate used to validate the peer's TLS certificate, or null.
 */
public record PeerInfo(@NonNull NodeId nodeId, @NonNull String hostname, @Nullable Certificate signingCertificate) {

    /**
     * Return a "node name" for the peer, e.g. "node1" for a peer with NodeId == 0.
     *
     * @return a "node name"
     */
    @NonNull
    public String nodeName() {
        return RosterUtils.formatNodeName(nodeId.id());
    }
}
