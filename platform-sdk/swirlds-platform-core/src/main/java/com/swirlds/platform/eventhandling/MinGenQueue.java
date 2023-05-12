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

package com.swirlds.platform.eventhandling;

import com.swirlds.platform.state.MinGenInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * A concurrent queue that keeps round generation information for rounds that have had fame decided.
 */
public class MinGenQueue {
    /** A deque used by doCons to store the minimum generation of famous witnesses per round */
    private final Deque<MinGenInfo> queue = new ConcurrentLinkedDeque<>();

    /**
     * Add round generation information
     *
     * @param round
     * 		the round
     * @param minGeneration
     * 		the minimum generation of all famous witnesses for that round
     */
    public void add(final long round, final long minGeneration) {
        add(new MinGenInfo(round, minGeneration));
    }

    /**
     * Same as {@link #add(long, long)}, where the key is the round and the value is the generation
     *
     * @param pair
     * 		a round/generation pair
     */
    public void add(final MinGenInfo pair) {
        queue.add(pair);
    }

    /**
     * Get an ordered list of all round/generation pairs up to and including maxRound
     *
     * @param maxRound
     * 		the maximum round included in the list
     * @return round/generation pairs
     */
    public List<MinGenInfo> getList(final long maxRound) {
        final List<MinGenInfo> list = new ArrayList<>();
        for (final MinGenInfo next : queue) {
            if (next.round() <= maxRound) {
                list.add(next);
            } else {
                break;
            }
        }
        return list;
    }

    /**
     * Return the generation for the specified round
     *
     * @param round
     * 		the round queried
     * @return the round generation
     * @throws NoSuchElementException
     * 		if the round is not in the queue
     */
    public long getRoundGeneration(final long round) {
        for (final MinGenInfo minGenInfo : queue) {
            if (minGenInfo.round() == round) {
                return minGenInfo.minimumGeneration();
            }
        }
        throw new NoSuchElementException("Missing generation for round " + round);
    }

    /**
     * Remove all rounds smaller then the one specified
     *
     * @param round
     * 		the highest round to keep
     */
    public void expire(final long round) {
        MinGenInfo minGenInfo = queue.peekFirst();

        // remove old min gen info we no longer need
        while (!queue.isEmpty() && minGenInfo.round() < round) {
            queue.pollFirst();
            minGenInfo = queue.peekFirst();
        }
    }

    /**
     * Same as {@link #add(MinGenInfo)} but for a whole collection
     *
     * @param collection
     * 		the collection to add
     */
    public void addAll(final Collection<MinGenInfo> collection) {
        queue.addAll(collection);
    }

    /**
     * Clear all the data
     */
    public void clear() {
        queue.clear();
    }
}
