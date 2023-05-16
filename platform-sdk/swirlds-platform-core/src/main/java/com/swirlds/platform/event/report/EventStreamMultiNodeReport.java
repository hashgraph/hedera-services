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

package com.swirlds.platform.event.report;

import static com.swirlds.common.formatting.TextEffect.BRIGHT_RED;
import static com.swirlds.common.formatting.TextEffect.BRIGHT_YELLOW;

import com.swirlds.common.formatting.TextTable;
import com.swirlds.platform.recovery.internal.EventStreamLowerBound;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

public class EventStreamMultiNodeReport {
    private final Duration granularity;
    private final EventStreamLowerBound bound;
    private final Map<String, EventStreamInfo> individualReports = new HashMap<>();

    public EventStreamMultiNodeReport(@NonNull final Duration granularity, @NonNull final EventStreamLowerBound bound) {
        this.granularity = Objects.requireNonNull(granularity);
        this.bound = Objects.requireNonNull(bound);
    }

    public EventStreamReport generateIndividualReport(@NonNull final Path nodeDirectory) {
        Objects.requireNonNull(nodeDirectory);

        final EventStreamReport report;
        try {
            report = new EventStreamScanner(nodeDirectory, bound, granularity, true).createReport();

            individualReports.put(nodeDirectory.toString(), report.summary());
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to generate event stream report", e);
        }

        return report;
    }

    public void addIndividualReport(@NonNull final Path directory, @NonNull final EventStreamReport individualReport) {
        Objects.requireNonNull(directory);
        Objects.requireNonNull(individualReport);

        individualReports.put(directory.toString(), individualReport.summary());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("\n");

        final Entry<String, EventStreamInfo> entryWithLatestEvent = individualReports.entrySet().stream()
                .max(Map.Entry.comparingByValue(((o1, o2) -> {
                    final long o1Timestamp = o1.lastEvent()
                            .getConsensusData()
                            .getConsensusTimestamp()
                            .toEpochMilli();
                    final long o2Timestamp = o2.lastEvent()
                            .getConsensusData()
                            .getConsensusTimestamp()
                            .toEpochMilli();

                    return Long.compare(o1Timestamp, o2Timestamp);
                })))
                .orElseThrow();

        new TextTable()
                .setTitle("--- Latest Event ---")
                .setBordersEnabled(false)
                .addRow(
                        BRIGHT_RED.apply("Directory with latest event"),
                        entryWithLatestEvent.getKey())
                .addRow(
                        BRIGHT_YELLOW.apply("Latest event consensus timestamp"),
                        entryWithLatestEvent.getValue())
                .render(sb);
        sb.append("\n\n");

        final Entry<String, EventStreamInfo> entryWithMostEvents = individualReports.entrySet().stream()
                .max(Map.Entry.comparingByValue((Comparator.comparingLong(EventStreamInfo::eventCount))))
                .orElseThrow();

        new TextTable()
                .setTitle("--- Greatest Event Count ---")
                .setBordersEnabled(false)
                .addRow(
                        BRIGHT_RED.apply("Directory with most events"),
                        entryWithMostEvents.getKey())
                .addRow(
                        BRIGHT_YELLOW.apply("Number of events"),
                        entryWithMostEvents.getValue())
                .render(sb);
        sb.append("\n\n");

        return sb.toString();
    }
}
