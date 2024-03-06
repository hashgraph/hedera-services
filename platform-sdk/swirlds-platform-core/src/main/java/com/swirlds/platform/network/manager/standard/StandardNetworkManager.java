package com.swirlds.platform.network.manager.standard;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.network.PeerInfo;
import com.swirlds.platform.network.connectivity.TlsFactory;
import com.swirlds.platform.network.manager.NetworkConnection;
import com.swirlds.platform.network.manager.NetworkManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A network manager that operates over TLS encrypted TCP connections.
 */
public class StandardNetworkManager implements NetworkManager {

    private TlsFactory tlsFactory;

    /**
     * A map of peer ID to the peer connection for all currently connected peers.
     */
    private final Map<NodeId, NetworkConnection> currentConnections = new HashMap<>();

    public StandardNetworkManager() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void specifyPeers(@NonNull final List<PeerInfo> peers) {

        // TODO tear down old TLS factory
        // TODO tear down old server socket
        // TODO break all connections we no longer have and connections with changed configuration
        // TODO create new TLS factory
        // TODO create new server socket
        // TODO create new NetworkConnection objects

    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public NetworkConnection getConnectionWithPeer(@NonNull final NodeId peer) {
        return null; // TODO
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        // TODO
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        // TODO
    }
}
