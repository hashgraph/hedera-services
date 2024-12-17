/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.turtle.gossip;

import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerConfiguration.DIRECT_THREADSAFE_CONFIGURATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.wiring.model.TraceableWiringModel;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.model.WiringModelBuilder;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.output.StandardOutputWire;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.event.hashing.DefaultEventHasher;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.EventDescriptorWrapper;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import com.swirlds.platform.test.fixtures.turtle.gossip.SimulatedNetwork;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        StaticSoftwareVersion.setSoftwareVersion(new BasicSoftwareVersion(1));
        ConstructableRegistry.getInstance().registerConstructables("");
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16, 32})
    void randomDataTest(final int networkSize) {
        final Randotron randotron = Randotron.create();

        final FakeTime time = new FakeTime();
        final PlatformContext context =
                TestPlatformContextBuilder.create().withTime(time).build();

        final AddressBook addressBook =
                RandomAddressBookBuilder.create(randotron).withSize(networkSize).build();

        // We can safely choose large numbers because time is simulated
        final Duration averageDelay = Duration.ofMillis(randotron.nextInt(1, 1_000_000));
        final Duration standardDeviationDelay = Duration.ofMillis((long) (averageDelay.toMillis() * 0.1));

        final SimulatedNetwork network =
                new SimulatedNetwork(randotron, addressBook, averageDelay, standardDeviationDelay);

        // Each node will add received events to the appropriate list
        final Map<NodeId, List<PlatformEvent>> receivedEvents = new HashMap<>();

        // Passing an event to one of these consumers causes the node to gossip the event to the network
        final Map<NodeId, Consumer<PlatformEvent>> eventSubmitters = new HashMap<>();

        // Wire things up
        for (final NodeId nodeId : addressBook.getNodeIdSet()) {
            final WiringModel model = WiringModelBuilder.create(context)
                    .withDeterministicModeEnabled(true)
                    .build();

            final TaskScheduler<Void> eventInputShim = model.schedulerBuilder("eventInputShim")
                    .configure(DIRECT_THREADSAFE_CONFIGURATION)
                    .build()
                    .cast();

            final List<PlatformEvent> receivedEventsForNode = new ArrayList<>();
            receivedEvents.put(nodeId, receivedEventsForNode);
            final StandardOutputWire<PlatformEvent> eventOutputWire =
                    new StandardOutputWire<>((TraceableWiringModel) model, "eventOutputWire");
            eventOutputWire.solderTo("handleOutputEvent", "event", receivedEventsForNode::add);

            final BindableInputWire<PlatformEvent, Void> eventInputWire =
                    eventInputShim.buildInputWire("eventInputWire");
            eventSubmitters.put(nodeId, eventInputWire::inject);

            network.getGossipInstance(nodeId)
                    .bind(
                            model,
                            eventInputWire,
                            mock(BindableInputWire.class),
                            eventOutputWire,
                            mock(BindableInputWire.class),
                            mock(BindableInputWire.class),
                            mock(BindableInputWire.class),
                            mock(BindableInputWire.class),
                            mock(BindableInputWire.class));
        }

        // For each event, choose a random subset of nodes that will submit the event. Our end goal is to see
        // each event distributed at least once to each node in the network.
        final int eventCount = networkSize * 100;
        final List<PlatformEvent> eventsToGossip = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            final NodeId creator = addressBook.getNodeId(randotron.nextInt(networkSize));
            final PlatformEvent event =
                    new TestingEventBuilder(randotron).setCreatorId(creator).build();
            new DefaultEventHasher().hashEvent(event);

            eventsToGossip.add(event);
        }

        // Gossip all of the events. Each event is guaranteed to be gossiped by at least one node.
        for (int eventIndex = 0; eventIndex < eventCount; eventIndex++) {
            final PlatformEvent event = eventsToGossip.get(eventIndex);

            for (final NodeId nodeId : addressBook.getNodeIdSet()) {
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
        for (final NodeId nodeId : addressBook.getNodeIdSet()) {
            final Set<EventDescriptorWrapper> uniqueDescriptors = getUniqueDescriptors(receivedEvents.get(nodeId));
            assertEquals(expectedDescriptors, uniqueDescriptors);
        }
    }
}
