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

package com.swirlds.common;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This utility class provides methods for reading the time that a node was started.
 */
public final class StartupTime {

    private static final AtomicReference<Instant> startupTime = new AtomicReference<>(null);

    private StartupTime() {}

    /**
     * This method must be called when a node originally starts. Must be called prior to spawning of threads
     * that may read startup time. If called multiple times (for example, if there are multiple local nodes)
     * then only the first call will assign the startup time.
     */
    public static void markStartupTime() {
        startupTime.compareAndSet(null, Instant.now());
    }

    /**
     * Get the time that this node was started. Exact instant is captured at some time when the JVM is starting.
     *
     * @return the time when this node started
     */
    public static Instant getStartupTime() {
        final Instant time = startupTime.get();
        if (time == null) {
            throw new IllegalStateException("startup time not marked");
        }
        return time;
    }

    /**
     * Get the time since this node was started. Exact instant is captured at some time when the JVM is starting.
     *
     * @return the time since when this node started
     */
    public static Duration getTimeSinceStartup() {
        return Duration.between(getStartupTime(), Instant.now());
    }
}
