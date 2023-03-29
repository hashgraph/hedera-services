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

package com.swirlds.platform.event.tipset;

import static com.swirlds.platform.event.tipset.Tipset.merge;

import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.sequence.map.StandardSequenceMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToLongFunction;
import java.util.function.LongToIntFunction;

/**
 * Computes and tracks tipsets for non-ancient events.
 */
public class TipsetBuilder {

    private final SequenceMap<EventFingerprint, Tipset> tipsets;

    /**
     * The number of nodes.
     */
    private final int nodeCount;

    /**
     * Maps node ID to node index.
     */
    private final LongToIntFunction nodeIdToIndex;

    /**
     * Maps node index to consensus weight.
     */
    private final IntToLongFunction indexToWeight;

    /**
     * Create a new tipset tracker.
     * @param nodeCount     the number of nodes in the address book
     * @param nodeIdToIndex maps node ID to node index
     * @param indexToWeight maps node index to consensus weight
     */
    public TipsetBuilder(
            final int nodeCount,
            @NonNull final LongToIntFunction nodeIdToIndex,
            @NonNull final IntToLongFunction indexToWeight) {

        this.nodeCount = nodeCount;
        this.nodeIdToIndex = nodeIdToIndex;
        this.indexToWeight = indexToWeight;

        tipsets = new StandardSequenceMap<>(
                0,
                1024, // TODO meet or exceed maximum generations allowed in memory past ancient
                EventFingerprint::generation);
    }

    /**
     * Set the minimum generation that is not considered ancient.
     *
     * @param minimumGenerationNonAncient
     * 		the minimum non-ancient generation, all lower generations are ancient
     */
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        tipsets.shiftWindow(minimumGenerationNonAncient);
    }

    /**
     * Add a new event to the tracker.
     *
     * @param eventFingerprint
     * 		the fingerprint of the event to add
     * @param parents
     * 		the parents of the event being added
     * @return the tipset for the event that was added
     */
    public Tipset addEvent(final EventFingerprint eventFingerprint, final List<EventFingerprint> parents) {
        final List<Tipset> parentTipsets = new ArrayList<>(parents.size());
        for (final EventFingerprint parent : parents) {
            final Tipset parentTipset = tipsets.get(parent);
            if (parentTipset != null) {
                parentTipsets.add(parentTipset);
            }
        }

        final Tipset eventTipset;
        if (parents.isEmpty()) {
            eventTipset = new Tipset(nodeCount, nodeIdToIndex, indexToWeight)
                    .advance(eventFingerprint.creator(), eventFingerprint.generation());
        } else {
            eventTipset = merge(parentTipsets).advance(eventFingerprint.creator(), eventFingerprint.generation());
        }

        tipsets.put(eventFingerprint, eventTipset);

        return eventTipset;
    }

    /**
     * Get the tipset of an event, or null if the event is not being tracked.
     *
     * @param eventFingerprint
     * 		the fingerprint of the event
     * @return the tipset of the event
     */
    public Tipset getTipset(final EventFingerprint eventFingerprint) {
        return tipsets.get(eventFingerprint);
    }

    /**
     * Get number of tipsets being tracked.
     */
    public int size() {
        return tipsets.getSize();
    }
}
