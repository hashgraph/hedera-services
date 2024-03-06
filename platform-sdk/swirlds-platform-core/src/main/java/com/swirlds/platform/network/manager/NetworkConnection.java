package com.swirlds.platform.network.manager;

import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A connection with a peer. A network connection has a lifespan over the entire time we wish to be connected with a
 * peer. If sockets are broken, this object persists and will attempt to re-establish that connection.
 */
public interface NetworkConnection {

    /**
     * Get the ID of the peer that this connection is with.
     *
     * @return the peer ID
     */
    @NonNull
    NodeId getPeerId();

    /**
     * Get a network session with a peer. If a session is not immediately available, this method will block.
     *
     * @return the network session
     */
    @NonNull
    NetworkSession getSession() throws InterruptedException;

}
