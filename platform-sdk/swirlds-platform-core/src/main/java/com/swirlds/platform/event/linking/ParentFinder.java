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

package com.swirlds.platform.event.linking;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import java.util.function.Function;

/**
 * Looks up the parents of an event if they are not ancient
 */
public class ParentFinder {
    /** Look up an event by its hash */
    private final Function<Hash, EventImpl> eventByHash;

    public ParentFinder(final Function<Hash, EventImpl> eventByHash) {
        this.eventByHash = eventByHash;
    }

    /**
     * An event's parent is required iff (a) the event does have that parent event, and (b) that
     * parent event is non-ancient.
     *
     * @return true iff the event's parent is required
     */
    private static boolean requiredParent(
            final GossipEvent event, final boolean selfParent, final long minGenerationNonAncient) {
        final long parentGeneration = selfParent
                ? event.getHashedData().getSelfParentGen()
                : event.getHashedData().getOtherParentGen();
        // if an event does not have a parent, its generation will be EventConstants.GENERATION_UNDEFINED,
        // which is always smaller than minGenerationNonAncient
        return parentGeneration >= minGenerationNonAncient;
    }

    private EventImpl getParent(final GossipEvent event, final boolean selfParent) {
        final Hash parentHash = selfParent
                ? event.getHashedData().getSelfParentHash()
                : event.getHashedData().getOtherParentHash();
        return eventByHash.apply(parentHash);
    }

    /**
     * Looks for the events parents if they are not ancient
     *
     * @param event
     * 		the event whose parents are looked for
     * @param minGenerationNonAncient
     * 		the generation below which all events are ancient
     * @return a {@link ChildEvent} which may be an orphan
     */
    public ChildEvent findParents(final GossipEvent event, final long minGenerationNonAncient) {
        final EventImpl selfParent = getParent(event, true);
        final EventImpl otherParent = getParent(event, false);
        final boolean missingSP;
        final boolean missingOP;
        if (requiredParent(event, true, minGenerationNonAncient)) {
            missingSP = selfParent == null;
        } else {
            missingSP = false;
        }
        if (requiredParent(event, false, minGenerationNonAncient)) {
            missingOP = otherParent == null;
        } else {
            missingOP = false;
        }
        return new ChildEvent(event, missingSP, missingOP, selfParent, otherParent);
    }
}
