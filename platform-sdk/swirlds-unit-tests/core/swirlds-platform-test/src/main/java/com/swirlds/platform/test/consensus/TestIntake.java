/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.common.wiring.wires.SolderType.INJECT;
import static org.mockito.Mockito.mock;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.ConsensusImpl;
import com.swirlds.platform.components.LinkedEventIntake;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.NoOpIntakeEventCounter;
import com.swirlds.platform.gossip.shadowgraph.LatestEventTipsetTracker;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraphEventObserver;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.state.signed.LoadableFromSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.consensus.framework.ConsensusOutput;
import com.swirlds.platform.test.fixtures.event.IndexedEvent;
import com.swirlds.platform.wiring.InOrderLinkerWiring;
import com.swirlds.platform.wiring.LinkedEventIntakeWiring;
import com.swirlds.platform.wiring.PlatformSchedulers;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Deque;
import java.util.List;

/**
 * Event intake with consensus and shadowgraph, used for testing
 */
public class TestIntake implements LoadableFromSignedState {
    private final ConsensusImpl consensus;
    private final ShadowGraph shadowGraph;
    private final ConsensusOutput output;

    private final ConsensusConfig consensusConfig;

    private final InOrderLinkerWiring linkerWiring;
    private final LinkedEventIntakeWiring linkedEventIntakeWiring;

    /**
     * @param addressBook the address book used by this intake
     */
    public TestIntake(@NonNull final AddressBook addressBook, @NonNull final ConsensusConfig consensusConfig) {
        final Time time = Time.getCurrent();
        output = new ConsensusOutput(time);
        this.consensusConfig = consensusConfig;

        // FUTURE WORK: Broaden this test sweet to include testing ancient threshold via birth round.
        consensus = new ConsensusImpl(consensusConfig, ConsensusUtils.NOOP_CONSENSUS_METRICS, addressBook, false);
        shadowGraph =
                new ShadowGraph(Time.getCurrent(), mock(SyncMetrics.class), mock(AddressBook.class), new NodeId(0));

        final NodeId selfId = new NodeId(0);
        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(new TestConfigBuilder().getOrCreateConfig())
                .build();
        final WiringModel model = WiringModel.create(platformContext, time);
        final PlatformSchedulers schedulers = PlatformSchedulers.create(platformContext, model);

        final IntakeEventCounter intakeEventCounter = new NoOpIntakeEventCounter();
        final InOrderLinker linker = new InOrderLinker(platformContext, time, intakeEventCounter);
        linkerWiring = InOrderLinkerWiring.create(schedulers.inOrderLinkerScheduler());
        linkerWiring.bind(linker);

        final EventObserverDispatcher dispatcher =
                new EventObserverDispatcher(new ShadowGraphEventObserver(shadowGraph, null), output);
        final LatestEventTipsetTracker latestEventTipsetTracker =
                new LatestEventTipsetTracker(time, addressBook, selfId);

        final LinkedEventIntake linkedEventIntake = new LinkedEventIntake(
                platformContext,
                time,
                () -> consensus,
                dispatcher,
                shadowGraph,
                latestEventTipsetTracker,
                intakeEventCounter);

        linkedEventIntakeWiring = LinkedEventIntakeWiring.create(schedulers.linkedEventIntakeScheduler());
        linkedEventIntakeWiring.bind(linkedEventIntake);

        linkerWiring.eventOutput().solderTo(linkedEventIntakeWiring.eventInput());
        linkedEventIntakeWiring
                .nonAncientEventWindowOutput()
                .solderTo(linkerWiring.nonAncientEventWindowInput(), INJECT);

        model.start();
    }

    /**
     * Link an event to its parents and add it to consensus and shadowgraph
     *
     * @param event the event to add
     */
    public void addEvent(@NonNull final GossipEvent event) {
        linkerWiring.eventInput().put(event);
    }

    /**
     * Same as {@link #addEvent(GossipEvent)} but for a list of events
     */
    public void addEvents(@NonNull final List<IndexedEvent> events) {
        for (final IndexedEvent event : events) {
            addEvent(event.getBaseEvent());
        }
    }

    /**
     * Same as {@link #addEvent(GossipEvent)} but skips the linking and inserts this instance
     */
    public void addLinkedEvent(@NonNull final EventImpl event) {
        linkedEventIntakeWiring.eventInput().put(event);
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
    }

    public void loadSnapshot(@NonNull final ConsensusSnapshot snapshot) {
        consensus.loadSnapshot(snapshot);

        // FUTURE WORK: remove the fourth variable setting useBirthRound to false when we switch from comparing
        // minGenNonAncient to comparing birthRound to minRoundNonAncient.  Until then, it is always false in
        // production.
        linkerWiring
                .nonAncientEventWindowInput()
                .put(NonAncientEventWindow.createUsingRoundsNonAncient(
                        consensus.getLastRoundDecided(),
                        consensus.getMinGenerationNonAncient(),
                        consensusConfig.roundsNonAncient(),
                        false));

        shadowGraph.clear();
        shadowGraph.startFromGeneration(consensus.getMinGenerationNonAncient());
    }

    public void flush() {
        linkerWiring.flushRunnable().run();
        linkedEventIntakeWiring.flushRunnable().run();
    }

    public @NonNull ConsensusOutput getOutput() {
        return output;
    }

    public void reset() {
        consensus.reset();
        shadowGraph.clear();
        output.clear();
    }
}
