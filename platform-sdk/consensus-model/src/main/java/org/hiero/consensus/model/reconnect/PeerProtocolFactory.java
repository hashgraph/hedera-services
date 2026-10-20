package org.hiero.consensus.model.reconnect;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.hiero.consensus.model.node.NodeId;

/**
 * API for building network protocols
 */
public interface PeerProtocolFactory {

    /**
     * Constructs an instance of a network protocol using the provided peerId
     * @return a network protocol for connectivity over the bidirectional network
     */
    PeerProtocol createPeerInstance(@NonNull final NodeId peerId);
}
