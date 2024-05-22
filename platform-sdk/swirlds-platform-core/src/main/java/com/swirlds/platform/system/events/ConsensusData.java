/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system.events;

import com.swirlds.base.utility.ToStringBuilder;
import java.util.Objects;

/**
 * A class used to store consensus data about an event.
 * <p>
 * This data is available for an event only after consensus has been determined for it. When an event is initially
 * created, there is no consensus data for it.
 */
public class ConsensusData {
    private static final int CLASS_VERSION_REMOVED_WITNESS_FAMOUS = 2;
    public static final int CLASS_VERSION = CLASS_VERSION_REMOVED_WITNESS_FAMOUS;

    /** Value used to indicate that consensus has not been reached */
    public static final long NO_CONSENSUS = -1;

    /* if isConsensus, round where >=1/2 famous see me */
    private long roundReceived = NO_CONSENSUS;
    /** is this event the last in consensus order of all those with the same received round? */
    private boolean lastInRoundReceived = false;

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ConsensusData that = (ConsensusData) o;

        return (roundReceived == that.roundReceived) && (lastInRoundReceived == that.lastInRoundReceived);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roundReceived, lastInRoundReceived);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("roundReceived", roundReceived)
                .append("lastInRoundReceived", lastInRoundReceived)
                .toString();
    }

    public long getRoundReceived() {
        return roundReceived;
    }

    public void setRoundReceived(long roundReceived) {
        this.roundReceived = roundReceived;
    }

    public boolean isLastInRoundReceived() {
        return lastInRoundReceived;
    }

    public void setLastInRoundReceived(boolean lastInRoundReceived) {
        this.lastInRoundReceived = lastInRoundReceived;
    }
}
