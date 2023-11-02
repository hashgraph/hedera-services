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

package com.swirlds.logging.internal.format;

import com.swirlds.logging.Level;
import com.swirlds.logging.Marker;
import com.swirlds.logging.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.extensions.emergency.EmergencyLoggerProvider;
import com.swirlds.logging.extensions.event.LogEvent;
import com.swirlds.logging.extensions.event.LogMessage;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

/**
 * This class is used to format a {@link LogEvent} as a line of text and write it to a {@link PrintWriter}.
 */
public class LineBasedFormat {

    /**
     * The emergency logger.
     */
    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    /**
     * The formatter used to format the timestamp.
     */
    private final DateTimeFormatter formatter;

    /**
     * The underlying {@link PrintWriter}.
     */
    private final PrintWriter printWriter;

    /**
     * Constructs a new instance of this class.
     *
     * @param printWriter
     */
    public LineBasedFormat(@NonNull final PrintWriter printWriter) {
        this.formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());
        this.printWriter = Objects.requireNonNull(printWriter, "printWriter must not be null");
    }

    /**
     * Prints the given event to the underlying {@link PrintWriter}.
     *
     * @param event the event to print
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

        final Marker marker = event.marker();
        if (marker != null) {
            printWriter.print(" - M:");
            printWriter.print(asString(marker));
        }

        final Map<String, String> context = event.context();
        if (context != null && !context.isEmpty()) {
            printWriter.print(" - C:");
            printWriter.print(context);
        }
        printWriter.println();

        final Throwable throwable = event.throwable();
        if (throwable != null) {
            throwable.printStackTrace(printWriter);
        }
    }

    /**
     * Returns the given string or a default value if the given string is {@code null}.
     *
     * @param str    the string to return or a default value if the given string is {@code null}
     * @param suffix the suffix to append to the default value
     * @return the given string or a default value if the given string is {@code null}
     */
    @NonNull
    private String asString(@Nullable final String str, @NonNull final String suffix) {
        if (str == null) {
            return "UNDEFINED-" + suffix;
        } else {
            return str;
        }
    }

    /**
     * Returns the given level as a string or {@code UNDEFINED} if the given level is {@code null}.
     *
     * @param level the level to return as a string or {@code UNDEFINED} if the given level is {@code null}
     * @return the given level as a string or {@code UNDEFINED} if the given level is {@code null}
     */
    @NonNull
    private String asString(@Nullable final Level level) {
        if (level == null) {
            return "UNDEFINED";
        } else {
            return level.name();
        }
    }

    /**
     * Returns the given message as a string or {@code UNDEFINED-MESSAGE} if the given message is {@code null}.
     *
     * @param message the message to return as a string or {@code UNDEFINED-MESSAGE} if the given message is
     *                {@code null}
     * @return the given message as a string or {@code UNDEFINED-MESSAGE} if the given message is {@code null}
     */
    @NonNull
    private String asString(@Nullable final LogMessage message) {
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
     * Returns the given instant as a string or {@code UNDEFINED-TIMESTAMP} if the given instant is {@code null}.
     *
     * @param instant the instant to return as a string or {@code UNDEFINED-TIMESTAMP} if the given instant is
     *                {@code null}
     * @return the given instant as a string or {@code UNDEFINED-TIMESTAMP} if the given instant is {@code null}
     */
    @NonNull
    private String asString(@Nullable final Instant instant) {
        if (instant == null) {
            return "UNDEFINED-TIMESTAMP       ";
        } else {
            try {
                return formatter.format(instant);
            } catch (final Throwable e) {
                EMERGENCY_LOGGER.log(Level.ERROR, "Failed to format instant", e);
                return "BROKEN-TIMESTAMP          ";
            }
        }
    }

    /**
     * Returns the given marker as a string or {@code null} if the given marker is {@code null}.
     *
     * @param marker the marker to return as a string or {@code null} if the given marker is {@code null}
     * @return the given marker as a string or {@code null} if the given marker is {@code null}
     */
    @NonNull
    private String asString(@Nullable final Marker marker) {
        if (marker == null) {
            return "null";
        } else {
            final Marker parent = marker.parent();
            if (parent == null) {
                return "Marker{name='" + marker.name() + "'}";
            } else {
                return "Marker{name='" + marker.name() + "', parent='" + asString(parent) + "'}";
            }
        }
    }
}
