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

package com.swirlds.platform.event.report;

import static com.swirlds.common.formatting.TextEffect.BRIGHT_RED;
import static com.swirlds.common.formatting.TextEffect.BRIGHT_YELLOW;

import com.swirlds.common.formatting.TextTable;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * A report summarizing a collection of event stream files from multiple nodes
 */
public class EventStreamMultiNodeReport {
    /**
     * A map from a directory to an {@link EventStreamInfo} which summarizes that event stream files in that directory
     */
    private final Map<String, EventStreamInfo> individualReports = new HashMap<>();

    /**
     * Add an individual node report to this multi-node report
     *
     * @param directory        the directory containing the event stream files of the individual report
     * @param individualReport the individual report
     */
    public void addIndividualReport(@NonNull final Path directory, @NonNull final EventStreamReport individualReport) {
        Objects.requireNonNull(directory);
        Objects.requireNonNull(individualReport);

        individualReports.put(directory.toString(), individualReport.summary());
    }

    @Override
    @NonNull
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("\n");

        final Entry<String, EventStreamInfo> entryWithLatestEvent = individualReports.entrySet().stream()
                .max(Map.Entry.comparingByValue(((o1, o2) -> {
                    final Instant o1Timestamp =
                            o1.lastEvent().getPlatformEvent().getConsensusTimestamp();
                    final Instant o2Timestamp =
                            o2.lastEvent().getPlatformEvent().getConsensusTimestamp();

                    return o1Timestamp.compareTo(o2Timestamp);
                })))
                .orElseThrow();

        final Entry<String, EventStreamInfo> entryWithMostEvents = individualReports.entrySet().stream()
                .max(Map.Entry.comparingByValue((Comparator.comparingLong(EventStreamInfo::eventCount))))
                .orElseThrow();

        new TextTable()
                .setTitle("Multi-node Event Stream Summary")
                .addRow(BRIGHT_RED.apply("Directory with latest event"), entryWithLatestEvent.getKey())
                .addRow(
                        BRIGHT_YELLOW.apply("Latest event consensus timestamp"),
                        entryWithLatestEvent
                                .getValue()
                                .lastEvent()
                                .getPlatformEvent()
                                .getConsensusTimestamp())
                .addRow(BRIGHT_RED.apply("Directory with most events"), entryWithMostEvents.getKey())
                .addRow(
                        BRIGHT_YELLOW.apply("Number of events"),
                        entryWithMostEvents.getValue().eventCount())
                .render(sb);
        sb.append("\n\n");

        return sb.toString();
    }
}
