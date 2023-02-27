/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.consensus;

import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.test.event.TestSequence;
import com.swirlds.platform.test.event.emitter.EventEmitter;
import com.swirlds.platform.test.event.generator.GraphGenerator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A type which defines the contextual data for a single consensus test. This is the type that
 * runs a test sequence. It encapsulates an {@link GraphGenerator}, a list of {@link NodeContext}s, and
 * a system PRNG.
 */
public class ConsensusTestContext {
    private final List<NodeContext> nodes;
    private long currentSequence = 0;
    private int sequenceNum = 0;

    public ConsensusTestContext(
            final long seed, final EventEmitter<?> node1Emitter, final EventEmitter<?> node2Emitter) {
        this.nodes = new ArrayList<>();
        final Random random = new Random(seed);

        final long graphSeed = random.nextLong();

        // Make the graph generators create a fresh set of events.
        // Use the same seed so that they create identical graphs.
        node1Emitter.setGraphGeneratorSeed(graphSeed);
        node2Emitter.setGraphGeneratorSeed(graphSeed);

        // Create two instances to run consensus on. Each instance reseeds the emitter so that they emit
        // events in different orders.
        nodes.add(NodeContext.genesisContext(random, node1Emitter));
        nodes.add(NodeContext.genesisContext(random, node2Emitter));
    }

    /**
     * Run a sequence of tests, and validate results of the test sequence.
     *
     * @param sequence
     * 		a specification for sequence of tests to run
     */
    public void runSequence(final TestSequence sequence) {
        currentSequence += sequence.getLength();
        sequenceNum++;

        nodes.forEach(node -> node.setDebug(sequence.isDebug()));

        nodes.forEach(node -> node.getEventEmitter().setCheckpoint(currentSequence));
        nodes.forEach(node -> node.applyEventsToConsensus(sequenceNum, sequence.getLength()));

        final NodeContext node1 = nodes.get(0);
        for (int i = 1; i < nodes.size(); i++) {
            final NodeContext node2 = nodes.get(i);
            sequence.validateSequence(
                    sequenceNum,
                    node1.getConsensusEventsInSequence(sequenceNum),
                    node2.getConsensusEventsInSequence(sequenceNum),
                    node1.getAllEventsInSequence(sequenceNum),
                    node2.getAllEventsInSequence(sequenceNum));
        }
    }

    /**
     * Restarts all nodes with events and generations stored in the signed state. This is the currently implemented
     * restart, it discards all non-consensus events.
     */
    public void restartAllNodes(final Path stateDir) throws ConstructableRegistryException, IOException {
        final long lastRoundDecided = nodes.get(0).getConsensus().getLastRoundDecided();
        if (lastRoundDecided < EventConstants.MINIMUM_ROUND_CREATED) {
            System.out.println("Cannot restart, no consensus reached yet");
            return;
        }
        System.out.println("Restarting at round " + lastRoundDecided);
        for (final NodeContext node : nodes) {
            node.restart(stateDir);
        }
        currentSequence = 0;
    }
}
