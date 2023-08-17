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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Objects;

/**
 * A timestamp based lower bound on event streams.
 */
public class EventStreamTimestampLowerBound implements EventStreamLowerBound {

    /** the timestamp of the bound */
    private final Instant timestamp;

    /**
     * Create an event stream bound with the specified timestamp.
     *
     * @param timestamp the timestamp
     */
    public EventStreamTimestampLowerBound(@NonNull final Instant timestamp) {
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(ConsensusData consensusData) {
        return consensusData.getConsensusTimestamp().compareTo(timestamp);
    }
}
