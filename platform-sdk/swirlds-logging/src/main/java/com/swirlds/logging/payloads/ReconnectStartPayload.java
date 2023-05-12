/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.logging.payloads;

/**
 * This payload is logged when a node begins a reconnect operation.
 */
public class ReconnectStartPayload extends AbstractLogPayload {

    private boolean receiving;
    private long nodeId;
    private long otherNodeId;
    private long round;

    public ReconnectStartPayload() {}

    /**
     * @param message
     * 		a human readable message
     * @param receiving
     * 		if true then this node is the receiver, i.e. it is the one attempting to reconnect.
     * 		If false then this node is the sender and is helping another node to reconnect.
     * @param nodeId
     * 		this node's ID
     * @param otherNodeId
     * 		the other node's ID
     * @param round
     * 		the latest signed round known by this node
     */
    public ReconnectStartPayload(
            final String message,
            final boolean receiving,
            final long nodeId,
            final long otherNodeId,
            final long round) {
        super(message);
        this.receiving = receiving;
        this.nodeId = nodeId;
        this.otherNodeId = otherNodeId;
        this.round = round;
    }

    public boolean isReceiving() {
        return receiving;
    }

    public void setReceiving(boolean receiving) {
        this.receiving = receiving;
    }

    public long getNodeId() {
        return nodeId;
    }

    public void setNodeId(int nodeId) {
        this.nodeId = nodeId;
    }

    public long getOtherNodeId() {
        return otherNodeId;
    }

    public void setOtherNodeId(int otherNodeId) {
        this.otherNodeId = otherNodeId;
    }

    public long getRound() {
        return round;
    }

    public void setRound(long round) {
        this.round = round;
    }
}
