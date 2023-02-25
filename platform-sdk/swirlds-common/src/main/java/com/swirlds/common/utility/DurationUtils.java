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

package com.swirlds.common.utility;

import java.time.Duration;

/**
 * Utilities for dealing with {@link Duration}
 */
public final class DurationUtils {
    private DurationUtils() {}

    /**
     * @param a
     * 		the first duration to compare
     * @param b
     * 		the second duration to compare
     * @return true if 'a' is longer than 'b', false if 'a' is shorter or equal
     */
    public static boolean isLonger(final Duration a, final Duration b) {
        return a.compareTo(b) > 0;
    }

    /**
     * @param a
     * 		the first duration
     * @param b
     * 		the second duration
     * @return the longer of the two durations
     */
    public static Duration max(final Duration a, final Duration b) {
        return isLonger(a, b) ? a : b;
    }
}
