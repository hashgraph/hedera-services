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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.platform.builder.internal.StaticPlatformBuilder.getMetricsProvider;
import static com.swirlds.platform.state.signed.StartupStateUtils.getInitialState;
import static com.swirlds.platform.system.address.AddressBookUtils.createRoster;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.filesystem.FileSystemManager;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.wiring.model.DeterministicWiringModel;
import com.swirlds.common.wiring.model.WiringModelBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.builder.PlatformBuilder;
import com.swirlds.platform.builder.PlatformComponentBuilder;
import com.swirlds.platform.config.BasicConfig_;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.turtle.gossip.SimulatedGossip;
import com.swirlds.platform.test.fixtures.turtle.gossip.SimulatedNetwork;
import com.swirlds.platform.util.RandomBuilder;
import com.swirlds.platform.wiring.PlatformSchedulersConfig_;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Supplier;

/**
 * Encapsulates a single node running in a TURTLE network.
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
public class TurtleNode {

    private final DeterministicWiringModel model;
    private final Platform platform;

    /**
     * Create a new TurtleNode. Simulates a single consensus node in a TURTLE network.
     *
     * @param randotron   a source of randomness
     * @param time        the current time
     * @param nodeId      the ID of this node
     * @param addressBook the address book for the network
     * @param privateKeys the private keys for this node
     * @param network     the simulated network
     */
    TurtleNode(
            @NonNull final Randotron randotron,
            @NonNull final Time time,
            @NonNull final NodeId nodeId,
            @NonNull final AddressBook addressBook,
            @NonNull final KeysAndCerts privateKeys,
            @NonNull final SimulatedNetwork network) {

        final Configuration configuration = new TestConfigBuilder()
                .withValue(PlatformSchedulersConfig_.CONSENSUS_EVENT_STREAM, "NO_OP")
                .withValue(BasicConfig_.JVM_PAUSE_DETECTOR_SLEEP_MS, "0")
                .getOrCreateConfig();

        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withTime(time)
                .withConfiguration(configuration)
                .build();

        model = WiringModelBuilder.create(platformContext)
                .withDeterministicModeEnabled(true)
                .build();
        final Supplier<MerkleRoot> genesisStateSupplier = TurtleTestingToolState::getStateRootNode;
        final var version = new BasicSoftwareVersion(1);

        final NodeId selfId = null;
        final var metrics = getMetricsProvider().createPlatformMetrics(selfId);
        final var fileSystemManager = FileSystemManager.create(configuration);
        final var recycleBin =
                RecycleBin.create(metrics, configuration, getStaticThreadManager(), time, fileSystemManager, selfId);

        final var reservedState = getInitialState(
                configuration,
                recycleBin,
                version,
                genesisStateSupplier,
                "foo",
                "bar",
                nodeId,
                addressBook);
        final var initialState = reservedState.state();
        final PlatformBuilder platformBuilder = PlatformBuilder.create(
                        "foo", "bar", new BasicSoftwareVersion(1), initialState, nodeId)
                .withModel(model)
                .withRandomBuilder(new RandomBuilder(randotron.nextLong()))
                .withAddressBook(addressBook)
                .withRoster(createRoster(addressBook))
                .withKeysAndCerts(privateKeys)
                .withPlatformContext(platformContext)
                .withConfiguration(configuration);

        final PlatformComponentBuilder platformComponentBuilder = platformBuilder.buildComponentBuilder();

        final SimulatedGossip gossip = network.getGossipInstance(nodeId);
        gossip.provideIntakeEventCounter(
                platformComponentBuilder.getBuildingBlocks().intakeEventCounter());

        platformComponentBuilder.withMetricsDocumentationEnabled(false).withGossip(network.getGossipInstance(nodeId));

        platform = platformComponentBuilder.build();
    }

    /**
     * Start this node.
     */
    public void start() {
        platform.start();
    }

    /**
     * Simulate the next time step for this node.
     */
    public void tick() {
        model.tick();
    }
}
