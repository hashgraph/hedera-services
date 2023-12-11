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

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig_;
import com.swirlds.platform.test.chatter.network.NoOpSimulatedEventPipeline;
import com.swirlds.test.framework.config.TestConfigBuilder;

/**
 * Builds a node for a simulated chatter test.
 *
 */
public class NodeBuilder {

    private NodeId nodeId;
    private NetworkSimulatorParams networkParams;
    private SimulatedEventCreator newEventCreator;
    private SimulatedEventPipeline eventPipeline;

    /**
     * Sets the node id for this node
     *
     * @return {@code this}
     */
    public NodeBuilder nodeId(final NodeId nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    /**
     * Sets the network parameters. Several parameters are set at the network level that need to be applied to all
     * nodes.
     *
     * @return {@code this}
     */
    public NodeBuilder networkParams(final NetworkSimulatorParams networkParams) {
        this.networkParams = networkParams;
        return this;
    }

    /**
     * Sets the event creator of this node
     *
     * @return {@code this}
     */
    public NodeBuilder eventCreator(final SimulatedEventCreator newEventCreator) {
        this.newEventCreator = newEventCreator;
        return this;
    }

    /**
     * Sets the event pipeline of this node. Events are sent to this pipeline as they are received from peers
     *
     * @return {@code this}
     */
    public NodeBuilder eventPipeline(final SimulatedEventPipeline eventPipeline) {
        this.eventPipeline = eventPipeline;
        return this;
    }

    /**
     * Builds a new node.
     *
     * @return the new node
     */
    public Node build() {
        if (newEventCreator == null) {
            throw new IllegalArgumentException("an event creator must be supplied");
        }
        if (eventPipeline == null) {
            eventPipeline = new NoOpSimulatedEventPipeline();
        }

        final TestConfigBuilder configBuilder = new TestConfigBuilder()
                .withValue(ChatterConfig_.OTHER_EVENT_DELAY, networkParams.otherEventDelay())
                .withValue(ChatterConfig_.PROCESSING_TIME_INTERVAL, networkParams.procTimeInterval())
                .withValue(ChatterConfig_.HEARTBEAT_INTERVAL, networkParams.heartbeatInterval());

        final ChatterInstance chatterInstance = new ChatterInstance(
                networkParams.numNodes(),
                nodeId,
                networkParams.time(),
                configBuilder.getOrCreateConfig().getConfigData(ChatterConfig.class),
                newEventCreator,
                eventPipeline);

        return new Node(nodeId, chatterInstance);
    }
}
