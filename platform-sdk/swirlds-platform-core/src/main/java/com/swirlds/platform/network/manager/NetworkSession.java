package com.swirlds.platform.network.manager;

import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * A connection instance with a peer.
 */
public interface NetworkSession {

    /**
     * Get the ID of the peer that this connection is with.
     *
     * @return the peer ID
     */
    @NonNull
    NodeId getPeerId();

    /**
     * Get a stream for sending data to a peer.
     *
     * @return the output stream
     */
    @NonNull
    DataInputStream dataToPeer();

    /**
     * Get a stream for receiving data from a peer.
     *
     * @return the input stream
     */
    DataOutputStream dataFromPeer();

    /**
     * Get the number of bytes that have been sent to the peer over this connection since
     * {@link #resetByteCount()} was last called.
     */
    long getSentByteCount();

    /**
     * Get the number of bytes that have been received from the peer over this connection since
     * {@link #resetByteCount()} was last called.
     */
    long getReceivedByteCount();

    /**
     * Reset the byte count for this connection.
     */
    void resetByteCount();

    /**
     * Destructively close the session. If this object is backed by a network connection, the connection will be
     * closed.
     */
    void close();

    // TODO options for changing socket timeout on the fly

}
