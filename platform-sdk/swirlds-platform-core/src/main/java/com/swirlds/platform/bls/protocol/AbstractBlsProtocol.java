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
import java.util.*;

/** An abstract implementation of a BLS protocol */
public abstract class AbstractBlsProtocol<T extends ProtocolOutput> implements BlsProtocol<T> {
    /** The unique id of the node where this protocol is executing */
    private final NodeId nodeId;

    /** An object which manages the state of the protocol */
    private final BlsStateManager stateManager;

    /** An object which keeps track of the behavior of counterparties */
    private final CounterpartyManager counterpartyManager;

    /** A list of protocol rounds, which will be called sequentially */
    private final List<BlsProtocolRound> protocolRounds;

    /** A source of randomness */
    private final Random random;

    /**
     * Constructor
     *
     * @param nodeId the id of the node where this protocol instance is running
     * @param random a source of randomness
     */
    protected AbstractBlsProtocol(final NodeId nodeId, final Random random) {
        this.nodeId = nodeId;

        this.stateManager = new BlsStateManager();
        this.counterpartyManager = new CounterpartyManager();

        this.protocolRounds = new LinkedList<>();
        this.random = random;
    }

    /** {@inheritDoc} */
    @Override
    public NodeId getNodeId() {
        return nodeId;
    }

    /** {@inheritDoc} */
    @Override
    public BlsStateManager getStateManager() {
        return stateManager;
    }

    /** {@inheritDoc} */
    @Override
    public ProtocolMessage executeNextRound(final List<ProtocolMessage> inputMessages) {
        if (stateManager.getRoundsStarted() >= protocolRounds.size()) {
            throw new IllegalStateException("No more rounds exist to be called");
        }

        if (stateManager.getRoundsStarted() != stateManager.getRoundsCompleted()) {
            throw new IllegalStateException("Round started before previous round finished");
        }

        stateManager.roundStarted();

        final ProtocolMessage outputMessage =
                protocolRounds.get(stateManager.getRoundsCompleted()).execute(inputMessages);

        stateManager.roundCompleted();

        return outputMessage;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Manages the state during the finish process, and calls {@link #performFinish} to actually
     * do the finishing
     */
    @Override
    public final T finish(final List<ProtocolMessage> inputMessages) {
        getStateManager().beginFinish();

        final T outputObject = performFinish(inputMessages);

        getStateManager().finishComplete();

        return outputObject;
    }

    /**
     * Actually do the work of finishing
     *
     * @param inputMessages the messages sent during the last protocol round
     * @return the output object of the finishing process
     */
    protected abstract T performFinish(final List<ProtocolMessage> inputMessages);

    /**
     * Accepts a list of messages, filters them, and returns a list containing messages which match
     * a certain subtype, cast to that subtype
     *
     * <p>Filters out messages:
     *
     * <ul>
     *   <li>from parties that have already been disqualified
     *   <li>from unexpected senders
     *   <li>from senders who sent more than 1 message
     *   <li>of the incorrect type
     * </ul>
     *
     * <p>Declares senders malicious who sent multiple messages, or a message of the incorrect type,
     * or who we weren't expecting a message from.
     *
     * @param inputMessages a list of messages of type U
     * @param subtype the type of messages that should be returned
     * @param <U> the type of messages that should be returned
     * @return a list of messages of type U
     */
    @SuppressWarnings("unchecked")
    public <U extends ProtocolMessage> List<U> filterCast(
            final List<ProtocolMessage> inputMessages,
            final Collection<NodeId> acceptableSenders,
            final Class<U> subtype) {

        final Set<NodeId> sendersIds = new HashSet<>();
        final Set<NodeId> duplicateSenders = new HashSet<>();

        // Find out which senders occur more than once in the list
        inputMessages.forEach(
                message -> {
                    final NodeId senderId = message.getSenderId();
                    if (!sendersIds.add(senderId) && duplicateSenders.add(senderId)) {
                        declareMaliciousCounterparty(
                                senderId,
                                new IncidentReport(
                                        senderId,
                                        "duplicate messages discovered in "
                                                + getProtocolInstantDescription(),
                                        message));
                    }
                });

        return inputMessages.stream()
                // previously disqualified parties
                .filter(message -> !counterpartyManager.checkDisqualified(message.getSenderId()))
                // messages from duplicate senders
                .filter(message -> !duplicateSenders.contains(message.getSenderId()))
                // messages from outside the acceptable collection of senders
                .filter(
                        message -> {
                            if (acceptableSenders.contains(message.getSenderId())) {
                                return true;
                            }

                            declareMaliciousCounterparty(
                                    message.getSenderId(),
                                    new IncidentReport(
                                            message.getSenderId(),
                                            "unacceptable sender discovered in "
                                                    + getProtocolInstantDescription(),
                                            message));

                            return false;
                        })
                // Filter out messages of incorrect type, and disqualify bad senders
                .filter(
                        message -> {
                            if (subtype.isInstance(message)) {
                                return true;
                            }

                            declareMaliciousCounterparty(
                                    message.getSenderId(),
                                    new IncidentReport(
                                            message.getSenderId(),
                                            "incorrect message type discovered in "
                                                    + getProtocolInstantDescription(),
                                            message));

                            return false;
                        })
                // Safely perform the cast, since messages of incorrect type are already filtered
                // out
                .map(message -> (U) message)
                .toList();
    }

    /**
     * Adds a {@link BlsProtocolRound} functional interface to the list of rounds that exist for
     * this protocol
     *
     * @param round the round to add
     */
    protected void addRound(final BlsProtocolRound round) {
        protocolRounds.add(round);
    }

    /**
     * Disqualifies as offline parties we expected a message from, but who didn't send anything
     *
     * @param expectedSenders the collection of senders we expect messages from
     * @param receivedMessages the list of messages actually received
     */
    protected void disqualifyNonSenders(
            final Collection<NodeId> expectedSenders,
            final List<? extends ProtocolMessage> receivedMessages) {

        final Set<NodeId> actualSenders = new HashSet<>();

        for (final ProtocolMessage message : receivedMessages) {
            actualSenders.add(message.getSenderId());
        }

        for (final NodeId expectedSender : expectedSenders) {
            if (!actualSenders.contains(expectedSender)) {
                declareOfflineCounterparty(
                        expectedSender,
                        new IncidentReport(
                                expectedSender,
                                "missing message discovered in " + getProtocolInstantDescription(),
                                null));
            }
        }
    }

    /**
     * Gets the source of randomness for the protocol
     *
     * @return the {@link #random} source
     */
    protected Random getRandom() {
        return random;
    }

    /**
     * Gets a string describing when in the protocol we currently are
     *
     * @return a string describing right now
     */
    private String getProtocolInstantDescription() {
        if (stateManager.getRoundsCompleted() == stateManager.getRoundsStarted()) {
            return "finish stage";
        } else {
            return "round " + stateManager.getRoundsStarted();
        }
    }

    /**
     * Declares a counterparty to be offline. Does necessary cleanup, and evaluates whether the
     * protocol still has enough honest parties to proceed
     *
     * @param nodeId the id of the node to declare offline
     * @param incidentReport the report of the incident that caused the counterparty to be declared
     *     offline
     */
    private void declareOfflineCounterparty(
            final NodeId nodeId, final IncidentReport incidentReport) {
        cleanupAfterDisqualification(nodeId);
        counterpartyManager.declareOffline(nodeId, incidentReport);

        if (!isProtocolViable()) {
            abortProtocol();
        }
    }

    /**
     * Declares a counterparty to be malicious. Does necessary cleanup, and evaluates whether the
     * protocol still has enough honest parties to proceed
     *
     * @param nodeId the id of the node to declare offline
     * @param incidentReport the report of the incident that caused the counterparty to be declared
     *     malicious
     */
    protected void declareMaliciousCounterparty(
            final NodeId nodeId, final IncidentReport incidentReport) {
        cleanupAfterDisqualification(nodeId);
        counterpartyManager.declareMalicious(nodeId, incidentReport);

        if (!isProtocolViable()) {
            abortProtocol();
        }
    }

    /**
     * Does whatever cleanup is necessary when a node is disqualified
     *
     * @param nodeId the id of the node that was disqualified
     */
    protected abstract void cleanupAfterDisqualification(final NodeId nodeId);

    /**
     * Checks whether the protocol has enough honest nodes remaining to continue execution
     *
     * @return true if enough honest nodes still exist to continue execution, otherwise false
     */
    protected abstract boolean isProtocolViable();

    /** {@inheritDoc} */
    @Override
    public Set<NodeId> getOfflineNodes() {
        return counterpartyManager.getOfflineNodes();
    }

    /** {@inheritDoc} */
    @Override
    public Set<NodeId> getMaliciousNodes() {
        return counterpartyManager.getMaliciousNodes();
    }

    /** {@inheritDoc} */
    @Override
    public List<IncidentReport> getIncidentReports() {
        return counterpartyManager.getIncidentReports();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCounterpartyDisqualified(final NodeId nodeId) {
        return counterpartyManager.checkDisqualified(nodeId);
    }

    /** Aborts the protocol */
    private void abortProtocol() {
        stateManager.errorOccurred();

        throw new BlsProtocolException("Disqualified stake is too high to continue");
    }
}
