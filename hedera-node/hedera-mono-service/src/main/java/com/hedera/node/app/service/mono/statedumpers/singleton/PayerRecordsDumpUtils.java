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

package com.hedera.node.app.service.mono.statedumpers.singleton;

import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.utils.FieldBuilder;
import com.hedera.node.app.service.mono.statedumpers.utils.ThingsToStrings;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.swirlds.base.utility.Pair;
import com.swirlds.fcqueue.FCQueue;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PayerRecordsDumpUtils {

    static final String FIELD_SEPARATOR = ";";

    @NonNull
    static List<Pair<String, BiConsumer<FieldBuilder, BBMPayerRecord>>> payerRecordFieldFormatters = List.of(
            Pair.of("txnId", getFieldFormatter(BBMPayerRecord::transactionId, Object::toString)),
            Pair.of(
                    "consensusTime",
                    getFieldFormatter(BBMPayerRecord::consensusTime, ThingsToStrings::toStringOfRichInstant)),
            Pair.of("payer", getFieldFormatter(BBMPayerRecord::payer, ThingsToStrings::toStringOfEntityId)));

    public static void dumpMonoPayerRecords(
            @NonNull final Path path,
            @NonNull final FCQueue<ExpirableTxnRecord> records,
            @NonNull final DumpCheckpoint checkpoint) {
        var transactionRecords = gatherTxnRecordsFromMono(records);
        int reportSize;
        try (@NonNull final var writer = new Writer(path)) {
            reportOnTxnRecords(writer, transactionRecords);
            reportSize = writer.getSize();
        }
        System.out.printf("=== payer records report is %d bytes %n", reportSize);
    }

    private static List<BBMPayerRecord> gatherTxnRecordsFromMono(FCQueue<ExpirableTxnRecord> records) {
        var listTxnRecords = new ArrayList<BBMPayerRecord>();
        records.stream().forEach(p -> listTxnRecords.add(BBMPayerRecord.fromMono(p)));
        return listTxnRecords;
    }

    public static void reportOnTxnRecords(@NonNull Writer writer, @NonNull List<BBMPayerRecord> records) {
        writer.writeln(formatHeader());
        records.stream()
                .sorted(Comparator.comparing(BBMPayerRecord::consensusTime))
                .forEach(e -> formatRecords(writer, e));
        writer.writeln("");
    }

    public static void formatRecords(@NonNull final Writer writer, @NonNull final BBMPayerRecord record) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        payerRecordFieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, record));
        writer.writeln(fb);
    }

    @NonNull
    static String formatHeader() {
        return payerRecordFieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    private static <T> BiConsumer<FieldBuilder, BBMPayerRecord> getFieldFormatter(
            @NonNull final Function<BBMPayerRecord, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> Function<T, String> getNullableFormatter(@NonNull final Function<T, String> formatter) {
        return t -> null != t ? formatter.apply(t) : "";
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final BBMPayerRecord transaction,
            @NonNull final Function<BBMPayerRecord, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(transaction)));
    }
}
