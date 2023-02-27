/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.swirlds.platform.bls.protocol;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.bls.message.ProtocolMessage;
import java.util.List;
import java.util.Set;

/**
 * An interface representing a BLS protocol
 *
 * @param <T> The type of the protocol output object
 */
public interface BlsProtocol<T extends ProtocolOutput> {
    /**
     * Gets the id of the node executing the protocol
     *
     * @return the value of nodeId
     */
    NodeId getNodeId();

    /**
     * Gets the {@link BlsStateManager} object of the protocol
     *
     * @return the state manager object
     */
    BlsStateManager getStateManager();

    /**
     * Executes the next round of the protocol
     *
     * @param inputMessages the messages that are an input to the round
     * @return the message generated from the protocol round
     * @throws IllegalStateException if no more rounds exist to be called
     */
    ProtocolMessage executeNextRound(final List<ProtocolMessage> inputMessages);

    /**
     * Finishes the protocol
     *
     * @param inputMessages the input messages needed to finish
     * @return the protocol output object
     */
    T finish(final List<ProtocolMessage> inputMessages);

    /**
     * Gets the output object created by the completed protocol. Will only be valid if the protocol
     * completed successfully
     *
     * @return the output object of the protocol
     */
    T getOutput();

    /**
     * Gets the set of nodes that have been marked offline
     *
     * @return the set of offline nodes
     */
    Set<NodeId> getOfflineNodes();

    /**
     * Gets the set of nodes that have been marked malicious
     *
     * @return the set of malicious nodes
     */
    Set<NodeId> getMaliciousNodes();

    /**
     * Checks whether a specific node has been disqualified
     *
     * @param nodeId the id of the node to check
     * @return true if the node has been disqualified, otherwise false
     */
    boolean isCounterpartyDisqualified(final NodeId nodeId);

    /**
     * Gets the list of incident reports that have been logged
     *
     * @return the list of incident reports
     */
    List<IncidentReport> getIncidentReports();
}
