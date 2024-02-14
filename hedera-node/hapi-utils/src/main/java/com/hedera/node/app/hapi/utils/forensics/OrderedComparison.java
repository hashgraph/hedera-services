/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.utils.forensics;

import static com.hedera.node.app.hapi.utils.forensics.RecordParsers.parseV6RecordStreamEntriesIn;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;

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
     * @param recordDiffSummarizer if present, a summarizer for record diffs
     * @param maybeInclusionTest if set, a consumer receiving the name of each file as it is parsed
     * @return the stream diff
     * @throws IOException if any of the record stream files cannot be read or parsed
     * @throws IllegalArgumentException if the directories contain misaligned record streams
     */
    public static List<DifferingEntries> findDifferencesBetweenV6(
            @NonNull final String firstStreamDir,
            @NonNull final String secondStreamDir,
            @Nullable final RecordDiffSummarizer recordDiffSummarizer,
            @Nullable final Predicate<String> maybeInclusionTest,
            @Nullable final String maybeInclusionDescription)
            throws IOException {
        final Predicate<String> inclusionTest = maybeInclusionTest == null ? f -> true : maybeInclusionTest;
        final String inclusionDescription = maybeInclusionDescription == null ? "all" : maybeInclusionDescription;
        System.out.println("Parsing stream @ " + firstStreamDir + "(including " + inclusionDescription + ")");
        final var firstEntries = parseV6RecordStreamEntriesIn(firstStreamDir, inclusionTest);
        System.out.println(" ➡️  Read " + firstEntries.size() + " entries");
        System.out.println("Parsing stream @ " + secondStreamDir + "(including " + inclusionDescription + ")");
        final var secondEntries = parseV6RecordStreamEntriesIn(secondStreamDir, inclusionTest);
        List<RecordStreamEntry> newSecondEntries = getNewSecondRecordStreamEntries(firstEntries, secondEntries);
        System.out.println(" ➡️  Read " + secondEntries.size() + " entries");
        return diff(firstEntries, newSecondEntries, recordDiffSummarizer);
    }

    @NonNull
    private static List<RecordStreamEntry> getNewSecondRecordStreamEntries(
            List<RecordStreamEntry> firstEntries, List<RecordStreamEntry> secondEntries) {
        List<RecordStreamEntry> ret = new ArrayList<>();
        if (secondEntries.isEmpty()) {
            return ret;
        }
        RecordStreamEntry firstEntry, secondEntry;
        int secondIndex = 0;
        for (RecordStreamEntry entry : firstEntries) {
            firstEntry = entry;
            secondEntry = secondEntries.get(secondIndex);
            if (secondEntry.consensusTime().equals(firstEntry.consensusTime())) {
                ret.add(secondEntry);
                secondIndex++;
            } else {
                ret.add(new RecordStreamEntry(null, null, firstEntry.consensusTime()));
            }
        }
        return ret;
    }

    public interface RecordDiffSummarizer extends BiFunction<TransactionRecord, TransactionRecord, String> {}

    private static class UnmatchableException extends Exception {
        public UnmatchableException(@NonNull final String message) {
            super(message);
        }
    }

    @VisibleForTesting
    static List<DifferingEntries> diff(
            @NonNull final List<RecordStreamEntry> firstEntries,
            @NonNull final List<RecordStreamEntry> secondEntries,
            @Nullable final RecordDiffSummarizer recordDiffSummarizer) {
        requireNonNull(firstEntries);
        requireNonNull(secondEntries);
        final List<DifferingEntries> diffs = new ArrayList<>();
        final var minSize = Math.min(firstEntries.size(), secondEntries.size());
        for (int i = 0; i < minSize; i++) {
            final var firstEntry = firstEntries.get(i);
            try {
                if (secondEntries.get(i).txnRecord() == null) {
                    diffs.add(new DifferingEntries(
                            firstEntry,
                            null,
                            "No record found at " + firstEntry.consensusTime() + " for transactionID : "
                                    + firstEntry.txnRecord().getTransactionID()));
                    continue;
                }
                final var secondEntry = entryWithMatchableRecord(secondEntries, i, firstEntry);
                if (!firstEntry.txnRecord().equals(secondEntry.txnRecord())) {
                    final var summary = recordDiffSummarizer == null
                            ? null
                            : recordDiffSummarizer.apply(firstEntry.txnRecord(), secondEntry.txnRecord());
                    diffs.add(new DifferingEntries(firstEntry, secondEntry, summary));
                }
            } catch (UnmatchableException e) {
                diffs.add(new DifferingEntries(firstEntry, secondEntries.get(i), e.getMessage()));
            }
        }
        if (firstEntries.size() != secondEntries.size()) {
            appendExtraEntriesFrom(firstEntries, secondEntries, minSize, diffs);
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
            counts.computeIfAbsent(entry.parts().function(), ignore -> new EnumMap<>(ResponseCodeEnum.class))
                    .merge(entry.finalStatus(), 1, Integer::sum);
        }
        return counts;
    }

    public static List<RecordStreamEntry> filterByFunction(
            final List<RecordStreamEntry> entries, final HederaFunctionality function) {
        return entries.stream().filter(entry -> entry.function() == function).toList();
    }

    /**
     * Assuming the given list of entries has a "matchable" entry at the given
     * index, returns that entry. Throws an exception if the entry at the given
     * index is not matchable; i.e., if it has a different consensus time or
     * transaction than the entry at the given index.
     *
     * @param entries a list of entries
     * @param i the index of the entry to match
     * @param entryToMatch the entry to match
     * @return the entry at the given index
     */
    @NonNull
    private static RecordStreamEntry entryWithMatchableRecord(
            @NonNull final List<RecordStreamEntry> entries, final int i, @NonNull final RecordStreamEntry entryToMatch)
            throws UnmatchableException {
        final var secondEntry = entries.get(i);
        if (!entryToMatch.consensusTime().equals(secondEntry.consensusTime())) {
            throw new UnmatchableException("Entries at position "
                    + i
                    + " had different consensus times ("
                    + entryToMatch.consensusTime()
                    + " vs "
                    + secondEntry.consensusTime()
                    + ")");
        }
        if (!entryToMatch.submittedTransaction().equals(secondEntry.submittedTransaction())) {
            throw new UnmatchableException("Entries at position "
                    + i
                    + " had different transactions ("
                    + entryToMatch.submittedTransaction()
                    + " vs "
                    + secondEntry.submittedTransaction()
                    + ")");
        }
        return secondEntry;
    }

    private static void appendExtraEntriesFrom(
            @NonNull final List<RecordStreamEntry> firstEntries,
            @NonNull final List<RecordStreamEntry> secondEntries,
            final int minSize,
            @NonNull final List<DifferingEntries> diffs) {
        final var firstHasExtra = firstEntries.size() > secondEntries.size();
        final var extraEntries = firstHasExtra
                ? firstEntries.subList(minSize, firstEntries.size())
                : secondEntries.subList(minSize, secondEntries.size());
        final var maxSize = Math.max(firstEntries.size(), secondEntries.size());
        for (var i = minSize; i < maxSize; i++) {
            final var summary = firstHasExtra
                    ? "Extra entry at index " + i + " from first stream"
                    : "Extra entry at index " + i + " from second stream";
            final var extraEntry = extraEntries.get(i - minSize);
            diffs.add(new DifferingEntries(
                    firstHasExtra ? extraEntry : null, firstHasExtra ? null : extraEntry, summary));
        }
    }
}
