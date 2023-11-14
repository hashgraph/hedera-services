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

package com.swirlds.logging.api.internal.format;

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Marker;
import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.event.LogMessage;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

/**
 * A utility class that formats a {@link LogEvent} as a line based format.
 */
public class LineBasedFormat {

    /**
     * The emergency logger.
     */
    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    /**
     * The formatter for the timestamp.
     */
    private final DateTimeFormatter formatter;

    /**
     * The print writer that is used to print the log event.
     */
    private final PrintWriter printWriter;

    /**
     * Creates a new line based format.
     *
     * @param printWriter The print writer that is used to print the log event.
     */
    public LineBasedFormat(@NonNull final PrintWriter printWriter) {
        this.formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());
        this.printWriter = Objects.requireNonNull(printWriter, "printWriter must not be null");
    }

    /**
     * Converts the given object to a string. If the object is {@code null}, the given default value is used.
     *
     * @param event
     */
    public void print(@NonNull final LogEvent event) {
        if (event == null) {
            EMERGENCY_LOGGER.logNPE("event");
        }
        printWriter.print(asString(event.timestamp()));
        printWriter.print(' ');
        printWriter.print(asString(event.level()));
        printWriter.print(' ');
        printWriter.print('[');
        printWriter.print(asString(event.threadName(), "THREAD"));
        printWriter.print(']');
        printWriter.print(' ');
        printWriter.print(asString(event.loggerName(), "LOGGER"));
        printWriter.print(" - ");
        printWriter.print(asString(event.message()));

        Marker marker = event.marker();
        if (marker != null) {
            printWriter.print(" - [M:");
            printWriter.print(asString(marker));
            printWriter.print("]");
        }

        final Map<String, String> context = event.context();
        if (context != null && !context.isEmpty()) {
            printWriter.print(" - C:");
            printWriter.print(context);
        }
        printWriter.println();

        Throwable throwable = event.throwable();
        if (throwable != null) {
            throwable.printStackTrace(printWriter);
        }
    }

    /**
     * Converts the given object to a string.
     *
     * @param str    The string
     * @param suffix The suffix that is used if the string is {@code null}
     * @return The string
     */
    private String asString(String str, String suffix) {
        if (str == null) {
            return "UNDEFINED-" + suffix;
        } else {
            return str;
        }
    }

    /**
     * Converts the given object to a string.
     *
     * @param level The level
     * @return The string
     */
    private String asString(Level level) {
        if (level == null) {
            return "UNDEFINED";
        } else {
            return "%-5s".formatted(level.name());
        }
    }

    /**
     * Converts the given object to a string.
     *
     * @param message The message
     * @return The string
     */
    private String asString(LogMessage message) {
        if (message == null) {
            return "UNDEFINED-MESSAGE";
        } else {
            try {
                return message.getMessage();
            } catch (final Throwable e) {
                EMERGENCY_LOGGER.log(Level.ERROR, "Failed to format message", e);
                return "BROKEN-MESSAGE";
            }
        }
    }

    /**
     * Converts the given object to a string.
     *
     * @param instant The instant
     * @return The string
     */
    private String asString(Instant instant) {
        if (instant == null) {
            return "UNDEFINED-TIMESTAMP       ";
        } else {
            try {
                return "%-26s".formatted(formatter.format(instant));
            } catch (final Throwable e) {
                EMERGENCY_LOGGER.log(Level.ERROR, "Failed to format instant", e);
                return "BROKEN-TIMESTAMP          ";
            }
        }
    }

    /**
     * Converts the given object to a string.
     *
     * @param marker The marker
     * @return The string
     */
    private String asString(@Nullable final Marker marker) {
        if (marker == null) {
            return "null";
        } else {
            return String.join(", ", marker.getAllMarkerNames());

        }
    }
}
