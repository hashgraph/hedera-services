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

package com.swirlds.platform.network.protocol;

import com.swirlds.platform.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import java.io.IOException;

/**
 * Represents a method for running a network protocol
 */
@FunctionalInterface
public interface ProtocolRunnable {
    /**
     * Run the protocol over the provided connection. Once the protocol is done running, it should not leave any unread
     * bytes in the input stream unless an exception is thrown. This is important since the connection will be reused.
     *
     * @param connection
     * 		the connection to run the protocol on
     * @throws NetworkProtocolException
     * 		if a protocol specific issue occurs
     * @throws IOException
     * 		if an I/O issue occurs
     * @throws InterruptedException
     * 		if the calling thread is interrupted while running the protocol
     */
    void runProtocol(Connection connection) throws NetworkProtocolException, IOException, InterruptedException;
}
