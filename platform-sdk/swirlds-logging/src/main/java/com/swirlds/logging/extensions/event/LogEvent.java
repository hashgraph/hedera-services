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

package com.swirlds.logging.extensions.event;

import com.swirlds.logging.Level;
import com.swirlds.logging.Marker;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Map;

/**
 * A log event that is passed to the {@link LogEventConsumer} for processing.
 *
 * @param level      The log level
 * @param loggerName The name of the logger
 * @param threadName The name of the thread
 * @param timestamp  The timestamp of the log event
 * @param message    The log message (this is not a String since the message can be parameterized. See
 *                   {@link LogMessage} for more details).
 * @param throwable  The throwable
 * @param marker     The marker
 * @param context    The context
 */
public record LogEvent(
        @NonNull Level level,
        @NonNull String loggerName,
        @NonNull String threadName,
        @NonNull Instant timestamp,
        @NonNull LogMessage message,
        @Nullable Throwable throwable,
        @Nullable Marker marker,
        @NonNull Map<String, String> context) {

    public LogEvent(
            @NonNull final Level level,
            @NonNull final String loggerName,
            @NonNull final String threadName,
            @NonNull final Instant timestamp,
            @NonNull final String message,
            @Nullable final Throwable throwable,
            @Nullable final Marker marker,
            @NonNull final Map<String, String> context) {
        this(level, loggerName, threadName, timestamp, new SimpleLogMessage(message), throwable, marker, context);
    }

    public LogEvent(@NonNull final Level level, @NonNull final String loggerName, @NonNull final String message) {
        this(level, loggerName, message, null);
    }

    public LogEvent(
            @NonNull final Level level,
            @NonNull final String loggerName,
            @NonNull final String message,
            @Nullable final Throwable throwable) {
        this(
                level,
                loggerName,
                Thread.currentThread().getName(),
                Instant.now(),
                new SimpleLogMessage(message),
                throwable,
                null,
                Map.of());
    }

    /**
     * Creates a new {@link LogEvent} that has all parameters of the given logEvent but a different context.
     *
     * @param logEvent the logEvent that should be copied (excluding the context)
     * @param context  the new context
     * @return the new copy of the event
     */
    @NonNull
    public static LogEvent createCopyWithDifferentContext(
            @NonNull final LogEvent logEvent, @NonNull final Map<String, String> context) {
        return new LogEvent(
                logEvent.level,
                logEvent.loggerName,
                logEvent.threadName,
                logEvent.timestamp,
                logEvent.message,
                logEvent.throwable,
                logEvent.marker,
                context);
    }

    /**
     * Creates a new {@link LogEvent} that has all parameters of the given logEvent but a different loggerName.
     *
     * @param logEvent   the logEvent that should be copied (excluding the loggerName)
     * @param loggerName the new logger name
     * @return the new copy of the event
     */
    @NonNull
    public static LogEvent createCopyWithDifferentName(
            @NonNull final LogEvent logEvent, @NonNull final String loggerName) {
        return new LogEvent(
                logEvent.level,
                loggerName,
                logEvent.threadName,
                logEvent.timestamp,
                logEvent.message,
                logEvent.throwable,
                logEvent.marker,
                logEvent.context);
    }
}
