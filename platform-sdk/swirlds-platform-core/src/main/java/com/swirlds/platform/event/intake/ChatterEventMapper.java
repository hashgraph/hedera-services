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

package com.swirlds.platform.event.intake;

import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.EventAddedObserver;
import com.swirlds.platform.state.signed.LoadableFromSignedState;
import com.swirlds.platform.state.signed.SignedState;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This data structure is used to track the most recent events from each node. This data structure will track the
 * event with the highest generation. If a fork exists, it will track whichever fork has a higher generation. This class
 * is thread safe.
 */
public class ChatterEventMapper implements EventAddedObserver, Clearable, LoadableFromSignedState {
    /**
     * Contains the most recent event added from each node
     */
    private final Map<Long, GossipEvent> mappings;

    public ChatterEventMapper() {
        mappings = new ConcurrentHashMap<>();
    }

    /**
     * Notifies the mapper that an event has been added
     *
     * @param event
     * 		the event that was added
     */
    @Override
    public void eventAdded(final EventImpl event) {
        mapEvent(event.getBaseEvent());
    }

    /**
     * Maps the most recent event by a creator
     *
     * @param event
     * 		the event to map
     */
    public void mapEvent(final GossipEvent event) {
        mappings.merge(event.getHashedData().getCreatorId(), event, ChatterEventMapper::mapIfNewer);
    }

    private static GossipEvent mapIfNewer(final GossipEvent oldEvent, final GossipEvent newEvent) {
        if (oldEvent == null || newEvent.getGeneration() >= oldEvent.getGeneration()) {
            // the event we are mapping has a newer generation than the old one, which is expected
            return newEvent;
        }
        return oldEvent;
    }

    /**
     * Get the most recent event from a given node, or null if no such event exists.
     *
     * @param nodeId
     * 		the ID of the node in question
     */
    public GossipEvent getMostRecentEvent(final long nodeId) {
        return mappings.get(nodeId);
    }

    /**
     * Reset this instance to its constructed state
     */
    @Override
    public void clear() {
        mappings.clear();
    }

    @Override
    public void loadFromSignedState(final SignedState signedState) {
        for (final EventImpl event : signedState.getEvents()) {
            event.getBaseEvent().buildDescriptor();
            mapEvent(event.getBaseEvent());
        }
    }
}
