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

import static com.swirlds.cli.logging.HtmlGenerator.ELAPSED_TIME_COLUMN_LABEL;
import static com.swirlds.cli.logging.HtmlGenerator.HIDEABLE_LABEL;
import static com.swirlds.cli.logging.HtmlGenerator.LOG_NUMBER_COLUMN_LABEL;
import static com.swirlds.cli.logging.HtmlGenerator.NODE_ID_COLUMN_LABEL;
import static com.swirlds.cli.logging.HtmlGenerator.NON_STANDARD_LABEL;
import static com.swirlds.cli.logging.HtmlGenerator.TIMESTAMP_COLUMN_LABEL;
import static com.swirlds.cli.logging.LogProcessingUtils.escapeString;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A log that doesn't adhere to the standard format. Becomes part of the most recent log line.
 */
public class NonStandardLog {
    /**
     * The original text
     */
    private String nonStandardText = "";

    /**
     * The most recent standard log line to come before this non-standard line
     */
    private final LogLine parentLogLine;

    /**
     * Constructor
     *
     * @param parentLogLine the most recent log line to come before this non-standard line
     */
    public NonStandardLog(@NonNull LogLine parentLogLine) {
        this.parentLogLine = parentLogLine;
    }

    public void addLogText(@NonNull final String logText) {
        if (!nonStandardText.isEmpty()) {
            this.nonStandardText += "\n";
        }
        this.nonStandardText += Objects.requireNonNull(logText);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public String generateHtmlString() {
        final List<String> dataCellTags = new ArrayList<>();

        // add some empty cells for alignment purposes
        final HtmlTagFactory nodeIdTagFactory =
                new HtmlTagFactory("td", "", false).addClasses(List.of(NODE_ID_COLUMN_LABEL, HIDEABLE_LABEL));
        dataCellTags.add(nodeIdTagFactory.generateTag());
        final HtmlTagFactory logStartTimeTagFactory =
                new HtmlTagFactory("td", "", false).addClasses(List.of(ELAPSED_TIME_COLUMN_LABEL, HIDEABLE_LABEL));
        dataCellTags.add(logStartTimeTagFactory.generateTag());
        final HtmlTagFactory timestampTagFactory =
                new HtmlTagFactory("td", "", false).addClasses(List.of(TIMESTAMP_COLUMN_LABEL, HIDEABLE_LABEL));
        dataCellTags.add(timestampTagFactory.generateTag());
        final HtmlTagFactory logNumberTagFactory =
                new HtmlTagFactory("td", "", false).addClasses(List.of(LOG_NUMBER_COLUMN_LABEL, HIDEABLE_LABEL));
        dataCellTags.add(logNumberTagFactory.generateTag());

        // add the non-standard contents
        final HtmlTagFactory contentsFactory = new HtmlTagFactory("td", escapeString(nonStandardText), false)
                .addClasses(List.of(NON_STANDARD_LABEL, HIDEABLE_LABEL))
                .addAttribute("colspan", "5");
        dataCellTags.add(contentsFactory.generateTag());

        // add classes via the parent line, so that this non-standard log will be filtered with its parent
        final List<String> rowClassNames = Stream.of(
                        parentLogLine.getLogLevel(),
                        parentLogLine.getMarker(),
                        parentLogLine.getThreadName(),
                        parentLogLine.getClassName(),
                        parentLogLine.getNodeId() == null ? "" : "node" + parentLogLine.getNodeId(),
                        HIDEABLE_LABEL)
                .map(LogProcessingUtils::escapeString)
                .toList();

        return new HtmlTagFactory("tr", "\n" + String.join("\n", dataCellTags) + "\n", false)
                .addClasses(rowClassNames)
                .generateTag();
    }
}
