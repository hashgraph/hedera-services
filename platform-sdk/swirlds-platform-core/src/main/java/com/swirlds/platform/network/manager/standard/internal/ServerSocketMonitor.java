package com.swirlds.platform.network.manager.standard.internal;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.network.PeerInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.ServerSocket;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * This class is responsible for building, rebuilding, maintaining, and monitoring a node's server socket.
 */
public class ServerSocketMonitor {

    private final PlatformContext platformContext;
    private final Certificate certificate;
    private final PrivateKey privateKey;

    private ServerSocket serverSocket;

    /**
     * Construct a new server socket monitor.
     *
     * @param platformContext the platform context
     * @param selfId          the ID of the node that this server socket is for
     * @param certificate     the TLS certificate to use
     * @param privateKey      the private key corresponding to the public key in the certificate
     */
    public ServerSocketMonitor(
            @NonNull final PlatformContext platformContext,
            @NonNull final NodeId selfId,
            @NonNull final Certificate certificate,
            @NonNull final PrivateKey privateKey) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.certificate = Objects.requireNonNull(certificate);
        this.privateKey = Objects.requireNonNull(privateKey);
    }

    /**
     * Specify which peers we should be connected to. Will cause the server socket to be rebuilt.
     *
     * @param peers a list of peers to connect to. The peer corresponding to this node is ignored, if present.
     */
    public void specifyPeers(@NonNull final List<PeerInfo> peers) {
        // TODO rebuild the server socket
    }

    /**
     * Called periodically.
     *
     * @param now the current time
     * @return a list of new network sessions that have been established
     */
    @Nullable
    public List<StandardNetworkSession> heartbeat(@NonNull final Instant now) {
        if (serverSocket == null) {
            // Sever socket has not yet been built, take no action.
            return null;
        }

        // TODO

        return null;
    }

    /**
     * Request that we call a peer. The connection to the peer will be established asynchronously and returned via the
     * heartbeat method.
     *
     * @param peer the peer to call
     */
    public void callPeer(@NonNull final NodeId peer) {
        // TODO
    }

}
