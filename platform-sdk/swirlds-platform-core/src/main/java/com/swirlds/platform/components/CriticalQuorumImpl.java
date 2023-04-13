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

import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.internal.EventImpl;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements the {@link CriticalQuorum} algorithm.
 */
public class CriticalQuorumImpl implements CriticalQuorum {
    private static final boolean DEFAULT_BOTH_PARENTS = true;
    private static final int DEFAULT_THRESHOLD_SOFTENING = 0;

    /**
     * The current address book for this node.
     */
    private final AddressBook addressBook;
    /** Should both parents be checked for critical quorum or just the self parent */
    private final boolean considerBothParents;
    /** 'soften' the threshold by this many events, the higher the number, the less strict the quorum is */
    private final int thresholdSoftening;

    /**
     * The number of events observed from each node in the current round.
     */
    private final Map<Long, Integer> eventCounts;

    /**
     * A map from possible thresholds to weights. The given weight is the weight of all
     * nodes that do not exceed the threshold.
     */
    private final Map<Integer, Long> weightNotExceedingThreshold;

    /**
     * Any nodes with an event count that does not exceed this threshold are considered
     * to be part of the critical quorum.
     */
    private final AtomicInteger threshold;

    /**
     * The current round. Observing an event from a higher round will increase this value and
     * reset event counts.
     */
    private long round;

    /**
     * Construct a critical quorum
     *
     * @param addressBook
     * 		the source address book
     * @param considerBothParents
     * 		true if both parents should be checked for critical quorum and false for just the self parent
     * @param thresholdSoftening
     * 		'soften' the threshold by this many events, the higher the number, the less strict the quorum is
     */
    public CriticalQuorumImpl(
            final AddressBook addressBook, final boolean considerBothParents, final int thresholdSoftening) {
        this.addressBook = addressBook;
        this.considerBothParents = considerBothParents;
        this.thresholdSoftening = thresholdSoftening;

        eventCounts = new ConcurrentHashMap<>();
        weightNotExceedingThreshold = new HashMap<>();

        threshold = new AtomicInteger(0);
    }

    /**
     * Construct a critical quorum from an address book
     *
     * @param addressBook
     * 		the source address book
     */
    public CriticalQuorumImpl(final AddressBook addressBook) {
        this(addressBook, DEFAULT_BOTH_PARENTS, DEFAULT_THRESHOLD_SOFTENING);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInCriticalQuorum(final long nodeId) {
        return eventCounts.getOrDefault(nodeId, 0) <= threshold.get() + thresholdSoftening;
    }

    /**
     * When the round increases we need to reset all of our counts.
     */
    private void handleRoundBoundary(final EventImpl event) {
        if (event.getRoundCreated() > round) {
            round = event.getRoundCreated();
            eventCounts.clear();
            weightNotExceedingThreshold.clear();
            threshold.set(0);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventAdded(final EventImpl event) {
        if (event.getRoundCreated() < round) {
            // No need to consider old events
            return;
        }

        handleRoundBoundary(event);

        final long nodeId = event.getCreatorId();
        final long totalState = addressBook.getTotalWeight();

        // Increase the event count
        final int originalEventCount = eventCounts.getOrDefault(nodeId, 0);
        eventCounts.put(nodeId, originalEventCount + 1);

        // Update threshold map
        final long originalWeightAtThreshold = weightNotExceedingThreshold.getOrDefault(originalEventCount, totalState);
        final long newWeightAtThreshold =
                originalWeightAtThreshold - addressBook.getAddress(nodeId).getWeight();
        weightNotExceedingThreshold.put(originalEventCount, newWeightAtThreshold);

        // Make sure threshold allows at least 1/3 of the weight to be part of the critical quorum
        if (!Utilities.isStrongMinority(
                weightNotExceedingThreshold.getOrDefault(threshold.get(), totalState), totalState)) {
            threshold.incrementAndGet();
        }
    }

    @Override
    public EventCreationRuleResponse shouldCreateEvent(final BaseEvent selfParent, final BaseEvent otherParent) {
        // if neither node is part of the superMinority in the latest round, don't create an event
        final boolean isSelfMinority = isInCriticalQuorum(EventUtils.getCreatorId(selfParent));
        if (!considerBothParents) {
            return isSelfMinority ? EventCreationRuleResponse.PASS : EventCreationRuleResponse.DONT_CREATE;
        }
        final boolean isOtherMinority = isInCriticalQuorum(EventUtils.getCreatorId(otherParent));
        if (isSelfMinority || isOtherMinority) {
            return EventCreationRuleResponse.PASS;
        }
        return EventCreationRuleResponse.DONT_CREATE;
    }
}
