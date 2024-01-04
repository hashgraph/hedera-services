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

package com.swirlds.platform.event.orphan;

import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.system.events.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterates over the parents of an event. This class is temporary and intended to allow code to be written that works
 * for the current binary event parentage and the future n-ary event parentage.
 */
class ParentIterator implements Iterator<EventDescriptor> {
    /**
     * The number of parents that have been returned so far.
     */
    private int returnedEvents;

    /**
     * The parents of the event.
     */
    private final List<EventDescriptor> parents;

    /**
     * Constructor.
     *
     * @param event the event whose parents we want to iterate over
     */
    ParentIterator(@NonNull final GossipEvent event) {
        parents = new ArrayList<>();

        final EventDescriptor selfParent = event.getHashedData().getSelfParent();
        final List<EventDescriptor> otherParents = event.getHashedData().getOtherParents();

        if (selfParent != null) {
            parents.add(selfParent);
        }
        otherParents.forEach(parents::add);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        return returnedEvents < parents.size();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public EventDescriptor next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        final int indexToReturn = returnedEvents;
        returnedEvents++;

        return parents.get(indexToReturn);
    }
}
