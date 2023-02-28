/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter.network;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.time.Time;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.platform.chatter.ChatterSubSetting;
import com.swirlds.platform.chatter.protocol.ChatterCore;
import com.swirlds.platform.chatter.protocol.PeerMessageException;
import com.swirlds.platform.chatter.protocol.peer.PeerInstance;
import com.swirlds.platform.test.simulated.GossipMessage;
import com.swirlds.platform.test.simulated.GossipMessageHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * A simulated node that uses chatter logic to determine which messages to send to peers. It does not use any of the
 * chatter protocols, but it does use the chatter peer instances that track which messages should be sent to each peer.
 *
 * @param <T> the type of event sent and received by this node
 */
public class ChatterNode<T extends SimulatedChatterEvent> implements GossipMessageHandler {

    private final NodeId selfId;
    private final Time time;
    private final ChatterCore<T> core;
    private final SimulatedEventCreator<T> newEventCreator;
    private final List<Long> peerIds = new ArrayList<>();

    /**
     * The first of N event processors. Each event processor passes the event to the next processor when it is time to
     * do so. This first processor provided events as they are received via
     * {@link #handleMessage(SelfSerializable, long)}
     */
    private final SimulatedEventPipeline<T> eventPipeline;

    public ChatterNode(
            final int numNodes,
            final NodeId selfId,
            final Class<T> clazz,
            final Time time,
            final SimulatedEventCreator<T> newEventCreator,
            final SimulatedEventPipeline<T> eventPipeline) {
        this.selfId = selfId;
        this.time = time;
        this.newEventCreator = newEventCreator;
        this.eventPipeline = eventPipeline;

        final ChatterSubSetting settings = spy(ChatterSubSetting.class);
        when(settings.getOtherEventDelay()).thenReturn(Duration.ZERO);
        when(settings.getProcessingTimeInterval()).thenReturn(Duration.ofMillis(10));

        core = new ChatterCore<>(time, clazz, e -> {}, settings, (id, ping) -> {}, newDefaultMetrics(selfId));

        for (long peerId = 0; peerId < numNodes; peerId++) {
            // Don't create a peer instance for self
            if (selfId.getId() == peerId) {
                continue;
            }

            core.newPeerInstance(peerId, this.eventPipeline::addEvent);
            peerIds.add(peerId);
        }
    }

    /**
     * Sets the communication and sync state for all peer instances so that events are added to the output aggregators
     * instead of being discarded.
     */
    public void enableChatter() {
        core.getPeerInstances().forEach(p -> {
            p.communicationState().chatterSyncStarted();
            p.communicationState().chatterSyncStartingPhase3();
            p.communicationState().chatterSyncSucceeded();
            p.communicationState().chatterStarted();
        });
    }

    public void maybeHandleEvents() {
        eventPipeline.maybeHandleEventsAndCallNext(core);
    }

    /**
     * Maybe create an event (depends on the creation rules and creation rate) and send it to the provided consumers
     */
    public void maybeCreateEvent() {
        final T event = newEventCreator.maybeCreateEvent();
        if (event != null) {
            eventPipeline.addEvent(event);
        }
    }

    public List<GossipMessage> getMessagesToGossip() {
        final List<GossipMessage> gossipMessages = new ArrayList<>();
        for (long peerId : peerIds) {
            if (peerId == selfId.getId()) {
                continue;
            }
            final PeerInstance peer = core.getPeerInstance(peerId);

            SelfSerializable message = peer.outputAggregator().getMessage();
            while (message != null) {
                if (message instanceof final CountingChatterEvent event) {
                    if (event.getCreator() != selfId.getId()) {
                        System.out.println("Sending other event to peer: " + event);
                    }
                }
                gossipMessages.add(GossipMessage.toPeer(message, selfId.getId(), peerId));
                message = peer.outputAggregator().getMessage();
            }
        }
        return gossipMessages;
    }

    @Override
    public void handleMessage(final SelfSerializable msg, final long fromPeer) {
        try {
            if (msg instanceof final SimulatedChatterEvent event) {
                event.setTimeReceived(time.now());
            }
            core.getPeerInstance(fromPeer).inputHandler().handleMessage(msg);
        } catch (PeerMessageException e) {
            throw new RuntimeException(e);
        }
    }

    public void printResults() {
        eventPipeline.printResultsAndCallNext();
    }

    @Override
    public NodeId getNodeId() {
        return selfId;
    }

    private static Metrics newDefaultMetrics(final NodeId selfId) {
        final MetricKeyRegistry registry = mock(MetricKeyRegistry.class);
        when(registry.register(any(), any(), any())).thenReturn(true);
        final Configuration configuration = ConfigurationBuilder.create()
                .withConfigDataType(MetricsConfig.class)
                .build();
        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        return new DefaultMetrics(
                selfId,
                registry,
                Executors.newSingleThreadScheduledExecutor(),
                new DefaultMetricsFactory(),
                metricsConfig);
    }
}
