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

package com.hedera.services.cli.signedstate;

import static java.util.Objects.requireNonNull;

import com.hedera.services.cli.signedstate.DumpStateCommand.EmitSummary;
import com.hedera.services.cli.signedstate.SignedStateCommand.Verbosity;
import com.hedera.services.cli.utils.Writer;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Dump all token associations (tokenrels) , from a signed state file, to a text file, in deterministic order */
@SuppressWarnings("java:S106")
// S106: "use of system.out/system.err instead of logger" - not needed/desirable for CLI tool
public class DumpTokenAssociationsSubcommand {

    static void doit(
            @NonNull final SignedStateHolder state,
            @NonNull final Path tokenRelPath,
            @NonNull final EmitSummary emitSummary,
            @NonNull final Verbosity verbosity) {
        new DumpTokenAssociationsSubcommand(state, tokenRelPath, emitSummary, verbosity).doit();
    }

    @NonNull
    final SignedStateHolder state;

    @NonNull
    final Path tokenRelPath;

    @NonNull
    final EmitSummary emitSummary;

    @NonNull
    final Verbosity verbosity;

    DumpTokenAssociationsSubcommand(
            @NonNull final SignedStateHolder state,
            @NonNull final Path tokenRelPath,
            @NonNull final EmitSummary emitSummary,
            @NonNull final Verbosity verbosity) {
        requireNonNull(state, "state");
        requireNonNull(tokenRelPath, "tokenRelPath");
        requireNonNull(emitSummary, "emitSummary");
        requireNonNull(verbosity, "verbosity");

        this.state = state;
        this.tokenRelPath = tokenRelPath;
        this.emitSummary = emitSummary;
        this.verbosity = verbosity;
    }

    void doit() {
        System.out.printf("=== %d token associations (%s) === %n", 0, "virtual");
        final var tokenAssociations = gatherTokenAssociations();

        int reportSize;
        try (@NonNull final var writer = new Writer(tokenRelPath)) {
            if (emitSummary == EmitSummary.YES) reportSummary(writer, tokenAssociations);
            reportOnTokenAssociations(writer, tokenAssociations);
            reportSize = writer.getSize();
        }
        System.out.printf("=== token association report is %d bytes %n", reportSize);
    }

    record TokenRel(
            long account,
            long tokenId,
            long balance,
            boolean isFrozen,
            boolean isKycGranted,
            boolean isAutomaticAssociation,
            long prev,
            long next) {

        @NonNull
        public Pair<Long /*accountId*/, Long /*tokenId*/> getKey() {
            return Pair.of(account, tokenId);
        }
    }

    void reportSummary(
            @NonNull final Writer writer, @NonNull final SortedMap<Pair<Long, Long>, TokenRel> tokenAssociations) {
        requireNonNull(writer, "writer");
        requireNonNull(tokenAssociations, "tokenAssociations");

        final var uniqueAccounts = new HashSet<Long>();
        final var uniqueTokens = new HashSet<Long>();
        final var tokensByAccounts = new HashMap<Long, Long>();
        tokenAssociations.keySet().forEach(p -> {
            uniqueAccounts.add(p.left());
            uniqueTokens.add(p.right());
            tokensByAccounts.merge(p.left(), 1L, (a, ignored) -> a + 1);
        });

        final var totalTokenRelsByUniques =
                tokensByAccounts.values().stream().mapToLong(i -> i).sum();

        writer.write(
                "# === %8d token associations (%d the hard way), %7d accounts with associations, %7d token types associated with accounts%n"
                        .formatted(
                                tokenAssociations.size(),
                                totalTokenRelsByUniques,
                                uniqueAccounts.size(),
                                uniqueTokens.size()));

        final var tokensByAccountsHistogram = tokensByAccounts.values().stream()
                .collect(Collectors.groupingBy(n -> 0 == n ? 0 : (int) Math.log10(n), Collectors.counting()));
        final int maxDigits = tokensByAccountsHistogram.keySet().stream()
                .max(Comparator.naturalOrder())
                .orElse(0);

        final var sb = new StringBuilder(1000);
        sb.append("#   === token associations per account histogram ===%n".formatted());
        for (int i = 0; i <= maxDigits; i++) {
            sb.append("# %11s: %8d%n"
                    .formatted("<=" + (int) Math.pow(10, i), tokensByAccountsHistogram.getOrDefault(i, 0L)));
        }
        writer.write(sb);
        writer.write("");
    }

    @NonNull
    String formatHeader() {
        return "account;token;balance;frozen;kycGranted;automaticAssociation;prev;next";
    }

    @NonNull
    String toCsv(final boolean b) {
        return b ? "T" : "";
    }

    @NonNull
    String formatTokenRel(@NonNull final TokenRel t) {
        return "%d;%d;%d;%s;%s;%s;%d;%d"
                .formatted(
                        t.account(),
                        t.tokenId(),
                        t.balance(),
                        toCsv(t.isFrozen()),
                        toCsv(t.isKycGranted()),
                        toCsv(t.isAutomaticAssociation()),
                        t.prev(),
                        t.next());
    }

    void reportOnTokenAssociations(
            @NonNull final Writer writer, @NonNull final SortedMap<Pair<Long, Long>, TokenRel> tokenAssociations) {
        requireNonNull(writer, "writer");
        requireNonNull(tokenAssociations, "tokenAssociations");
        writer.writeln(formatHeader());
        tokenAssociations.forEach((ignored, value) -> writer.writeln(formatTokenRel(value)));
        writer.writeln("");
    }

    @NonNull
    SortedMap<Pair<Long, Long>, TokenRel> gatherTokenAssociations() {
        return new TreeMap<>();
    }

    @NonNull
    static Pair<Long, Long> toLongsPair(@NonNull final Pair<AccountID, TokenID> pat) {
        return Pair.of(pat.left().getAccountNum(), pat.right().getTokenNum());
    }

    @NonNull
    static Comparator<Pair<Long, Long>> getPairOfLongsComparator() {
        // This two-step dance necessary because of Java inference limitations
        final Comparator<Pair<Long, Long>> cx = Comparator.comparingLong(Pair::left);
        return cx.thenComparingLong(Pair::right);
    }
}
