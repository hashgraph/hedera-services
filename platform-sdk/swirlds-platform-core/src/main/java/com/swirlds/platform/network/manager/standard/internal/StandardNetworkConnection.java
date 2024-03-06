package com.swirlds.platform.network.manager.standard.internal;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.network.manager.NetworkConnection;
import com.swirlds.platform.network.manager.NetworkSession;
import edu.umd.cs.findbugs.annotations.NonNull;

public class StandardNetworkConnection implements NetworkConnection {
    @NonNull
    @Override
    public NodeId getPeerId() {
        return null;
    }

    @NonNull
    @Override
    public NetworkSession getSession() throws InterruptedException {
        return null;
    }
}
