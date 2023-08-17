/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components;

import static com.swirlds.common.metrics.Metrics.INFO_CATEGORY;

import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.event.SelfEventStorage;
import com.swirlds.platform.observers.EventAddedObserver;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This data structure is used to track the most recent events from each node. This data structure will track the most
 * recent event added and will not check the ordering of these events. If a fork exists, it will track whichever fork is
 * added last.
 */
public class EventMapper implements EventAddedObserver, SelfEventStorage, Clearable {
    private static final EventMapping DEFAULT_RETURN = new EventMapping(null);
    /**
     * Contains the most recent event added from each node, with information about its descendants
     */
    private final Map<NodeId, EventMapping> mappings;

    /**
     * The ID of this node
     */
    private final NodeId selfId;

    /**
     * Constructor
     *
     * @param metrics the {@link Metrics} object to use
     * @param selfId  this node's {@link NodeId}
     */
    public EventMapper(@NonNull final Metrics metrics, @NonNull final NodeId selfId) {
        this.selfId = Objects.requireNonNull(selfId);
        mappings = new HashMap<>();

        metrics.getOrCreate(new FunctionGauge.Config<>(
                        INFO_CATEGORY, "lastGen", Long.class, () -> getHighestGenerationNumber(selfId))
                .withDescription("last event generation number by me")
                .withFormat("%d"));
    }

    /**
     * Notifies the mapper that an event has been added
     *
     * @param event the event that was added
     */
    @Override
    public synchronized void eventAdded(@NonNull final EventImpl event) {
        Objects.requireNonNull(event, "event must not be null");
        final NodeId nodeId = event.getCreatorId();
        mappings.put(nodeId, new EventMapping(event));

        final EventImpl otherParent = event.getOtherParent();
        if (otherParent == null) {
            return;
        }
        final EventMapping parentMapping = mappings.get(otherParent.getCreatorId());
        if (parentMapping == null || !parentMapping.getEvent().getBaseHash().equals(otherParent.getBaseHash())) {
            // if the other parent is not an event we are tracking, then there is nothing to do
            return;
        }

        // we now know this event has a descendant
        parentMapping.setHasDescendant(true);
        if (event.isCreatedBy(selfId)) {
            // if we have created the event added, then the parent has a direct self descendant
            parentMapping.setHasDirectSelfDescendant(true);
        }
    }

    /**
     * Reset this instance to its constructed state
     */
    @Override
    public synchronized void clear() {
        mappings.clear();
    }

    /**
     * Get the most recent event from a given node, or null if no such event exists.
     *
     * @param nodeId the ID of the node in question
     */
    @Nullable
    public synchronized EventImpl getMostRecentEvent(@Nullable final NodeId nodeId) {
        return mappings.getOrDefault(nodeId, DEFAULT_RETURN).getEvent();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public synchronized EventImpl getMostRecentSelfEvent() {
        return getMostRecentEvent(selfId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void setMostRecentSelfEvent(final EventImpl selfEvent) {
        // does nothing, self events will be added through the eventAdded() method
    }

    /**
     * Get the generation number of the most recent event from a given node, or
     * {@link EventConstants#GENERATION_UNDEFINED} if there is no event from that node.
     *
     * @param nodeId the ID of the node in question
     */
    public synchronized long getHighestGenerationNumber(@Nullable final NodeId nodeId) {
        final EventMapping mapping = mappings.get(nodeId);
        if (mapping == null) {
            return EventConstants.GENERATION_UNDEFINED;
        }
        return mapping.getEvent().getGeneration();
    }

    /**
     * Check if the most recent event created by a given node has been used as an other parent by an event created by
     * this node.
     *
     * @param nodeId the ID of the node in question
     */
    public synchronized boolean hasMostRecentEventBeenUsedAsOtherParent(@Nullable final NodeId nodeId) {
        return mappings.getOrDefault(nodeId, DEFAULT_RETURN).isHasDirectSelfDescendant();
    }

    /**
     * Check if the most recent event from a given node has any descendants.
     *
     * @param nodeId the node ID in question
     * @return true if the most recent event has descendants, otherwise false. False if there are no events for the
     * given node ID.
     */
    public synchronized boolean doesMostRecentEventHaveDescendants(@Nullable final NodeId nodeId) {
        return mappings.getOrDefault(nodeId, DEFAULT_RETURN).isHasDescendant();
    }

    /**
     * @return a list of the most recently added events by each creator
     */
    public synchronized List<EventImpl> getMostRecentEventsByEachCreator() {
        return mappings.values().stream().map(EventMapping::getEvent).collect(Collectors.toList());
    }

    private static class EventMapping {
        private final EventImpl event;
        private boolean hasDirectSelfDescendant;
        private boolean hasDescendant;

        public EventMapping(final EventImpl event) {
            this.event = event;
            // The most recent event added can't, by definition, have any descendants yet.
            hasDescendant = false;
            hasDirectSelfDescendant = false;
        }

        public EventImpl getEvent() {
            return event;
        }

        public boolean isHasDirectSelfDescendant() {
            return hasDirectSelfDescendant;
        }

        public boolean isHasDescendant() {
            return hasDescendant;
        }

        public void setHasDirectSelfDescendant(boolean hasDirectSelfDescendant) {
            this.hasDirectSelfDescendant = hasDirectSelfDescendant;
        }

        public void setHasDescendant(boolean hasDescendant) {
            this.hasDescendant = hasDescendant;
        }
    }
}
