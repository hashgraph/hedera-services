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
import java.util.HashMap;
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

    // TODO inline all of these
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
     * Add a css rule to the cssRules map
     *
     * @param cssRules     the css rules map that will be added to
     * @param selector     the selector
     * @param declarations the declarations for the selector
     */
    private static void addToCssRules(
            @NonNull final Map<String, List<CssDeclaration>> cssRules,
            @NonNull final String selector,
            @NonNull CssDeclaration... declarations) {

        cssRules.computeIfAbsent(selector, key -> new ArrayList<>());
        cssRules.get(selector).addAll(List.of(declarations));
    }

    /**
     * Generate the CSS rules for the HTML page
     *
     * @param logLines the log lines
     * @param cssRules a map that contains css rules
     * @return the CSS rules, processed into a single string
     */
    private static String generateCss(
            @NonNull final List<LogLine> logLines, @NonNull final Map<String, List<CssDeclaration>> cssRules) {

        // set page defaults
        addToCssRules(
                cssRules,
                "html *",
                new CssDeclaration("font-family", DEFAULT_FONT),
                new CssDeclaration("background-color", PAGE_BACKGROUND_COLOR),
                new CssDeclaration("color", DEFAULT_TEXT_COLOR),
                new CssDeclaration("white-space", "nowrap"),
                new CssDeclaration("vertical-align", "top"));

        // hide elements that have a data-hide value that isn't 0 or NaN
        addToCssRules(
                cssRules,
                "[%s]:not([%s~='0']):not([%s~=\"NaN\"])".formatted(DATA_HIDE_LABEL, DATA_HIDE_LABEL, DATA_HIDE_LABEL),
                new CssDeclaration("display", "none"));

        // pad the log table columns
        addToCssRules(cssRules, HTML_DATA_CELL_TAG, new CssDeclaration("padding-left", "1em"));

        // set a max width for remainder column, and wrap words
        addToCssRules(
                cssRules,
                "." + REMAINDER_OF_LINE_COLUMN_LABEL,
                new CssDeclaration("max-width", "100em"),
                new CssDeclaration("overflow-wrap", "break-word"),
                new CssDeclaration("word-break", "break-word"),
                new CssDeclaration("white-space", "normal"));

        // wrap non standard log lines
        addToCssRules(
                cssRules,
                "." + NON_STANDARD_LABEL,
                new CssDeclaration("white-space", "pre-wrap"),
                new CssDeclaration("word-break", "break-word"),
                new CssDeclaration("overflow-wrap", "break-word"));

        // set a max width for thread name column, and wrap words
        addToCssRules(
                cssRules,
                "." + THREAD_NAME_COLUMN_LABEL,
                new CssDeclaration("max-width", "30em"),
                new CssDeclaration("overflow-wrap", "break-word"),
                new CssDeclaration("word-break", "break-word"),
                new CssDeclaration("white-space", "normal"));

        // TODO make these different colors
        addToCssRules(cssRules, "." + NODE_ID_COLUMN_LABEL, new CssDeclaration("color", getHtmlColor(TIMESTAMP_COLOR)));
        // TODO make this a better color
        addToCssRules(
                cssRules, "." + ELAPSED_TIME_COLUMN_LABEL, new CssDeclaration("color", getHtmlColor(TIMESTAMP_COLOR)));
        addToCssRules(
                cssRules, "." + TIMESTAMP_COLUMN_LABEL, new CssDeclaration("color", getHtmlColor(TIMESTAMP_COLOR)));
        addToCssRules(
                cssRules, "." + LOG_NUMBER_COLUMN_LABEL, new CssDeclaration("color", getHtmlColor(LOG_NUMBER_COLOR)));
        addToCssRules(cssRules, "." + MARKER_COLUMN_LABEL, new CssDeclaration("color", getHtmlColor(LOG_MARKER_COLOR)));
        addToCssRules(
                cssRules, "." + THREAD_NAME_COLUMN_LABEL, new CssDeclaration("color", getHtmlColor(THREAD_NAME_COLOR)));
        addToCssRules(
                cssRules, "." + CLASS_NAME_COLUMN_LABEL, new CssDeclaration("color", getHtmlColor(CLASS_NAME_COLOR)));
        addToCssRules(
                cssRules,
                "." + STATUS_HTML_CLASS,
                new CssDeclaration("color", getHtmlColor(PlatformStatusLog.STATUS_COLOR)));

        // highlight log lines when you hover over them with your mouse TODO add spans
        addToCssRules(
                cssRules, "." + LOG_LINE_LABEL + ":hover td", new CssDeclaration("background-color", HIGHLIGHT_COLOR));

        // create color rules for each log level
        logLines.stream()
                .map(LogLine::getLogLevel)
                .distinct()
                .forEach(logLevel -> addToCssRules(
                        cssRules,
                        HTML_DATA_CELL_TAG + "." + logLevel,
                        new CssDeclaration("color", getHtmlColor(getLogLevelColor(logLevel)))));

        return cssRules.entrySet().stream()
                .map(entry -> new CssRuleSetFactory(entry.getKey(), entry.getValue()).generateCss())
                .reduce("", (a, b) -> a + "\n" + b + "\n");
    }

    /**
     * Generate the head of the HTML page
     *
     * @param css the css rules
     * @return the head of the HTML page
     */
    private static String generateHead(@NonNull final String css) {
        final String cssTag = new HtmlTagFactory(HTML_STYLE_TAG, css, false).generateTag();

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
    private static String generateFiltersDiv(
            @NonNull final List<LogLine> logLines, @NonNull final Map<String, List<CssDeclaration>> cssRules) {

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

        // make the filter columns and the log table scroll independently
        addToCssRules(cssRules, "." + INDEPENDENT_SCROLL_LABEL, new CssDeclaration("overflow", "auto"));

        return new HtmlTagFactory(HTML_DIV_TAG, filtersHeading + "\n" + scrollableFilterColumn, false).generateTag();
    }

    /**
     * Generate the log table for the HTML page
     *
     * @param logLines the log lines
     * @return the log table for the HTML page
     */
    private static String generateLogTable(
            @NonNull final List<LogLine> logLines, @NonNull final Map<String, List<CssDeclaration>> cssRules) {
        final List<String> formattedLogLines =
                logLines.stream().map(LogLine::generateHtmlString).toList();
        final String combinedLogLines = "\n" + String.join("\n", formattedLogLines) + "\n";

        addToCssRules(cssRules, "." + LOG_TABLE_LABEL, new CssDeclaration("border-collapse", "collapse"));

        return new HtmlTagFactory(HTML_TABLE_TAG, combinedLogLines, false)
                .addClass(LOG_TABLE_LABEL)
                .generateTag();
    }

    /**
     * Generate the body of the HTML page
     *
     * @param logLines the log lines
     * @param cssRules a map for adding css rules. this is an output parameter
     * @return the body of the HTML page
     */
    private static String generateBody(
            @NonNull final List<LogLine> logLines, @NonNull final Map<String, List<CssDeclaration>> cssRules) {

        final String filtersDiv = generateFiltersDiv(logLines, cssRules);
        final String tableDiv = new HtmlTagFactory(HTML_DIV_TAG, generateLogTable(logLines, cssRules), false)
                .addClass(INDEPENDENT_SCROLL_LABEL)
                .addClass(TABLE_INDEPENDENT_SCROLL_LABEL)
                .generateTag();

        // make the log table independent scroll fill 100% of width
        addToCssRules(cssRules, "." + TABLE_INDEPENDENT_SCROLL_LABEL, new CssDeclaration("width", "100%"));

        // this is a div surrounding the filters and the log table
        // its purpose is so that there can be 2 independently scrollable columns
        final String doubleColumnDiv = new HtmlTagFactory(HTML_DIV_TAG, filtersDiv + "\n" + tableDiv, false)
                .addClass(DOUBLE_COLUMNS_DIV_LABEL)
                .generateTag();

        addToCssRules(
                cssRules,
                "." + DOUBLE_COLUMNS_DIV_LABEL,
                new CssDeclaration("display", "flex"),
                new CssDeclaration("height", "100%"));

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

        final Map<String, List<CssDeclaration>> cssRules = new HashMap<>();

        // pass cssRules into generateBody, where the map will be populated
        final String body = generateBody(logLines, cssRules);
        // pass the now populated cssRules map into processCssRules, where the css will be generated
        final String css = generateCss(logLines, cssRules);
        final String head = generateHead(css);

        return new HtmlTagFactory(HTML_HTML_TAG, "\n" + head + "\n" + body + "\n", false).generateTag();
    }
}
