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

package com.hedera.node.app.bbm.singleton;

import com.hedera.hapi.node.state.recordcache.TransactionRecordEntry;
import com.hedera.node.app.bbm.DumpCheckpoint;
import com.hedera.node.app.bbm.utils.FieldBuilder;
import com.hedera.node.app.bbm.utils.ThingsToStrings;
import com.hedera.node.app.bbm.utils.Writer;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.state.merkle.queue.QueueNode;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TxnRecordQueueDumpUtils {

    static final String FIELD_SEPARATOR = ";";

    @NonNull
    static List<Pair<String, BiConsumer<FieldBuilder, TxnRecord>>> fieldFormatters = List.of(
            Pair.of("txnId", getFieldFormatter(TxnRecord::transactionId, Object::toString)),
            Pair.of(
                    "consensusTime",
                    getFieldFormatter(TxnRecord::consensusTime, ThingsToStrings::toStringOfRichInstant)),
            Pair.of("payer", getFieldFormatter(TxnRecord::payer, ThingsToStrings::toStringOfEntityId)));

    public static void dumpMonoPayerRecords(
            @NonNull final Path path,
            @NonNull final List<ExpirableTxnRecord> records,
            @NonNull final DumpCheckpoint checkpoint) {
        var transactionRecords = gatherTxnRecordsFromMono(records);
        int reportSize;
        try (@NonNull final var writer = new Writer(path)) {
            reportOnTxnRecords(writer, transactionRecords);
            reportSize = writer.getSize();
        }
        System.out.printf("=== staking rewards report is %d bytes %n", reportSize);
    }

    public static void dumpModTxnRecordQueue(
            @NonNull final Path path,
            @NonNull final QueueNode<TransactionRecordEntry> queue,
            @NonNull final DumpCheckpoint checkpoint) {
        var transactionRecords = gatherTxnRecordsFromMod(queue);
        int reportSize;
        try (@NonNull final var writer = new Writer(path)) {
            reportOnTxnRecords(writer, transactionRecords);
            reportSize = writer.getSize();
        }
        System.out.printf("=== staking rewards report is %d bytes %n", reportSize);
    }

    private static List<TxnRecord> gatherTxnRecordsFromMod(QueueNode<TransactionRecordEntry> queue) {
        var iterator = queue.iterator();
        var records = new ArrayList<TxnRecord>();
        while (iterator.hasNext()) {
            records.add(TxnRecord.fromMod(iterator.next()));
        }

        return records;
    }

    private static List<TxnRecord> gatherTxnRecordsFromMono(List<ExpirableTxnRecord> records) {
        var listTxnRecords = new ArrayList<TxnRecord>();
        records.stream().forEach(p -> listTxnRecords.add(TxnRecord.fromMono(p)));
        return listTxnRecords;
    }

    static void reportOnTxnRecords(@NonNull Writer writer, @NonNull List<TxnRecord> records) {
        writer.writeln(formatHeader());
        records.stream().sorted(Comparator.comparing(TxnRecord::consensusTime)).forEach(e -> formatRecords(writer, e));
        writer.writeln("");
    }

    static void formatRecords(@NonNull final Writer writer, @NonNull final TxnRecord record) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, record));
        writer.writeln(fb);
    }

    @NonNull
    static String formatHeader() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    private static <T> BiConsumer<FieldBuilder, TxnRecord> getFieldFormatter(
            @NonNull final Function<TxnRecord, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> Function<T, String> getNullableFormatter(@NonNull final Function<T, String> formatter) {
        return t -> null != t ? formatter.apply(t) : "";
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final TxnRecord transaction,
            @NonNull final Function<TxnRecord, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(transaction)));
    }
}
