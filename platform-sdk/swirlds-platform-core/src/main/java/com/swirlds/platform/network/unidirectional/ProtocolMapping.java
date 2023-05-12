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

/**
 * Mapping between a protocol initiation byte and a handler for this protocol
 *
 * @param initialByte
 * 		the byte that start the protocol
 * @param protocolHandler
 * 		the handler responsible for this protocol
 */
public record ProtocolMapping(byte initialByte, NetworkProtocolResponder protocolHandler) {
    /**
     * Creates a mapping between the supplied byte and handler
     *
     * @param initialByte
     * 		the byte to map
     * @param protocolHandler
     * 		the handler to map
     * @return the new mapping
     */
    public static ProtocolMapping map(final byte initialByte, final NetworkProtocolResponder protocolHandler) {
        return new ProtocolMapping(initialByte, protocolHandler);
    }
}
