/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.network.communication.states;

import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.communication.NegotiationException;
import com.swirlds.platform.network.protocol.Protocol;
import java.io.IOException;

/**
 * Runs a protocol previously negotiated
 */
public class ProtocolNegotiated extends NegotiationStateWithDescription {
    private final Connection connection;
    private Protocol protocol;

    /**
     * @param connection
     * 		the connection to run the protocol on
     */
    public ProtocolNegotiated(final Connection connection) {
        this.connection = connection;
    }

    /**
     * Set the protocol to run on the next transition
     *
     * @param protocol
     * 		the protocol to run
     * @return this state
     */
    public NegotiationState runProtocol(final Protocol protocol) {
        this.protocol = protocol;
        return this;
    }

    //    private final int magic = 1337; // TODO remove

    /**
     * {@inheritDoc}
     */
    @Override
    public NegotiationState transition()
            throws NegotiationException, NetworkProtocolException, IOException, InterruptedException {
        if (protocol == null) {
            throw new IllegalStateException("Cannot run a protocol because it is null");
        }
        try {

            // TODO
            //            connection.getDos().writeInt(magic);
            //            connection.getDos().flush();
            //            if (connection.getDis().readInt() != magic) {
            //                throw new IOException("magic number mismatch before protocol " +
            // protocol.getProtocolName()
            //                        + " with peer " + connection.getOtherId());
            //            }

            protocol.runProtocol(connection);

            // TODO
            //            connection.getDos().writeInt(magic);
            //            connection.getDos().flush();
            //            if (connection.getDis().readInt() != magic) {
            //                throw new IOException("magic number mismatch after protocol " + protocol.getProtocolName()
            //                        + " with peer " + connection.getOtherId());
            //            }

        } finally {
            setDescription("ran protocol " + protocol.getProtocolName());
            protocol = null;
        }
        return null; // back to initial state
    }
}
