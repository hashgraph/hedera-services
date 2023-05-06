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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps track of events created that have no children. These events are candidates to be used as parents when creating
 * a new event.
 */
public class ChildlessEventTracker { // TODO test

    private final Set<EventFingerprint> childlessEvents = new HashSet<>();

    public ChildlessEventTracker() {}

    /**
     * Add a new event.
     *
     * @param eventFingerprint the event to add
     * @param parents          the parents of the event being added
     */
    public void addEvent(
            @NonNull final EventFingerprint eventFingerprint, @NonNull final List<EventFingerprint> parents) {

        childlessEvents.add(eventFingerprint);

        for (final EventFingerprint parent : parents) {
            childlessEvents.remove(parent);
        }
    }

    /**
     * Remove ancient events.
     *
     * @param minimumGenerationNonAncient the minimum generation of non-ancient events
     */
    public void pruneOldEvents(final long minimumGenerationNonAncient) {
        childlessEvents.removeIf(event -> event.generation() < minimumGenerationNonAncient);
    }

    /**
     * Get a list of non-ancient childless events.
     *
     * @return the childless events, this list is safe to modify
     */
    @NonNull
    public List<EventFingerprint> getChildlessEvents() {
        return new ArrayList<>(childlessEvents);
    }
}
