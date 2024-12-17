/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event;

import static com.swirlds.platform.consensus.ConsensusConstants.MIN_TRANS_TIMESTAMP_INCR_NANOS;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Utility methods for events.
 */
public final class EventUtils {
    /**
     * Hidden constructor
     */
    private EventUtils() {}

    /**
     * Calculate the creation time for a new event.
     * <p>
     * Regardless of whatever the host computer's clock says, the event creation time must always advance from self
     * parent to child. Further, the time in between the self parent and the child must be large enough so that every
     * transaction in the parent can be assigned a unique timestamp at nanosecond precision.
     *
     * @param now                        the current time
     * @param selfParentCreationTime     the creation time of the self parent
     * @param selfParentTransactionCount the number of transactions in the self parent
     * @return the creation time for the new event
     */
    @NonNull
    public static Instant calculateNewEventCreationTime(
            @NonNull final Instant now,
            @NonNull final Instant selfParentCreationTime,
            final int selfParentTransactionCount) {

        final int minimumIncrement = Math.max(1, selfParentTransactionCount);
        final Instant minimumNextEventTime = selfParentCreationTime.plusNanos(minimumIncrement);
        if (now.isBefore(minimumNextEventTime)) {
            return minimumNextEventTime;
        } else {
            return now;
        }
    }

    /**
     * Returns the timestamp of the transaction with given index in this event
     *
     * @param event            the event to get the transaction time from
     * @param transactionIndex index of the transaction in this event
     * @return timestamp of the given index transaction
     */
    public static @NonNull Instant getTransactionTime(@NonNull final PlatformEvent event, final int transactionIndex) {
        if (event.getConsensusTimestamp() == null) {
            throw new IllegalArgumentException("Event is not a consensus event");
        }
        if (transactionIndex >= event.getTransactionCount()) {
            throw new IllegalArgumentException("Event does not have a transaction with index: " + transactionIndex);
        }
        return event.getConsensusTimestamp().plusNanos(transactionIndex * MIN_TRANS_TIMESTAMP_INCR_NANOS);
    }

    /**
     * Returns the timestamp of the last transaction in this event. If this event has no transaction, then the timestamp
     * of the event will be returned
     *
     * @param event the event to get the transaction time from
     * @return timestamp of the last transaction
     */
    public static @NonNull Instant getLastTransTime(@NonNull final PlatformEvent event) {
        if (event.getConsensusTimestamp() == null) {
            throw new IllegalArgumentException("Event is not a consensus event");
        }
        // this is a special case. if an event has 0 or 1 transactions, the timestamp of the last transaction can be
        // considered to be the same, equivalent to the timestamp of the event
        if (event.getTransactionCount() <= 1) {
            return event.getConsensusTimestamp();
        }
        return getTransactionTime(event, event.getTransactionCount() - 1);
    }
}
