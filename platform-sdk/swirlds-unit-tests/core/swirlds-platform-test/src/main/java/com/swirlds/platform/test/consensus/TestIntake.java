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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.mockito.Mockito.mock;

import com.swirlds.base.time.Time;
import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.extensions.PhaseTimer;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.ConsensusImpl;
import com.swirlds.platform.components.EventIntake;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.linking.EventLinker;
import com.swirlds.platform.event.linking.OrphanBufferingLinker;
import com.swirlds.platform.event.linking.ParentFinder;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraphEventObserver;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.state.signed.LoadableFromSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.test.consensus.framework.ConsensusOutput;
import com.swirlds.platform.test.fixtures.event.IndexedEvent;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/** Event intake with consensus and shadowgraph, used for testing */
public class TestIntake implements LoadableFromSignedState {
    private final ConsensusImpl consensus;
    private final EventLinker linker;
    private final ShadowGraph shadowGraph;
    private final EventIntake intake;
    private final ConsensusOutput output;
    private int numEventsAdded = 0;

    /**
     * See {@link #TestIntake(AddressBook, Time, ConsensusConfig)}
     */
    public TestIntake(@NonNull final AddressBook ab) {
        this(ab, Time.getCurrent());
    }

    /**
     * See {@link #TestIntake(AddressBook, Time, ConsensusConfig)}
     */
    public TestIntake(@NonNull final AddressBook ab, @NonNull final Time time) {
        this(ab, time, new TestConfigBuilder().getOrCreateConfig().getConfigData(ConsensusConfig.class));
    }

    /**
     * See {@link #TestIntake(AddressBook, Time, ConsensusConfig)}
     */
    public TestIntake(@NonNull final AddressBook ab, @NonNull final ConsensusConfig consensusConfig) {
        this(ab, Time.getCurrent(), consensusConfig);
    }

    /**
     * @param ab the address book used by this intake
     * @param time the time used by this intake
     * @param consensusConfig the consensus config used by this intake
     */
    public TestIntake(
            @NonNull final AddressBook ab, @NonNull final Time time, @NonNull final ConsensusConfig consensusConfig) {
        output = new ConsensusOutput(time);
        consensus = new ConsensusImpl(consensusConfig, ConsensusUtils.NOOP_CONSENSUS_METRICS, ab);
        shadowGraph = new ShadowGraph(mock(SyncMetrics.class));
        final ParentFinder parentFinder = new ParentFinder(shadowGraph::hashgraphEvent);

        linker = new OrphanBufferingLinker(consensusConfig, parentFinder, 100000, mock(IntakeEventCounter.class));

        final EventObserverDispatcher dispatcher =
                new EventObserverDispatcher(new ShadowGraphEventObserver(shadowGraph), output);

        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(new TestConfigBuilder()
                        .withValue("event.asyncPrehandle", false)
                        .getOrCreateConfig())
                .build();

        intake = new EventIntake(
                platformContext,
                getStaticThreadManager(),
                Time.getCurrent(),
                new NodeId(0L), // only used for logging
                linker,
                this::getConsensus,
                ab,
                dispatcher,
                mock(PhaseTimer.class),
                shadowGraph,
                e -> {},
                mock(IntakeEventCounter.class));
    }

    /**
     * Link an event to its parents and add it to consensus and shadowgraph
     *
     * @param event the event to add
     */
    public void addEvent(@NonNull final GossipEvent event) {
        intake.addUnlinkedEvent(event);
        numEventsAdded++;
    }

    /**
     * Same as {@link #addEvent(GossipEvent)}
     *
     * <p>Note: this event won't be the one inserted, intake will create a new instance that will
     * wrap the {@link com.swirlds.common.system.events.BaseEvent}
     */
    public void addEvent(@NonNull final EventImpl event) {
        intake.addUnlinkedEvent(event.getBaseEvent());
        numEventsAdded++;
    }

    /** Same as {@link #addEvent(GossipEvent)} but for a list of events */
    public void addEvents(@NonNull final List<IndexedEvent> events) {
        for (final IndexedEvent event : events) {
            addEvent(event.getBaseEvent());
        }
    }

    /** Same as {@link #addEvent(GossipEvent)} but skips the linking and inserts this instance */
    public void addLinkedEvent(@NonNull final EventImpl event) {
        intake.addEvent(event);
        numEventsAdded++;
    }

    /**
     * @return the consensus used by this intake
     */
    public @NonNull Consensus getConsensus() {
        return consensus;
    }

    /**
     * @return the shadowgraph used by this intake
     */
    public @NonNull ShadowGraph getShadowGraph() {
        return shadowGraph;
    }

    /**
     * @return a queue of all rounds that have reached consensus
     */
    public @NonNull Deque<ConsensusRound> getConsensusRounds() {
        return output.getConsensusRounds();
    }

    public @Nullable ConsensusRound getLatestRound() {
        return output.getConsensusRounds().pollLast();
    }

    @Override
    public void loadFromSignedState(@NonNull final SignedState signedState) {
        consensus.loadFromSignedState(signedState);
        shadowGraph.clear();
        shadowGraph.initFromEvents(Arrays.asList(signedState.getEvents()), consensus.getMinRoundGeneration());
    }

    public void loadSnapshot(@NonNull final ConsensusSnapshot snapshot) {
        consensus.loadSnapshot(snapshot);
        linker.updateGenerations(consensus);
        shadowGraph.clear();
        shadowGraph.startFromGeneration(consensus.getMinGenerationNonAncient());
    }

    public int getNumEventsAdded() {
        return numEventsAdded;
    }

    public @NonNull ConsensusOutput getOutput() {
        return output;
    }

    public void reset() {
        consensus.reset();
        shadowGraph.clear();
        output.clear();
        numEventsAdded = 0;
    }
}
