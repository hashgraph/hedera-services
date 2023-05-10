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

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.platform.chatter.ChatterSubSetting;
import com.swirlds.platform.test.chatter.network.NoOpSimulatedEventPipeline;
import java.time.Duration;

/**
 * Builds a node for a simulated chatter test.
 *
 * @param <T> the type of event this node gossips
 */
public class NodeBuilder<T extends SimulatedChatterEvent> {

    private NodeId nodeId;
    private int numNodes;
    private FakeTime time;
    private Class<T> eventClass;
    private Duration otherEventDelay;
    private Duration procTimeInterval;
    private SimulatedEventCreator<T> newEventCreator;
    private SimulatedEventPipeline<T> eventPipeline;

    public NodeBuilder<T> nodeId(final NodeId nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    public NodeBuilder<T> numNodes(final int numNodes) {
        this.numNodes = numNodes;
        return this;
    }

    public NodeBuilder<T> time(final FakeTime time) {
        this.time = time;
        return this;
    }

    public NodeBuilder<T> eventClass(final Class<T> eventClass) {
        this.eventClass = eventClass;
        return this;
    }

    public NodeBuilder<T> eventCreator(final SimulatedEventCreator<T> newEventCreator) {
        this.newEventCreator = newEventCreator;
        return this;
    }

    public NodeBuilder<T> eventPipeline(final SimulatedEventPipeline<T> eventPipeline) {
        this.eventPipeline = eventPipeline;
        return this;
    }

    public NodeBuilder<T> otherEventDelay(final Duration otherEventDelay) {
        this.otherEventDelay = otherEventDelay;
        return this;
    }

    public NodeBuilder<T> procTimeInterval(final Duration procTimeInterval) {
        this.procTimeInterval = procTimeInterval;
        return this;
    }

    public Node<T> build() {
        if (newEventCreator == null) {
            throw new IllegalArgumentException("an event creator must be supplied");
        }
        if (eventPipeline == null) {
            eventPipeline = new NoOpSimulatedEventPipeline<>();
        }

        final ChatterSubSetting settings = spy(ChatterSubSetting.class);
        when(settings.getOtherEventDelay()).thenReturn(otherEventDelay);
        when(settings.getProcessingTimeInterval()).thenReturn(procTimeInterval);

        final ChatterInstance<T> chatterInstance =
                new ChatterInstance<>(numNodes, nodeId, eventClass, time, settings, newEventCreator, eventPipeline);

        return new Node<>(nodeId, chatterInstance);
    }
}
