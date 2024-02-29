package com.swirlds.platform.network;

import com.swirlds.common.platform.NodeId;
import java.security.cert.Certificate;

public record PeerInfo(NodeId nodeId, String nodeName, String hostname, Certificate signingCertificate) {
}
