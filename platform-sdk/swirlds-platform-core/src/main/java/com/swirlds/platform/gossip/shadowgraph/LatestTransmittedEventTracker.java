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

package com.swirlds.platform.gossip.shadowgraph;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// TODO test

/**
 * For each peer, tracks the latest events that have been sent to that peer. If we sync with that peer twice in close
 * succession, we can use this information to avoid sending the same events again.
 */
public class LatestTransmittedEventTracker {

    /**
     * Tracks the latest transmitted events for a single peer. Is thread safe because we only sync with any particular
     * peer on exactly one thread at a time.
     */
    private static class LatestTransmittedEventSet {

        private List<Hash> latestTransmittedEvents = List.of();
        private long previousConnectionId = -1;

        /**
         * Store the latest transmitted events for a peer.
         *
         * @param currentConnectionId     the connection id of the connection that was used to transmit the events
         * @param latestTransmittedEvents the latest transmitted events
         */
        public void setLatestTransmittedEvents(
                final long currentConnectionId, @NonNull final List<Hash> latestTransmittedEvents) {

            this.latestTransmittedEvents = latestTransmittedEvents;
            this.previousConnectionId = currentConnectionId;
        }

        /**
         * Get the latest transmitted events for a peer.
         *
         * @param currentConnectionId the connection id of the connection that is being used to transmit the events
         * @return the latest transmitted events over this connection
         */
        @NonNull
        public List<Hash> getLatestTransmittedEvents(final long currentConnectionId) {
            if (currentConnectionId != previousConnectionId) {
                return latestTransmittedEvents = List.of();
            }
            return latestTransmittedEvents;
        }
    }

    /**
     * A map from node id to the latest transmitted events for that peer.
     */
    private final Map<NodeId, LatestTransmittedEventSet> latestTransmittedEventsByPeer = new HashMap<>();

    /**
     * Constructor.
     *
     * @param addressBook the address book
     * @param selfId      the id of this node
     */
    public LatestTransmittedEventTracker(@NonNull final AddressBook addressBook, @NonNull final NodeId selfId) {
        for (final NodeId node : addressBook.getNodeIdSet()) {
            if (node.equals(selfId)) {
                continue;
            }
            latestTransmittedEventsByPeer.put(node, new LatestTransmittedEventSet());
        }
    }

    /**
     * Store the latest transmitted events for a peer.
     *
     * @param peer                    the peer
     * @param currentConnectionId     the connection id of the connection that was used to transmit the events
     * @param latestTransmittedEvents the latest transmitted events
     */
    public void setLatestTransmittedEvents(
            @NonNull final NodeId peer,
            final long currentConnectionId,
            @NonNull final List<EventImpl> latestTransmittedEvents) {

        final List<Hash> tipHashesOfLatestTransmittedEvents = findTipHashesOfEventList(latestTransmittedEvents);

        latestTransmittedEventsByPeer
                .get(peer)
                .setLatestTransmittedEvents(currentConnectionId, tipHashesOfLatestTransmittedEvents);
    }

    /**
     * Given a list of events we are transmitting, find the hashes of the tips of these events. A tip is defined as an
     * event with no self child.
     *
     * @param events the events we are transmitting
     * @return the hashes of the tips of these events
     */
    private static List<Hash> findTipHashesOfEventList(@NonNull final List<EventImpl> events) {
        final Set<Hash> eventHashes = new HashSet<>();
        for (final EventImpl event : events) {
            eventHashes.add(event.getBaseHash());
        }

        // A tip is an event with no self child. Check the self parent of each event, and remove that parent
        // from the set if it exists.

        for (final EventImpl event : events) {
            final EventImpl selfParent = event.getSelfParent();
            if (selfParent == null) {
                continue;
            }
            eventHashes.remove(selfParent.getBaseHash());
        }

        return new ArrayList<>(eventHashes);
    }

    /**
     * Get the latest transmitted events for a peer.
     *
     * @param peer                the peer
     * @param currentConnectionId the connection id of the connection that is being used to transmit the events
     * @return the latest transmitted events
     */
    @NonNull
    public List<Hash> getLatestTransmittedEvents(@NonNull final NodeId peer, final long currentConnectionId) {
        return latestTransmittedEventsByPeer.get(peer).getLatestTransmittedEvents(currentConnectionId);
    }
}
