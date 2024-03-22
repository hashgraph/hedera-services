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
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * Formats a {@link LogEvent} as a {@link String} and prints it to a given {@link Appendable}
 */
public class FormattedLinePrinter {

    private static final String THREAD_SUFFIX = "UNDEFINED-THREAD";
    private static final String LOGGER_SUFFIX = "UNDEFINED-LOGGER";
    private static final String UNDEFINED_MESSAGE = "UNDEFINED-MESSAGE";
    private static final String BROKEN_MESSAGE = "BROKEN-MESSAGE";
    /**
     * The emergency logger.
     */
    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    /**
     * Defines whether timestamps should be formatted as string or raw epoc values.
     */
    private final boolean formatTimestamp;

    /**
     * Creates a format
     *
     * @param formatTimestamp if true, timestamps will be converted to a human-readable format defined by
     *                        {@link EpochFormatUtils}
     */
    public FormattedLinePrinter(boolean formatTimestamp) {
        this.formatTimestamp = formatTimestamp;
    }

    /**
     * Formats a {@link LogEvent} as a {@link String} and prints it to a given {@link Appendable}
     *
     * @param appendable Non-null appendable. Destination to write into.
     * @param event      Non-null event to write.
     */
    public void print(@NonNull final Appendable appendable, @NonNull final LogEvent event) {
        if (appendable == null) {
            EMERGENCY_LOGGER.logNPE("printer");
            return;
        }
        if (event == null) {
            EMERGENCY_LOGGER.logNPE("event");
            return;
        }
        try {
            if (formatTimestamp && event.marker() != null && event.context() != null) {
                printAll(appendable, event);
            } else {
                printConditional(appendable, event);
            }

            Throwable throwable = event.throwable();
            if (throwable != null) {
                StackTracePrinter.print(appendable, throwable);
            }

        } catch (final Throwable e) {
            EMERGENCY_LOGGER.log(Level.ERROR, "Failed to format and print event", e);
        }
    }

    private void printConditional(final @NonNull Appendable appendable, final @NonNull LogEvent event)
            throws IOException {
        if (formatTimestamp) {
            appendable.append(EpochFormatUtils.timestampAsString(event.timestamp()));
        } else {
            appendable.append(Long.toString(event.timestamp()));
        }
        dataAndLevel(appendable, event);

        Marker marker = event.marker();
        if (marker != null) {
            appendable.append(" - [");
            appendable.append(asString(marker));
            appendable.append("]");
        }

        final Map<String, String> context = event.context();
        if (context != null && !context.isEmpty()) {
            appendable.append(" - ");
            appendable.append(context.toString());
        }
        appendable.append(System.lineSeparator());


    }

    private static void dataAndLevel(final @NonNull Appendable appendable, final @NonNull LogEvent event)
            throws IOException {
        appendable.append(' ');
        appendable.append(asString(event.level()));
        appendable.append(" [");
        appendable.append(requireNonNullElse(event.threadName(), THREAD_SUFFIX));
        appendable.append("] ");
        appendable.append(requireNonNullElse(event.loggerName(), LOGGER_SUFFIX));
        appendable.append(" - ");
        appendable.append(asString(event.message()));
    }

    private void printAll(@NonNull final Appendable appendable,
            @NonNull final LogEvent event) throws IOException {
        appendable.append(EpochFormatUtils.timestampAsString(event.timestamp()));
        dataAndLevel(appendable, event);
        appendable.append(" - [");
        appendable.append(asString(event.marker()));
        appendable.append("]");
        if (!event.context().isEmpty()) {
            appendable.append(" - ");
            appendable.append(event.context().toString());
        }
        appendable.append(System.lineSeparator());
    }

    /**
     * Converts the given {@link Level} object to a string.
     *
     * @param level The level
     * @return The string
     */
    private static String asString(@Nullable final Level level) {
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
     * Creates in instance of {@link FormattedLinePrinter}
     *
     * @throws NullPointerException if any of the arguments is {@code null}
     */
    public static @NonNull FormattedLinePrinter createForHandler(
            @NonNull final String handlerName, @NonNull final Configuration configuration) {
        Objects.requireNonNull(handlerName, "handlerName must not be null");
        Objects.requireNonNull(configuration, "configuration must not be null");
        final String formatTimestampKey = "logging.handler." + handlerName + ".formatTimestamp";
        final Boolean formatTimestamp = configuration.getValue(formatTimestampKey, Boolean.class, true);
        return new FormattedLinePrinter(formatTimestamp != null && formatTimestamp);
    }
}
