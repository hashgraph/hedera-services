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

package com.swirlds.platform.chatter.protocol.output;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Calculates the delay for sending other events to a peer.
 */
public class OtherEventDelay {
    private final Supplier<Long> heartbeatRoundTrip;
    private final Supplier<Long> peerProcessingTime;
    private final Duration constantDelay;

    /**
     * @param heartbeatRoundTrip
     * 		latest round trip time in nanos, or null if no value is available
     * @param peerProcessingTime
     * 		the event processing time of the peer in nanoseconds, or null if it is not available yet
     * @param constantDelay
     * 		a constant delay to add on top of all calculation
     */
    public OtherEventDelay(
            final Supplier<Long> heartbeatRoundTrip,
            final Supplier<Long> peerProcessingTime,
            final Duration constantDelay) {
        this.heartbeatRoundTrip = heartbeatRoundTrip;
        this.peerProcessingTime = peerProcessingTime;
        this.constantDelay = constantDelay;
    }

    /**
     * @return the other event delay to use, or null if other events should not be sent to the peer at this time
     */
    public Duration getOtherEventDelay() {
        final Long peerProcessingNanos = peerProcessingTime.get();
        final Long roundTripNanos = heartbeatRoundTrip.get();
        if (peerProcessingNanos == null || roundTripNanos == null) {
            return null;
        }
        final long oneWayTripNanos = roundTripNanos / 2;
        return Duration.ofNanos(oneWayTripNanos + peerProcessingNanos).plus(constantDelay);
    }
}
