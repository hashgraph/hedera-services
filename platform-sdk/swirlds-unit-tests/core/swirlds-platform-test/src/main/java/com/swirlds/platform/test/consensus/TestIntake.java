/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

import static com.swirlds.component.framework.wires.SolderType.INJECT;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.component.framework.component.ComponentWiring;
import com.swirlds.component.framework.model.WiringModel;
import com.swirlds.component.framework.model.WiringModelBuilder;
import com.swirlds.component.framework.schedulers.TaskScheduler;
import com.swirlds.component.framework.schedulers.builders.TaskSchedulerType;
import com.swirlds.component.framework.wires.output.OutputWire;
import com.swirlds.platform.components.DefaultEventWindowManager;
import com.swirlds.platform.components.EventWindowManager;
import com.swirlds.platform.components.consensus.ConsensusEngine;
import com.swirlds.platform.components.consensus.DefaultConsensusEngine;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.consensus.SyntheticSnapshot;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.event.hashing.DefaultEventHasher;
import com.swirlds.platform.event.hashing.EventHasher;
import com.swirlds.platform.event.orphan.DefaultOrphanBuffer;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.NoOpIntakeEventCounter;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.roster.RosterRetriever;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.consensus.framework.ConsensusOutput;
import com.swirlds.platform.wiring.components.PassThroughWiring;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.LinkedList;
import java.util.List;

/**
 * Event intake with consensus and shadowgraph, used for testing
 */
public class TestIntake {
    private final ConsensusOutput output;

    private final ComponentWiring<EventHasher, PlatformEvent> hasherWiring;
    private final ComponentWiring<OrphanBuffer, List<PlatformEvent>> orphanBufferWiring;
    private final ComponentWiring<ConsensusEngine, List<ConsensusRound>> consensusEngineWiring;
    private final WiringModel model;
    private final int roundsNonAncient;
    private final AncientMode ancientMode;

    /**
     * @param platformContext the platform context used to configure this intake.
     * @param addressBook     the address book used by this intake
     */
    public TestIntake(@NonNull final PlatformContext platformContext, @NonNull final AddressBook addressBook) {
        final NodeId selfId = NodeId.of(0);
        roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();
        ancientMode = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode();

        output = new ConsensusOutput(ancientMode);

        model = WiringModelBuilder.create(platformContext).build();

        hasherWiring = new ComponentWiring<>(model, EventHasher.class, directScheduler("eventHasher"));
        final EventHasher eventHasher = new DefaultEventHasher();
        hasherWiring.bind(eventHasher);

        final PassThroughWiring<PlatformEvent> postHashCollectorWiring =
                new PassThroughWiring(model, "PlatformEvent", "postHashCollector", TaskSchedulerType.DIRECT);

        final IntakeEventCounter intakeEventCounter = new NoOpIntakeEventCounter();
        final OrphanBuffer orphanBuffer = new DefaultOrphanBuffer(platformContext, intakeEventCounter);
        orphanBufferWiring = new ComponentWiring<>(model, OrphanBuffer.class, directScheduler("orphanBuffer"));
        orphanBufferWiring.bind(orphanBuffer);

        final ConsensusEngine consensusEngine =
                new DefaultConsensusEngine(platformContext, RosterRetriever.buildRoster(addressBook), selfId);

        consensusEngineWiring = new ComponentWiring<>(model, ConsensusEngine.class, directScheduler("consensusEngine"));
        consensusEngineWiring.bind(consensusEngine);

        final ComponentWiring<EventWindowManager, EventWindow> eventWindowManagerWiring =
                new ComponentWiring<>(model, EventWindowManager.class, directScheduler("eventWindowManager"));
        eventWindowManagerWiring.bind(new DefaultEventWindowManager());

        hasherWiring.getOutputWire().solderTo(postHashCollectorWiring.getInputWire());
        postHashCollectorWiring.getOutputWire().solderTo(orphanBufferWiring.getInputWire(OrphanBuffer::handleEvent));
        final OutputWire<PlatformEvent> splitOutput = orphanBufferWiring.getSplitOutput();
        splitOutput.solderTo(consensusEngineWiring.getInputWire(ConsensusEngine::addEvent));

        final OutputWire<ConsensusRound> consensusRoundOutputWire = consensusEngineWiring.getSplitOutput();
        consensusRoundOutputWire.solderTo(
                eventWindowManagerWiring.getInputWire(EventWindowManager::extractEventWindow));
        consensusRoundOutputWire.solderTo("consensusOutputTestTool", "round output", output::consensusRound);

        eventWindowManagerWiring
                .getOutputWire()
                .solderTo(orphanBufferWiring.getInputWire(OrphanBuffer::setEventWindow), INJECT);

        // Ensure unsoldered wires are created.
        hasherWiring.getInputWire(EventHasher::hashEvent);

        // Make sure this unsoldered wire is properly built
        consensusEngineWiring.getInputWire(ConsensusEngine::outOfBandSnapshotUpdate);

        model.start();
    }

    /**
     * Link an event to its parents and add it to consensus and shadowgraph
     *
     * @param event the event to add
     */
    public void addEvent(@NonNull final PlatformEvent event) {
        hasherWiring.getInputWire(EventHasher::hashEvent).put(event);
        output.eventAdded(event);
    }

    /**
     * Same as {@link #addEvent(PlatformEvent)} but for a list of events
     */
    public void addEvents(@NonNull final List<EventImpl> events) {
        for (final EventImpl event : events) {
            addEvent(event.getBaseEvent());
        }
    }

    /**
     * @return a queue of all rounds that have reached consensus
     */
    public @NonNull LinkedList<ConsensusRound> getConsensusRounds() {
        return output.getConsensusRounds();
    }

    public @Nullable ConsensusRound getLatestRound() {
        return output.getConsensusRounds().getLast();
    }

    public void loadSnapshot(@NonNull final ConsensusSnapshot snapshot) {

        // FUTURE WORK: remove the fourth variable setting useBirthRound to false when we switch from comparing
        // minGenNonAncient to comparing birthRound to minRoundNonAncient.  Until then, it is always false in
        // production.

        final EventWindow eventWindow = new EventWindow(
                snapshot.round(),
                snapshot.getAncientThreshold(roundsNonAncient),
                snapshot.getAncientThreshold(roundsNonAncient),
                ancientMode);

        orphanBufferWiring.getInputWire(OrphanBuffer::setEventWindow).put(eventWindow);
        consensusEngineWiring
                .getInputWire(ConsensusEngine::outOfBandSnapshotUpdate)
                .put(snapshot);
    }

    public @NonNull ConsensusOutput getOutput() {
        return output;
    }

    public void reset() {
        loadSnapshot(SyntheticSnapshot.getGenesisSnapshot(ancientMode));
        output.clear();
    }

    public <X> TaskScheduler<X> directScheduler(final String name) {
        return model.<X>schedulerBuilder(name)
                .withType(TaskSchedulerType.DIRECT)
                .withUncaughtExceptionHandler((t, e) -> {
                    throw new RuntimeException("Uncaught exception in task " + t, e);
                })
                .build();
    }
}
