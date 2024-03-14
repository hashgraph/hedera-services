/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.logging.api.extensions.event;

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Marker;
import com.swirlds.logging.api.internal.event.SimpleLogMessage;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;

/**
 * A factory that creates {@link LogEvent}s.
 */
public interface LogEventFactory {

    /**
     * Creates a new log event.
     *
     * @param level      the log level
     * @param loggerName the name of the logger
     * @param threadName the name of the thread
     * @param timestamp  the timestamp
     * @param message    the log message
     * @param throwable  the throwable
     * @param marker     the marker
     * @param context    the context
     * @return the new log event
     */
    @NonNull
    LogEvent createLogEvent(
            @NonNull Level level,
            @NonNull String loggerName,
            @NonNull String threadName,
            long timestamp,
            @NonNull LogMessage message,
            @Nullable Throwable throwable,
            @Nullable Marker marker,
            @Nullable Map<String, String> context);

    /**
     * Creates a new log event.
     *
     * @param level      the log level
     * @param loggerName the name of the logger
     * @param message    the log message
     * @param throwable  the throwable
     * @param marker     the marker
     * @param context    the context
     * @return the new log event
     */
    @NonNull
    default LogEvent createLogEvent(
            @NonNull Level level,
            @NonNull String loggerName,
            @NonNull LogMessage message,
            @Nullable Throwable throwable,
            @Nullable Marker marker,
            @NonNull Map<String, String> context) {
        return createLogEvent(
                level,
                loggerName,
                Thread.currentThread().getName(),
                System.currentTimeMillis(),
                message,
                throwable,
                marker,
                context);
    }

    /**
     * Creates a new log event.
     *
     * @param level      the log level
     * @param loggerName the name of the logger
     * @param threadName the name of the thread
     * @param timestamp  the timestamp
     * @param message    the log message
     * @param throwable  the throwable
     * @param marker     the marker
     * @param context    the context
     * @return the new log event
     */
    @NonNull
    default LogEvent createLogEvent(
            @NonNull Level level,
            @NonNull String loggerName,
            @NonNull String threadName,
            long timestamp,
            @NonNull String message,
            @Nullable Throwable throwable,
            @Nullable Marker marker,
            @NonNull Map<String, String> context) {
        return createLogEvent(
                level, loggerName, threadName, timestamp, new SimpleLogMessage(message), throwable, marker, context);
    }

    /**
     * Creates a new log event.
     *
     * @param level      the log level
     * @param loggerName the name of the logger
     * @param message    the log message
     * @return the new log event
     */
    @NonNull
    default LogEvent createLogEvent(@NonNull Level level, @NonNull String loggerName, @NonNull String message) {
        return createLogEvent(level, loggerName, message, null);
    }

    /**
     * Creates a new log event.
     *
     * @param level      the log level
     * @param loggerName the name of the logger
     * @param message    the log message
     * @param throwable  the throwable
     * @return the new log event
     */
    @NonNull
    default LogEvent createLogEvent(
            @NonNull Level level, @NonNull String loggerName, @NonNull String message, @Nullable Throwable throwable) {
        return createLogEvent(
                level,
                loggerName,
                Thread.currentThread().getName(),
                System.currentTimeMillis(),
                new SimpleLogMessage(message),
                throwable,
                null,
                Map.of());
    }
}
