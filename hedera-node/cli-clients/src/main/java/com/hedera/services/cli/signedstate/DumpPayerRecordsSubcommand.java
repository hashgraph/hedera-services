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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.services.cli.utils.FieldBuilder;
import com.hedera.services.cli.utils.RichInstant;
import com.hedera.services.cli.utils.Writer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Dump payer records from a signed state file to a text file in a deterministic order  */
public class DumpPayerRecordsSubcommand {

    static void doit(@NonNull final SignedStateHolder state, @NonNull final Path payerRecordsPath) {
        new DumpPayerRecordsSubcommand(state, payerRecordsPath).doit();
    }

    @NonNull
    final SignedStateHolder state;

    @NonNull
    final Path payerRecordsPath;

    DumpPayerRecordsSubcommand(@NonNull final SignedStateHolder state, @NonNull final Path payerRecordsPath) {
        requireNonNull(state, "state");
        requireNonNull(payerRecordsPath, "payerRecordsPath");

        this.state = state;
        this.payerRecordsPath = payerRecordsPath;
    }

    void doit() {
        System.out.printf("=== payer records ===%n");

        final var records = gatherTxnRecordsFromMono();

        int reportSize;
        try (@NonNull final var writer = new Writer(payerRecordsPath)) {
            reportOnTxnRecords(writer, records);
            reportSize = writer.getSize();
        }

        System.out.printf("=== payer records report is %d bytes%n", reportSize);
    }

    private static List<PayerRecord> gatherTxnRecordsFromMono() {
        return List.of();
    }

    @SuppressWarnings(
            "java:S6218") // "Equals/hashcode method should be overridden in records containing array fields" - this
    public record PayerRecord(
            @NonNull TransactionID transactionId, @NonNull RichInstant consensusTime, @NonNull AccountID payer) {}

    static void reportOnTxnRecords(@NonNull Writer writer, @NonNull List<PayerRecord> records) {
        writer.writeln(formatHeader());
        records.stream()
                .sorted(Comparator.comparing(PayerRecord::consensusTime))
                .forEach(e -> formatRecords(writer, e));
        writer.writeln("");
    }

    static void formatRecords(@NonNull final Writer writer, @NonNull final PayerRecord record) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        writer.writeln(fb);
    }

    @NonNull
    static String formatHeader() {
        return "";
    }

    static final String FIELD_SEPARATOR = ";";

    @NonNull
    static <T> BiConsumer<FieldBuilder, PayerRecord> getFieldFormatter(
            @NonNull final Function<PayerRecord, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final PayerRecord record,
            @NonNull final Function<PayerRecord, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(record)));
    }
}
