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

package com.swirlds.cli.logging;

import static com.swirlds.cli.logging.CssRuleSetFactory.BACKGROUND_COLOR_PROPERTY;
import static com.swirlds.cli.logging.CssRuleSetFactory.BORDER_COLLAPSE_PROPERTY;
import static com.swirlds.cli.logging.CssRuleSetFactory.BREAK_WORD_VALUE;
import static com.swirlds.cli.logging.CssRuleSetFactory.DISPLAY_PROPERTY;
import static com.swirlds.cli.logging.CssRuleSetFactory.FONT_PROPERTY;
import static com.swirlds.cli.logging.CssRuleSetFactory.HEIGHT_PROPERTY;
import static com.swirlds.cli.logging.CssRuleSetFactory.MAX_WIDTH_PROPERTY;
import static com.swirlds.cli.logging.CssRuleSetFactory.NORMAL_VALUE;
import static com.swirlds.cli.logging.CssRuleSetFactory.NO_WRAP_VALUE;
import static com.swirlds.cli.logging.CssRuleSetFactory.OVERFLOW_WRAP_PROPERTY;
import static com.swirlds.cli.logging.CssRuleSetFactory.PADDING_LEFT_PROPERTY;
import static com.swirlds.cli.logging.CssRuleSetFactory.TEXT_COLOR_PROPERTY;
import static com.swirlds.cli.logging.CssRuleSetFactory.TOP_VALUE;
import static com.swirlds.cli.logging.CssRuleSetFactory.VERTICAL_ALIGN_PROPERTY;
import static com.swirlds.cli.logging.CssRuleSetFactory.WHITE_SPACE_PROPERTY;
import static com.swirlds.cli.logging.CssRuleSetFactory.WIDTH_PROPERTY;
import static com.swirlds.cli.logging.CssRuleSetFactory.WORD_BREAK_PROPERTY;
import static com.swirlds.cli.logging.HtmlColors.getHtmlColor;
import static com.swirlds.cli.logging.HtmlTagFactory.DATA_HIDE_LABEL;
import static com.swirlds.cli.logging.LogLine.CLASS_NAME_COLOR;
import static com.swirlds.cli.logging.LogLine.LOG_MARKER_COLOR;
import static com.swirlds.cli.logging.LogLine.LOG_NUMBER_COLOR;
import static com.swirlds.cli.logging.LogLine.THREAD_NAME_COLOR;
import static com.swirlds.cli.logging.LogLine.TIMESTAMP_COLOR;
import static com.swirlds.cli.logging.LogProcessingUtils.getLogLevelColor;
import static com.swirlds.cli.logging.PlatformStatusLog.STATUS_HTML_CLASS;

import com.swirlds.common.system.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates an HTML log page
 */
public class HtmlGenerator {
    // These values have been chosen to mimic the jetbrains terminal
    /**
     * This is a dark gray color
     */
    public static final String PAGE_BACKGROUND_COLOR = "#1e1e23";

    public static final String HIGHLIGHT_COLOR = "#353539";

    /**
     * This is a light gray color
     */
    public static final String DEFAULT_TEXT_COLOR = "#bdbfc4";

    /**
     * Jetbrains font
     */
    public static final String DEFAULT_FONT = "Jetbrains Mono, monospace";

    /**
     * The source for the minified jQuery library
     */
    public static final String MIN_JS_SOURCE = "https://ajax.googleapis.com/ajax/libs/jquery/3.6.4/jquery.min.js";

    public static final String HTML_HTML_TAG = "html";
    public static final String HTML_H2_TAG = "h2";
    public static final String HTML_H3_TAG = "h3";
    public static final String HTML_SCRIPT_TAG = "script";
    public static final String HTML_LABEL_TAG = "label";
    public static final String HTML_INPUT_TAG = "input";
    public static final String HTML_BODY_TAG = "body";
    public static final String HTML_TABLE_TAG = "table";
    public static final String HTML_ROW_TAG = "tr";
    public static final String HTML_DATA_CELL_TAG = "td";
    public static final String HTML_SPAN_TAG = "span";
    public static final String HTML_HEAD_TAG = "head";
    public static final String HTML_STYLE_TAG = "style";
    public static final String HTML_BREAK_TAG = "br";
    public static final String HTML_DIV_TAG = "div";

    public static final String HTML_CLASS_ATTRIBUTE = "class";
    public static final String HTML_SOURCE_ATTRIBUTE = "src";
    public static final String HTML_TYPE_ATTRIBUTE = "type";

    /**
     * This causes input elements to be rendered as a checkbox
     */
    public static final String HTML_CHECKBOX_TYPE = "checkbox";

    /**
     * HTML elements with this class can be hidden with the filter checkboxes
     */
    public static final String HIDEABLE_LABEL = "hideable";

    public static final String LOG_LINE_LABEL = "log-line";

    public static final String NODE_ID_COLUMN_LABEL = "node-id";
    public static final String ELAPSED_TIME_COLUMN_LABEL = "elapsed-time";
    public static final String TIMESTAMP_COLUMN_LABEL = "timestamp";
    public static final String LOG_NUMBER_COLUMN_LABEL = "log-number";
    public static final String LOG_LEVEL_COLUMN_LABEL = "log-level";
    public static final String MARKER_COLUMN_LABEL = "marker";
    public static final String THREAD_NAME_COLUMN_LABEL = "thread-name";
    public static final String CLASS_NAME_COLUMN_LABEL = "class-name";
    public static final String REMAINDER_OF_LINE_COLUMN_LABEL = "remainder";
    public static final String NON_STANDARD_LABEL = "non-standard";

    /**
     * This label is used so that the filter checkboxes aren't themselves hidden. They have the same label as the
     * elements they can hide, but shouldn't be made invisible
     */
    public static final String FILTER_CHECKBOX_LABEL = "filter-checkbox";

    /**
     * This label is used to make the filter column and log table full height, side by side
     */
    public static final String DOUBLE_COLUMNS_DIV_LABEL = "double-columns";

    /**
     * This label is used to make the filter column and log table scroll independently
     */
    public static final String INDEPENDENT_SCROLL_LABEL = "independent-scroll";
    /**
     * For styling particular to the log table instance of an independent scroll box
     */
    public static final String TABLE_INDEPENDENT_SCROLL_LABEL = "table-independent-scroll";

    public static final String LOG_TABLE_LABEL = "log-table";

    /**
     * The javascript that is used to hide/show elements when the filter checkboxes are clicked
     */
    public static final String FILTER_JS =
            """
                    // the checkboxes that have the ability to hide things
                    var filterCheckboxes = document.getElementsByClassName("%s");

                    // add a listener to each checkbox
                    for (var i = 0; i < filterCheckboxes.length; i++) {
                        filterCheckboxes[i].addEventListener("click", function() {
                            // the classes that exist on the checkbox that is clicked
                            var checkboxClasses = this.classList;

                            // the name of the class that should be hidden
                            // each checkbox has 2 classes, "%s", and the name of the class to be hidden
                            var toggleClass;
                            for (j = 0; j < checkboxClasses.length; j++) {
                                if (checkboxClasses[j] == "%s") {
                                    continue;
                                }

                                toggleClass = checkboxClasses[j];
                                break;
                            }

                            // these are the objects on the page which match the class to toggle (discluding the input boxes)
                            var matchingObjects = $("." + toggleClass).not("input");

                            // go through each of the matching objects, and modify the hide count according to the value of the checkbox
                            for (j = 0; j < matchingObjects.length; j++) {
                                var currentHideCount = parseInt($(matchingObjects[j]).attr('data-hide')) || 0;

                                var newHideCount;
                                if ($(this).is(":checked")) {
                                    newHideCount = currentHideCount + 1;
                                } else {
                                    newHideCount = currentHideCount - 1;
                                }

                                $(matchingObjects[j]).attr('data-hide', newHideCount);
                            }
                        });
                    }
                    """
                    .formatted(FILTER_CHECKBOX_LABEL, FILTER_CHECKBOX_LABEL, FILTER_CHECKBOX_LABEL);

    /**
     * Hidden constructor.
     */
    private HtmlGenerator() {}

    /**
     * Create a checkbox that can hide elements with the given name
     *
     * @param elementName the name of the element to hide
     * @return the checkbox
     */
    private static String createFilterCheckbox(@NonNull final String elementName) {
        final String inputTag = new HtmlTagFactory(HTML_INPUT_TAG, null, true)
                .addClasses(List.of(FILTER_CHECKBOX_LABEL, elementName))
                .addAttribute(HTML_TYPE_ATTRIBUTE, HTML_CHECKBOX_TYPE)
                .generateTag();

        final String labelTag = new HtmlTagFactory(HTML_LABEL_TAG, elementName, false).generateTag();
        final String breakTag = new HtmlTagFactory(HTML_BREAK_TAG, null, true).generateTag();

        return inputTag + "\n" + labelTag + "\n" + breakTag + "\n";
    }

    /**
     * Create a filter div for the given filter name and values
     * <p>
     * The filter div has a heading, and a series of checkboxes that can hide elements with the given names
     *
     * @param filterName   the filter name
     * @param filterValues the filter values
     * @return the filter div
     */
    private static String createFilterDiv(@NonNull final String filterName, @NonNull final List<String> filterValues) {
        final String filterHeading = new HtmlTagFactory(HTML_H3_TAG, filterName, false).generateTag();
        final List<String> filterCheckboxes =
                filterValues.stream().map(HtmlGenerator::createFilterCheckbox).toList();

        return new HtmlTagFactory(
                        HTML_DIV_TAG, "\n" + filterHeading + "\n" + String.join("\n", filterCheckboxes), false)
                .generateTag();
    }

    /**
     * Generate the CSS rules for the HTML page
     *
     * @param logLines the log lines
     * @return the CSS rules
     */
    private static List<String> generateCssRules(@NonNull final List<LogLine> logLines) {
        final List<String> cssRules = new ArrayList<>();

        // set page defaults
        cssRules.add(new CssRuleSetFactory(
                        "html *",
                        List.of(
                                new CssDeclaration(FONT_PROPERTY, DEFAULT_FONT),
                                new CssDeclaration(BACKGROUND_COLOR_PROPERTY, PAGE_BACKGROUND_COLOR),
                                new CssDeclaration(TEXT_COLOR_PROPERTY, DEFAULT_TEXT_COLOR),
                                new CssDeclaration(WHITE_SPACE_PROPERTY, NO_WRAP_VALUE),
                                new CssDeclaration(VERTICAL_ALIGN_PROPERTY, TOP_VALUE)))
                .generateCss());

        // make log table have no border
        cssRules.add(
                new CssRuleSetFactory("." + LOG_TABLE_LABEL, new CssDeclaration(BORDER_COLLAPSE_PROPERTY, "collapse"))
                        .generateCss());

        // hide elements that have a data-hide value that isn't 0 or NaN
        cssRules.add(new CssRuleSetFactory(
                        "[%s]:not([%s~='0']):not([%s~=\"NaN\"])"
                                .formatted(DATA_HIDE_LABEL, DATA_HIDE_LABEL, DATA_HIDE_LABEL),
                        new CssDeclaration(DISPLAY_PROPERTY, "none"))
                .generateCss());

        // display the filter columns and the log table side by side, at full height
        cssRules.add(new CssRuleSetFactory(
                        "." + DOUBLE_COLUMNS_DIV_LABEL,
                        List.of(
                                new CssDeclaration(DISPLAY_PROPERTY, "flex"),
                                new CssDeclaration(HEIGHT_PROPERTY, "100%")))
                .generateCss());

        // make the filter columns and the log table scroll independently
        cssRules.add(new CssRuleSetFactory("." + INDEPENDENT_SCROLL_LABEL, new CssDeclaration("overflow", "auto"))
                .generateCss());

        // make the log table independent scroll fill 100% of width
        cssRules.add(
                new CssRuleSetFactory("." + TABLE_INDEPENDENT_SCROLL_LABEL, new CssDeclaration(WIDTH_PROPERTY, "100%"))
                        .generateCss());

        // pad the log table columns
        cssRules.add(new CssRuleSetFactory(HTML_DATA_CELL_TAG, new CssDeclaration(PADDING_LEFT_PROPERTY, "1em"))
                .generateCss());

        // set a max width for remainder column, and wrap words
        cssRules.add(new CssRuleSetFactory(
                        "." + REMAINDER_OF_LINE_COLUMN_LABEL,
                        List.of(
                                new CssDeclaration(MAX_WIDTH_PROPERTY, "100em"),
                                new CssDeclaration(OVERFLOW_WRAP_PROPERTY, BREAK_WORD_VALUE),
                                new CssDeclaration(WORD_BREAK_PROPERTY, BREAK_WORD_VALUE),
                                new CssDeclaration(WHITE_SPACE_PROPERTY, NORMAL_VALUE)))
                .generateCss());

        // wrap non standard log lines
        cssRules.add(new CssRuleSetFactory(
                        "." + NON_STANDARD_LABEL,
                        List.of(
                                new CssDeclaration(OVERFLOW_WRAP_PROPERTY, BREAK_WORD_VALUE),
                                new CssDeclaration(WORD_BREAK_PROPERTY, BREAK_WORD_VALUE),
                                new CssDeclaration(WHITE_SPACE_PROPERTY, "pre-wrap")))
                .generateCss());

        // set a max width for thread name column, and wrap words
        cssRules.add(new CssRuleSetFactory(
                        "." + THREAD_NAME_COLUMN_LABEL,
                        List.of(
                                new CssDeclaration(MAX_WIDTH_PROPERTY, "30em"),
                                new CssDeclaration(OVERFLOW_WRAP_PROPERTY, BREAK_WORD_VALUE),
                                new CssDeclaration(WORD_BREAK_PROPERTY, BREAK_WORD_VALUE),
                                new CssDeclaration(WHITE_SPACE_PROPERTY, NORMAL_VALUE)))
                .generateCss());

        // specify colors for each column
        cssRules.add(new CssRuleSetFactory(
                        "." + TIMESTAMP_COLUMN_LABEL,
                        new CssDeclaration(TEXT_COLOR_PROPERTY, getHtmlColor(TIMESTAMP_COLOR)))
                .generateCss());
        cssRules.add(new CssRuleSetFactory(
                        "." + LOG_NUMBER_COLUMN_LABEL,
                        new CssDeclaration(TEXT_COLOR_PROPERTY, getHtmlColor(LOG_NUMBER_COLOR)))
                .generateCss());
        cssRules.add(new CssRuleSetFactory(
                        "." + MARKER_COLUMN_LABEL,
                        new CssDeclaration(TEXT_COLOR_PROPERTY, getHtmlColor(LOG_MARKER_COLOR)))
                .generateCss());
        cssRules.add(new CssRuleSetFactory(
                        "." + THREAD_NAME_COLUMN_LABEL,
                        new CssDeclaration(TEXT_COLOR_PROPERTY, getHtmlColor(THREAD_NAME_COLOR)))
                .generateCss());
        cssRules.add(new CssRuleSetFactory(
                        "." + CLASS_NAME_COLUMN_LABEL,
                        new CssDeclaration(TEXT_COLOR_PROPERTY, getHtmlColor(CLASS_NAME_COLOR)))
                .generateCss());
        cssRules.add(new CssRuleSetFactory(
                        "." + STATUS_HTML_CLASS,
                        new CssDeclaration(TEXT_COLOR_PROPERTY, getHtmlColor(PlatformStatusLog.STATUS_COLOR)))
                .generateCss());

        // highlight log lines when you hover over them with your mouse
        cssRules.add(new CssRuleSetFactory(
                        "." + LOG_LINE_LABEL + ":hover td",
                        new CssDeclaration(BACKGROUND_COLOR_PROPERTY, HIGHLIGHT_COLOR))
                .generateCss());

        // create color rules for each log level
        final List<String> existingLogLevels =
                logLines.stream().map(LogLine::getLogLevel).distinct().toList();
        cssRules.addAll(existingLogLevels.stream()
                .map(logLevel -> new CssRuleSetFactory(
                                HTML_DATA_CELL_TAG + "." + logLevel,
                                new CssDeclaration(TEXT_COLOR_PROPERTY, getHtmlColor(getLogLevelColor(logLevel))))
                        .generateCss())
                .toList());

        return cssRules;
    }

    /**
     * Generate the head of the HTML page
     *
     * @param logLines the log lines
     * @return the head of the HTML page
     */
    private static String generateHead(@NonNull final List<LogLine> logLines) {
        final List<String> cssRules = generateCssRules(logLines);
        final String cssCombinedRules = String.join("\n", cssRules);
        final String cssTag = new HtmlTagFactory(HTML_STYLE_TAG, cssCombinedRules, false).generateTag();

        final String minJsSourceTag = new HtmlTagFactory(HTML_SCRIPT_TAG, "", false)
                .addAttribute(HTML_SOURCE_ATTRIBUTE, MIN_JS_SOURCE)
                .generateTag();

        return new HtmlTagFactory(HTML_HEAD_TAG, "\n" + cssTag + "\n" + minJsSourceTag + "\n", false).generateTag();
    }

    /**
     * Generate the generate filters div for the html page
     *
     * @param logLines the log lines
     * @return the generate filters div for the html page
     */
    private static String generateFiltersDiv(@NonNull final List<LogLine> logLines) {
        final List<String> filterDivs = new ArrayList<>();

        filterDivs.add(createFilterDiv(
                "Column",
                List.of(
                        NODE_ID_COLUMN_LABEL,
                        ELAPSED_TIME_COLUMN_LABEL,
                        TIMESTAMP_COLUMN_LABEL,
                        LOG_NUMBER_COLUMN_LABEL,
                        LOG_LEVEL_COLUMN_LABEL,
                        MARKER_COLUMN_LABEL,
                        THREAD_NAME_COLUMN_LABEL,
                        CLASS_NAME_COLUMN_LABEL,
                        REMAINDER_OF_LINE_COLUMN_LABEL)));

        filterDivs.add(createFilterDiv(
                "Log Level",
                logLines.stream().map(LogLine::getLogLevel).distinct().toList()));
        filterDivs.add(createFilterDiv(
                "Log Marker",
                logLines.stream().map(LogLine::getMarker).distinct().toList()));

        final String filterDivsCombined = "\n" + String.join("\n", filterDivs) + "\n";

        final String filtersHeading = new HtmlTagFactory(HTML_H2_TAG, "Filters", false).generateTag();

        final String scrollableFilterColumn = new HtmlTagFactory(HTML_DIV_TAG, filterDivsCombined, false)
                .addClass(INDEPENDENT_SCROLL_LABEL)
                .generateTag();

        return new HtmlTagFactory(HTML_DIV_TAG, filtersHeading + "\n" + scrollableFilterColumn, false).generateTag();
    }

    /**
     * Generate the log table for the HTML page
     *
     * @param logLines the log lines
     * @return the log table for the HTML page
     */
    private static String generateLogTable(@NonNull final List<LogLine> logLines) {
        final List<String> formattedLogLines =
                logLines.stream().map(LogLine::generateHtmlString).toList();
        final String combinedLogLines = "\n" + String.join("\n", formattedLogLines) + "\n";

        return new HtmlTagFactory(HTML_TABLE_TAG, combinedLogLines, false)
                .addClass(LOG_TABLE_LABEL)
                .generateTag();
    }

    /**
     * Generate the body of the HTML page
     *
     * @param logLines the log lines
     * @return the body of the HTML page
     */
    private static String generateBody(@NonNull final List<LogLine> logLines) {
        final String filtersDiv = generateFiltersDiv(logLines);
        final String tableDiv = new HtmlTagFactory(HTML_DIV_TAG, generateLogTable(logLines), false)
                .addClass(INDEPENDENT_SCROLL_LABEL)
                .addClass(TABLE_INDEPENDENT_SCROLL_LABEL)
                .generateTag();

        // this is a div surrounding the filters and the log table
        // its purpose is so that there can be 2 independently scrollable columns
        final String doubleColumnDiv = new HtmlTagFactory(HTML_DIV_TAG, filtersDiv + "\n" + tableDiv, false)
                .addClass(DOUBLE_COLUMNS_DIV_LABEL)
                .generateTag();

        final String scriptTag = new HtmlTagFactory(HTML_SCRIPT_TAG, FILTER_JS, false).generateTag();

        return new HtmlTagFactory(HTML_BODY_TAG, doubleColumnDiv + "\n" + scriptTag, false).generateTag();
    }

    /**
     * Go through all log lines, find the earliest time, and set the log start time for each log line
     *
     * @param logLines the log lines
     */
    private static void setFirstLogTime(@NonNull final List<LogLine> logLines) {
        final LogLine firstLogLine = logLines.stream()
                .min(Comparator.comparing(LogLine::getTimestamp))
                .orElse(null);

        final Instant firstLogTime = firstLogLine == null ? null : firstLogLine.getTimestamp();

        if (firstLogTime != null) {
            logLines.forEach(logLine -> logLine.setLogStartTime(firstLogTime));
        }
    }

    /**
     * Goes through the raw log line strings from a given node, and returns the list of log lines
     *
     * @param nodeId         the node id
     * @param logLineStrings the raw log line strings
     * @return the list of log lines, which represent the raw log strings
     */
    private static List<LogLine> processNodeLogLines(
            @NonNull final NodeId nodeId, @NonNull final List<String> logLineStrings) {
        final List<LogLine> outputLines = new ArrayList<>();

        LogLine previousLogLine = null;
        for (final String logLineString : logLineStrings) {
            if (logLineString == null) {
                continue;
            }

            try {
                previousLogLine = new LogLine(logLineString, ZoneId.systemDefault(), nodeId);
                outputLines.add(previousLogLine);
            } catch (final Exception e) {
                // everything in front of the first standard log line is discarded
                if (previousLogLine != null) {
                    previousLogLine.addNonStandardLine(logLineString);
                }
            }
        }
        return outputLines;
    }

    /**
     * Generate an HTML page from a list of log line strings
     *
     * @param logLineStrings the log line strings
     * @return the HTML page
     */
    public static String generateHtmlPage(@NonNull final Map<NodeId, List<String>> logLineStrings) {
        Objects.requireNonNull(logLineStrings);

        final List<LogLine> logLines = logLineStrings.entrySet().stream()
                .flatMap(entry -> processNodeLogLines(entry.getKey(), entry.getValue()).stream())
                .toList();

        setFirstLogTime(logLines);

        final List<String> pageElements = List.of(generateHead(logLines), generateBody(logLines));
        final String pageElementsCombined = "\n" + String.join("\n", pageElements) + "\n";

        return new HtmlTagFactory(HTML_HTML_TAG, pageElementsCombined, false).generateTag();
    }
}
