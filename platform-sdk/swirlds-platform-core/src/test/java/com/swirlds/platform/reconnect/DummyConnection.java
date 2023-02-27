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

package com.swirlds.platform.reconnect;

import static org.mockito.Mockito.mock;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.Connection;
import com.swirlds.platform.SocketConnection;
import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

/**
 * An implementation of {@link Connection} for local testing.
 */
public class DummyConnection extends SocketConnection {

    private final SyncInputStream dis;
    private final SyncOutputStream dos;
    private final Socket socket;

    public DummyConnection(
            final NodeId selfId,
            final NodeId otherId,
            final SerializableDataInputStream in,
            final SerializableDataOutputStream out) {
        this(
                selfId,
                otherId,
                SyncInputStream.createSyncInputStream(in, 1024 * 8),
                SyncOutputStream.createSyncOutputStream(out, 1024 * 8),
                mock(Socket.class));
    }

    public DummyConnection(final SerializableDataInputStream in, final SerializableDataOutputStream out) {
        this(
                SyncInputStream.createSyncInputStream(in, 1024 * 8),
                SyncOutputStream.createSyncOutputStream(out, 1024 * 8),
                mock(Socket.class));
    }

    public DummyConnection(
            final NodeId selfId,
            final NodeId otherId,
            final SyncInputStream syncInputStream,
            final SyncOutputStream syncOutputStream,
            final Socket socket) {
        super(selfId, otherId, mock(SwirldsPlatform.class), false, socket, syncInputStream, syncOutputStream);
        this.dis = syncInputStream;
        this.dos = syncOutputStream;
        this.socket = socket;
    }

    public DummyConnection(
            final SyncInputStream syncInputStream, final SyncOutputStream syncOutputStream, final Socket socket) {
        super(null, null, mock(SwirldsPlatform.class), false, socket, syncInputStream, syncOutputStream);
        this.dis = syncInputStream;
        this.dos = syncOutputStream;
        this.socket = socket;
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
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket getSocket() {
        return socket;
    }

    @Override
    public void setTimeout(final int timeoutMillis) throws SocketException {
        socket.setSoTimeout(timeoutMillis);
    }

    @Override
    public int getTimeout() throws SocketException {
        return socket.getSoTimeout();
    }

    @Override
    public void initForSync() throws IOException {}
}
