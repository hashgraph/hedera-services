package com.swirlds.platform.network;

import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.cert.Certificate;

/**
 * A record representing a peer's network information.
 *
 * @param nodeId             the ID of the peer
 * @param nodeName           the name of the peer
 * @param hostname           the hostname (or IP address) of the peer
 * @param signingCertificate the certificate used to validate the peer's TLS certificate
 */
public record PeerInfo(
        @NonNull NodeId nodeId,
        @NonNull String nodeName,
        @NonNull String hostname,
        @NonNull Certificate signingCertificate) {
}
