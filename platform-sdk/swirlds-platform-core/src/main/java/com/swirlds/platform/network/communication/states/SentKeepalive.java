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

package com.swirlds.platform.network.communication.states;

import static com.swirlds.platform.network.communication.NegotiatorBytes.ACCEPT;
import static com.swirlds.platform.network.communication.NegotiatorBytes.KEEPALIVE;
import static com.swirlds.platform.network.communication.NegotiatorBytes.REJECT;

import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.communication.NegotiationException;
import com.swirlds.platform.network.communication.NegotiatorBytes;
import java.io.IOException;
import java.io.InputStream;

/**
 * We have already sent a keepalive, this state waits for, and handles, the byte sent by the peer in parallel
 */
public class SentKeepalive extends NegotiationStateWithDescription {
    private final InputStream byteInput;

    private final NegotiationState sleep;
    private final ReceivedInitiate receivedInitiate;

    /**
     * @param byteInput
     * 		the stream to read from
     * @param sleep
     * 		the sleep state to transition to if the peers also sends a keepalive
     * @param receivedInitiate
     * 		the state to transition to if the peer sends an initiate
     */
    public SentKeepalive(
            final InputStream byteInput, final NegotiationState sleep, final ReceivedInitiate receivedInitiate) {
        this.byteInput = byteInput;
        this.sleep = sleep;
        this.receivedInitiate = receivedInitiate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NegotiationState transition()
            throws NegotiationException, NetworkProtocolException, InterruptedException, IOException {
        final int b = byteInput.read();
        NegotiatorBytes.checkByte(b);
        return switch (b) {
            case KEEPALIVE -> {
                setDescription("both sent keepalive");
                yield sleep;
            } // we both sent keepalive
            case ACCEPT, REJECT -> throw new NegotiationException("Unexpected ACCEPT or REJECT");
            default -> {
                setDescription("received initiate - " + b);
                yield receivedInitiate.receivedInitiate(b);
            }
        };
    }
}
