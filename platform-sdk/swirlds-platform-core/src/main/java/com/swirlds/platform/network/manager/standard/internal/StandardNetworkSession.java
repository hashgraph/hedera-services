package com.swirlds.platform.network.manager.standard.internal;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.network.manager.NetworkSession;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class StandardNetworkSession implements NetworkSession {
    @NonNull
    @Override
    public NodeId getPeerId() {
        return null;
    }

    @NonNull
    @Override
    public DataInputStream dataToPeer() {
        return null;
    }

    @Override
    public DataOutputStream dataFromPeer() {
        return null;
    }

    @Override
    public long getSentByteCount() {
        return 0;
    }

    @Override
    public long getReceivedByteCount() {
        return 0;
    }

    @Override
    public void resetByteCount() {

    }

    @Override
    public void close() {

    }
}
