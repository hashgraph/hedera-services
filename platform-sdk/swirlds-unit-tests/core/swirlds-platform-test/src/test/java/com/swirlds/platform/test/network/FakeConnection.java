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

package com.swirlds.platform.test.network;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.Connection;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FakeConnection implements Connection {
    public final CountDownLatch disconnect = new CountDownLatch(1);
    private final NodeId selfId;
    private final NodeId peerId;

    public FakeConnection() {
        this(NodeId.createMain(0), NodeId.createMain(1));
    }

    public FakeConnection(final NodeId selfId, final NodeId peerId) {
        this.selfId = selfId;
        this.peerId = peerId;
    }

    @Override
    public void disconnect() {
        disconnect.countDown();
    }

    public boolean awaitDisconnect() throws InterruptedException {
        return disconnect.await(1, TimeUnit.MILLISECONDS);
    }

    @Override
    public NodeId getSelfId() {
        return selfId;
    }

    @Override
    public NodeId getOtherId() {
        return peerId;
    }

    @Override
    public SyncInputStream getDis() {
        throw new UnsupportedOperationException("N/A");
    }

    @Override
    public SyncOutputStream getDos() {
        throw new UnsupportedOperationException("N/A");
    }

    @Override
    public boolean connected() {
        return disconnect.getCount() > 0;
    }

    @Override
    public int getTimeout() {
        throw new UnsupportedOperationException("N/A");
    }

    @Override
    public void setTimeout(final int timeoutMillis) {
        throw new UnsupportedOperationException("N/A");
    }

    @Override
    public void initForSync() {
        throw new UnsupportedOperationException("N/A");
    }

    @Override
    public boolean isOutbound() {
        throw new UnsupportedOperationException("N/A");
    }

    @Override
    public String getDescription() {
        return toString();
    }
}
