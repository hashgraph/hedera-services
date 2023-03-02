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

package com.swirlds.platform.test.network.communication.multithreaded;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.Connection;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wraps another connection, but returns true for {@link #connected()} only the specified number of times
 */
public class ExpiringConnection implements Connection {
    private final Connection connection;
    private final AtomicInteger returnConnectedTimes;

    public ExpiringConnection(final Connection connection, final int returnConnectedTimes) {
        this.connection = connection;
        this.returnConnectedTimes = new AtomicInteger(returnConnectedTimes);
    }

    @Override
    public void disconnect() {
        connection.disconnect();
    }

    @Override
    public NodeId getSelfId() {
        return connection.getSelfId();
    }

    @Override
    public NodeId getOtherId() {
        return connection.getOtherId();
    }

    @Override
    public SyncInputStream getDis() {
        return connection.getDis();
    }

    @Override
    public SyncOutputStream getDos() {
        return connection.getDos();
    }

    @Override
    public boolean connected() {
        return returnConnectedTimes.getAndDecrement() > 0;
    }

    @Override
    public int getTimeout() throws SocketException {
        return connection.getTimeout();
    }

    @Override
    public void setTimeout(final int timeoutMillis) throws SocketException {
        connection.setTimeout(timeoutMillis);
    }

    @Override
    public void initForSync() throws IOException {
        connection.initForSync();
    }

    @Override
    public boolean isOutbound() {
        return connection.isOutbound();
    }

    @Override
    public String getDescription() {
        return connection.getDescription();
    }
}
