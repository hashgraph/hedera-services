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

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.platform.Consensus;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.test.consensus.ConsensusUtils;
import com.swirlds.platform.test.consensus.TestIntake;
import com.swirlds.platform.test.event.emitter.EventEmitter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Random;

/** A type which is responsible for managing a node in a consensus test */
public class ConsensusTestNode {
    /** The event emitter that produces events. */
    private final EventEmitter<?> eventEmitter;

    /** The instance to apply events to. */
    private final TestIntake intake;

    private final Random random;

    /**
     * Creates a new instance.
     *
     * @param eventEmitter the emitter of events
     * @param intake the instance to apply events to
     */
    public ConsensusTestNode(@NonNull final EventEmitter<?> eventEmitter, @NonNull final TestIntake intake) {
        this.eventEmitter = eventEmitter;
        this.intake = intake;
        this.random = new Random();
    }

    /**
     * Creates a new instance with a freshly seeded {@link EventEmitter}.
     *
     * @param eventEmitter the emitter of events
     */
    public static @NonNull ConsensusTestNode genesisContext(@NonNull final EventEmitter<?> eventEmitter) {
        return new ConsensusTestNode(
                eventEmitter, new TestIntake(eventEmitter.getGraphGenerator().getAddressBook()));
    }

    /** Simulates a restart on a node */
    public void restart() {
        // clear all generators
        eventEmitter.reset();
        final ConsensusSnapshot snapshot = Objects.requireNonNull(
                        getOutput().getConsensusRounds().peekLast())
                .getSnapshot();
        intake.reset();
        intake.loadSnapshot(snapshot);
    }

    /**
     * Create a new {@link ConsensusTestNode} that will be created by simulating a reconnect with
     * this context
     *
     * @return a new {@link ConsensusTestNode}
     */
    public @NonNull ConsensusTestNode reconnect() {
        // create a new context
        final EventEmitter<?> newEmitter = eventEmitter.cleanCopy(random.nextLong());
        newEmitter.reset();

        final ConsensusTestNode consensusTestNode = new ConsensusTestNode(
                newEmitter, new TestIntake(newEmitter.getGraphGenerator().getAddressBook()));
        consensusTestNode.intake.loadSnapshot(
                Objects.requireNonNull(getOutput().getConsensusRounds().peekLast())
                        .getSnapshot());

        assertTrue(consensusTestNode.intake.getConsensusRounds().isEmpty(), "we should not have reached consensus yet");

        return consensusTestNode;
    }

    public void loadSignedState(@NonNull final SignedState signedState) {
        eventEmitter.reset();
        intake.reset();
        ConsensusUtils.loadEventsIntoGenerator(signedState, eventEmitter.getGraphGenerator(), random);
        intake.loadFromSignedState(signedState);
    }

    /**
     * Adds a number of events from the emitter to the node
     *
     * @param numberOfEvents the number of events to add
     */
    public void addEvents(final long numberOfEvents) {
        for (int i = 0; i < numberOfEvents; i++) {
            intake.addLinkedEvent(eventEmitter.emitEvent());
        }
    }

    /**
     * @return the event emitter that produces events
     */
    public @NonNull EventEmitter<?> getEventEmitter() {
        return eventEmitter;
    }

    /**
     * @return the consensus instance
     */
    public @NonNull Consensus getConsensus() {
        return intake.getConsensus();
    }

    /**
     * @return the output of the consensus instance
     */
    public @NonNull ConsensusOutput getOutput() {
        return intake.getOutput();
    }

    public @NonNull TestIntake getIntake() {
        return intake;
    }

    public @NonNull Random getRandom() {
        return random;
    }
}
