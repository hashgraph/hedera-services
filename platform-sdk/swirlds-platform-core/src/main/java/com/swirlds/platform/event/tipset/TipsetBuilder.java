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
import com.swirlds.platform.event.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntToLongFunction;
import java.util.function.LongToIntFunction;

/**
 * Computes and tracks tipsets for non-ancient events.
 */
public class TipsetBuilder {

    private static final int INITIAL_TIPSET_MAP_CAPACITY = 64;

    /**
     * Tipsets for all recent events we know about.
     */
    private final SequenceMap<EventDescriptor, Tipset> tipsets;

    /**
     * This tipset is equivalent to a tipset that would be created by merging all tipsets of all events that
     * this object has ever observed. If you ask this tipset for the generation for a particular node,
     * it will return the highest generation of all events we have ever received from that node.
     */
    private Tipset latestGenerations;

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
     *
     * @param nodeCount     the number of nodes in the address book
     * @param nodeIdToIndex maps node ID to node index
     * @param indexToWeight maps node index to consensus weight
     */
    public TipsetBuilder(
            final int nodeCount,
            @NonNull final LongToIntFunction nodeIdToIndex,
            @NonNull final IntToLongFunction indexToWeight) {

        this.nodeCount = nodeCount;
        this.nodeIdToIndex = Objects.requireNonNull(nodeIdToIndex);
        this.indexToWeight = Objects.requireNonNull(indexToWeight);
        this.latestGenerations = new Tipset(nodeCount, nodeIdToIndex, indexToWeight);

        tipsets = new StandardSequenceMap<>(0, INITIAL_TIPSET_MAP_CAPACITY, true, EventDescriptor::getGeneration);
    }

    /**
     * Set the minimum generation that is not considered ancient.
     *
     * @param minimumGenerationNonAncient the minimum non-ancient generation, all lower generations are ancient
     */
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        tipsets.shiftWindow(minimumGenerationNonAncient);
    }

    /**
     * Add a new event to the tracker.
     *
     * @param eventDescriptor the descriptor of the event to add
     * @param parents          the parents of the event being added
     * @return the tipset for the event that was added
     */
    @NonNull
    public Tipset addEvent(
            @NonNull final EventDescriptor eventDescriptor, @NonNull final List<EventDescriptor> parents) {
        final List<Tipset> parentTipsets = new ArrayList<>(parents.size());
        for (final EventDescriptor parent : parents) {
            final Tipset parentTipset = tipsets.get(parent);
            if (parentTipset != null) {
                parentTipsets.add(parentTipset);
            }
        }

        final Tipset eventTipset;
        if (parents.isEmpty()) {
            eventTipset = new Tipset(nodeCount, nodeIdToIndex, indexToWeight)
                    .advance(eventDescriptor.getCreator(), eventDescriptor.getGeneration());
        } else {
            eventTipset = merge(parentTipsets).advance(eventDescriptor.getCreator(), eventDescriptor.getGeneration());
        }

        tipsets.put(eventDescriptor, eventTipset);
        latestGenerations = latestGenerations.advance(eventDescriptor.getCreator(), eventDescriptor.getGeneration());

        return eventTipset;
    }

    /**
     * Get the tipset of an event, or null if the event is not being tracked.
     *
     * @param eventDescriptor the fingerprint of the event
     * @return the tipset of the event, or null if the event is not being tracked
     */
    @Nullable
    public Tipset getTipset(@NonNull final EventDescriptor eventDescriptor) {
        return tipsets.get(eventDescriptor);
    }

    /**
     * Get the highest generation of all events we have received from a particular node.
     *
     * @param nodeId the ID of the node
     * @return the highest generation of all events received by a node
     */
    public long getLatestGenerationForNodeId(final long nodeId) { // TODO test
        return latestGenerations.getTipGenerationForNodeId(nodeId);
    }

    /**
     * Get the highest generation of all events we have received from a particular node index.
     *
     * @param nodeIndex the index of the node
     * @return the highest generation of all events received by a node index
     */
    public long getLatestGenerationForNodeIndex(final int nodeIndex) { // TODO test
        return latestGenerations.getTipGenerationForNodeIndex(nodeIndex);
    }

    /**
     * Get number of tipsets being tracked.
     */
    public int size() {
        return tipsets.getSize();
    }
}
