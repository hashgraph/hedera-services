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

import com.swirlds.base.time.Time;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.gossip.chatter.protocol.ChatterCore;
import com.swirlds.platform.gossip.chatter.protocol.PeerMessageException;
import com.swirlds.platform.gossip.chatter.protocol.peer.PeerInstance;
import com.swirlds.platform.test.simulated.GossipMessage;
import com.swirlds.platform.test.simulated.GossipMessageHandler;
import com.swirlds.platform.test.simulated.config.NodeConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * A simulated node that uses chatter logic to determine which messages to send to peers. It does not use any of the
 * chatter protocols, but it does use the chatter peer instances that track which messages should be sent to each peer
 * and when.
 * </p>
 * <p>
 * Once an event is received via gossip, it is passed on to the event pipeline. The event pipeline is a custom set of
 * one or more components that can be used to simulate certain behaviors, such as a full intake queue, in-order orphan
 * buffer, etc. These components can also be used to track data about the events that pass through, such as the number
 * of events received from a peer, or the number of duplicate events received.
 * </p>
 *
 * @param <T> the type of event sent and received by this node
 */
public class ChatterInstance<T extends SimulatedChatterEvent> implements GossipMessageHandler, NodeConfigurable {

    /** This node's id */
    private final NodeId selfId;
    /** The time instances used by the simulation */
    private final Time time;
    /** This node's chatter core instance */
    private final ChatterCore<T> core;
    /** Creator of self events */
    private final SimulatedEventCreator<T> newEventCreator;
    /** List of all peers in the network */
    private final List<NodeId> peerIds = new ArrayList<>();
    /**
     * The first of N event pipeline components chained together in a singly linked list. Each event component passes
     * the event to the next component when it is time to do so. This first component provided events as they are
     * received via {@link #handleMessageFromWire(SelfSerializable, NodeId)}
     */
    private final SimulatedEventPipeline<T> eventPipeline;

    public ChatterInstance(
            final int numNodes,
            final NodeId selfId,
            final Class<T> clazz,
            final Time time,
            final ChatterConfig config,
            final SimulatedEventCreator<T> newEventCreator,
            final SimulatedEventPipeline<T> eventPipeline) {
        this.selfId = selfId;
        this.time = time;
        this.newEventCreator = newEventCreator;
        this.eventPipeline = eventPipeline;

        core = new ChatterCore<>(time, clazz, e -> {}, config, (id, ping) -> {}, new NoOpMetrics());

        for (long peerId = 0; peerId < numNodes; peerId++) {
            // Don't create a peer instance for self
            if (selfId.id() == peerId) {
                continue;
            }

            core.newPeerInstance(new NodeId(peerId), this.eventPipeline::addEvent);
            peerIds.add(new NodeId(peerId));
        }
    }

    /**
     * Returns the first event pipeline component matching the provided class. Useful for making assertions.
     *
     * @param clazz the class of the pipeline component to get
     * @param <R>   the type of pipeline component
     * @return the pipeline component, or {@code null} if none match
     */
    @SuppressWarnings("unchecked")
    public <R extends SimulatedEventPipeline<T>> R getPipelineComponent(final Class<R> clazz) {
        SimulatedEventPipeline<T> pipelineComponent = eventPipeline;
        while (pipelineComponent != null) {
            if (clazz.isAssignableFrom(eventPipeline.getClass())) {
                return (R) pipelineComponent;
            } else {
                pipelineComponent = pipelineComponent.getNext();
            }
        }
        return null;
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

    /**
     * Causes events to be handled by the event pipeline if it is time to do so.
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyNodeConfig(final NodeConfig nodeConfig) {
        newEventCreator.applyNodeConfig(nodeConfig);
        eventPipeline.applyNodeConfigAndCallNext(nodeConfig);
    }

    /**
     * Returns a list of messages to gossip. This list may include more than just events.
     *
     * @return a list of messages to gossip
     */
    public List<GossipMessage> getMessagesToGossip() {
        final List<GossipMessage> gossipMessages = new ArrayList<>();
        for (final NodeId peerId : peerIds) {
            if (peerId == selfId) {
                continue;
            }
            final PeerInstance peer = core.getPeerInstance(peerId);

            SelfSerializable message = peer.outputAggregator().getMessage();
            while (message != null) {
                gossipMessages.add(GossipMessage.toPeer(message, selfId, peerId));
                message = peer.outputAggregator().getMessage();
            }
        }
        return gossipMessages;
    }

    /**
     * The chatter entry point. Messages are received from the gossip simulator and sent here.
     *
     * @param msg      the message received
     * @param fromPeer the peer who sent the message
     */
    @Override
    public void handleMessageFromWire(final SelfSerializable msg, final NodeId fromPeer) {
        try {
            if (msg instanceof final SimulatedChatterEvent event) {
                // Create a copy so that each node sets its own time received
                final SimulatedChatterEvent eventCopy = event.copy();
                eventCopy.setTimeReceived(time.now());
                core.getPeerInstance(fromPeer).inputHandler().handleMessage(eventCopy);
            } else {
                core.getPeerInstance(fromPeer).inputHandler().handleMessage(msg);
            }
        } catch (PeerMessageException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Prints the current state of the chatter instance and event pipeline. Useful for debugging.
     */
    public void printResults() {
        eventPipeline.printCurrentStateAndCallNext();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeId getNodeId() {
        return selfId;
    }
}
