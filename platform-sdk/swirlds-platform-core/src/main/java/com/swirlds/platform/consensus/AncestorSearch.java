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

package com.swirlds.platform.consensus;

import com.swirlds.platform.internal.EventImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Used for searching for ancestors events in a hashgraph */
public class AncestorSearch {
    /** the iterator that returns ancestors */
    private final AncestorIterator iterator;

    public AncestorSearch() {
        this(new EventVisitedMark());
    }

    public AncestorSearch(final EventVisitedMark mark) {
        this.iterator = new AncestorIterator(mark);
    }

    /**
     * The set of ancestors of the given event (the root of the search) for which valid is true.
     * This will not include a valid ancestor that is only reachable through invalid ancestors.
     *
     * @param root the root event whose ancestors should be searched
     * @param valid do a depth-first search, but backtrack from any event e where valid(e)==false
     */
    public AncestorIterator search(final EventImpl root, final Predicate<EventImpl> valid) {
        iterator.search(root, valid);
        return iterator;
    }

    /**
     * Finds events that ancestors to all the supplied events. It will only traverse valid events,
     * where valid is defined by the predicate supplied. This method will also populate {@link
     * EventImpl#getRecTimes()} for the events returned.
     *
     * @param events the events whose ancestors we are searching for
     * @param valid checks if the event should be part of the search
     * @return a list of all common ancestors
     */
    public List<EventImpl> commonAncestorsOf(final List<EventImpl> events, final Predicate<EventImpl> valid) {
        // each event visited by iterator from at least one of the provided events
        final ArrayList<EventImpl> visited = new ArrayList<>();
        // Do a non-recursive search of the hashgraph, without using the Java stack, and being
        // efficient when it's a
        // DAG that isn't a tree.
        for (final EventImpl e : events) {
            final AncestorIterator validAncestors = search(e, valid);
            while (validAncestors.hasNext()) {
                final EventImpl event = validAncestors.next();
                if (event.getRecTimes() == null) {
                    event.setRecTimes(new ArrayList<>());
                    visited.add(event);
                }
                event.getRecTimes().add(validAncestors.getTime());
            }
        }

        final ArrayList<EventImpl> commonAncestors = new ArrayList<>();
        visited.forEach(e -> {
            if (e.getRecTimes().size() == events.size()) {
                commonAncestors.add(e);
            } else {
                // reclaim the memory for the list of received times
                e.setRecTimes(null);
            }
        });
        return commonAncestors;
    }
}
