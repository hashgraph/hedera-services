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

package com.swirlds.platform.gossip.chatter.protocol.peer;

import com.swirlds.common.sequence.Shiftable;
import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.sequence.map.StandardSequenceMap;
import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.gossip.chatter.protocol.messages.ChatterEvent;

/**
 * Keeps track of the state of chatter communication with a peer, including events we are sure the peer knows
 */
public class PeerGossipState implements Shiftable {
    /** non-ancient events we know the peer knows */
    private final SequenceMap<EventDescriptor, Void> events;
    /** the maximum generation of all event descriptors received */
    private long maxReceivedDescriptorGeneration;

    /**
     * Create a new object for tracking communication with a peer.
     *
     * @param futureGenerationLimit the maximum number of generations in the future we are willing to accept from the
     *                              peer
     */
    public PeerGossipState(final int futureGenerationLimit) {
        events = new StandardSequenceMap<>(
                GraphGenerations.FIRST_GENERATION, futureGenerationLimit, EventDescriptor::getGeneration);
        maxReceivedDescriptorGeneration = GraphGenerations.FIRST_GENERATION;
    }

    /**
     * Mark an event represented by this descriptor as known by the peer
     *
     * @param event the descriptor of the event the peer knows
     */
    public synchronized void setPeerKnows(final EventDescriptor event) {
        events.put(event, null);
    }

    /**
     * Query the state about the knowledge of this event
     *
     * @param event the descriptor of the event being queried
     * @return true if the peer knows this event, false otherwise
     */
    public synchronized boolean getPeerKnows(final EventDescriptor event) {
        return events.containsKey(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void shiftWindow(final long generation) {
        // very rarely, after a restart, generations can go in reverse, so we need this safeguard
        if (generation > events.getFirstSequenceNumberInWindow()) {
            events.shiftWindow(generation);
        }
    }

    /**
     * Handle the descriptor received by this peer
     *
     * @param descriptor the descriptor received
     */
    public synchronized void handleDescriptor(final EventDescriptor descriptor) {
        maxReceivedDescriptorGeneration = Math.max(maxReceivedDescriptorGeneration, descriptor.getGeneration());
        setPeerKnows(descriptor);
    }

    /**
     * Handle the event received by this peer
     *
     * @param event the event received
     */
    public synchronized void handleEvent(final ChatterEvent event) {
        setPeerKnows(event.getDescriptor());
    }
}
