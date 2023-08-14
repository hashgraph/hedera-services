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

package com.swirlds.cli.utility;

import static com.swirlds.cli.utility.LogProcessingUtils.parseTimestamp;

import com.swirlds.common.formatting.TextEffect;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents a single line of log output
 * <p>
 * 2023-05-18 09:03:03.618     80         INFO    PLATFORM_STATUS    main      SwirldsPlatform: Platform status changed to...
 * timestamp               log number   log level      marker     thread name     class name         remainder of line
 */
public class LogLine implements FormattableString {
    public final Logger logger = LogManager.getLogger();
    public static final String CAPTURED_WHITESPACE_REGEX = "(\\s+)";
    public static final String TIMESTAMP_REGEX = "(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+)";
    public static final String LOG_NUMBER_REGEX = "(\\d+)";
    public static final String LOG_LEVEL_REGEX = "(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)";
    public static final String MARKER_REGEX = "([A-Za-z0-9_]+)";
    public static final String THREAD_NAME_REGEX = "(<<?[^>]+>>?)";
    public static final String CLASS_THREAD_NAME_REGEX = "([A-Za-z0-9_]+)";
    public static final String COLON_SPACE_REGEX = "(: )";
    public static final String REMAINDER_OF_LINE_REGEX = "(.*)";

    public static final TextEffect TIMESTAMP_COLOR = TextEffect.GRAY;
    public static final TextEffect LOG_NUMBER_COLOR = TextEffect.WHITE;
    public static final TextEffect LOG_MARKER_COLOR = TextEffect.BRIGHT_BLUE;
    public static final TextEffect THREAD_NAME_COLOR = TextEffect.BRIGHT_WHITE;
    public static final TextEffect CLASS_NAME_COLOR = TextEffect.BRIGHT_CYAN;

    public static final TextEffect HARMLESS_LOG_LEVEL_COLOR = TextEffect.BRIGHT_GREEN;
    public static final TextEffect WARN_LOG_LEVEL_COLOR = TextEffect.BRIGHT_YELLOW;
    public static final TextEffect ERROR_LOG_LEVEL_COLOR = TextEffect.BRIGHT_RED;

    public static final String FULL_REGEX = TIMESTAMP_REGEX
            + CAPTURED_WHITESPACE_REGEX
            + LOG_NUMBER_REGEX
            + CAPTURED_WHITESPACE_REGEX
            + LOG_LEVEL_REGEX
            + CAPTURED_WHITESPACE_REGEX
            + MARKER_REGEX
            + CAPTURED_WHITESPACE_REGEX
            + THREAD_NAME_REGEX
            + CAPTURED_WHITESPACE_REGEX
            + CLASS_THREAD_NAME_REGEX
            + COLON_SPACE_REGEX
            + REMAINDER_OF_LINE_REGEX;

    /**
     * The original log line string
     */
    private final String originalLogString;

    /**
     * The timezone of the timestamp in the log line
     */
    private final ZoneId zoneId;

    /**
     * The timestamp of the log line, as an instant
     */
    private final Instant timestamp;

    /**
     * The original timestamp string
     */
    private final String timestampOriginalString;

    /**
     * The log number of the log line
     */
    private final String logNumber;

    /**
     * The log level of the log line
     */
    private final String logLevel;

    /**
     * The marker of the log line
     */
    private final String marker;

    /**
     * The thread name of the log line
     */
    private final String threadName;

    /**
     * The class name of the log line
     */
    private final String className;

    /**
     * ": "
     */
    private final String colonSpace;

    /**
     * The remainder of the log line that follows the colonSpace
     */
    private final FormattableString remainderOfLine;

    /**
     * The list of whitespace strings that were captured by the regex
     */
    private final List<String> whitespaces = new ArrayList<>();

    /**
     * Construct a new LogLine from a log line string.
     *
     * @param logLineString the log line string
     * @param zoneId        the zone ID of the timestamp in the log line
     */
    public LogLine(@NonNull final String logLineString, @NonNull final ZoneId zoneId) {
        this.originalLogString = Objects.requireNonNull(logLineString);
        this.zoneId = Objects.requireNonNull(zoneId);

        final Matcher logLineMatcher = Pattern.compile(FULL_REGEX).matcher(logLineString.trim());

        if (!logLineMatcher.matches()) {
            throw new IllegalArgumentException("Log line string does not match expected format: " + logLineString);
        }

        timestampOriginalString = logLineMatcher.group(1);
        timestamp = parseTimestamp(timestampOriginalString, zoneId);
        whitespaces.add(logLineMatcher.group(2));
        logNumber = logLineMatcher.group(3);
        whitespaces.add(logLineMatcher.group(4));
        logLevel = logLineMatcher.group(5);
        whitespaces.add(logLineMatcher.group(6));
        marker = logLineMatcher.group(7);
        whitespaces.add(logLineMatcher.group(8));
        threadName = logLineMatcher.group(9);
        whitespaces.add(logLineMatcher.group(10));
        className = logLineMatcher.group(11);
        colonSpace = logLineMatcher.group(12);

        final String remainderString = logLineMatcher.group(13);

        switch (marker) {
            case "PLATFORM_STATUS" -> remainderOfLine = new PlatformStatusLog(remainderString);
            default -> remainderOfLine = new DefaultFormattableString(remainderString);
        }
    }

    /**
     * Get the timestamp of the log line
     *
     * @return the timestamp
     */
    @NonNull
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Get the log number of the log line
     *
     * @return the log number
     */
    @NonNull
    public String getLogNumber() {
        return logNumber;
    }

    /**
     * Get the level of the log line
     *
     * @return the log level
     */
    @NonNull
    public String getLogLevel() {
        return logLevel;
    }

    /**
     * Get the marker of the log line
     *
     * @return the marker
     */
    @NonNull
    public String getMarker() {
        return marker;
    }

    /**
     * Get the thread name of the log line
     *
     * @return the thread name
     */
    @NonNull
    public String getThreadName() {
        return threadName;
    }

    /**
     * Get the class name of the log line
     *
     * @return the class name
     */
    @NonNull
    public String getClassName() {
        return className;
    }

    /**
     * Get the formattable string remainder of the log line
     *
     * @return the remainder of the log line
     */
    @NonNull
    public FormattableString getRemainderOfLine() {
        return remainderOfLine;
    }

    /**
     * Get the correct color for a given log level.
     *
     * @param logLevel the log level
     * @return the color
     */
    @NonNull
    private static TextEffect getLogLevelColor(@NonNull final String logLevel) {
        return switch (logLevel) {
            case "TRACE", "DEBUG", "INFO" -> HARMLESS_LOG_LEVEL_COLOR;
            case "WARN" -> WARN_LOG_LEVEL_COLOR;
                // all other log levels are critical
            default -> ERROR_LOG_LEVEL_COLOR;
        };
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getOriginalPlaintext() {
        return originalLogString;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public String generateAnsiString() {
        int whitespaceIndex = 0;

        return TIMESTAMP_COLOR.apply(timestampOriginalString)
                + whitespaces.get(whitespaceIndex++)
                + LOG_NUMBER_COLOR.apply(logNumber)
                + whitespaces.get(whitespaceIndex++)
                + getLogLevelColor(logLevel).apply(logLevel)
                + whitespaces.get(whitespaceIndex++)
                + LOG_MARKER_COLOR.apply(marker)
                + whitespaces.get(whitespaceIndex++)
                + THREAD_NAME_COLOR.apply(threadName)
                + whitespaces.get(whitespaceIndex)
                + CLASS_NAME_COLOR.apply(className)
                + colonSpace
                + remainderOfLine.generateAnsiString();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String generateHtmlString() {
        throw new UnsupportedOperationException("FUTURE WORK");
    }
}
