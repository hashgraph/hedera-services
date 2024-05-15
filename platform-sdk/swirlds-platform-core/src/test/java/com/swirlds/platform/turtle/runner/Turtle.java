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

package com.swirlds.platform.turtle.runner;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.wiring.model.DeterministicWiringModel;
import com.swirlds.common.wiring.model.WiringModelBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.builder.PlatformBuilder;
import com.swirlds.platform.builder.PlatformComponentBuilder;
import com.swirlds.platform.eventhandling.EventConfig_;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import com.swirlds.platform.test.fixtures.turtle.gossip.SimulatedGossip;
import com.swirlds.platform.test.fixtures.turtle.gossip.SimulatedNetwork;
import com.swirlds.platform.wiring.PlatformSchedulersConfig_;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a TURTLE network. All nodes run in this JVM, and if configured properly the execution is expected to be
 * deterministic.
 * <pre>
 *    _________________
 *  /   Testing        \
 * |    Utility         |
 * |    Running         |    _ -
 * |    Totally in a    |=<( o 0 )
 * |    Local           |   \===/
 *  \   Environment    /
 *   ------------------
 *   / /       | | \ \
 *  """        """ """
 * </pre>
 */
public class Turtle {

    // TODO we need a builder

    private final Randotron randotron;
    private final FakeTime time;

    /**
     * Constructor.
     *
     * @param nodeCount the number of nodes to run
     * @param timeStep  the time step to use
     */
    public Turtle(final int nodeCount, @NonNull final Duration timeStep) {
        randotron = Randotron.create();

        try {
            ConstructableRegistry.getInstance().registerConstructables("");
        } catch (final ConstructableRegistryException e) {
            throw new RuntimeException(e);
        }

        final Configuration configuration = new TestConfigBuilder()
                .withValue(EventConfig_.USE_OLD_STYLE_INTAKE_QUEUE, false)
                .withValue(PlatformSchedulersConfig_.CONSENSUS_EVENT_STREAM, "NO_OP")
                .withValue(PlatformSchedulersConfig_.RUNNING_EVENT_HASHER, "NO_OP")
                .getOrCreateConfig();

        time = new FakeTime(randotron.nextInstant(), Duration.ZERO);
        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withTime(time)
                .withConfiguration(configuration)
                .build();

        final RandomAddressBookBuilder addressBookBuilder =
                RandomAddressBookBuilder.create(randotron).withSize(nodeCount).withRealKeysEnabled(true);
        final AddressBook addressBook = addressBookBuilder.build();

        final SimulatedNetwork network =
                new SimulatedNetwork(randotron, addressBook, Duration.ofMillis(200), Duration.ofMillis(10));

        final List<Platform> platforms = new ArrayList<>(nodeCount);
        final List<DeterministicWiringModel> models = new ArrayList<>(nodeCount);

        for (final NodeId nodeId : addressBook.getNodeIdSet()) {

            final DeterministicWiringModel model = WiringModelBuilder.create(platformContext)
                    .withDeterministicModeEnabled(true)
                    .build();
            models.add(model);

            final PlatformBuilder platformBuilder = PlatformBuilder.create(
                            "foo", "bar", new BasicSoftwareVersion(1), TurtleTestingToolState::new, nodeId)
                    .withModel(model)
                    .withPlatformContext(platformContext)
                    .withBootstrapAddressBook(addressBook)
                    .withKeysAndCerts(addressBookBuilder.getPrivateKeys(nodeId));

            final PlatformComponentBuilder platformComponentBuilder = platformBuilder.buildComponentBuilder();

            final SimulatedGossip gossip = network.getGossipInstance(nodeId);
            gossip.provideIntakeEventCounter(
                    platformComponentBuilder.getBuildingBlocks().intakeEventCounter());

            platformComponentBuilder.withGossip(network.getGossipInstance(nodeId));

            platforms.add(platformComponentBuilder.build());
        }

        for (final Platform platform : platforms) {
            platform.start();
        }

        long tickCount = 0;
        while (true) {
            if (tickCount % 1_000_000 == 0) {
                System.out.println("tick " + tickCount + ": " + time.now());
            }
            tickCount++;

            time.tick(timeStep);
            network.tick(time.now());
            for (final DeterministicWiringModel model : models) {
                model.tick();
            }
        }
    }
}
