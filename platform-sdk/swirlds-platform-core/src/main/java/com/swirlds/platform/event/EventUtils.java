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
}
