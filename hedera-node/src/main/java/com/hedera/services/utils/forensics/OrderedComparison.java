/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.utils.forensics;

import static com.hedera.services.utils.forensics.RecordParsers.parseV6RecordStreamEntriesIn;

import com.google.common.annotations.VisibleForTesting;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Provides helpers to compare and analyze record streams. */
public class OrderedComparison {
    private OrderedComparison() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Given two directories, each containing the <b>same set</b> of compressed V6 record stream
     * files, returns a "stream diff". Each entry in the diff is a {@link DifferingEntries} object
     * that captures a single record divergence---that is, two records that had the same consensus
     * time and same source transaction in both streams, but differed in some way (e.g., their
     * receipt status).
     *
     * @param firstStreamDir the first record stream
     * @param secondStreamDir the second record stream
     * @return the stream diff
     * @throws IOException if any of the record stream files cannot be read or parsed
     * @throws IllegalArgumentException if the directories contain misaligned record streams
     */
    public static List<DifferingEntries> findDifferencesBetweenV6(
            final String firstStreamDir, final String secondStreamDir) throws IOException {
        final var firstEntries = parseV6RecordStreamEntriesIn(firstStreamDir);
        final var secondEntries = parseV6RecordStreamEntriesIn(secondStreamDir);
        return diff(firstEntries, secondEntries);
    }

    @VisibleForTesting
    static List<DifferingEntries> diff(
            final List<RecordStreamEntry> firstEntries,
            final List<RecordStreamEntry> secondEntries) {
        final List<DifferingEntries> diffs = new ArrayList<>();
        if (firstEntries.size() != secondEntries.size()) {
            throw new IllegalArgumentException(
                    "Cannot diff entries of different lengths "
                            + firstEntries.size()
                            + " and "
                            + secondEntries.size());
        }
        for (int i = 0, n = firstEntries.size(); i < n; i++) {
            final var firstEntry = firstEntries.get(i);
            final var secondEntry = secondEntries.get(i);
            if (!firstEntry.consensusTime().equals(secondEntry.consensusTime())) {
                throw new IllegalArgumentException(
                        "Entries at position "
                                + i
                                + " had different consensus times ("
                                + firstEntry.consensusTime()
                                + " vs "
                                + secondEntry.consensusTime()
                                + ")");
            }
            if (!firstEntry.submittedTransaction().equals(secondEntry.submittedTransaction())) {
                throw new IllegalArgumentException(
                        "Entries at position "
                                + i
                                + " had different transactions ("
                                + firstEntry.submittedTransaction()
                                + " vs "
                                + secondEntry.submittedTransaction()
                                + ")");
            }
            if (!firstEntry.txnRecord().equals(secondEntry.txnRecord())) {
                diffs.add(new DifferingEntries(firstEntry, secondEntry));
            }
        }
        return diffs;
    }

    /**
     * Given a list of record stream entries, returns a map that, for each {@link
     * HederaFunctionality} value, includes the counts of all {@link ResponseCodeEnum} values that
     * appeared in the record stream.
     *
     * @param entries record stream entries
     * @return a "histogram" of resolved statuses
     */
    public static Map<HederaFunctionality, Map<ResponseCodeEnum, Integer>> statusHistograms(
            final List<RecordStreamEntry> entries) {
        final Map<HederaFunctionality, Map<ResponseCodeEnum, Integer>> counts =
                new EnumMap<>(HederaFunctionality.class);
        for (final var entry : entries) {
            final var accessor = entry.accessor();
            final var function = accessor.getFunction();
            counts.computeIfAbsent(function, ignore -> new EnumMap<>(ResponseCodeEnum.class))
                    .merge(entry.finalStatus(), 1, Integer::sum);
        }
        return counts;
    }

    public static List<RecordStreamEntry> filterByFunction(
            final List<RecordStreamEntry> entries, final HederaFunctionality function) {
        return entries.stream().filter(entry -> entry.function() == function).toList();
    }
}
