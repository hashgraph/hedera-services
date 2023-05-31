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

package com.swirlds.platform.test.consensus.framework;

import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.test.consensus.framework.validation.ConsensusOutputValidation;
import com.swirlds.platform.test.consensus.framework.validation.Validations;
import com.swirlds.platform.test.event.generator.GraphGenerator;
import java.util.List;
import java.util.function.Consumer;

/** A type which orchestrates the generation of events and the validation of the consensus output */
public class ConsensusTestOrchestrator {
    private final List<ConsensusTestNode> nodes;
    private long currentSequence = 0;
    private final List<Long> stakes;
    private final int totalEventNum;

    public ConsensusTestOrchestrator(
            final List<ConsensusTestNode> nodes, final List<Long> stakes, final int totalEventNum) {
        this.nodes = nodes;
        this.stakes = stakes;
        this.totalEventNum = totalEventNum;
    }

    /** Adds a new node to the test context by simulating a reconnect */
    public void addReconnectNode() {
        final ConsensusTestNode node = nodes.get(0).reconnect();
        node.getEventEmitter().setCheckpoint(currentSequence);
        node.addEvents(currentSequence);
        nodes.add(node);
    }

    private void generateEvents(final int numEvents) {
        currentSequence += numEvents;
        nodes.forEach(node -> node.getEventEmitter().setCheckpoint(currentSequence));
        nodes.forEach(node -> node.addEvents(numEvents));
    }

    /** Generates all events defined in the input */
    public ConsensusTestOrchestrator generateAllEvents() {
        return generateEvents(1d);
    }

    /**
     * Generates a fraction of the total events defined in the input
     *
     * @param fractionOfTotal the fraction of events to generate
     * @return this
     */
    public ConsensusTestOrchestrator generateEvents(final double fractionOfTotal) {
        generateEvents((int) (totalEventNum * fractionOfTotal));
        return this;
    }

    /**
     * Validates the output of all nodes against the given validations and clears the output
     *
     * @param validations the validations to run
     */
    public void validateAndClear(final Validations validations) {
        validate(validations);
        clearOutput();
    }

    /**
     * Validates the output of all nodes against the given validations
     *
     * @param validations the validations to run
     */
    public void validate(final Validations validations) {
        final ConsensusTestNode node1 = nodes.get(0);
        for (int i = 1; i < nodes.size(); i++) {
            final ConsensusTestNode node2 = nodes.get(i);
            for (final ConsensusOutputValidation validator : validations.getList()) {
                validator.validate(node1.getOutput(), node2.getOutput());
            }
        }
    }

    /** Clears the output of all nodes */
    public void clearOutput() {
        nodes.forEach(n -> n.getOutput().clear());
    }

    /**
     * Restarts all nodes with events and generations stored in the signed state. This is the
     * currently implemented restart, it discards all non-consensus events.
     */
    public void restartAllNodes() {
        final long lastRoundDecided = nodes.get(0).getConsensus().getLastRoundDecided();
        if (lastRoundDecided < EventConstants.MINIMUM_ROUND_CREATED) {
            System.out.println("Cannot restart, no consensus reached yet");
            return;
        }
        System.out.println("Restarting at round " + lastRoundDecided);
        for (final ConsensusTestNode node : nodes) {
            node.restart();
            node.getEventEmitter().setCheckpoint(currentSequence);
            node.addEvents(currentSequence);
        }
    }

    /**
     * Configures the graph generators of all nodes with the given configurator. This must be done
     * for all nodes so that the generators generate the same graphs
     */
    public ConsensusTestOrchestrator configGenerators(final Consumer<GraphGenerator<?>> configurator) {
        for (final ConsensusTestNode node : nodes) {
            configurator.accept(node.getEventEmitter().getGraphGenerator());
        }
        return this;
    }

    /**
     * Calls {@link com.swirlds.platform.test.event.source.EventSource#setNewEventWeight(double)}
     */
    public void setNewEventWeight(final int nodeIndex, final double eventWeight) {
        for (final ConsensusTestNode node : nodes) {
            node.getEventEmitter().getGraphGenerator().getSource(nodeIndex).setNewEventWeight(eventWeight);
        }
    }

    /** Calls {@link GraphGenerator#setOtherParentAffinity(List)} */
    public void setOtherParentAffinity(final List<List<Double>> matrix) {
        for (final ConsensusTestNode node : nodes) {
            node.getEventEmitter().getGraphGenerator().setOtherParentAffinity(matrix);
        }
    }

    public List<Long> getStakes() {
        return stakes;
    }
}
