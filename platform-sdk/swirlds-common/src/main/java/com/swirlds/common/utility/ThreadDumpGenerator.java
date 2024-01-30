/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.swirlds.logging.legacy.LogMarker.STARTUP;

import com.swirlds.base.time.Time;
import com.swirlds.common.utility.throttle.RateLimiter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A utility class for generating thread dumps.
 */
public class ThreadDumpGenerator {

    private static final Logger logger = LogManager.getLogger(ThreadDumpGenerator.class);

    private final RateLimiter rateLimiter;

    /**
     * Constructor.
     *
     * @param time          provides wall clock time
     * @param minimumPeriod the minimum period between thread dumps that are written to the log
     */
    public ThreadDumpGenerator(@NonNull final Time time, @NonNull final Duration minimumPeriod) {
        Objects.requireNonNull(time);
        Objects.requireNonNull(minimumPeriod);

        if (minimumPeriod.isNegative()) {
            throw new IllegalArgumentException("minimumPeriod must be non-negative");
        }

        rateLimiter = new RateLimiter(time, minimumPeriod);
    }

    /**
     * Appends a thread dump to the given string builder.
     *
     * @param sb the string builder to append the thread dump to
     */
    public static void appendThreadDump(@NonNull final StringBuilder sb) {
        final Map<Thread, StackTraceElement[]> threadDumps = Thread.getAllStackTraces();

        for (final Map.Entry<Thread, StackTraceElement[]> entry : threadDumps.entrySet()) {
            final Thread thread = entry.getKey();
            final StackTraceElement[] frames = entry.getValue();
            final StackTrace stackTrace = new StackTrace(frames);

            sb.append(thread.getName())
                    .append(" #")
                    .append(thread.threadId())
                    .append(thread.isDaemon() ? " daemon" : "")
                    .append(" prio=")
                    .append(thread.getPriority())
                    .append(" tid=")
                    .append(thread.getId())
                    .append(" ")
                    .append(thread.getState())
                    .append("\n");
            sb.append("   ").append(stackTrace).append("\n");
        }
    }

    /**
     * Log a thread dump. Respects rate limit, if called very frequently then will not always log a thread dump.
     */
    public synchronized void logThreadDump() {
        if (!rateLimiter.requestAndTrigger()) {
            // We've logged a stack trace too recently. Don't log another one.
            return;
        }

        final StringBuilder sb = new StringBuilder();
        appendThreadDump(sb);
        logger.info(STARTUP.getMarker(), sb);
    }
}
