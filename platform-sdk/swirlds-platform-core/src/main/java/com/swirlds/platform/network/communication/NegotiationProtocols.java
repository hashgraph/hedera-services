/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.network.communication;

import com.swirlds.platform.network.protocol.Protocol;
import java.util.List;

/**
 * Manages protocols during a protocol negotiation
 */
public class NegotiationProtocols {
    private final Protocol[] allProtocols;
    /** the protocol initiated, if any */
    private Protocol initiatedProtocol = null;

    /**
     * @param protocols
     * 		a list of protocols to negotiate in order of priority
     */
    public NegotiationProtocols(final List<Protocol> protocols) {
        if (protocols == null || protocols.isEmpty() || protocols.size() > NegotiatorBytes.MAX_NUMBER_OF_PROTOCOLS) {
            throw new IllegalArgumentException(
                    "the list of protocols supplied should be: non-null, non-empty and have a size lower than "
                            + NegotiatorBytes.MAX_NUMBER_OF_PROTOCOLS);
        }
        this.allProtocols = protocols.toArray(new Protocol[0]);
    }

    /**
     * Get the protocol with the supplied ID
     *
     * @param id
     * 		the ID of the protocol
     * @return the protocol requested
     * @throws NegotiationException
     * 		if an invalid ID is supplied
     */
    public Protocol getProtocol(final int id) throws NegotiationException {
        if (id < 0 || id >= allProtocols.length) {
            throw new NegotiationException("not a valid protocol ID: " + id);
        }
        return allProtocols[id];
    }

    /**
     * The protocol initiated has been accepted
     *
     * @return the protocol initiated
     * @throws IllegalStateException
     * 		if no protocol was previously initiated
     */
    public Protocol initiateAccepted() {
        throwIfNoneInitiated();
        final Protocol ret = initiatedProtocol;
        initiatedProtocol = null;
        return ret;
    }

    /**
     * The protocol initated has not been accepted
     *
     * @throws IllegalStateException
     * 		if no protocol was previously initiated
     */
    public void initiateFailed() {
        throwIfNoneInitiated();
        initiatedProtocol.initiateFailed();
        initiatedProtocol = null;
    }

    /**
     * If a negotiation exception occurred, we must notify any initiated protocol that the initiate failed
     */
    public void negotiationExceptionOccurred() {
        if (initiatedProtocol != null) {
            initiatedProtocol.initiateFailed();
        }
    }

    /**
     * @return the protocol that was initiated by self
     * @throws IllegalStateException
     * 		if no protocol was previously initiated
     */
    public Protocol getInitiatedProtocol() {
        throwIfNoneInitiated();
        return initiatedProtocol;
    }

    /**
     * @return the ID of the protocol that should be initiated, or -1 if none
     */
    public byte initiateProtocol() {
        // check each protocol in order of priority until we find one we should initiate
        for (byte i = 0; i < allProtocols.length; i++) {
            if (allProtocols[i].shouldInitiate()) {
                initiatedProtocol = allProtocols[i];
                return i;
            }
        }
        return -1;
    }

    private void throwIfNoneInitiated() {
        if (initiatedProtocol == null) {
            throw new IllegalStateException("no protocol initiated");
        }
    }
}
