/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.formatting.StringFormattingUtils.commaSeparatedNumber;
import static com.swirlds.common.formatting.TextEffect.BRIGHT_CYAN;
import static com.swirlds.common.formatting.TextEffect.BRIGHT_RED;
import static com.swirlds.common.formatting.TextEffect.BRIGHT_YELLOW;
import static com.swirlds.common.units.DataUnit.UNIT_BYTES;

import com.swirlds.common.formatting.HorizontalAlignment;
import com.swirlds.common.formatting.TextHistogram;
import com.swirlds.common.formatting.TextTable;
import com.swirlds.common.formatting.UnitFormatter;
import com.swirlds.common.system.events.DetailedConsensusEvent;
import java.time.Duration;
import java.util.List;

/**
 * Useful information about an event stream
 *
 * @param granularInfo
 * 		information about the event stream broken down into small time periods
 * @param summary
 * 		a summary about the entire event stream
 */
public record EventStreamReport(List<EventStreamInfo> granularInfo, EventStreamInfo summary) {

    private static final int HASH_STRING_LENGTH = 12;

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("\n");

        sb.append("--- Event Count ---\n");
        new TextHistogram<>(granularInfo, EventStreamInfo::eventCount)
                .setTimestampExtractor(EventStreamInfo::start)
                .render(sb);
        sb.append("\n");

        sb.append("--- Rounds ---\n");
        new TextHistogram<>(granularInfo, EventStreamInfo::roundCount)
                .setTimestampExtractor(EventStreamInfo::start)
                .render(sb);
        sb.append("\n");

        sb.append("--- Transaction Count ---\n");
        new TextHistogram<>(granularInfo, EventStreamInfo::transactionCount)
                .setTimestampExtractor(EventStreamInfo::start)
                .render(sb);
        sb.append("\n");

        sb.append("--- System Transaction Count ---\n");
        new TextHistogram<>(granularInfo, EventStreamInfo::systemTransactionCount)
                .setTimestampExtractor(EventStreamInfo::start)
                .render(sb);
        sb.append("\n");

        sb.append("--- Application Transaction Count ---\n");
        new TextHistogram<>(granularInfo, EventStreamInfo::applicationTransactionCount)
                .setTimestampExtractor(EventStreamInfo::start)
                .render(sb);
        sb.append("\n");

        sb.append("--- File Count ---\n");
        new TextHistogram<>(granularInfo, EventStreamInfo::fileCount)
                .setTimestampExtractor(EventStreamInfo::start)
                .render(sb);
        sb.append("\n");

        sb.append("--- Damaged File Count ---\n");
        new TextHistogram<>(granularInfo, EventStreamInfo::damagedFileCount)
                .setTimestampExtractor(EventStreamInfo::start)
                .render(sb);
        sb.append("\n");

        sb.append("--- Byte Count ---\n");
        new TextHistogram<>(granularInfo, EventStreamInfo::byteCount)
                .setTimestampExtractor(EventStreamInfo::start)
                .setValueUnit(UNIT_BYTES)
                .render(sb);
        sb.append("\n");

        final DetailedConsensusEvent firstEvent = summary.firstEvent();
        final DetailedConsensusEvent lastEvent = summary.lastEvent();
        new TextTable()
                .setTitle("First/Last Event Info")
                .addTitleEffects(BRIGHT_CYAN)
                .addColumnEffects(0, BRIGHT_RED)
                .addRowEffects(0, BRIGHT_RED)
                .setRowHorizontalAlignment(0, HorizontalAlignment.ALIGNED_CENTER)
                .addRow("", "first event", "last event")
                .addRow(
                        "round",
                        commaSeparatedNumber(firstEvent.getConsensusData().getRoundReceived()),
                        commaSeparatedNumber(lastEvent.getConsensusData().getRoundReceived()))
                .addRow(
                        "timestamp",
                        firstEvent.getConsensusData().getConsensusTimestamp(),
                        lastEvent.getConsensusData().getConsensusTimestamp())
                .addRow(
                        "hash",
                        firstEvent.getHash().toShortString(HASH_STRING_LENGTH),
                        lastEvent.getHash().toShortString(HASH_STRING_LENGTH))
                .addRow(
                        "running hash",
                        firstEvent.getRunningHash().getHash().toShortString(HASH_STRING_LENGTH),
                        lastEvent.getRunningHash().getHash().toShortString(HASH_STRING_LENGTH))
                .addRow(
                        "consensus order",
                        commaSeparatedNumber(firstEvent.getConsensusData().getConsensusOrder()),
                        commaSeparatedNumber(lastEvent.getConsensusData().getConsensusOrder()))
                .addRow(
                        "generation",
                        commaSeparatedNumber(firstEvent.getBaseEventHashedData().getGeneration()),
                        commaSeparatedNumber(lastEvent.getBaseEventHashedData().getGeneration()))
                .addRow(
                        "creator ID",
                        firstEvent.getBaseEventHashedData().getCreatorId(),
                        lastEvent.getBaseEventHashedData().getCreatorId())
                .addRow(
                        "last in round",
                        firstEvent.getConsensusData().isLastInRoundReceived() ? "yes" : "no",
                        lastEvent.getConsensusData().isLastInRoundReceived() ? "yes" : "no")
                .addRow(
                        "transaction count",
                        commaSeparatedNumber(firstEvent.getBaseEventHashedData().getTransactions().length),
                        commaSeparatedNumber(lastEvent.getBaseEventHashedData().getTransactions().length))
                .render(sb);

        sb.append("\n\n");
        new TextTable()
                .setBordersEnabled(false)
                .addRow(
                        BRIGHT_RED.apply("first event full hash"),
                        firstEvent.getHash().toString())
                .addRow(
                        BRIGHT_YELLOW.apply("first event full running hash"),
                        firstEvent.getRunningHash().getHash().toString())
                .addRow(
                        BRIGHT_RED.apply("last event full hash"),
                        lastEvent.getHash().toString())
                .addRow(
                        BRIGHT_YELLOW.apply("last event full running hash"),
                        lastEvent.getRunningHash().getHash().toString())
                .render(sb);
        sb.append("\n\n");

        new TextTable()
                .setTitle(BRIGHT_CYAN.apply("Stream Info"))
                .addTitleEffects(BRIGHT_CYAN)
                .addColumnEffects(0, BRIGHT_RED)
                .addRow("rounds", commaSeparatedNumber(summary.roundCount()))
                .addRow(
                        "time",
                        commaSeparatedNumber(Duration.between(summary.start(), summary.end())
                                        .toSeconds()) + "s")
                .addRow("events", commaSeparatedNumber(summary.eventCount()))
                .addRow("transactions", commaSeparatedNumber(summary.transactionCount()))
                .addRow("system transactions", commaSeparatedNumber(summary.systemTransactionCount()))
                .addRow("application transactions", commaSeparatedNumber(summary.applicationTransactionCount()))
                .addRow("files", commaSeparatedNumber(summary.fileCount()))
                .addRow("bytes", new UnitFormatter(summary.byteCount(), UNIT_BYTES).render())
                .addRow("damaged file count", commaSeparatedNumber(summary.damagedFileCount()))
                .render(sb);

        return sb.toString();
    }
}
