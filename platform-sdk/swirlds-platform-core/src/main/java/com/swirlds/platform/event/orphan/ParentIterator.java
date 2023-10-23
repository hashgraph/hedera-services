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

package com.swirlds.platform.event.orphan;

import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterates over the parents of an event. This class is temporary and intended to allow code to be written that works
 * for the current binary event parentage and the future n-ary event parentage.
 */
class ParentIterator implements Iterator<EventDescriptor> {

    private final EventDescriptor selfParent;
    private final List<EventDescriptor> otherParents;
    private int parentsToReturn;

    /**
     * Constructor.
     *
     * @param event the event whose parents we want to iterate over
     */
    public ParentIterator(@NonNull final GossipEvent event) {
        selfParent = event.getHashedData().getSelfParent();
        otherParents = event.getHashedData().getOtherParents();
        parentsToReturn = (selfParent == null ? 0 : 1) + (otherParents == null ? 0 : otherParents.size());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        return parentsToReturn > 0;
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

        final int otherParentSize = (otherParents == null ? 0 : otherParents.size());
        if (parentsToReturn > otherParentSize) {
            parentsToReturn--;
            return selfParent;
        } else {
            return otherParents.get(otherParentSize - parentsToReturn--);
        }
    }
}
