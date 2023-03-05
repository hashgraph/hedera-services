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

package com.swirlds.platform.network.unidirectional;

import com.swirlds.platform.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import java.io.IOException;

/**
 * Used to transfer control of the communication channel to the handler of the initiated protocol
 */
public interface NetworkProtocolResponder {
    /**
     * Called when a network protocol is initiated by the peer
     *
     * @param initialByte
     * 		the initial byte signifying the protocol type
     * @param connection
     * 		the connection over which the protocol was initiated
     * @throws IOException
     * 		if any connection issues occur
     * @throws NetworkProtocolException
     * 		if any protocol execution issues occur
     * @throws InterruptedException
     * 		if the thread is interrupted
     */
    void protocolInitiated(final byte initialByte, final Connection connection)
            throws IOException, NetworkProtocolException, InterruptedException;
}
