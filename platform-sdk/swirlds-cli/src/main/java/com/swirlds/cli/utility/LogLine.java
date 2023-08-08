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

import static com.swirlds.cli.utility.LogProcessingUtils.colorizeStringAnsi;
import static com.swirlds.cli.utility.LogProcessingUtils.parseTimestamp;

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

    public static final Color TIMESTAMP_COLOR = Color.GRAY;
    public static final Color LOG_NUMBER_COLOR = Color.WHITE;
    public static final Color LOG_MARKER_COLOR = Color.BRIGHT_BLUE;
    public static final Color THREAD_NAME_COLOR = Color.BRIGHT_WHITE;
    public static final Color CLASS_NAME_COLOR = Color.TEAL;

    public static final Color HARMLESS_LOG_LEVEL_COLOR = Color.GREEN;
    public static final Color WARN_LOG_LEVEL_COLOR = Color.YELLOW;
    public static final Color ERROR_LOG_LEVEL_COLOR = Color.RED;

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
    private final LogLevel logLevel;

    /**
     * The original log level string
     */
    private final String logLevelOriginalString;

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
        logLevelOriginalString = logLineMatcher.group(5);
        logLevel = LogLevel.valueOf(logLevelOriginalString);
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

    @NonNull
    public Instant getTimestamp() {
        return timestamp;
    }

    @NonNull
    public String getLogNumber() {
        return logNumber;
    }

    @NonNull
    public LogLevel getLogLevel() {
        return logLevel;
    }

    @NonNull
    public String getMarker() {
        return marker;
    }

    @NonNull
    public String getThreadName() {
        return threadName;
    }

    @NonNull
    public String getClassName() {
        return className;
    }

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
    private Color getLogLevelColor(@NonNull final LogLevel logLevel) {
        return switch (logLevel) {
            case TRACE, DEBUG, INFO -> HARMLESS_LOG_LEVEL_COLOR;
            case WARN -> WARN_LOG_LEVEL_COLOR;
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

        return colorizeStringAnsi(timestampOriginalString, TIMESTAMP_COLOR)
                + whitespaces.get(whitespaceIndex++)
                + colorizeStringAnsi(logNumber, LOG_NUMBER_COLOR)
                + whitespaces.get(whitespaceIndex++)
                + colorizeStringAnsi(logLevelOriginalString, getLogLevelColor(logLevel))
                + whitespaces.get(whitespaceIndex++)
                + colorizeStringAnsi(marker, LOG_MARKER_COLOR)
                + whitespaces.get(whitespaceIndex++)
                + colorizeStringAnsi(threadName, THREAD_NAME_COLOR)
                + whitespaces.get(whitespaceIndex)
                + colorizeStringAnsi(className, CLASS_NAME_COLOR)
                + colonSpace
                + remainderOfLine.generateAnsiString();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String generateHtmlString() {
        return "FUTURE WORK";
    }
}
