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

package com.swirlds.platform.test.chatter.simulator;

import static com.swirlds.common.formatting.StringFormattingUtils.commaSeparatedNumber;
import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;
import static com.swirlds.common.utility.Units.MILLISECONDS_TO_SECONDS;
import static com.swirlds.common.utility.Units.NANOSECONDS_TO_SECONDS;
import static com.swirlds.platform.test.chatter.simulator.GossipSimulationUtils.roundDecimal;
import static java.time.temporal.ChronoUnit.NANOS;

import com.swirlds.common.threading.framework.config.ExecutorServiceProfile;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Simulates gossip on a network.
 */
public class GossipSimulation {
    private final boolean singleThreaded;
    private final Duration timeStep;
    private final Duration timeReportPeriod;
    private final SimulatedNetwork network;
    private final EventTracker eventTracker;
    private final List<SimulatedNode> nodes = new ArrayList<>();
    private final Random random;
    private final ExecutorService executor;
    private Instant now;
    private long currentRound;
    private Instant lastTimeReport;
    private double previousSimulationSeconds;

    /**
     * Build a new simulator. Intentionally package private.
     *
     * @param builder
     * 		the builder object
     */
    GossipSimulation(final GossipSimulationBuilder builder) {
        singleThreaded = builder.isSingleThreaded();
        timeStep = Objects.requireNonNull(builder.getTimeStep(), "time step is not optional");
        now = Instant.ofEpochSecond(0);

        timeReportPeriod = builder.getTimeReportPeriod();
        lastTimeReport = Instant.now();

        final int nodeCount = builder.getNodeCount();
        if (nodeCount < 0) {
            throw new IllegalArgumentException("node count must exceed 0");
        }

        network = new SimulatedNetwork(builder);
        eventTracker = new EventTracker(builder);

        Objects.requireNonNull(builder.getChatterFactory(), "chatter factory must be set");

        random = new Random(builder.getSeed());

        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            nodes.add(new SimulatedNode(builder, nodeId, random.nextLong(), network, eventTracker, () -> currentRound));
        }

        if (singleThreaded) {
            executor = null;
        } else {
            executor = getStaticThreadManager()
                    .newExecutorServiceConfiguration("gossip simulation: node")
                    .setProfile(ExecutorServiceProfile.FIXED_THREAD_POOL)
                    .setCorePoolSize(builder.getThreadCount())
                    .build();
        }
    }

    /**
     * Simulate the system for a period of time.
     *
     * @param time
     * 		the length of time to simulate.
     */
    public void simulate(final Duration time) {
        final Instant start = Instant.now();

        final int steps = (int) (time.toNanos() / timeStep.toNanos());
        for (int step = 0; step < steps; step++) {
            simulateOneStep();
        }

        final Instant finish = Instant.now();
        final double secondsSimulated = time.toNanos() * NANOSECONDS_TO_SECONDS;
        final double realtimeSeconds = Duration.between(start, finish).toNanos() * NANOSECONDS_TO_SECONDS;
        final double temporalCompression = secondsSimulated / realtimeSeconds;
        System.out.println("Finished simulating " + commaSeparatedNumber(time.toSeconds())
                + " seconds, temporal compression = " + roundDecimal(temporalCompression, 2));
    }

    /**
     * Simulate the system for a single time step.
     */
    public void simulateOneStep() {
        now = now.plus(timeStep.toNanos(), NANOS);

        reportTime();
        maybeAdvanceRound();
        network.simulateOneStep(random, now);
        simulateNodes();
        purge();
    }

    /**
     * Periodically print the current simulated time to the console.
     */
    private void reportTime() {
        if (timeReportPeriod != null) {
            final Instant realTime = Instant.now();
            final Duration timeSinceLastReport = Duration.between(lastTimeReport, realTime);
            if (timeSinceLastReport.compareTo(timeReportPeriod) > 0) {

                final double simulationSeconds = now.toEpochMilli() * MILLISECONDS_TO_SECONDS;
                final double elapsedSeconds = simulationSeconds - previousSimulationSeconds;
                previousSimulationSeconds = simulationSeconds;
                final double temporalCompression =
                        elapsedSeconds / (timeSinceLastReport.toNanos() * NANOSECONDS_TO_SECONDS);

                System.out.println("Simulation time: " + roundDecimal(simulationSeconds, 2) + " seconds, tc = "
                        + roundDecimal(temporalCompression, 2));
                lastTimeReport = realTime;
            }
        }
    }

    /**
     * Every once in a while advance the current round.
     */
    private void maybeAdvanceRound() {
        currentRound = now.getEpochSecond();
    }

    /**
     * Simulate each node for this time step.
     */
    private void simulateNodes() {
        if (singleThreaded) {
            for (final SimulatedNode node : nodes) {
                node.simulateOneStep(now);
            }
            return;
        }

        final List<Future<?>> futures = new LinkedList<>();

        for (final SimulatedNode node : nodes) {
            futures.add(executor.submit(() -> node.simulateOneStep(now)));
        }

        for (final Future<?> future : futures) {
            try {
                future.get();
            } catch (final ExecutionException e) {
                throw new RuntimeException(e);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Purge all simulation data that is too old.
     */
    private void purge() {
        if (currentRound > 60) {
            final long purgeLessThan = currentRound - 60;
            eventTracker.purge(purgeLessThan);
        }
    }

    /**
     * Must be called when the simulation has been completed.
     */
    public void close() {
        eventTracker.close();
        network.close();
    }
}
