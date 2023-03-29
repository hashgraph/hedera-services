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

import static com.swirlds.common.utility.CommonUtils.throwArgNull;
import static com.swirlds.platform.Utilities.isSuperMajority;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.IntToLongFunction;
import java.util.function.LongToIntFunction;

/**
 * Calculates tipset scores for events created by a node.
 */
public class TipsetScoreCalculator {

    /**
     * The node ID that is being tracked by this window.
     */
    private final long selfId;

    /**
     * Builds tipsets for each event. Is maintained outside this object.
     */
    private final TipsetBuilder builder;

    /**
     * The current tipset snapshot.
     */
    private Tipset snapshot;

    /**
     * The N most recent snapshots.
     */
    private final Deque<Tipset> snapshotHistory = new LinkedList<>();

    /**
     * The number of snapshots to keep in {@link #snapshotHistory}.
     */
    private final int snapshotHistorySize = 10; // TODO setting

    /**
     * The total number of nodes in the address book.
     */
    private final int nodeCount;

    /**
     * The total weight of all nodes.
     */
    private final long totalWeight;

    /**
     * The weight of the node tracked by this window.
     */
    private final long selfWeight;

    /**
     * The maximum possible advancement score for an event.
     */
    private final long maximumPossibleScore;

    /**
     * The previous tipset score.
     */
    private long previousScore = 0;

    /**
     * Create a new tipset window.
     *
     * @param selfId        the ID of the node tracked by this window
     * @param builder       builds tipsets for individual events
     * @param nodeCount     the number of nodes in the address book
     * @param nodeIdToIndex maps node ID to node index
     * @param indexToWeight maps node index to consensus weight
     * @param totalWeight   the sum of all weight
     */
    public TipsetScoreCalculator(
            final long selfId,
            @NonNull final TipsetBuilder builder,
            final int nodeCount,
            @NonNull final LongToIntFunction nodeIdToIndex,
            @NonNull final IntToLongFunction indexToWeight,
            final long totalWeight) {

        this.selfId = selfId;
        this.builder = builder;
        this.nodeCount = nodeCount;
        this.totalWeight = totalWeight;
        this.selfWeight = indexToWeight.applyAsLong(nodeIdToIndex.applyAsInt(selfId));
        this.maximumPossibleScore = totalWeight - selfWeight;

        this.snapshot = new Tipset(nodeCount, nodeIdToIndex, indexToWeight);
    }

    /**
     * Get the maximum possible tipset score that a new event can achieve.
     */
    public long getMaximumPossibleScore() {
        return maximumPossibleScore;
    }

    /**
     * Get the current tipset snapshot.
     *
     * @return the current tipset snapshot
     */
    public @NonNull Tipset getSnapshot() {
        return snapshot;
    }

    /**
     * Add an event created by this node and compute the increase in tipset score. Higher score changes mean that this
     * event caused consensus to advance more. A score change of 0 means that this event did not advance consensus. A
     * score change close to the total weight means that this event did a very good job at advancing consensus. It's
     * impossible to get a perfect score, since the weight of advancing self events is not included. The maximum score
     * an event can achieve is equal to the sum of all weights minus the sum of this node's weight.
     *
     * @param event the event that is being added
     * @return the change in the tipset advancement score
     */
    public long addEventAndGetAdvancementScore(@NonNull final EventFingerprint event) {
        throwArgNull(event, "event");
        if (event.creator() != selfId) {
            throw new IllegalArgumentException("event creator must be the same as the window ID");
        }

        final Tipset eventTipset = builder.getTipset(event);
        if (eventTipset == null) {
            throw new IllegalArgumentException("event is not in the tipset tracker");
        }

        final long score = snapshot.getWeightedAdvancementCount(selfId, eventTipset);
        if (score > maximumPossibleScore) {
            throw new IllegalStateException(
                    "score " + score + " is greater than the maximum possible score " + maximumPossibleScore);
        }

        final long scoreImprovement = score - previousScore;

        if (isSuperMajority(score + selfWeight, totalWeight)) {
            snapshot = eventTipset;
            snapshotHistory.add(snapshot);
            if (snapshotHistory.size() > snapshotHistorySize) {
                snapshotHistory.remove();
            }
            previousScore = 0;
        } else {
            previousScore = score;
        }

        return scoreImprovement;
    }

    /**
     * Figure out what advancement score we would get if we created an event with a given list of parents.
     *
     * @param parents the proposed parents of an event
     * @return the advancement score we would get by creating an event with the given parents
     */
    public long getTheoreticalAdvancementScore(final List<EventFingerprint> parents) { // TODO test
        final List<Tipset> parentTipsets = new ArrayList<>(parents.size());
        for (final EventFingerprint parent : parents) {
            parentTipsets.add(builder.getTipset(parent));
        }

        // Don't bother advancing the self generation, since self advancement doesn't contribute to tipset score.
        final Tipset newTipset = Tipset.merge(parentTipsets);

        return snapshot.getWeightedAdvancementCount(selfId, newTipset);
    }

    /**
     * Compute the current maximum bully score with respect to all nodes. This is a measure of how well slow node's
     * events are being incorporated in the hashgraph by faster nodes. A high score means slow nodes are being bullied
     * by fast nodes. A low score means slow nodes are being included in consensus. Lower scores are better.
     *
     * @return the current tipset bully score
     */
    public int getBullyScore() { // TODO test
        int bullyScore = 0;
        for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
            bullyScore = Math.max(bullyScore, getBullyScoreForNodeIndex(nodeIndex));
        }
        return bullyScore;
    }

    /**
     * Get the bully score with respect to one node. A high bully score means that we have access to events that could
     * go into our ancestry, but for whatever reason we have decided not to put into our ancestry.
     *
     * @param nodeIndex the index of the node in question
     * @return the bully score with respect to this node
     */
    public int getBullyScoreForNodeIndex(final int nodeIndex) { // TODO test
        int bullyScore = 0;
        final long latestGeneration = builder.getLatestGenerationForNodeIndex(nodeIndex);

        Tipset previousTipset = snapshot;

        // Iterate backwards in time until we find an event from the node being added to our ancestry, or if
        // we find that there are no eligible nodes to be added to our ancestry.
        final Iterator<Tipset> iterator = snapshotHistory.descendingIterator();
        while (iterator.hasNext()) {
            final Tipset currentTipset = iterator.next();

            final long previousGeneration = previousTipset.getTipGenerationForNodeIndex(nodeIndex);
            final long currentGeneration = currentTipset.getTipGenerationForNodeIndex(nodeIndex);

            if (previousGeneration == latestGeneration || previousGeneration < currentGeneration) {
                // We stop increasing the bully score if we observe one of the two following events:
                //
                // 1) we find that the latest generation provided by a node matches a snapshot's generation
                //    (i.e. we've used all events provided by this creator as other parents)
                // 2) we observe an advancement between snapshots, which means that we have put one of this node's
                //    events into our ancestry.
                break;
            }

            bullyScore++;
            previousTipset = currentTipset;
        }

        return bullyScore;
    }
}
