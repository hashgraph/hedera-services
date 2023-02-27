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

package com.swirlds.platform.test.consensus;

import static org.mockito.Mockito.mock;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.time.OSTime;
import com.swirlds.common.time.Time;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.ConsensusImpl;
import com.swirlds.platform.components.EventIntake;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.linking.EventLinker;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.event.linking.ParentFinder;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.state.signed.LoadableFromSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.sync.ShadowGraph;
import com.swirlds.platform.sync.ShadowGraphEventObserver;
import com.swirlds.platform.test.consensus.framework.ConsensusOutput;
import com.swirlds.platform.test.event.IndexedEvent;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.BiConsumer;

/** Event intake with consensus and shadowgraph, used for testing */
public class TestIntake implements LoadableFromSignedState {
    private static final BiConsumer<Long, Long> NOOP_MINGEN = (l1, l2) -> {};

    private final ConsensusImpl consensus;
    private final ShadowGraph shadowGraph;
    private final EventIntake intake;
    private final ConsensusOutput output;
    private int numEventsAdded = 0;

    public TestIntake(final AddressBook ab) {
        this(ab, NOOP_MINGEN);
    }

    public TestIntake(final AddressBook ab, final BiConsumer<Long, Long> minGenConsumer) {
        this(ab, minGenConsumer, OSTime.getInstance());
    }

    public TestIntake(final AddressBook ab, final Time time) {
        this(ab, NOOP_MINGEN, time);
    }

    public TestIntake(final AddressBook ab, final BiConsumer<Long, Long> minGenConsumer, final Time time) {
        this(ab, minGenConsumer, time, ConfigurationHolder.getConfigData(ConsensusConfig.class));
    }

    public TestIntake(final AddressBook ab, final ConsensusConfig consensusConfig) {
        this(ab, NOOP_MINGEN, OSTime.getInstance(), consensusConfig);
    }

    /**
     * @param ab the address book used by this intake
     * @param minGenConsumer the consumer of minimum generations per round
     */
    public TestIntake(
            final AddressBook ab,
            final BiConsumer<Long, Long> minGenConsumer,
            final Time time,
            final ConsensusConfig consensusConfig) {
        output = new ConsensusOutput(time);
        consensus = new ConsensusImpl(consensusConfig, ConsensusUtils.NOOP_CONSENSUS_METRICS, minGenConsumer, ab);
        shadowGraph = new ShadowGraph(mock(SyncMetrics.class));
        final ParentFinder parentFinder = new ParentFinder(shadowGraph::hashgraphEvent);
        final EventLinker linker =
                new InOrderLinker(ConfigurationHolder.getConfigData(ConsensusConfig.class), parentFinder, l -> null);
        final EventObserverDispatcher dispatcher =
                new EventObserverDispatcher(new ShadowGraphEventObserver(shadowGraph), output);
        intake = new EventIntake(
                NodeId.createMain(0), // only used for logging
                linker,
                this::getConsensus,
                ab,
                dispatcher,
                ConsensusUtils.NOOP_INTAKE_CYCLE_STATS,
                shadowGraph);
    }

    /**
     * Link an event to its parents and add it to consensus and shadowgraph
     *
     * @param event the event to add
     */
    public void addEvent(final GossipEvent event) {
        intake.addUnlinkedEvent(event);
        numEventsAdded++;
    }

    /**
     * Same as {@link #addEvent(GossipEvent)}
     *
     * <p>Note: this event won't be the one inserted, intake will create a new instance that will
     * wrap the {@link com.swirlds.common.system.events.BaseEvent}
     */
    public void addEvent(final EventImpl event) {
        intake.addUnlinkedEvent(event.getBaseEvent());
        numEventsAdded++;
    }

    /** Same as {@link #addEvent(GossipEvent)} but for a list of events */
    public void addEvents(final List<IndexedEvent> events) {
        for (final IndexedEvent event : events) {
            addEvent(event.getBaseEvent());
        }
    }

    /** Same as {@link #addEvent(GossipEvent)} but skips the linking and inserts this instance */
    public void addLinkedEvent(final EventImpl event) {
        intake.addEvent(event);
        numEventsAdded++;
    }

    /**
     * @return the consensus used by this intake
     */
    public Consensus getConsensus() {
        return consensus;
    }

    /**
     * @return the shadowgraph used by this intake
     */
    public ShadowGraph getShadowGraph() {
        return shadowGraph;
    }

    /**
     * @return a queue of all events that have been marked as stale
     */
    public Deque<EventImpl> getStaleEvents() {
        return output.getStaleEvents();
    }

    /**
     * @return a queue of all rounds that have reached consensus
     */
    public Deque<ConsensusRound> getConsensusRounds() {
        return output.getConsensusRounds();
    }

    /** prints the number of events in each round that reached consensus */
    public void printRoundSizes() {
        for (final ConsensusRound round : getConsensusRounds()) {
            System.out.printf("%s in round %s%n", round.getNumEvents(), round.getRoundNum());
        }
    }

    @Override
    public void loadFromSignedState(final SignedState signedState) {
        consensus.loadFromSignedState(signedState);
        shadowGraph.clear();
        shadowGraph.initFromEvents(Arrays.asList(signedState.getEvents()), consensus.getMinRoundGeneration());
    }

    public void loadSnapshot(final ConsensusSnapshot snapshot) {
        consensus.loadSnapshot(snapshot);
    }

    public int getNumEventsAdded() {
        return numEventsAdded;
    }

    public ConsensusOutput getOutput() {
        return output;
    }

    public void reset() {
        consensus.reset();
        shadowGraph.clear();
        output.clear();
        numEventsAdded = 0;
    }
}
