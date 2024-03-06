package com.swirlds.platform.network.manager;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.network.PeerInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Establishes, maintains, and manages network connections with current peers.
 */
public interface NetworkManager {

    /**
     * Specify which peers we should currently be connected to. Will attempt to initiate connections with new peers, and
     * will break connections with peers that we no longer have.
     *
     * @param peers a list of peers to connect to. The peer corresponding to this node is ignored, if present.
     */
    void specifyPeers(@NonNull final List<PeerInfo> peers);

    /**
     * Get the connection with a peer (if we are allowed to be connected to them).
     *
     * @param peer the peer to get the connection manager for
     * @return the connection manager for the peer
     * @throws java.util.NoSuchElementException if the requested node ID is not a peer we are currently permitted to be
     *                                          in connection with
     */
    @NonNull
    NetworkConnection getConnectionWithPeer(@NonNull final NodeId peer);

    /**
     * Start the connection manager. (May deprecate this in favor of the wiring framework)
     */
    void start();

    /**
     * Stop the connection manager. (May deprecate this in favor of the wiring framework)
     */
    void stop();

}
