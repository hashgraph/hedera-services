/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.base.time;

import com.swirlds.base.time.internal.NanoClock;
import com.swirlds.base.time.internal.OSTime;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Clock;

/**
 * Facade for all time functionality that is part of the base module
 */
public final class TimeFacade {

    private static final Clock nanoClock = new NanoClock();

    private TimeFacade() {
    }

    /**
     * Returns an implementation of {@link Time} that will return the true wall clock time (according to the OS).
     *
     * @return implementation of {@link Time} that will return the true wall clock time (according to the OS)
     */
    @NonNull
    public static Time getOsTime() {
        return OSTime.getInstance();
    }

    /**
     * Returns a clock that is accurate to the nanosecond, as compared to the standard Java "Instant" clock, which is
     * only accurate to the nearest millisecond.
     *
     * @return a clock that is accurate to the nanosecond
     */
    @NonNull
    public static Clock getNanoClock() {
        return nanoClock;
    }
}
