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

package com.swirlds.base.time;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * An API for getting the time. All platform code should utilize this API instead of the raw standard java time APIs.
 * This makes it much easier to simulate time in test environments.
 */
public interface Time {

    /**
     * A method that returns the time in nanoseconds. May not start at the epoch. Equivalent to {@link
     * System#nanoTime()}.
     *
     * @return the current relative time in nanoseconds
     */
    long nanoTime();

    /**
     * A method that returns the current time in milliseconds since the epoch. Equivalent to {@link
     * System#currentTimeMillis()}.
     *
     * @return the current time since the epoch in milliseconds
     */
    long currentTimeMillis();

    /**
     * Returns the current time, relative to the epoch. Equivalent to {@link Instant#now()}.
     *
     * @return the curren time relative to the epoch
     */
    @NonNull
    Instant now();
}
