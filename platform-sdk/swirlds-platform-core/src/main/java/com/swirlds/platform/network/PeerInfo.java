// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.network;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.roster.RosterUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.cert.Certificate;

/**
 * A record representing a peer's network information.  If the certificate is not null, it must be encodable as a valid
 * X509Certificate.  It is assumed the calling code has already validated the certificate.
 *
 * @param nodeId             the ID of the peer
 * @param hostname           the hostname (or IP address) of the peer
 * @param signingCertificate the certificate used to validate the peer's TLS certificate, or null.
 */
public record PeerInfo(@NonNull NodeId nodeId, @NonNull String hostname, @NonNull Certificate signingCertificate) {

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
