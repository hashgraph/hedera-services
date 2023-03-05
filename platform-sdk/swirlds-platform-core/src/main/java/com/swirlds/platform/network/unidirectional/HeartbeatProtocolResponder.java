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
import com.swirlds.platform.network.ByteConstants;
import java.io.IOException;

/**
 * Handles incoming heartbeat protocol requests
 */
public final class HeartbeatProtocolResponder {

    private HeartbeatProtocolResponder() {}

    /**
     * A static heartbeat implementation of {@link NetworkProtocolResponder} since the protocol is stateless
     */
    public static void heartbeatProtocol(final byte ignored, final Connection connection) throws IOException {
        connection.getDos().writeByte(ByteConstants.HEARTBEAT_ACK);
        connection.getDos().flush();
    }
}
