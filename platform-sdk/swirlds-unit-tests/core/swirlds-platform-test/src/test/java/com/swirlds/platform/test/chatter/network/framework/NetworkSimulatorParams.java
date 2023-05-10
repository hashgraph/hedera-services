/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter.network.framework;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.platform.test.simulated.NetworkLatency;
import com.swirlds.platform.test.simulated.config.NetworkConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Parameters for a network simulation
 *
 * @param time           the time instance to use to advance time during the test
 * @param networkConfigs configuration(s) for the network for a period of time. When a network config time effective
 *                       time elapses, the next network config is applied. Only nodes whose configuration changes need
 *                       to be supplied. The first number of node configurations in the first item defines the size of
 *                       the network.
 * @param networkLatency the network latency model
 * @param simulationTime the amount of time to simulate
 * @param simulationStep the step size of the fake clock
 */
public record NetworkSimulatorParams(
        FakeTime time,
        Duration otherEventDelay,
        Duration procTimeInterval,
        NetworkLatency networkLatency,
        Duration simulationTime,
        Duration simulationStep,
        List<NetworkConfig> networkConfigs) {

    private static final Duration DEFAULT_SIMULATION_STEP = Duration.ofMillis(10);
    private static final Duration DEFAULT_NODE_LATENCY = Duration.ofMillis(50);
    private static final Duration DEFAULT_OTHER_EVENT_DELAY = Duration.ofMillis(20);
    private static final Duration DEFAULT_PROC_TIME_INTERVAL = Duration.ofMillis(10);

    public Set<NodeId> nodeIds() {
        return networkConfigs.get(0).nodeConfigs().keySet();
    }

    public static class NetworkSimulatorParamsBuilder {
        final List<NetworkConfig> networkConfigs;
        FakeTime time;
        Duration otherEventDelay;
        Duration procTimeInterval;
        NetworkLatency networkLatency;
        Duration simulationTime;
        Duration simulationStep;

        public NetworkSimulatorParamsBuilder() {
            this.networkConfigs = new ArrayList<>();
        }

        public NetworkSimulatorParamsBuilder time(final FakeTime time) {
            this.time = time;
            return this;
        }

        public NetworkSimulatorParamsBuilder simulationStep(final Duration simulationStep) {
            this.simulationStep = simulationStep;
            return this;
        }

        public NetworkSimulatorParamsBuilder otherEventDelay(final Duration otherEventDelay) {
            this.otherEventDelay = otherEventDelay;
            return this;
        }

        public NetworkSimulatorParamsBuilder latency(final NetworkLatency networkLatency) {
            this.networkLatency = networkLatency;
            return this;
        }

        public NetworkSimulatorParamsBuilder procTimeInterval(final Duration procTimeInterval) {
            this.procTimeInterval = procTimeInterval;
            return this;
        }

        public NetworkSimulatorParamsBuilder networkConfig(final NetworkConfig networkConfig) {
            this.networkConfigs.add(networkConfig);
            return this;
        }

        public NetworkSimulatorParams build() {
            if (networkConfigs.isEmpty()) {
                throw new IllegalArgumentException("At least one network configuration must be supplied");
            }
            time = time == null ? new FakeTime() : time;
            procTimeInterval = procTimeInterval == null ? DEFAULT_PROC_TIME_INTERVAL : procTimeInterval;
            otherEventDelay = otherEventDelay == null ? DEFAULT_OTHER_EVENT_DELAY : otherEventDelay;
            simulationStep = simulationStep == null ? DEFAULT_SIMULATION_STEP : simulationStep;

            final Set<NodeId> nodeIds = networkConfigs.get(0).nodeConfigs().keySet();
            networkLatency = networkLatency == null
                    ? NetworkLatency.uniformLatency(nodeIds, DEFAULT_NODE_LATENCY)
                    : networkLatency;

            final AtomicLong simulationTimeInMillis = new AtomicLong(0L);
            networkConfigs.forEach(
                    nc -> simulationTimeInMillis.addAndGet(nc.duration().toMillis()));
            simulationTime = Duration.ofMillis(simulationTimeInMillis.get());

            return new NetworkSimulatorParams(
                    time,
                    otherEventDelay,
                    procTimeInterval,
                    networkLatency,
                    simulationTime,
                    simulationStep,
                    networkConfigs);
        }
    }

    public int numNodes() {
        if (networkConfigs == null || networkConfigs.isEmpty()) {
            return 0;
        }
        return networkConfigs.get(0).getNumConfigs();
    }
}
