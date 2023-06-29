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

package com.swirlds.platform.event.tipset;

import com.swirlds.platform.event.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Keeps track of events created that have no children. These events are candidates to be used as parents when creating
 * a new event.
 *
 * <p>
 * At the surface, this class may appear similar to ChatterEventMapper. But unlike that class, this class specifically
 * only tracks events without children, as opposed to tracking the most recent event from each creator. This class
 * provides no guarantees that an event from any particular node will always be present.
 */
public class ChildlessEventTracker {

    private final Set<EventDescriptor> childlessEvents = new HashSet<>();

    /**
     * Add a new event.
     *
     * @param eventDescriptor the event to add
     * @param parents         the parents of the event being added
     */
    public void addEvent(@NonNull final EventDescriptor eventDescriptor, @NonNull final List<EventDescriptor> parents) {
        Objects.requireNonNull(eventDescriptor);
        childlessEvents.add(eventDescriptor);

        for (final EventDescriptor parent : parents) {
            childlessEvents.remove(parent);
        }
    }

    /**
     * Register a self event. Removes parents but does not add the event to the set of childless events.
     *
     * @param parents the parents of the self event
     */
    public void registerSelfEventParents(@NonNull final List<EventDescriptor> parents) {
        for (final EventDescriptor parent : parents) {
            childlessEvents.remove(parent);
        }
    }

    /**
     * Remove ancient events.
     *
     * @param minimumGenerationNonAncient the minimum generation of non-ancient events
     */
    public void pruneOldEvents(final long minimumGenerationNonAncient) {
        childlessEvents.removeIf(event -> event.getGeneration() < minimumGenerationNonAncient);
    }

    /**
     * Get a list of non-ancient childless events.
     *
     * @return the childless events, this list is safe to modify
     */
    @NonNull
    public List<EventDescriptor> getChildlessEvents() {
        return new ArrayList<>(childlessEvents);
    }
}
