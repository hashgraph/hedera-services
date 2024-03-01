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

package com.swirlds.logging.api.internal.format;

import static java.util.Objects.requireNonNullElse;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Marker;
import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.event.LogMessage;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.Objects;

/**
 * A utility class that formats a {@link LogEvent} as a line based format.
 */
public class LineBasedFormat {

    private static final String THREAD_SUFFIX = "UNDEFINED-THREAD";
    private static final String LOGGER_SUFFIX = "UNDEFINED-LOGGER";
    private static final String UNDEFINED_MESSAGE = "UNDEFINED-MESSAGE";
    private static final String BROKEN_MESSAGE = "BROKEN-MESSAGE";
    /**
     * The emergency logger.
     */
    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    private final boolean formatTimestamp;

    public LineBasedFormat(boolean formatTimestamp) {
        this.formatTimestamp = formatTimestamp;
    }

    /**
     * Converts the given object to a string. If the object is {@code null}, the given default value is used.
     */
    public void print(@Nullable final Appendable writer, @Nullable final LogEvent event) {
        if (writer == null) {
            EMERGENCY_LOGGER.logNPE("printer");
            return;
        }
        if (event == null) {
            EMERGENCY_LOGGER.logNPE("event");
            return;
        }
        try {
            if (formatTimestamp) {
                writer.append(DateFormatUtils.timestampAsString(event.timestamp()));
            } else {
                writer.append(Long.toString(event.timestamp()));
            }
            writer.append(' ');
            writer.append(asString(event.level()));
            writer.append(" [");
            writer.append(requireNonNullElse(event.threadName(), THREAD_SUFFIX));
            writer.append("] ");
            writer.append(requireNonNullElse(event.loggerName(), LOGGER_SUFFIX));
            writer.append(" - ");
            writer.append(asString(event.message()));

            Marker marker = event.marker();
            if (marker != null) {
                writer.append(" - [");
                writer.append(asString(marker));
                writer.append("]");
            }

            final Map<String, String> context;
            context = event.context();
            if (context != null && !context.isEmpty()) {
                writer.append(" - ");
                writer.append(context.toString());
            }
            writer.append(System.lineSeparator());

            Throwable throwable = event.throwable();
            if (throwable != null) {
                StackTracePrinter.print(writer, throwable);
            }
        } catch (final Throwable e) {
            EMERGENCY_LOGGER.log(Level.ERROR, "Failed to format and print event", e);
        }
    }

    /**
     * Converts the given object to a string.
     *
     * @param level The level
     * @return The string
     */
    private static String asString(@Nullable Level level) {
        if (level == null) {
            EMERGENCY_LOGGER.logNPE("level");
            return "NO_LV"; // Must be 5 chars long to fit in pattern
        } else {
            return level.nameWithFixedSize();
        }
    }

    /**
     * Converts the given object to a string.
     *
     * @param message The message
     * @return The string
     */
    private static String asString(@Nullable final LogMessage message) {
        if (message == null) {
            EMERGENCY_LOGGER.logNPE("message");
            return UNDEFINED_MESSAGE;
        } else {
            try {
                return message.getMessage();
            } catch (final Throwable e) {
                EMERGENCY_LOGGER.log(Level.ERROR, "Failed to format message", e);
                return BROKEN_MESSAGE;
            }
        }
    }

    /**
     * Converts the given object to a string.
     *
     * @param marker The marker
     * @return The string
     */
    private static String asString(@Nullable final Marker marker) {
        if (marker == null) {
            EMERGENCY_LOGGER.logNPE("marker");
            return "null";
        } else {
            return String.join(", ", marker.getAllMarkerNames());
        }
    }

    /**
     * Creates in instance of {@link LineBasedFormat}
     *
     * @throws NullPointerException if any of the arguments is {@code null}
     */
    public static @NonNull LineBasedFormat createForHandler(
            @NonNull final String handlerName, @NonNull final Configuration configuration) {
        Objects.requireNonNull(handlerName, "handlerName must not be null");
        Objects.requireNonNull(configuration, "configuration must not be null");
        final String formatTimestampKey = "logging.handler." + handlerName + ".formatTimestamp";
        final Boolean formatTimestamp = configuration.getValue(formatTimestampKey, Boolean.class, true);
        return new LineBasedFormat(formatTimestamp != null && formatTimestamp);
    }
}
