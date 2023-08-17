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

package com.swirlds.platform.recovery.events;

import com.swirlds.common.system.events.ConsensusData;

/**
 * A round based lower bound on an event stream.
 */
public class EventStreamRoundLowerBound implements EventStreamLowerBound {

    /** the round of the lower bound */
    private final long round;

    /**
     * Create an event stream round lower bound with the specified round.
     *
     * @param round     the round
     * @throws IllegalArgumentException if the round is less than 1
     */
    public EventStreamRoundLowerBound(final long round) {
        if (round < 1) {
            throw new IllegalArgumentException("round must be >= 1");
        }
        this.round = round;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(ConsensusData consensusData) {
        return Long.compare(consensusData.getRoundReceived(), round);
    }

    /**
     * get the round of the lower bound.
     *
     * @return the round of the lower bound.
     */
    public long getRound() {
        return round;
    }
}
