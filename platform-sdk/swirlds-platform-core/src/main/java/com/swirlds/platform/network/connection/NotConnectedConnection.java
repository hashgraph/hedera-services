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

package com.swirlds.platform.network.connection;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.Connection;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;
import java.net.SocketException;

/**
 * An implementation of {@link Connection} that is used to avoid returning null if there is no connection.
 * This connection will never be connected and will do nothing on disconnect. All other methods will throw an
 * exception.
 */
public class NotConnectedConnection implements Connection {
    private static final Connection SINGLETON = new NotConnectedConnection();
    private static final UnsupportedOperationException NOT_IMPLEMENTED =
            new UnsupportedOperationException("Not implemented");

    public static Connection getSingleton() {
        return SINGLETON;
    }

    /**
     * Does nothing since its not a real connection
     */
    @Override
    public void disconnect() {
        // nothing to do
    }

    /**
     * Throws an {@link UnsupportedOperationException} since this is not a real connection
     *
     * @return never returns, always throws
     */
    @Override
    public NodeId getSelfId() {
        throw NOT_IMPLEMENTED;
    }

    /**
     * Throws an {@link UnsupportedOperationException} since this is not a real connection
     *
     * @return never returns, always throws
     */
    @Override
    public NodeId getOtherId() {
        throw NOT_IMPLEMENTED;
    }

    /**
     * Throws an {@link UnsupportedOperationException} since this is not a real connection
     *
     * @return never returns, always throws
     */
    @Override
    public SyncInputStream getDis() {
        throw NOT_IMPLEMENTED;
    }

    /**
     * Throws an {@link UnsupportedOperationException} since this is not a real connection
     *
     * @return never returns, always throws
     */
    @Override
    public SyncOutputStream getDos() {
        throw NOT_IMPLEMENTED;
    }

    /**
     * @return always returns false
     */
    @Override
    public boolean connected() {
        return false;
    }

    /**
     * Throws an {@link UnsupportedOperationException} since this is not a real connection
     *
     * @return never returns, always throws
     */
    @Override
    public int getTimeout() throws SocketException {
        throw NOT_IMPLEMENTED;
    }

    /**
     * Throws an {@link UnsupportedOperationException} since this is not a real connection
     */
    @Override
    public void setTimeout(int timeoutMillis) throws SocketException {
        throw NOT_IMPLEMENTED;
    }

    /**
     * Throws an {@link UnsupportedOperationException} since this is not a real connection
     */
    @Override
    public void initForSync() {
        throw NOT_IMPLEMENTED;
    }

    /**
     * Throws an {@link UnsupportedOperationException} since this is not a real connection
     *
     * @return never returns, always throws
     */
    @Override
    public boolean isOutbound() {
        throw NOT_IMPLEMENTED;
    }

    @Override
    public String getDescription() {
        return "NotConnectedConnection";
    }
}
