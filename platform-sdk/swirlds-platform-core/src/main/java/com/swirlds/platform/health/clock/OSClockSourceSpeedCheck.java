/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.health.clock;

import java.time.Instant;

/**
 * Checks the speed of the operating system's clock source
 */
public final class OSClockSourceSpeedCheck {

    private OSClockSourceSpeedCheck() {}

    /**
     * Get the speed of the {@link Instant#now()} operation by counting the number of calls achieved in 1 second.
     *
     * @return the clock source speed
     */
    public static Report execute() {
        int numCalls = 0;

        // count the number of calls achieved in 1 second
        final Instant deadline = Instant.now().plusSeconds(1);
        while (Instant.now().compareTo(deadline) < 0) {
            numCalls++;
        }

        return new Report(numCalls);
    }

    /**
     * Contains data about the speed of the OS clock source
     *
     * @param callsPerSec
     * 		the calls per second achieved
     */
    public record Report(long callsPerSec) {

        private static final String NAME = "Clock Source Speed Check";

        /**
         * @return the name of the check this report applies to
         */
        public static String name() {
            return NAME;
        }
    }
}
