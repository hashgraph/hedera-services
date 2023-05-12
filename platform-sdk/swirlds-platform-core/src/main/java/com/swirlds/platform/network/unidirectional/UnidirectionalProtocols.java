/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

import com.swirlds.platform.network.ByteConstants;

/**
 * Lists all network protocols that are supported
 */
public enum UnidirectionalProtocols {
    HEARTBEAT(ByteConstants.HEARTBEAT),
    SYNC(ByteConstants.COMM_SYNC_REQUEST),
    RECONNECT(ByteConstants.COMM_STATE_REQUEST);
    private final byte initialByte;

    UnidirectionalProtocols(final int initialByte) {
        this.initialByte = (byte) initialByte;
    }

    public byte getInitialByte() {
        return initialByte;
    }
}
