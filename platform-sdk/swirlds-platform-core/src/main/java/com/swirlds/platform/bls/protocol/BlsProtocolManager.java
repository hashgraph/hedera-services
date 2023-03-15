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
import com.swirlds.platform.bls.message.BlsProtocolMessage;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A manager for a BLS protocol
 *
 * @param <T> the type of output of the protocol being managed
 */
public interface BlsProtocolManager<T extends BlsProtocolOutput> {
    /**
     * Executes the next round of the protocol
     *
     * @param inputMessages the messages that are an input to the round
     * @return the message generated from the protocol round
     * @throws IllegalStateException if no more rounds exist to be called
     */
    BlsProtocolMessage executeNextRound(List<BlsProtocolMessage> inputMessages);

    /**
     * Finishes the protocol
     *
     * @param inputMessages the input messages needed to finish
     * @return the protocol output object
     */
    T finish(List<BlsProtocolMessage> inputMessages);

    /**
     * Accepts a list of messages, filters them, and returns a list containing messages which match a certain subtype,
     * cast to that subtype
     * <p>
     * Filters out messages that should be ignored by the protocol
     *
     * @param inputMessages        a list of messages of type U
     * @param acceptableSenders    a list of senders from whom we are expecting to potentially receive messages
     * @param subtype              the type of messages that should be returned
     * @param disqualifyNonSenders if true, nodes included in the acceptableSenders parameter that didn't send a valid
     *                             message will be declared offline
     * @param <U>                  the type of messages that should be returned
     * @return a list of messages of type U
     */
    <U extends BlsProtocolMessage> List<U> filterCast(
            List<BlsProtocolMessage> inputMessages,
            Collection<NodeId> acceptableSenders,
            Class<U> subtype,
            boolean disqualifyNonSenders);

    /**
     * Adds a {@link BlsProtocolRound} to the list of rounds that exist for this protocol
     *
     * @param round the round to add
     */
    void addRound(BlsProtocolRound round);

    /**
     * Declares a counterparty to be offline. Does necessary cleanup, and evaluates whether the protocol still has
     * enough honest parties to proceed
     *
     * @param nodeId the id of the node to declare offline
     * @param reason a string describing why the party was declared malicious
     */
    void declareOfflineCounterparty(NodeId nodeId, String reason);

    /**
     * Declares a counterparty to be malicious. Does necessary cleanup, and evaluates whether the protocol still has
     * enough honest parties to proceed
     *
     * @param nodeId  the id of the node to declare offline
     * @param reason  a string describing why the party was declared malicious
     * @param trigger the message which caused the counterparty to be declared malicious
     */
    void declareMaliciousCounterparty(NodeId nodeId, String reason, BlsProtocolMessage trigger);

    /**
     * Gets a set of nodes that have been declared offline
     *
     * @return the offline nodes
     */
    Set<NodeId> getOfflineNodes();

    /**
     * Gets the set of nodes that have been declared malicious
     *
     * @return the malicious nodes
     */
    Set<NodeId> getMaliciousNodes();

    /**
     * Gets a list of {@link BlsIncidentReport}s, which each describe when/how a node has been disqualified
     *
     * @return the incident reports
     */
    List<BlsIncidentReport> getIncidentReports();

    /**
     * Checks whether a given node has been disqualified
     *
     * @param nodeId the id of the node to check
     * @return true if the node has been disqualified, otherwise false
     */
    boolean isCounterpartyDisqualified(NodeId nodeId);

    /**
     * Called by a protocol when a catastrophic error has occurred, and execution cannot continue
     */
    void errorOccurred();

    /**
     * Gets the current state of the protocol
     *
     * @return the protocol state
     */
    BlsProtocolState getState();

    /**
     * Sets the method used to clean up after disqualifying a counterparty
     * <p>
     * This will be set by the protocol itself, after the manager has already been created and passed into the protocol
     * constructor
     * <p>
     * Throws an {@link IllegalStateException} if caller is attempting to overwrite a previous value
     *
     * @param cleanupMethod the method to use to clean up
     */
    void setDisqualificationCleanup(BlsDisqualificationCleanup cleanupMethod);

    /**
     * Sets the method that the protocol uses to perform final calculations
     * <p>
     * This will be set by the protocol itself, after the manager has already been created and passed into the protocol
     * constructor
     * <p>
     * Throws an {@link IllegalStateException} if caller is attempting to overwrite a previous value
     *
     * @param finishMethod the method which performs the calculations of the protocol finish phase
     */
    void setFinishingMethod(BlsProtocolFinisher<T> finishMethod);

    /**
     * Sets the method which the protocol uses to determine if enough qualified parties still exist to continue
     * execution
     * <p>
     * This will be set by the protocol itself, after the manager has already been created and passed into the protocol
     * constructor
     * <p>
     * Throws an {@link IllegalStateException} if caller is attempting to overwrite a previous value
     *
     * @param viabilityChecker the method to check whether the protocol is still viable
     */
    void setViabilityChecker(BlsProtocolViabilityChecker viabilityChecker);
}
