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

import com.swirlds.common.platform.NodeId;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig_;
import com.swirlds.platform.test.chatter.network.NoOpSimulatedEventPipeline;

/**
 * Builds a node for a simulated chatter test.
 *
 * @param <T> the type of event this node gossips
 */
public class NodeBuilder<T extends SimulatedChatterEvent> {

    private NodeId nodeId;
    private NetworkSimulatorParams networkParams;
    private Class<T> eventClass;
    private SimulatedEventCreator<T> newEventCreator;
    private SimulatedEventPipeline<T> eventPipeline;

    /**
     * Sets the node id for this node
     *
     * @return {@code this}
     */
    public NodeBuilder<T> nodeId(final NodeId nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    /**
     * Sets the network parameters. Several parameters are set at the network level that need to be applied to all
     * nodes.
     *
     * @return {@code this}
     */
    public NodeBuilder<T> networkParams(final NetworkSimulatorParams networkParams) {
        this.networkParams = networkParams;
        return this;
    }

    /**
     * Sets the class of events gossiped by this network
     *
     * @return {@code this}
     */
    public NodeBuilder<T> eventClass(final Class<T> eventClass) {
        this.eventClass = eventClass;
        return this;
    }

    /**
     * Sets the event creator of this node
     *
     * @return {@code this}
     */
    public NodeBuilder<T> eventCreator(final SimulatedEventCreator<T> newEventCreator) {
        this.newEventCreator = newEventCreator;
        return this;
    }

    /**
     * Sets the event pipeline of this node. Events are sent to this pipeline as they are received from peers
     *
     * @return {@code this}
     */
    public NodeBuilder<T> eventPipeline(final SimulatedEventPipeline<T> eventPipeline) {
        this.eventPipeline = eventPipeline;
        return this;
    }

    /**
     * Builds a new node.
     *
     * @return the new node
     */
    public Node<T> build() {
        if (newEventCreator == null) {
            throw new IllegalArgumentException("an event creator must be supplied");
        }
        if (eventPipeline == null) {
            eventPipeline = new NoOpSimulatedEventPipeline<>();
        }

        final TestConfigBuilder configBuilder = new TestConfigBuilder()
                .withValue(ChatterConfig_.OTHER_EVENT_DELAY, networkParams.otherEventDelay())
                .withValue(ChatterConfig_.PROCESSING_TIME_INTERVAL, networkParams.procTimeInterval())
                .withValue(ChatterConfig_.HEARTBEAT_INTERVAL, networkParams.heartbeatInterval());

        final ChatterInstance<T> chatterInstance = new ChatterInstance<>(
                networkParams.numNodes(),
                nodeId,
                eventClass,
                networkParams.time(),
                configBuilder.getOrCreateConfig().getConfigData(ChatterConfig.class),
                newEventCreator,
                eventPipeline);

        return new Node<>(nodeId, chatterInstance);
    }
}
