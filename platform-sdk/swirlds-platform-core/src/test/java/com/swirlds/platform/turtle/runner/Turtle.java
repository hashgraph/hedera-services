/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import com.swirlds.platform.test.fixtures.turtle.gossip.SimulatedNetwork;
import com.swirlds.state.merkle.MerkleStateRoot;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs a TURTLE network. All nodes run in this JVM, and if configured properly the execution is expected to be
 * deterministic.
 * <pre>
 *             _________________
 *           /   Testing        \
 *          |    Utility         |
 *          |    Running         |    _ -
 *          |    Totally in a    |=<( o 0 )
 *          |    Local           |   \===/
 *           \   Environment    /
 *            ------------------
 *             / /       | | \ \
 *            """        """ """
 *            _________________
 *           /        gnitseT   \
 *          |         ytilitU    |
 *   - _    |         gninnuR    |
 * ( o O )<=|    a ni yllatoT    |
 *  \===/   |           lacoL    |
 *           \    tnemnorivnE   /
 *            ------------------
 *             / / | |       \ \
 *            """  """        """
 *             _________________
 *           /   Testing        \
 *          |    Utility         |
 *          |    Running         |    _ -
 *          |    Totally in a    |=<( o 0 )
 *          |    Local           |   \===/
 *           \   Environment    /
 *            ------------------
 *             / /       | | \ \
 *            """        """ """
 * </pre>
 */
public class Turtle {

    private final FakeTime time;
    private final Duration simulationGranularity;

    private final ExecutorService threadPool;

    private final SimulatedNetwork network;
    private final ArrayList<TurtleNode> nodes = new ArrayList<>();

    private final boolean timeReportingEnabled;
    private long tickCount;
    private Instant previousRealTime;
    private Instant previousSimulatedTime;

    /**
     * Constructor.
     *
     * @param builder the turtle builder
     */
    Turtle(@NonNull final TurtleBuilder builder) {
        final Randotron randotron = builder.getRandotron();
        simulationGranularity = builder.getSimulationGranularity();
        timeReportingEnabled = builder.isTimeReportingEnabled();

        try {
            ConstructableRegistry.getInstance()
                    .registerConstructable(new ClassConstructorPair(
                            MerkleStateRoot.class,
                            () -> new PlatformMerkleStateRoot(version -> new BasicSoftwareVersion(1))));
        } catch (final ConstructableRegistryException e) {
            throw new RuntimeException(e);
        }

        threadPool = Executors.newFixedThreadPool(
                Math.min(builder.getNodeCount(), Runtime.getRuntime().availableProcessors()));
        time = new FakeTime(randotron.nextInstant(), Duration.ZERO);

        final RandomAddressBookBuilder addressBookBuilder = RandomAddressBookBuilder.create(randotron)
                .withSize(builder.getNodeCount())
                .withRealKeysEnabled(true);
        final AddressBook addressBook = addressBookBuilder.build();

        network = new SimulatedNetwork(randotron, addressBook, Duration.ofMillis(200), Duration.ofMillis(10));

        for (final NodeId nodeId : addressBook.getNodeIdSet().stream().sorted().toList()) {
            nodes.add(new TurtleNode(
                    randotron, time, nodeId, addressBook, addressBookBuilder.getPrivateKeys(nodeId), network));
        }
    }

    /**
     * Start the network. Does not simulate any time after each node is started.
     */
    public void start() {
        for (final TurtleNode node : nodes) {
            node.start();
        }
    }

    /**
     * Simulate the network for a period of time.
     *
     * @param duration the duration to simulate
     */
    public void simulateTime(@NonNull final Duration duration) {
        final Instant simulatedStart = time.now();
        final Instant simulatedEnd = simulatedStart.plus(duration);

        while (time.now().isBefore(simulatedEnd)) {
            reportThePassageOfTime();

            time.tick(simulationGranularity);
            network.tick(time.now());
            tickAllNodes();
        }
    }

    /**
     * Generate a report of the passage of time.
     */
    private void reportThePassageOfTime() {
        if (!timeReportingEnabled) {
            return;
        }
        if (tickCount == 0) {
            previousRealTime = Instant.now();
            previousSimulatedTime = time.now();
        }

        if (tickCount > 0 && tickCount % 1000 == 0) {
            final Instant now = Instant.now();

            final Duration realElapsedTime = Duration.between(previousRealTime, now);
            final Duration simulatedElapsedTime = Duration.between(previousSimulatedTime, time.now());

            final double temporalVelocity;
            if (realElapsedTime.isZero()) {
                temporalVelocity = -1;
            } else {
                temporalVelocity = simulatedElapsedTime.toNanos() / (double) realElapsedTime.toNanos();
            }

            previousSimulatedTime = time.now();
            previousRealTime = now;

            System.out.printf(
                    "tick %d, temporal velocity = %.2f, simulated time = %s%n",
                    tickCount, temporalVelocity, time.now());
        }

        tickCount++;
    }

    /**
     * Call tick() on all nodes in the network. Nodes do not interact with each other during their tick() phase, so it
     * is safe to run them in parallel.
     */
    private void tickAllNodes() {
        final List<Future<Void>> futures = new ArrayList<>();

        // Iteration order over nodes does not need to be deterministic -- nodes are not permitted to communicate with
        // each other during the tick phase, and they run on separate threads to boot.
        for (final TurtleNode node : nodes) {
            final Future<Void> future = threadPool.submit(() -> {
                node.tick();
                return null;
            });
            futures.add(future);
        }

        for (final Future<Void> future : futures) {
            try {
                future.get();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while ticking nodes", e);
            } catch (final ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
