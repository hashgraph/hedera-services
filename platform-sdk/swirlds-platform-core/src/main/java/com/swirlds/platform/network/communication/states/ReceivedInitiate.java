/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.communication.NegotiationException;
import com.swirlds.platform.network.communication.NegotiationProtocols;
import com.swirlds.platform.network.communication.NegotiatorBytes;
import com.swirlds.platform.network.protocol.PeerProtocol;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Responds to a protocol initiation by the peer
 */
public class ReceivedInitiate extends NegotiationStateWithDescription {
    private final NegotiationProtocols protocols;
    private final OutputStream byteOutput;

    private final ProtocolNegotiated stateNegotiated;
    private final NegotiationState sleep;

    private int protocolInitiated = NegotiatorBytes.UNINITIALIZED;

    /**
     * @param protocols
     * 		protocols being negotiated
     * @param byteOutput
     * 		the stream to write to
     * @param stateNegotiated
     * 		the state to transition to if a protocol gets negotiated
     * @param sleep
     * 		the sleep state to transition to if the negotiation fails
     */
    public ReceivedInitiate(
            final NegotiationProtocols protocols,
            final OutputStream byteOutput,
            final ProtocolNegotiated stateNegotiated,
            final NegotiationState sleep) {
        this.protocols = protocols;
        this.byteOutput = byteOutput;
        this.stateNegotiated = stateNegotiated;
        this.sleep = sleep;
    }

    /**
     * Set the protocol ID that was initiated by the peer
     *
     * @param protocolId
     * 		the ID of the protocol initiated
     * @return this state
     */
    public NegotiationState receivedInitiate(final int protocolId) {
        protocolInitiated = protocolId;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NegotiationState transition()
            throws NegotiationException, NetworkProtocolException, InterruptedException, IOException {
        final PeerProtocol peerProtocol = protocols.getProtocol(protocolInitiated);
        if (peerProtocol.shouldAccept()) {
            try {
                byteOutput.write(NegotiatorBytes.ACCEPT);
                byteOutput.flush();
            } catch (final IOException ex) {
                peerProtocol.acceptFailed();
                throw ex;
            }
            stateNegotiated.runProtocol(peerProtocol);
            protocolInitiated = NegotiatorBytes.UNINITIALIZED;
            setDescription("accepted protocol initiated by peer - " + peerProtocol.getProtocolName());
            return stateNegotiated;
        } else {
            byteOutput.write(NegotiatorBytes.REJECT);
            byteOutput.flush();
            protocolInitiated = NegotiatorBytes.UNINITIALIZED;
            setDescription("rejected protocol initiated by peer - " + peerProtocol.getProtocolName());
            return sleep;
        }
    }
}
