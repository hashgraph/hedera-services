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

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.IntToLongFunction;
import java.util.function.LongToIntFunction;

/**
 * A window into a node's view of tipsets.
 */
public class TipsetWindow {

    /**
     * The node ID that is being tracked by this window.
     */
    private final long selfId;

    /**
     * Tracks tipsets for each event. Is maintained outside this object.
     */
    private final TipsetTracker tracker;

    /**
     * The current tipset snapshot.
     */
    private Tipset snapshot;

    /**
     * The N most recent snapshots.
     */
    private Queue<Tipset> snapshotHistory = new LinkedList<>();

    /**
     * The number of snapshots to keep in {@link #snapshotHistory}.
     */
    private final int snapshotHistorySize = 10; // TODO setting

    /**
     * The total weight of all nodes.
     */
    private final long totalWeight;

    /**
     * The weight of the node tracked by this window.
     */
    private final long selfWeight;

    /**
     * The maximum possible score for an event.
     */
    private final long maximumPossibleScore;

    /**
     * The previous tipset score.
     */
    private long previousScore = 0;

    /**
     * Create a new tipset window.
     *
     * @param selfId      the ID of the node tracked by this window
     * @param tracker       tracks tipsets for individual events
     * @param nodeCount     the number of nodes in the address book
     * @param nodeIdToIndex maps node ID to node index
     * @param indexToWeight maps node index to consensus weight
     * @param totalWeight   the sum of all weight
     */
    public TipsetWindow(
            final long selfId,
            final TipsetTracker tracker,
            final int nodeCount,
            final LongToIntFunction nodeIdToIndex,
            final IntToLongFunction indexToWeight,
            final long totalWeight) {

        snapshot = new Tipset(nodeCount, nodeIdToIndex, indexToWeight);

        this.selfId = selfId;
        this.tracker = tracker;
        this.totalWeight = totalWeight;
        this.selfWeight = indexToWeight.applyAsLong(nodeIdToIndex.applyAsInt(selfId));
        this.maximumPossibleScore = totalWeight - selfWeight;
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
    public Tipset getSnapshot() {
        return snapshot;
    }

    /**
     * Add an event created by this window's node and compute the increase in tipset score. Higher score changes mean
     * that this event caused consensus to advance more. A score change of 0 means that this event did not advance
     * consensus. A score change close to the total weight means that this event did a very good job at advancing
     * consensus. It's impossible to get a perfect score, since the weight of advancing self events is not included. The
     * maximum score an event can achieve is equal to the sum of all weights minus the sum of this node's weight.
     *
     * @param event the event that is being added
     * @return the change in the tipset advancement score
     */
    public long addEvent(final EventFingerprint event) {
        throwArgNull(event, "event");
        if (event.creator() != selfId) {
            throw new IllegalArgumentException("event creator must be the same as the window ID");
        }

        final Tipset eventTipset = tracker.getTipset(event);
        if (eventTipset == null) {
            throw new IllegalArgumentException("event is not in the tipset tracker");
        }

        final long score = snapshot.getAdvancementCount(selfId, eventTipset);
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
     * Compute the current tipset bully score. This is a measure of how well slow node's events are being incorporated
     * in the hashgraph by faster nodes. A high score means slow nodes are being bullied by fast nodes. A low score
     * means slow nodes are being included in consensus. Lower scores are better.
     *
     * @return the current tipset bully score
     */
    public int getBullyScore() {
        return 0; // TODO
    }

    // FUTURE WORK: create mechanisms for computing theoretical advancement scores depending on other parent choice

}
