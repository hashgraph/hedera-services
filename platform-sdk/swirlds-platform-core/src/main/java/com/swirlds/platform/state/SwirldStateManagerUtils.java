/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import static com.swirlds.common.utility.Units.NANOSECONDS_TO_MICROSECONDS;
import static com.swirlds.platform.components.transaction.throttle.TransThrottleSyncAndCreateRuleResponse.PASS;
import static com.swirlds.platform.components.transaction.throttle.TransThrottleSyncAndCreateRuleResponse.SYNC_AND_CREATE;

import com.swirlds.platform.components.transaction.throttle.TransThrottleSyncAndCreateRule;
import com.swirlds.platform.components.transaction.throttle.TransThrottleSyncAndCreateRuleResponse;
import com.swirlds.platform.metrics.SwirldStateMetrics;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * A utility class with useful methods for implementations of {@link SwirldStateManager}.
 */
public final class SwirldStateManagerUtils {

    // prevent instantiation of a static utility class
    private SwirldStateManagerUtils() {}

    /**
     * Performs a fast copy on a {@link State}. The {@code state} must not be modified during execution of this method.
     *
     * @param state
     * 		the state object to fast copy
     * @param stats
     * 		object to record stats in
     * @return the newly created state copy
     */
    public static State fastCopy(final State state, final SwirldStateMetrics stats) {
        final long copyStart = System.nanoTime();

        // Create a fast copy
        final State copy = state.copy();

        // Increment the reference count because this reference becomes the new value
        copy.reserve();

        final long copyEnd = System.nanoTime();

        stats.stateCopyMicros((copyEnd - copyStart) * NANOSECONDS_TO_MICROSECONDS);

        return copy;
    }

    /**
     * @see TransThrottleSyncAndCreateRule#shouldSyncAndCreate
     */
    public static TransThrottleSyncAndCreateRuleResponse shouldSyncAndCreate(final State consensusState) {
        // if current time is 1 minute before or during the freeze period, initiate a sync
        if (isInFreezePeriod(Instant.now().plus(1, ChronoUnit.MINUTES), consensusState)
                || isInFreezePeriod(Instant.now(), consensusState)) {
            return SYNC_AND_CREATE;
        } else {
            return PASS;
        }
    }

    /**
     * Determines if a {@code timestamp} is in a freeze period according to the provided state.
     *
     * @param timestamp
     * 		the timestamp to check
     * @param consensusState
     * 		the state that contains the freeze periods
     * @return true is the {@code timestamp} is in a freeze period
     */
    public static boolean isInFreezePeriod(final Instant timestamp, final State consensusState) {
        final PlatformDualState dualState = consensusState.getPlatformDualState();
        return dualState != null && dualState.isInFreezePeriod(timestamp);
    }
}
