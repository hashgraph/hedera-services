/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.cli.utils.ThingsToStrings.quoteForCsv;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.services.cli.utils.FieldBuilder;
import com.hedera.services.cli.utils.Writer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

/** Dump all scheduled transactions from a signed state file to a text file in a deterministic order  */
public class DumpScheduledTransactionsSubcommand {
    static void doit(
            @NonNull final SignedStateHolder state,
            @NonNull final Path scheduledTxsPath,
            @NonNull final DumpStateCommand.EmitSummary emitSummary,
            @NonNull final SignedStateCommand.Verbosity verbosity) {
        new DumpScheduledTransactionsSubcommand(state, scheduledTxsPath, emitSummary, verbosity).doit();
    }

    @NonNull
    final SignedStateHolder state;

    @NonNull
    final Path scheduledTxsPath;

    @NonNull
    final DumpStateCommand.EmitSummary emitSummary;

    @NonNull
    final SignedStateCommand.Verbosity verbosity;

    DumpScheduledTransactionsSubcommand(
            @NonNull final SignedStateHolder state,
            @NonNull final Path scheduledTxsPath,
            @NonNull final DumpStateCommand.EmitSummary emitSummary,
            @NonNull final SignedStateCommand.Verbosity verbosity) {
        requireNonNull(state, "state");
        requireNonNull(scheduledTxsPath, "signedTxsPath");
        requireNonNull(emitSummary, "emitSummary");
        requireNonNull(verbosity, "verbosity");

        this.state = state;
        this.scheduledTxsPath = scheduledTxsPath;
        this.emitSummary = emitSummary;
        this.verbosity = verbosity;
    }

    void doit() {
        System.out.printf("=== %d scheduled transactions ===%n", 0);

        final var allScheduledTxs = gatherScheduledTransactions();

        int reportSize;
        try (@NonNull final var writer = new Writer(scheduledTxsPath)) {
            if (emitSummary == DumpStateCommand.EmitSummary.YES) reportSummary(writer, allScheduledTxs);
            reportOnScheduledTransactions(writer, allScheduledTxs);
            reportSize = writer.getSize();
        }

        System.out.printf("=== scheduled transactions report is %d bytes%n", reportSize);
    }

    @SuppressWarnings(
            "java:S6218") // "Equals/hashcode method should be overridden in records containing array fields" - this
    @NonNull
    Map<Long, Schedule> gatherScheduledTransactions() {
        return Map.of();
    }

    void reportSummary(@NonNull Writer writer, @NonNull Map<Long, Schedule> scheduledTxs) {
        writer.writeln("=== %7d: scheduled transactions".formatted(scheduledTxs.size()));
        writer.writeln("");
    }

    void reportOnScheduledTransactions(@NonNull Writer writer, @NonNull Map<Long, Schedule> scheduledTxs) {
        writer.writeln(formatHeader());
        scheduledTxs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> formatScheduledTransaction(writer, e.getValue()));
        writer.writeln("");
    }

    void formatScheduledTransaction(@NonNull final Writer writer, @NonNull final Schedule scheduledTx) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        writer.writeln(fb);
    }

    @NonNull
    String formatHeader() {
        return "";
    }

    static final String FIELD_SEPARATOR = ";";
    static final String SUBFIELD_SEPARATOR = ",";
    static Function<Boolean, String> booleanFormatter = b -> b ? "T" : "";
    static Function<Object, String> csvQuote = s -> quoteForCsv(FIELD_SEPARATOR, (s == null) ? "" : s.toString());
}
