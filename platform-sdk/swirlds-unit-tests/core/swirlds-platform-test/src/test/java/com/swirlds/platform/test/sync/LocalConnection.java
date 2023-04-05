/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.test.sync;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.Connection;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An implementation of {@link Connection} that is local to a machine and does not use sockets.
 */
public class LocalConnection implements Connection {
    private final SyncInputStream dis;
    private final SyncOutputStream dos;
    private final NodeId selfId;
    private final NodeId otherId;
    private final boolean outbound;
    private boolean connected = true;

    public LocalConnection(
            final NodeId selfId,
            final NodeId otherId,
            final InputStream in,
            final OutputStream out,
            final int bufferSize,
            final boolean outbound) {
        this.selfId = selfId;
        this.otherId = otherId;
        dis = SyncInputStream.createSyncInputStream(in, bufferSize);
        dos = SyncOutputStream.createSyncOutputStream(out, bufferSize);
        this.outbound = outbound;
    }

    @Override
    public void disconnect() {
        connected = false;
        NetworkUtils.close(dis, dos);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeId getSelfId() {
        return selfId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeId getOtherId() {
        return otherId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SyncInputStream getDis() {
        return dis;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SyncOutputStream getDos() {
        return dos;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean connected() {
        return connected;
    }

    /**
     * @return 0, since there is no timout for this connection
     */
    @Override
    public int getTimeout() {
        return 0;
    }

    /**
     * Does nothing, since there is no timout for this connection
     */
    @Override
    public void setTimeout(final int timeoutMillis) {}

    @Override
    public void initForSync() {}

    @Override
    public boolean isOutbound() {
        return outbound;
    }

    @Override
    public String getDescription() {
        return generateDescription();
    }
}
