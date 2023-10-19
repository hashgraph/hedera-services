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

import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static com.swirlds.common.utility.Threshold.STRONG_MINORITY;

import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements the {@link CriticalQuorum} algorithm.
 */
public class CriticalQuorumImpl implements CriticalQuorum {
    private static final int DEFAULT_THRESHOLD_SOFTENING = 0;

    /**
     * The current address book for this node.
     */
    private final AddressBook addressBook;
    /** 'soften' the threshold by this many events, the higher the number, the less strict the quorum is */
    private final int thresholdSoftening;

    /**
     * The number of events observed from each node in the current round.
     */
    private final Map<NodeId, Integer> eventCounts;

    /**
     * A map from possible thresholds to weights. The given weight is the weight of all nodes that do not exceed the
     * threshold.
     */
    private final Map<Integer, Long> weightNotExceedingThreshold;

    /**
     * Any nodes with an event count that does not exceed this threshold are considered to be part of the critical
     * quorum.
     */
    private final AtomicInteger threshold;

    /**
     * The current round. Observing an event from a higher round will increase this value and reset event counts.
     */
    private long round;

    /**
     * Construct a critical quorum
     *
     * @param metrics             the metrics engine
     * @param selfId              the ID of this node
     * @param addressBook         the source address book
     * @param thresholdSoftening  'soften' the threshold by this many events, the higher the number, the less strict the
     *                            quorum is
     */
    public CriticalQuorumImpl(
            @NonNull final Metrics metrics,
            @NonNull final NodeId selfId,
            @NonNull final AddressBook addressBook,
            final int thresholdSoftening) {

        Objects.requireNonNull(metrics, "metrics must not be null");
        Objects.requireNonNull(selfId, "selfId must not be null");
        this.addressBook = Objects.requireNonNull(addressBook, "addressBook must not be null");
        this.thresholdSoftening = thresholdSoftening;

        eventCounts = new ConcurrentHashMap<>();
        weightNotExceedingThreshold = new HashMap<>();

        threshold = new AtomicInteger(0);

        metrics.getOrCreate(new FunctionGauge.Config<>(
                        INTERNAL_CATEGORY,
                        "isStrongMinorityInMaxRound",
                        Boolean.class,
                        () -> isInCriticalQuorum(selfId))
                .withDescription("Whether this node is in the critical quorum in the max round")
                .withUnit("is node in the critical quorum"));
    }

    /**
     * Construct a critical quorum from an address book
     *
     * @param metrics     the metrics engine
     * @param selfId      the id of this node
     * @param addressBook the source address book
     */
    public CriticalQuorumImpl(
            @NonNull final Metrics metrics, @NonNull final NodeId selfId, @NonNull final AddressBook addressBook) {
        this(metrics, selfId, addressBook, DEFAULT_THRESHOLD_SOFTENING);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInCriticalQuorum(@Nullable final NodeId nodeId) {
        if (nodeId == null) {
            return false;
        }
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

        final NodeId nodeId = event.getCreatorId();
        if (!addressBook.contains(nodeId)) {
            // No need to consider events from nodes not in the address book
            return;
        }
        final long totalWeight = addressBook.getTotalWeight();

        // Increase the event count
        final int originalEventCount = eventCounts.getOrDefault(nodeId, 0);
        eventCounts.put(nodeId, originalEventCount + 1);

        // Update threshold map
        final long originalWeightAtThreshold =
                weightNotExceedingThreshold.getOrDefault(originalEventCount, totalWeight);
        final long newWeightAtThreshold =
                originalWeightAtThreshold - addressBook.getAddress(nodeId).getWeight();
        weightNotExceedingThreshold.put(originalEventCount, newWeightAtThreshold);

        // Make sure threshold allows at least 1/3 of the weight to be part of the critical quorum
        if (!STRONG_MINORITY.isSatisfiedBy(
                weightNotExceedingThreshold.getOrDefault(threshold.get(), totalWeight), totalWeight)) {
            threshold.incrementAndGet();
        }
    }
}
