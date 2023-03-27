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

package com.swirlds.base.time.internal;

import com.swirlds.base.ArgumentUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A clock that is accurate to the nanosecond, as compared to the standard Java "Instant" clock, which is only accurate
 * to the nearest millisecond.
 */
public final class NanoClock extends Clock {

    private final Clock clock;
    private final long initialNanos;
    private final Instant initialInstant;

    public NanoClock() {
        this(Clock.systemUTC());
    }

    public NanoClock(@NonNull final Clock clock) {
        this.clock = ArgumentUtils.throwArgNull(clock, "clock");
        initialInstant = clock.instant();
        initialNanos = getSystemNanos();
    }

    @Override
    @NonNull
    public ZoneId getZone() {
        return clock.getZone();
    }

    @Override
    @NonNull
    public Instant instant() {
        return initialInstant.plusNanos(getSystemNanos() - initialNanos);
    }

    @Override
    @NonNull
    public Clock withZone(final @NonNull ZoneId zone) {
        ArgumentUtils.throwArgNull(zone, "zone");
        return new NanoClock(clock.withZone(zone));
    }

    private static long getSystemNanos() {
        return System.nanoTime();
    }
}
