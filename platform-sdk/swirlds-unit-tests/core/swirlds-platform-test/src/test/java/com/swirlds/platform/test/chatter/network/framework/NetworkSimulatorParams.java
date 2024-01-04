/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.test.simulated.NetworkLatency;
import com.swirlds.platform.test.simulated.config.NetworkConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Parameters for a network simulation
 *
 * @param time              the time instance to use to advance time during the test
 * @param otherEventDelay   see {@link com.swirlds.platform.gossip.chatter.ChatterSubSetting#otherEventDelay}
 * @param procTimeInterval  see {@link com.swirlds.platform.gossip.chatter.ChatterSubSetting#processingTimeInterval}
 * @param heartbeatInterval see {@link com.swirlds.platform.gossip.chatter.ChatterSubSetting#heartbeatInterval}
 * @param networkLatency    the network latency model
 * @param simulationTime    the amount of time to simulate
 * @param simulationStep    the step size of the fake clock
 * @param networkConfigs    configuration(s) for the network for a period of time, keyed by the time at which they
 *                          become effective. When a network config time effective time is reached, the next network
 *                          config is applied. Only nodes whose configuration changes need to be supplied. The first
 *                          number of node configurations in the first item defines the size of the network.
 */
public record NetworkSimulatorParams(
        FakeTime time,
        Duration otherEventDelay,
        Duration procTimeInterval,
        Duration heartbeatInterval,
        NetworkLatency networkLatency,
        Duration simulationTime,
        Duration simulationStep,
        TreeMap<Instant, NetworkConfig> networkConfigs,
        Set<NodeId> nodeIds) {

    private static final Duration DEFAULT_SIMULATION_STEP = Duration.ofMillis(10);
    private static final Duration DEFAULT_NODE_LATENCY = Duration.ofMillis(50);
    private static final Duration DEFAULT_OTHER_EVENT_DELAY = Duration.ofMillis(20);
    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofMillis(10);
    private static final Duration DEFAULT_PROC_TIME_INTERVAL = Duration.ofMillis(10);

    /**
     * Returns the number of nodes in the network
     */
    public int numNodes() {
        return nodeIds.size();
    }

    public static class NetworkSimulatorParamsBuilder {
        final List<NetworkConfig> networkConfigs;
        FakeTime time;
        Duration otherEventDelay;
        Duration procTimeInterval;
        Duration heartbeatInterval;
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

        public NetworkSimulatorParamsBuilder heartbeatInterval(final Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
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
            heartbeatInterval = heartbeatInterval == null ? DEFAULT_HEARTBEAT_INTERVAL : heartbeatInterval;
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

            final TreeMap<Instant, NetworkConfig> networkConfigMap = new TreeMap<>();
            Instant nextEffectiveTime = time.now();
            for (final NetworkConfig networkConfig : networkConfigs) {
                networkConfigMap.put(nextEffectiveTime, networkConfig);
                nextEffectiveTime = nextEffectiveTime.plus(networkConfig.duration());
            }

            return new NetworkSimulatorParams(
                    time,
                    otherEventDelay,
                    procTimeInterval,
                    heartbeatInterval,
                    networkLatency,
                    simulationTime,
                    simulationStep,
                    networkConfigMap,
                    networkConfigs.get(0).nodeConfigs().keySet());
        }
    }
}
