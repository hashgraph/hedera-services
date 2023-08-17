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

import static com.swirlds.cli.logging.CssRuleSetFactory.COLSPAN_PROPERTY;
import static com.swirlds.cli.logging.HtmlGenerator.ELAPSED_TIME_COLUMN_LABEL;
import static com.swirlds.cli.logging.HtmlGenerator.HIDEABLE_LABEL;
import static com.swirlds.cli.logging.HtmlGenerator.HTML_DATA_CELL_TAG;
import static com.swirlds.cli.logging.HtmlGenerator.HTML_ROW_TAG;
import static com.swirlds.cli.logging.HtmlGenerator.LOG_LINE_LABEL;
import static com.swirlds.cli.logging.HtmlGenerator.LOG_NUMBER_COLUMN_LABEL;
import static com.swirlds.cli.logging.HtmlGenerator.NODE_ID_COLUMN_LABEL;
import static com.swirlds.cli.logging.HtmlGenerator.NON_STANDARD_LABEL;
import static com.swirlds.cli.logging.HtmlGenerator.TIMESTAMP_COLUMN_LABEL;
import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringEscapeUtils;

/**
 * A log line that doesn't adhere to the standard format.
 */
public class NonStandardLogLine implements LogLine {
    /**
     * The original text
     */
    final String logText;

    /**
     * The most recent standard log line to come before this non-standard line
     */
    private final StandardLogLine parentLogLine;

    /**
     * Constructor
     *
     * @param logText       the original text
     * @param parentLogLine the most recent standard log line to come before this non-standard line
     */
    public NonStandardLogLine(@NonNull final String logText, @NonNull StandardLogLine parentLogLine) {
        this.logText = logText;
        this.parentLogLine = parentLogLine;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public String generateHtmlString() {
        final List<String> dataCellTags = new ArrayList<>();

        // add some empty cells for alignment purposes
        final HtmlTagFactory nodeIdTagFactory = new HtmlTagFactory(HTML_DATA_CELL_TAG, "", false)
                .addClasses(List.of(NODE_ID_COLUMN_LABEL, HIDEABLE_LABEL));
        dataCellTags.add(nodeIdTagFactory.generateTag());
        final HtmlTagFactory logStartTimeTagFactory = new HtmlTagFactory(HTML_DATA_CELL_TAG, "", false)
                .addClasses(List.of(ELAPSED_TIME_COLUMN_LABEL, HIDEABLE_LABEL));
        dataCellTags.add(logStartTimeTagFactory.generateTag());
        final HtmlTagFactory timestampTagFactory = new HtmlTagFactory(HTML_DATA_CELL_TAG, "", false)
                .addClasses(List.of(TIMESTAMP_COLUMN_LABEL, HIDEABLE_LABEL));
        dataCellTags.add(timestampTagFactory.generateTag());
        final HtmlTagFactory logNumberTagFactory = new HtmlTagFactory(HTML_DATA_CELL_TAG, "", false)
                .addClasses(List.of(LOG_NUMBER_COLUMN_LABEL, HIDEABLE_LABEL));
        dataCellTags.add(logNumberTagFactory.generateTag());

        // add the non-standard contents
        final HtmlTagFactory contentsFactory = new HtmlTagFactory(HTML_DATA_CELL_TAG, escapeHtml4(logText), false)
                .addClasses(List.of(NON_STANDARD_LABEL, HIDEABLE_LABEL))
                .addAttribute(COLSPAN_PROPERTY, "5");
        dataCellTags.add(contentsFactory.generateTag());

        // add classes via the parent line, so that this non-standard log line will be filtered with its parent
        final List<String> rowClassNames = Stream.of(
                        parentLogLine.getLogLevel(),
                        parentLogLine.getMarker(),
                        parentLogLine.getThreadName(),
                        parentLogLine.getClassName(),
                        parentLogLine.getNodeId() == null ? "" : "node" + parentLogLine.getNodeId(),
                        HIDEABLE_LABEL,
                        LOG_LINE_LABEL)
                .map(StringEscapeUtils::escapeHtml4)
                .toList();

        return new HtmlTagFactory(HTML_ROW_TAG, "\n" + String.join("\n", dataCellTags) + "\n", false)
                .addClasses(rowClassNames)
                .generateTag();
    }
}
