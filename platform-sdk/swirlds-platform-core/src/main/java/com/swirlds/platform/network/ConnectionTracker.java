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

package com.swirlds.platform.network;

import com.swirlds.platform.Connection;

/**
 * Tracks all connections that have been opened and closed by the platform
 */
public interface ConnectionTracker {
    /**
     * Notifies the tracker that a new connection has been opened
     *
     * @param connection the connection that was just established
     */
    void newConnectionOpened(final Connection connection);

    /**
     * Notifies the tracker that a connection has been closed
     *
     * @param outbound true if it was an outbound connection (initiated by self)
     * @param connection the connection that was closed.
     */
    void connectionClosed(final boolean outbound, final Connection connection);
}
