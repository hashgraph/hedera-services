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

import static com.hedera.services.cli.utils.ThingsToStrings.toStructureSummaryOfKey;
import static java.util.function.Predicate.not;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.services.cli.signedstate.DumpStateCommand.Format;
import com.hedera.services.cli.signedstate.DumpStateCommand.KeyDetails;
import com.hedera.services.cli.signedstate.SignedStateCommand.Verbosity;
import com.hedera.services.cli.utils.Writer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Dump all Hedera account objects, from a signed state file, to a text file, in deterministic order.
 * Can output in either CSV format (actually: semicolon-separated) or in an "elided field" format where fields are
 * dumped in "name:value" pairs and missing fields or fields with default values are skipped.
 */
@SuppressWarnings("java:S106") // "use of system.out/system.err instead of logger" - not needed/desirable for CLI tool
public class DumpAccountsSubcommand {

    static void doit(
            @NonNull final SignedStateHolder state,
            @NonNull final Path accountPath,
            final int lowLimit,
            final int highLimit,
            @NonNull final EnumSet<KeyDetails> keyDetails,
            @NonNull final Format format,
            @NonNull final Verbosity verbosity) {
        new DumpAccountsSubcommand(state, accountPath, lowLimit, highLimit, keyDetails, format, verbosity).doit();
    }

    @NonNull
    final SignedStateHolder state;

    @NonNull
    final Path accountPath;

    @NonNull
    final Verbosity verbosity;

    @NonNull
    final Format format;

    final int lowLimit;

    final int highLimit;

    @NonNull
    final EnumSet<KeyDetails> keyDetails;

    DumpAccountsSubcommand(
            @NonNull final SignedStateHolder state,
            @NonNull final Path accountPath,
            final int lowLimit,
            final int highLimit,
            @NonNull final EnumSet<KeyDetails> keyDetails,
            @NonNull final Format format,
            @NonNull final Verbosity verbosity) {
        this.state = state;
        this.accountPath = accountPath;
        this.lowLimit = Math.max(lowLimit, 0);
        this.highLimit = Math.max(highLimit, 0);
        this.keyDetails = keyDetails;
        this.format = format;
        this.verbosity = verbosity;
    }

    void doit() {
        System.out.printf("=== %d accounts (%s)%n", 0, "on disk");

        final var accountsArr = gatherAccounts();

        int reportSize;
        try (@NonNull final var writer = new Writer(accountPath)) {
            reportOnAccounts(writer, accountsArr);
            if (keyDetails.contains(KeyDetails.STRUCTURE) || keyDetails.contains(KeyDetails.STRUCTURE_WITH_IDS))
                reportOnKeyStructure(writer, accountsArr);
            reportSize = writer.getSize();
        }

        System.out.printf("=== accounts report is %d bytes%n", reportSize);
        System.out.printf("=== fields with exceptions: %s%n", String.join(",", fieldsWithExceptions));
    }

    void reportOnAccounts(@NonNull final Writer writer, @NonNull final Account[] accountsArr) {
        if (format == Format.CSV) {
            writer.write("account#");
            writer.write(FIELD_SEPARATOR);
            writer.write(formatCsvHeader(allFieldNamesInOrder()));
            writer.newLine();
        }

        final var sb = new StringBuilder();
        Arrays.stream(accountsArr).map(a -> formatAccount(sb, a)).forEachOrdered(s -> {
            writer.write(s);
            writer.newLine();
        });
    }

    void reportOnKeyStructure(@NonNull final Writer writer, @NonNull final Account[] accountsArr) {
        final var eoaKeySummary = new HashMap<String, Integer>();
        accumulateSummaries(not(Account::smartContract), eoaKeySummary, accountsArr);
        writeKeySummaryReport(writer, "EOA", eoaKeySummary);

        final var scKeySummary = new HashMap<String, Integer>();
        accumulateSummaries(Account::smartContract, scKeySummary, accountsArr);
        writeKeySummaryReport(writer, "Smart Contract", scKeySummary);
    }

    @SuppressWarnings("java:S135")
    // Loops should not contain more than a single "break" or "continue" statement - disagree it
    // would make things clearer here
    void accumulateSummaries(
            @NonNull final Predicate<Account> filter,
            @NonNull final HashMap<String, Integer> structureSummary,
            @NonNull final Account[] accountsArr) {
        for (@NonNull final var ha : accountsArr) {
            if (ha.deleted()) continue;
            if (!filter.test(ha)) continue;
            final var sb = new StringBuilder();
            toStructureSummaryOfKey(sb, CommonPbjConverters.fromPbj(ha.keyOrThrow()));
            structureSummary.merge(sb.toString(), 1, Integer::sum);
        }
    }

    void writeKeySummaryReport(
            @NonNull final Writer writer, @NonNull final String kind, @NonNull final Map<String, Integer> keySummary) {

        writer.write("=== %s Key Summary ===%n".formatted(kind));
        keySummary.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEachOrdered(e -> writer.write("%7d: %s%n".formatted(e.getValue(), e.getKey())));
    }

    /**
     * Traverses the dehydrated signed state file to pull out all the accounts for later processing.
     * <p>
     * Currently, we pull out _all_ of the accounts at once and _sort_ them before processing any. This is because we
     * want to output accounts in a deterministic order and the traversal - multithreaded - gives you accounts in a
     * non-deterministic order.
     * <p>
     * But another approach would be to _format_ the accounts into strings as you do the traversal, saving those
     * strings, and then after the traversal is complete sorting _them_ into a deterministic order.  Not sure which
     * way is better.  No need to find out, either:  This approach works, is fast enough, and runs on current mainnet
     * state on my laptop.  If in fact it doesn't run (say, memory issues) on larger states (e.g., testnet) then we can
     * try another approach that may save memory.  (But the fact is the entire state must be resident in memory
     * anyway - except for the on-disk portions (for the mono service) and so we're talking about the difference
     * between the `Account` objects and the things they own vs. the formatted strings.)
     */
    @NonNull
    Account[] gatherAccounts() {
        // (FUTURE) - extract accounts
        final var accountsArr = new Account[0];
        System.out.printf(
                "=== %d accounts iterated over (%d saved%s)%n",
                0,
                accountsArr.length,
                lowLimit > 0 || highLimit < Integer.MAX_VALUE
                        ? ", limits: [%d..%d]".formatted(lowLimit, highLimit)
                        : "");
        return accountsArr;
    }

    /**
     * String that separates all fields in the CSV format, and also the primitive-typed fields from each other and
     * the other-typed fields from each other in the compressed format.
     */
    static final String FIELD_SEPARATOR = ";";

    /**
     * String that separates sub-fields (the primitive-type fields) in the compressed format.
     */
    static final String SUBFIELD_SEPARATOR = ",";

    /**
     * String that separates field names from field values in the compressed format
     */
    static final String NAME_TO_VALUE_SEPARATOR = ":";

    /**
     * Produces the CSV header line: A CSV line from all the field names in the deterministic order.
     */
    @NonNull
    String formatCsvHeader(@NonNull final List<String> names) {
        return String.join(FIELD_SEPARATOR, names);
    }

    /**
     * Returns the list of _all_ field names in the deterministic order, expanding the abbreviations to the full
     * field name.
     */
    @NonNull
    List<String> allFieldNamesInOrder() {
        return List.of();
    }

    /**
     * Formats an entire account as a text string.  First field of the string is the account number, followed by all
     * of its fields.
     */
    @NonNull
    String formatAccount(@NonNull final StringBuilder sb, @NonNull final Account a) {
        sb.setLength(0);
        return sb.toString();
    }

    final Set<String> fieldsWithExceptions = new TreeSet<>();
}
