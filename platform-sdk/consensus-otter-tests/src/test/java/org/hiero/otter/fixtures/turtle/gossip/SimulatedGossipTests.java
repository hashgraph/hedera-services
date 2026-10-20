// SPDX-License-Identifier: Apache-2.0
package org.hiero.otter.fixtures.turtle.gossip;

import static org.hiero.consensus.wiring.framework.schedulers.builders.TaskSchedulerConfiguration.DIRECT_THREADSAFE_CONFIGURATION;
import static org.hiero.otter.fixtures.network.BandwidthLimit.UNLIMITED_BANDWIDTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.base.time.Time;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.assertj.core.data.Percentage;
import org.hiero.consensus.crypto.DefaultEventHasher;
import org.hiero.consensus.fakes.noop.NoOpMetrics;
import org.hiero.consensus.gossip.impl.gossip.sync.protocol.SyncStatusChecker;
import org.hiero.consensus.model.event.EventDescriptorWrapper;
import org.hiero.consensus.model.event.PlatformEvent;
import org.hiero.consensus.model.hashgraph.EventWindow;
import org.hiero.consensus.model.node.NodeId;
import org.hiero.consensus.model.status.PlatformStatus;
import org.hiero.consensus.model.test.fixtures.event.TestingEventBuilder;
import org.hiero.consensus.roster.RosterUtils;
import org.hiero.consensus.roster.test.fixtures.RosterFactory;
import org.hiero.consensus.test.fixtures.Randotron;
import org.hiero.consensus.wiring.framework.model.TraceableWiringModel;
import org.hiero.consensus.wiring.framework.model.WiringModel;
import org.hiero.consensus.wiring.framework.model.WiringModelBuilder;
import org.hiero.consensus.wiring.framework.schedulers.ExceptionHandlers;
import org.hiero.consensus.wiring.framework.schedulers.TaskScheduler;
import org.hiero.consensus.wiring.framework.wires.input.BindableInputWire;
import org.hiero.consensus.wiring.framework.wires.output.StandardOutputWire;
import org.hiero.otter.fixtures.internal.network.ConnectionKey;
import org.hiero.otter.fixtures.network.Topology.ConnectionState;
import org.hiero.otter.fixtures.network.simulation.SimulatedNetworkConnectivity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class SimulatedGossipTests {

    /**
     * Given a list of events that may contain duplicates, create a set of unique event descriptors.
     *
     * @param events the list of events
     * @return the set of unique event descriptors
     */
    private static Set<EventDescriptorWrapper> getUniqueDescriptors(@NonNull final List<PlatformEvent> events) {
        final HashSet<EventDescriptorWrapper> uniqueDescriptors = new HashSet<>();
        for (final PlatformEvent event : events) {
            uniqueDescriptors.add(event.getDescriptor());
        }
        return uniqueDescriptors;
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16, 32})
    void randomDataTest(final int networkSize) {
        final Randotron randotron = Randotron.create();

        final FakeTime time = new FakeTime();

        final Roster roster = RosterFactory.randomRoster(randotron, networkSize);
        final List<NodeId> nodeIds = roster.rosterEntries().stream()
                .map(RosterUtils::getNodeId)
                .sorted()
                .toList();

        final SimulatedNetworkConnectivity network = new SimulatedNetworkConnectivity(randotron);

        // We can safely choose large numbers because time is simulated
        final Duration averageDelay = Duration.ofMillis(randotron.nextInt(1, 1_000_000));
        final ConnectionState connectionState =
                new ConnectionState(true, averageDelay, Percentage.withPercentage(10.0), UNLIMITED_BANDWIDTH);
        final Map<ConnectionKey, ConnectionState> connections = new HashMap<>();
        for (final NodeId sender : nodeIds) {
            for (final NodeId receiver : nodeIds) {
                if (!sender.equals(receiver)) {
                    final NodeId fromNode = NodeId.of(sender.id());
                    final NodeId toNode = NodeId.of(receiver.id());
                    connections.put(new ConnectionKey(fromNode, toNode), connectionState);
                }
            }
        }
        network.setConnections(connections);

        // Each node will add received events to the appropriate list
        final Map<NodeId, List<PlatformEvent>> receivedEvents = new HashMap<>();

        // Passing an event to one of these consumers causes the node to gossip the event to the network
        final Map<NodeId, Consumer<PlatformEvent>> eventSubmitters = new HashMap<>();

        // Wire things up
        for (final NodeId nodeId : nodeIds) {
            final WiringModel model = WiringModelBuilder.create(new NoOpMetrics(), Time.getCurrent())
                    .deterministic()
                    .build();

            final TaskScheduler<Void> eventInputShim = model.<Void>schedulerBuilder("eventInputShim")
                    .configure(DIRECT_THREADSAFE_CONFIGURATION)
                    .build();

            final List<PlatformEvent> receivedEventsForNode = new ArrayList<>();
            receivedEvents.put(nodeId, receivedEventsForNode);
            final StandardOutputWire<PlatformEvent> eventOutputWire = new StandardOutputWire<>(
                    (TraceableWiringModel) model, "eventOutputWire", ExceptionHandlers.RETHROW_UNCAUGHT_EXCEPTION);
            eventOutputWire.solderTo("handleOutputEvent", "event", receivedEventsForNode::add);

            final BindableInputWire<PlatformEvent, Void> eventInputWire =
                    eventInputShim.buildInputWire("eventInputWire");
            eventSubmitters.put(nodeId, eventInputWire::inject);

            final SimulatedGossip gossip = new SimulatedGossip(network, nodeId);
            final BindableInputWire<PlatformStatus, Void> statusInput = mock(BindableInputWire.class);
            final ArgumentCaptor<Consumer<PlatformStatus>> statusCaptor = ArgumentCaptor.forClass(Consumer.class);
            doNothing().when(statusInput).bindConsumer(statusCaptor.capture());
            gossip.bind(
                    model,
                    eventInputWire,
                    mock(BindableInputWire.class),
                    eventOutputWire,
                    mock(BindableInputWire.class),
                    mock(BindableInputWire.class),
                    mock(BindableInputWire.class),
                    mock(BindableInputWire.class),
                    mock(BindableInputWire.class),
                    mock(BindableInputWire.class),
                    statusInput,
                    mock(StandardOutputWire.class));
            statusCaptor.getValue().accept(PlatformStatus.ACTIVE);
            network.addNode(nodeId, gossip);
            model.start();
        }

        // For each event, choose a random subset of nodes that will submit the event. Our end goal is to see
        // each event distributed at least once to each node in the network.
        final int eventCount = networkSize * 100;
        final List<PlatformEvent> eventsToGossip = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            final NodeId creator = nodeIds.get(randotron.nextInt(networkSize));
            final PlatformEvent event =
                    new TestingEventBuilder(randotron).setCreatorId(creator).build();
            new DefaultEventHasher().hashEvent(event);

            eventsToGossip.add(event);
        }

        // Gossip all of the events. Each event is guaranteed to be gossiped by at least one node.
        for (int eventIndex = 0; eventIndex < eventCount; eventIndex++) {
            final PlatformEvent event = eventsToGossip.get(eventIndex);

            for (final NodeId nodeId : nodeIds) {
                if (event.getCreatorId().equals(nodeId) || randotron.nextBoolean(0.1)) {
                    eventSubmitters.get(nodeId).accept(event);

                    // When a node sends out an event, add it to the list of events that node knows about.
                    receivedEvents.get(nodeId).add(event);
                }
            }

            if (randotron.nextBoolean(0.1)) {
                time.tick(averageDelay.dividedBy(10));
                network.tick(time.now());
            }
        }

        // Move time forward enough to ensure that all events have been gossiped around the network.
        // Ticking twice ensures everything is properly flushed out of the system.
        network.tick(time.now());
        time.tick(averageDelay.multipliedBy(100000));
        network.tick(time.now());

        // Verify that all nodes received all events.
        final Set<EventDescriptorWrapper> expectedDescriptors = getUniqueDescriptors(eventsToGossip);
        for (final NodeId nodeId : nodeIds) {
            final Set<EventDescriptorWrapper> uniqueDescriptors = getUniqueDescriptors(receivedEvents.get(nodeId));
            assertEquals(expectedDescriptors, uniqueDescriptors);
        }
    }

    /**
     * Receiving must be refused in exactly the statuses that don't permit syncing, while submissions to the network
     * are unaffected by the status.
     */
    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @EnumSource(PlatformStatus.class)
    void statusGatesReceivingButNotSubmitting(final PlatformStatus status) {
        final Randotron randotron = Randotron.create();
        final FakeTime time = new FakeTime();
        final NodeId selfId = NodeId.of(0);
        final NodeId peerId = NodeId.of(1);

        final SimulatedNetworkConnectivity network = new SimulatedNetworkConnectivity(randotron);
        final ConnectionState connectionState =
                new ConnectionState(true, Duration.ofMillis(1), Percentage.withPercentage(0.0), UNLIMITED_BANDWIDTH);
        network.setConnections(Map.of(
                new ConnectionKey(selfId, peerId), connectionState,
                new ConnectionKey(peerId, selfId), connectionState));

        final List<PlatformEvent> selfReceived = new ArrayList<>();
        final List<PlatformEvent> peerReceived = new ArrayList<>();
        final Consumer<PlatformEvent> selfSubmitter = addNode(network, selfId, status, selfReceived);
        final Consumer<PlatformEvent> peerSubmitter = addNode(network, peerId, PlatformStatus.ACTIVE, peerReceived);

        // submissions are unaffected by the status of the submitter
        final PlatformEvent submitted =
                new TestingEventBuilder(randotron).setCreatorId(selfId).build();
        new DefaultEventHasher().hashEvent(submitted);
        selfSubmitter.accept(submitted);

        // an event from the peer is only delivered if the status permits syncing
        final PlatformEvent fromPeer =
                new TestingEventBuilder(randotron).setCreatorId(peerId).build();
        new DefaultEventHasher().hashEvent(fromPeer);
        peerSubmitter.accept(fromPeer);

        network.tick(time.now());
        time.tick(Duration.ofSeconds(1));
        network.tick(time.now());

        final boolean permitted = SyncStatusChecker.doesStatusPermitSync(status);
        assertEquals(1, peerReceived.size());
        assertEquals(permitted ? 1 : 0, selfReceived.size());

        final SimulatedGossip self = gossipsByNode.get(selfId);
        assertEquals(permitted, self.receiveEvent(fromPeer));
        assertEquals(permitted, self.receiveEventWindow(peerId, EventWindow.getGenesisEventWindow()));
        if (permitted) {
            assertTrue(selfReceived.contains(fromPeer));
        } else {
            assertFalse(selfReceived.contains(fromPeer));
        }
    }

    private final Map<NodeId, SimulatedGossip> gossipsByNode = new HashMap<>();

    @SuppressWarnings("unchecked")
    private Consumer<PlatformEvent> addNode(
            @NonNull final SimulatedNetworkConnectivity network,
            @NonNull final NodeId nodeId,
            @NonNull final PlatformStatus status,
            @NonNull final List<PlatformEvent> received) {
        final WiringModel model = WiringModelBuilder.create(new NoOpMetrics(), Time.getCurrent())
                .deterministic()
                .build();
        final TaskScheduler<Void> shim = model.<Void>schedulerBuilder("eventInputShim")
                .configure(DIRECT_THREADSAFE_CONFIGURATION)
                .build();
        final StandardOutputWire<PlatformEvent> output = new StandardOutputWire<>(
                (TraceableWiringModel) model, "eventOutputWire", ExceptionHandlers.RETHROW_UNCAUGHT_EXCEPTION);
        output.solderTo("handleOutputEvent", "event", received::add);
        final BindableInputWire<PlatformEvent, Void> eventInput = shim.buildInputWire("eventInputWire");

        final BindableInputWire<PlatformStatus, Void> statusInput = mock(BindableInputWire.class);
        final ArgumentCaptor<Consumer<PlatformStatus>> statusCaptor = ArgumentCaptor.forClass(Consumer.class);
        doNothing().when(statusInput).bindConsumer(statusCaptor.capture());

        final SimulatedGossip gossip = new SimulatedGossip(network, nodeId);
        gossip.bind(
                model,
                eventInput,
                mock(BindableInputWire.class),
                output,
                mock(BindableInputWire.class),
                mock(BindableInputWire.class),
                mock(BindableInputWire.class),
                mock(BindableInputWire.class),
                mock(BindableInputWire.class),
                mock(BindableInputWire.class),
                statusInput,
                mock(StandardOutputWire.class));
        statusCaptor.getValue().accept(status);
        network.addNode(nodeId, gossip);
        gossipsByNode.put(nodeId, gossip);
        model.start();
        return eventInput::inject;
    }
}
