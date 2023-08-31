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
import org.apache.logging.log4j.Level;

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

    /**
     * HTML elements with this class can be hidden with the filter checkboxes
     */
    public static final String HIDEABLE_LABEL = "hideable";

    /**
     * This label is used to hold the value of how many blacklist filters are currently applied to a field
     */
    public static final String BLACKLIST_LABEL = "blacklist";

    /**
     * This label is for fields we absolutely positively do not want displayed, regardless of what other filters say
     */
    public static final String ABSOLUTELY_NO_SHOW = "no-show";

    /**
     * This label is used to hold the value of how many whitelist filters are currently applied to a field
     */
    public static final String WHITELIST_LABEL = "whitelist";

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
     * Signifies filter radio buttons
     */
    public static final String FILTER_RADIO_LABEL = "filter-radio";

    public static final String WHITELIST_RADIO_LABEL = "whitelist-radio";
    public static final String NEUTRALLIST_RADIO_LABEL = "neutrallist-radio";
    public static final String BLACKLIST_RADIO_LABEL = "blacklist-radio";

    /**
     * Signifies filter checkboxes
     */
    public static final String FILTER_CHECKBOX_LABEL = "filter-checkbox";

    /**
     * Signifies checkboxes where unchecked results in ABSOLUTELY_NO_SHOW, rather than simple blacklist
     */
    public static final String NO_SHOW_CHECKBOX_LABEL = "no-show-checkbox";

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
            // the radio buttons that have the ability to hide things
            let filterRadios = document.getElementsByClassName("filter-radio");

            // create a map from radio name to previous value
            let previousValues = new Map();

            // add a listener to each radio button
            for (const element of filterRadios) {
                // set defaults values to neutral
                previousValues.set($(element).attr("name"), "2");

                element.addEventListener("change", function() {
                    // the classes that exist on the checkbox that is clicked
                    let radioClasses = this.classList;

                    // the name of the class that should be hidden
                    let toggleClass;

                    // each radio button has 3 classes, "filter-radio", the type of radio button this is, and the name of the class to be hidden
                    for (const element of radioClasses) {
                        if (element === "filter-radio") {
                            continue;
                        }

                        toggleClass = element;
                    }

                    let newCheckedValue = $(this).filter(":checked").val();
                    let previousCheckedValue = previousValues.get($(this).attr("name"));

                    // record the current value of the radio button that was clicked
                    previousValues.set($(this).attr("name"), $(this).filter(":checked").val());

                    // these are the objects on the page which match the class to toggle (discluding the input boxes)
                    let matchingObjects = $("." + toggleClass).not("input");

                    // go through each of the matching objects, and modify the hide count according to the value of the checkbox
                    for (const element of matchingObjects) {
                        let currentBlacklistCount = parseInt($(element).attr('blacklist')) || 0;
                        let currentWhitelistCount = parseInt($(element).attr('whitelist')) || 0;
                        let currentNoShowCount = parseInt($(element).attr('no-show')) || 0;

                        let newBlacklistCount;
                        let newWhitelistCount;
                        let newNoShowCount;

                        // modify blacklist and whitelist counts depending on the new checked value, and the previous checked value
                        if (newCheckedValue === "1") {
                            newWhitelistCount = currentWhitelistCount + 1;
                            if (previousCheckedValue === "3") {
                                newBlacklistCount = currentBlacklistCount - 1;
                            } else if(previousCheckedValue === "4") {
                                newNoShowCount = currentNoShowCount - 1;
                            }
                        } else if (newCheckedValue === "2") {
                            if (previousCheckedValue === "1") {
                                newWhitelistCount = currentWhitelistCount - 1;
                            } else if (previousCheckedValue === "3") {
                                newBlacklistCount = currentBlacklistCount - 1;
                            } else if(previousCheckedValue === "4") {
                                newNoShowCount = currentNoShowCount - 1;
                            }
                        } else if (newCheckedValue === "3") {
                            newBlacklistCount = currentBlacklistCount + 1;
                            if (previousCheckedValue === "1") {
                                newWhitelistCount = currentWhitelistCount - 1;
                            } else if(previousCheckedValue === "4") {
                                newNoShowCount = currentNoShowCount - 1;
                            }
                        } else if (newCheckedValue === "4") {
                            newNoShowCount = currentNoShowCount + 1;
                            if (previousCheckedValue === "1") {
                                newWhitelistCount = currentWhitelistCount - 1;
                            } else if(previousCheckedValue === "3") {
                                newBlacklistCount = currentBlacklistCount - 1;
                            }
                        }

                        $(element).attr('whitelist', newWhitelistCount);
                        $(element).attr('blacklist', newBlacklistCount);
                        $(element).attr('no-show', newNoShowCount);
                    }
                });
            }

            // the checkboxes that have the ability to hide things
            let filterCheckboxes = document.getElementsByClassName("filter-checkbox");

            // add a listener to each checkbox
            for (const element of filterCheckboxes) {
                element.addEventListener("change", function() {
                    // the classes that exist on the checkbox that is clicked
                    let checkboxClasses = this.classList;

                    // the name of the class that should be hidden
                    let toggleClass;

                    for (const element of checkboxClasses) {
                        if (element === "filter-checkbox") {
                            continue;
                        }

                        toggleClass = element;
                    }

                    // these are the objects on the page which match the class to toggle (discluding the input boxes)
                    let matchingObjects = $("." + toggleClass).not("input");

                    // go through each of the matching objects, and modify the hide count according to the value of the checkbox
                    for (const element of matchingObjects) {
                        let currentBlacklistCount = parseInt($(element).attr('blacklist')) || 0;

                        let newBlacklistCount;
                        if ($(this).is(":checked")) {
                            newBlacklistCount = currentBlacklistCount - 1;
                        } else {
                            newBlacklistCount = currentBlacklistCount + 1;
                        }

                        $(element).attr('blacklist', newBlacklistCount);
                    }
                });
            }

            // the checkboxes that have the ability to REALLY hide things
            let noShowCheckboxes = document.getElementsByClassName("no-show-checkbox");

            // add a listener to each checkbox
            for (const element of noShowCheckboxes) {
                element.addEventListener("change", function() {
                    // the classes that exist on the checkbox that is clicked
                    let checkboxClasses = this.classList;

                    // the name of the class that should be hidden
                    let toggleClass;

                    for (const element of checkboxClasses) {
                        if (element === "no-show-checkbox") {
                            continue;
                        }

                        toggleClass = element;
                    }

                    // these are the objects on the page which match the class to toggle (discluding the input boxes)
                    let matchingObjects = $("." + toggleClass).not("input");

                    // go through each of the matching objects, and modify the hide count according to the value of the checkbox
                    for (const element of matchingObjects) {
                        let currentNoShowCount = parseInt($(element).attr('no-show')) || 0;

                        let newNoShowCount;
                        if ($(this).is(":checked")) {
                            newNoShowCount = currentNoShowCount - 1;
                        } else {
                            newNoShowCount = currentNoShowCount + 1;
                        }

                        $(element).attr('no-show', newNoShowCount);
                    }
                });
            }
            """;

    /**
     * Hidden constructor.
     */
    private HtmlGenerator() {}

    /**
     * Create show / hide checkboxes for node IDs
     *
     * @param nodeId the node ID
     * @return the radio buttons
     */
    private static String createNodeIdCheckbox(@NonNull final String nodeId) {
        // label used for filtering by node ID
        final String nodeLogicLabel = "node" + nodeId;
        // label used for colorizing by node ID
        final String nodeStylingLabel = "node-" + nodeId;

        final String inputTag = new HtmlTagFactory("input", null, true)
                .addClasses(List.of(NO_SHOW_CHECKBOX_LABEL, nodeLogicLabel))
                .addAttribute("type", "checkbox")
                .addAttribute("checked", "checked")
                .generateTag();

        final String labelTag = new HtmlTagFactory("label", nodeLogicLabel, false)
                .addClass(nodeStylingLabel)
                .generateTag();
        final String breakTag = new HtmlTagFactory("br", null, true).generateTag();

        return inputTag + "\n" + labelTag + "\n" + breakTag + "\n";
    }

    /**
     * Create a single checkbox filter
     *
     * @param elementName the name of the element to hide / show
     * @return the checkbox
     */
    private static String createCheckboxFilter(@NonNull final String elementName) {
        final String inputTag = new HtmlTagFactory("input", null, true)
                .addClasses(List.of(FILTER_CHECKBOX_LABEL, elementName))
                .addAttribute("type", "checkbox")
                .addAttribute("checked", "checked")
                .generateTag();

        final String labelTag = new HtmlTagFactory("label", elementName, false).generateTag();
        final String breakTag = new HtmlTagFactory("br", null, true).generateTag();

        return inputTag + "\n" + labelTag + "\n" + breakTag + "\n";
    }

    /**
     * Create a set of radio buttons that can hide elements with the given name
     * <p>
     * This method creates 3 radio buttons, whitelist, blacklist, and neutral
     *
     * @param elementName the name of the element to hide
     * @return the checkbox
     */
    private static String createStandardRadioFilter(@NonNull final String elementName) {
        final String commonRadioLabel = elementName + "-radio";

        final String whitelistTag = new HtmlTagFactory("input", null, true)
                .addClasses(List.of(FILTER_RADIO_LABEL, WHITELIST_LABEL, WHITELIST_RADIO_LABEL, elementName))
                .addAttribute("type", "radio")
                .addAttribute("name", commonRadioLabel)
                .addAttribute("value", "1")
                .generateTag();
        final String neutralTag = new HtmlTagFactory("input", null, true)
                .addClasses(List.of(FILTER_RADIO_LABEL, NEUTRALLIST_RADIO_LABEL, elementName))
                .addAttribute("type", "radio")
                .addAttribute("name", commonRadioLabel)
                .addAttribute("checked", "checked")
                .addAttribute("value", "2")
                .generateTag();
        final String blacklistTag = new HtmlTagFactory("input", null, true)
                .addClasses(List.of(FILTER_RADIO_LABEL, BLACKLIST_LABEL, BLACKLIST_RADIO_LABEL, elementName))
                .addAttribute("type", "radio")
                .addAttribute("name", commonRadioLabel)
                .addAttribute("value", "3")
                .generateTag();

        final String labelTag = new HtmlTagFactory("label", elementName, false).generateTag();
        final String breakTag = new HtmlTagFactory("br", null, true).generateTag();

        return whitelistTag + "\n" + neutralTag + "\n" + blacklistTag + "\n" + labelTag + "\n" + breakTag + "\n";
    }

    /**
     * Wraps a filter heading and series of filter input buttons in a form, then a div
     *
     * @param heading      the heading for the filter
     * @param bodyElements the filter input buttons
     * @return the div
     */
    private static String createInputDiv(@NonNull final String heading, @NonNull final List<String> bodyElements) {
        final String filterHeading = new HtmlTagFactory("h3", heading, false).generateTag();

        final String form = new HtmlTagFactory("form", "\n" + String.join("\n", bodyElements), false)
                .addAttribute("autocomplete", "off")
                .generateTag();

        return new HtmlTagFactory("div", "\n" + filterHeading + "\n" + form, false).generateTag();
    }

    /**
     * Create the div for node ID filters
     *
     * @param filterValues the different node IDs to make filters for
     * @return the node ID filter div
     */
    private static String createNodeIdFilterDiv(@NonNull final List<String> filterValues) {
        final List<String> filterRadios =
                filterValues.stream().map(HtmlGenerator::createNodeIdCheckbox).toList();
        return createInputDiv("Node ID", filterRadios);
    }

    /**
     * Create the div for column filters
     *
     * @param filterValues the different column names to make filters for
     * @return the column filter div
     */
    private static String createColumnFilterDiv(@NonNull final List<String> filterValues) {
        final List<String> filterCheckboxes =
                filterValues.stream().map(HtmlGenerator::createCheckboxFilter).toList();
        return createInputDiv("Columns", filterCheckboxes);
    }

    /**
     * Create a standard 3 radio filter div for the given filter name and values
     * <p>
     * The filter div has a heading, and a series of radio buttons that can hide elements with the given names
     *
     * @param filterName   the filter name
     * @param filterValues the filter values
     * @return the filter div
     */
    private static String createStandardFilterDiv(
            @NonNull final String filterName, @NonNull final List<String> filterValues) {
        final List<String> filterRadios = filterValues.stream()
                .map(HtmlGenerator::createStandardRadioFilter)
                .toList();
        return createInputDiv(filterName, filterRadios);
    }

    /**
     * Create CSS rules that apply to the whole HTML page
     * <p>
     * Rules specific to individual elements should be added where those elements are being constructed
     *
     * @param logLines   the log lines
     * @param cssFactory a factory that new rules can be added to
     */
    private static void createGeneralCssRules(
            @NonNull final List<LogLine> logLines, @NonNull final CssRuleSetFactory cssFactory) {

        // set page defaults
        cssFactory.addRule(
                "html *",
                new CssDeclaration("font-family", DEFAULT_FONT),
                new CssDeclaration("background-color", PAGE_BACKGROUND_COLOR),
                new CssDeclaration("color", DEFAULT_TEXT_COLOR),
                new CssDeclaration("white-space", "nowrap"),
                new CssDeclaration("vertical-align", "top"));

        // hide elements that have a blacklist value that isn't 0 or NaN
        cssFactory.addRule(
                "[%s]:not([%s~='0']):not([%s~=\"NaN\"]):is([%s='0'])"
                        .formatted(BLACKLIST_LABEL, BLACKLIST_LABEL, BLACKLIST_LABEL, WHITELIST_LABEL),
                new CssDeclaration("display", "none"));

        // absolutely hide any elements with a no-show value of > 1
        cssFactory.addRule(
                "[%s]:not([%s~='0'])".formatted(ABSOLUTELY_NO_SHOW, ABSOLUTELY_NO_SHOW),
                new CssDeclaration("display", "none !important"));

        // pad the log table columns
        cssFactory.addRule("td", new CssDeclaration("padding-left", "1em"));

        // set a max width for remainder column, and wrap words
        cssFactory.addRule(
                "." + REMAINDER_OF_LINE_COLUMN_LABEL,
                new CssDeclaration("max-width", "100em"),
                new CssDeclaration("overflow-wrap", "break-word"),
                new CssDeclaration("word-break", "break-word"),
                new CssDeclaration("white-space", "normal"));

        // wrap non standard log lines
        cssFactory.addRule(
                "." + NON_STANDARD_LABEL,
                new CssDeclaration("white-space", "pre-wrap"),
                new CssDeclaration("word-break", "break-word"),
                new CssDeclaration("overflow-wrap", "break-word"));

        // set a max width for thread name column, and wrap words
        cssFactory.addRule(
                "." + THREAD_NAME_COLUMN_LABEL,
                new CssDeclaration("max-width", "30em"),
                new CssDeclaration("overflow-wrap", "break-word"),
                new CssDeclaration("word-break", "break-word"),
                new CssDeclaration("white-space", "normal"));

        cssFactory.addRule("." + NODE_ID_COLUMN_LABEL, new CssDeclaration("color", getHtmlColor(TIMESTAMP_COLOR)));
        cssFactory.addRule("." + ELAPSED_TIME_COLUMN_LABEL, new CssDeclaration("color", getHtmlColor(TIMESTAMP_COLOR)));
        cssFactory.addRule("." + TIMESTAMP_COLUMN_LABEL, new CssDeclaration("color", getHtmlColor(TIMESTAMP_COLOR)));
        cssFactory.addRule("." + LOG_NUMBER_COLUMN_LABEL, new CssDeclaration("color", getHtmlColor(LOG_NUMBER_COLOR)));
        cssFactory.addRule("." + MARKER_COLUMN_LABEL, new CssDeclaration("color", getHtmlColor(LOG_MARKER_COLOR)));
        cssFactory.addRule(
                "." + THREAD_NAME_COLUMN_LABEL, new CssDeclaration("color", getHtmlColor(THREAD_NAME_COLOR)));
        cssFactory.addRule("." + CLASS_NAME_COLUMN_LABEL, new CssDeclaration("color", getHtmlColor(CLASS_NAME_COLOR)));
        cssFactory.addRule(
                "." + STATUS_HTML_CLASS,
                new CssDeclaration("color", getHtmlColor(PlatformStatusLog.STATUS_COLOR)),
                new CssDeclaration("background-color", "inherit"));

        // highlight log lines when you hover over them with your mouse
        cssFactory.addRule("." + LOG_LINE_LABEL + ":hover td", new CssDeclaration("background-color", HIGHLIGHT_COLOR));

        // create color rules for each log level
        logLines.stream()
                .map(LogLine::getLogLevel)
                .distinct()
                .forEach(logLevel -> cssFactory.addRule(
                        "td" + "." + logLevel + "-level",
                        new CssDeclaration("color", getHtmlColor(getLogLevelColor(logLevel)))));

        // create color rules for each node ID
        logLines.stream()
                .map(LogLine::getNodeId)
                .distinct()
                .filter(Objects::nonNull)
                .forEach(nodeId -> {
                    final String color = NodeIdColorizer.getNodeIdColor(nodeId);

                    cssFactory.addRule(
                            "td.node-" + nodeId + ", label.node-" + nodeId,
                            new CssDeclaration("color", color == null ? DEFAULT_TEXT_COLOR : color));
                });
    }

    /**
     * Generate the head of the HTML page
     *
     * @param cssFactory the css rule factory
     * @return the head of the HTML page
     */
    private static String generateHead(@NonNull final CssRuleSetFactory cssFactory) {
        final String cssTag = new HtmlTagFactory("style", cssFactory.generateCss(), false).generateTag();

        final String minJsSourceTag = new HtmlTagFactory("script", "", false)
                .addAttribute("src", MIN_JS_SOURCE)
                .generateTag();

        return new HtmlTagFactory("head", "\n" + cssTag + "\n" + minJsSourceTag + "\n", false).generateTag();
    }

    /**
     * Generate the generate filters div for the html page
     *
     * @param logLines   the log lines
     * @param cssFactory a factory that new rules can be added to
     * @return the generate filters div for the html page
     */
    private static String generateFiltersDiv(
            @NonNull final List<LogLine> logLines, @NonNull final CssRuleSetFactory cssFactory) {

        final List<String> filterDivs = new ArrayList<>();

        filterDivs.add(createNodeIdFilterDiv(logLines.stream()
                .map(LogLine::getNodeId)
                .distinct()
                .filter(Objects::nonNull)
                .sorted()
                .map(NodeId::toString)
                .toList()));

        filterDivs.add(createColumnFilterDiv(List.of(
                NODE_ID_COLUMN_LABEL,
                ELAPSED_TIME_COLUMN_LABEL,
                TIMESTAMP_COLUMN_LABEL,
                LOG_NUMBER_COLUMN_LABEL,
                LOG_LEVEL_COLUMN_LABEL,
                MARKER_COLUMN_LABEL,
                THREAD_NAME_COLUMN_LABEL,
                CLASS_NAME_COLUMN_LABEL,
                REMAINDER_OF_LINE_COLUMN_LABEL)));

        filterDivs.add(createStandardFilterDiv(
                "Log Level",
                logLines.stream()
                        .map(LogLine::getLogLevel)
                        .distinct()
                        .sorted(Comparator.comparing(Level::toLevel))
                        .toList()));
        filterDivs.add(createStandardFilterDiv(
                "Log Marker",
                logLines.stream().map(LogLine::getMarker).distinct().toList()));

        final String filterDivsCombined = "\n" + String.join("\n", filterDivs) + "\n";

        final String filtersHeading = new HtmlTagFactory("h2", "Filters", false).generateTag();

        final String scrollableFilterColumn = new HtmlTagFactory("div", filterDivsCombined, false)
                .addClass(INDEPENDENT_SCROLL_LABEL)
                .generateTag();

        cssFactory.addRule("." + WHITELIST_RADIO_LABEL, new CssDeclaration("accent-color", "#6FD154"));
        cssFactory.addRule("." + NEUTRALLIST_RADIO_LABEL, new CssDeclaration("accent-color", "#F3D412"));
        cssFactory.addRule("." + BLACKLIST_RADIO_LABEL, new CssDeclaration("accent-color", "#DA4754"));

        // make the filter columns and the log table scroll independently
        cssFactory.addRule("." + INDEPENDENT_SCROLL_LABEL, new CssDeclaration("overflow", "auto"));

        return new HtmlTagFactory("div", filtersHeading + "\n" + scrollableFilterColumn, false).generateTag();
    }

    /**
     * Generate the log table for the HTML page
     *
     * @param logLines   the log lines
     * @param cssFactory a factory that new rules can be added to
     * @return the log table for the HTML page
     */
    private static String generateLogTable(
            @NonNull final List<LogLine> logLines, @NonNull final CssRuleSetFactory cssFactory) {
        final List<String> formattedLogLines =
                logLines.stream().map(LogLine::generateHtmlString).toList();
        final String combinedLogLines = "\n" + String.join("\n", formattedLogLines) + "\n";

        cssFactory.addRule("." + LOG_TABLE_LABEL, new CssDeclaration("border-collapse", "collapse"));

        return new HtmlTagFactory("table", combinedLogLines, false)
                .addClass(LOG_TABLE_LABEL)
                .generateTag();
    }

    /**
     * Generate the body of the HTML page
     *
     * @param logLines   the log lines
     * @param cssFactory a factory that new rules can be added to
     * @return the body of the HTML page
     */
    private static String generateBody(
            @NonNull final List<LogLine> logLines, @NonNull final CssRuleSetFactory cssFactory) {

        final String filtersDiv = generateFiltersDiv(logLines, cssFactory);
        final String tableDiv = new HtmlTagFactory("div", generateLogTable(logLines, cssFactory), false)
                .addClass(INDEPENDENT_SCROLL_LABEL)
                .addClass(TABLE_INDEPENDENT_SCROLL_LABEL)
                .generateTag();

        // make the log table independent scroll fill 100% of width
        cssFactory.addRule("." + TABLE_INDEPENDENT_SCROLL_LABEL, new CssDeclaration("width", "100%"));

        // this is a div surrounding the filters and the log table
        // its purpose is so that there can be 2 independently scrollable columns
        final String doubleColumnDiv = new HtmlTagFactory("div", filtersDiv + "\n" + tableDiv, false)
                .addClass(DOUBLE_COLUMNS_DIV_LABEL)
                .generateTag();

        cssFactory.addRule(
                "." + DOUBLE_COLUMNS_DIV_LABEL,
                new CssDeclaration("display", "flex"),
                new CssDeclaration("height", "100%"));

        final String scriptTag = new HtmlTagFactory("script", FILTER_JS, false).generateTag();

        return new HtmlTagFactory("body", doubleColumnDiv + "\n" + scriptTag, false).generateTag();
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
     * @param logLineStrings a map of node id to the raw log line strings for that node
     * @return the HTML page
     */
    public static String generateHtmlPage(@NonNull final Map<NodeId, List<String>> logLineStrings) {
        Objects.requireNonNull(logLineStrings);

        final List<LogLine> logLines = logLineStrings.entrySet().stream()
                .flatMap(entry -> processNodeLogLines(entry.getKey(), entry.getValue()).stream())
                .sorted(Comparator.comparing(LogLine::getTimestamp))
                .toList();

        setFirstLogTime(logLines);

        final CssRuleSetFactory cssFactory = new CssRuleSetFactory();

        createGeneralCssRules(logLines, cssFactory);

        final String body = generateBody(logLines, cssFactory);

        // head must be generated last, since that's where we turn the CSS rules into a string to put into the HTML doc
        final String head = generateHead(cssFactory);

        return new HtmlTagFactory("html", "\n" + head + "\n" + body + "\n", false).generateTag();
    }
}
