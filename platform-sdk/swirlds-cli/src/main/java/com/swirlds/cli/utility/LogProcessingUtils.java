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

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang3.StringEscapeUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility methods for processing log files into a more readable format.
 */
public class LogProcessingUtils {
    public static final String HTML_ROW_TAG = "tr";
    public static final String HTML_DATA_CELL_TAG = "td";
    public static final String HTML_SPAN_TAG = "span";

    public static final String HIDEABLE_LABEL = "hideable";
    public static final String NODE_ID_LABEL = "node-id";
    public static final String ELAPSED_TIME_LABEL = "elapsed-time";
    public static final String TIMESTAMP_LABEL = "timestamp";
    public static final String LOG_NUMBER_LABEL = "log-number";
    public static final String LOG_LEVEL_LABEL = "log-level";
    public static final String MARKER_LABEL = "marker";
    public static final String THREAD_NAME_LABEL = "thread-name";
    public static final String CLASS_NAME_LABEL = "class-name";
    public static final String REMAINDER_OF_LINE_LABEL = "remainder-of-line";

    /**
     * Hidden constructor.
     */
    private LogProcessingUtils() {
    }

    /**
     * Parse a log timestamp string into an Instant.
     *
     * @param timestampString the timestamp string to parse
     * @param zoneId          the zone ID of the timestamp
     * @return the parsed Instant
     */
    public static Instant parseTimestamp(@NonNull final String timestampString, @NonNull final ZoneId zoneId) {
        return LocalDateTime.parse(timestampString, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
                .atZone(zoneId)
                .toInstant();
    }

    /**
     * Generate an ANSI colorized version of a log line if the line can be parsed.
     * <p>
     * If the line cannot be parsed, it is returned without any colorization.
     *
     * @param inputString the input log line string
     * @param zoneId      the timezone of the timestamp in the log line
     * @return the colorized log line if it can be parsed, otherwise the original log line
     */
    @NonNull
    static String colorizeLogLineAnsi(@NonNull final String inputString, @NonNull final ZoneId zoneId) {
        try {
            final LogLine logLine = new LogLine(inputString, zoneId);

            return logLine.generateAnsiString();
        } catch (final Exception e) {
            return inputString;
        }
    }

    /**
     * Concatenate a list of HTML class names into a single string
     * <p>
     * Escapes the class names.
     *
     * @param classNames the class names to concatenate
     * @return the concatenated class names
     */
    private static String concatenateHtmlClassNames(@NonNull final List<String> classNames) {
        return classNames.stream().map(StringEscapeUtils::escapeHtml4).collect(Collectors.joining(" "));
    }

    /**
     * Generate an HTML tag with string content.
     * <p>
     * Content string must be escaped.
     *
     * @param tagName    the tag name
     * @param content    the content of the tag. MUST BE ESCAPED ALREADY
     * @param classNames the class names to use
     * @return the HTML tag
     */
    public static String createHtmlTag(
            @NonNull final String tagName, @NonNull final String content, @NonNull final List<String> classNames) {

        return "<" + tagName + " class=\"" + concatenateHtmlClassNames(classNames) + "\">" + content + "</" + tagName
                + ">";
    }
}
