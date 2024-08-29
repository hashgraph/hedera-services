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

package com.hedera.node.app.statedumpers;

import com.hedera.hapi.node.state.recordcache.TransactionRecordEntry;
import com.hedera.node.app.statedumpers.legacy.EntityId;
import com.hedera.node.app.statedumpers.legacy.RichInstant;
import com.hedera.node.app.statedumpers.legacy.TxnId;
import com.hedera.node.app.statedumpers.utils.FieldBuilder;
import com.hedera.node.app.statedumpers.utils.ThingsToStrings;
import com.swirlds.base.utility.Pair;
import com.swirlds.state.merkle.queue.QueueNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PayerRecordsDumpUtils {
    static final String FIELD_SEPARATOR = ";";

    public static void dumpModTxnRecordQueue(
            @NonNull final Path path, @NonNull final QueueNode<TransactionRecordEntry> queue, final JsonWriter jsonWriter) {
        var transactionRecords = gatherTxnRecordsFromMod(queue);
        System.out.println("=== Dumping payer records ===");
        System.out.println(transactionRecords.size() + " records found");
        int reportSize;
        try (@NonNull final var writer = new Writer(path)) {
            reportOnTxnRecords(writer, transactionRecords);
            reportSize = writer.getSize();
        }
        System.out.printf("=== payer records report is %d bytes %n", reportSize);
    }

    private static List<BBMPayerRecord> gatherTxnRecordsFromMod(QueueNode<TransactionRecordEntry> queue) {
        var iterator = queue.iterator();
        var records = new ArrayList<BBMPayerRecord>();
        while (iterator.hasNext()) {
            records.add(fromMod(iterator.next()));
        }

        return records;
    }

    public static BBMPayerRecord fromMod(@NonNull TransactionRecordEntry recordEntry) {
        Objects.requireNonNull(recordEntry.transactionRecord(), "Record is null");

        var modTransactionId = recordEntry.transactionRecord().transactionID();
        var accountId = EntityId.fromPbjAccountId(modTransactionId.accountID());
        var validStartTimestamp = modTransactionId.transactionValidStart();
        var txnId = new TxnId(
                accountId,
                new RichInstant(validStartTimestamp.seconds(), validStartTimestamp.nanos()),
                modTransactionId.scheduled(),
                modTransactionId.nonce());
        var consensusTimestamp = recordEntry.transactionRecord().consensusTimestamp();

        return new BBMPayerRecord(
                txnId,
                new RichInstant(consensusTimestamp.seconds(), consensusTimestamp.nanos()),
                EntityId.fromPbjAccountId(recordEntry.payerAccountId()));
    }

    public static void reportOnTxnRecords(@NonNull Writer writer, @NonNull List<BBMPayerRecord> records) {
        writer.writeln(formatHeader());
        records.stream()
                .sorted(Comparator.<BBMPayerRecord>comparingLong(
                                r -> r.consensusTime().getSeconds())
                        .thenComparingLong(r -> r.consensusTime().getNanos()))
                .forEach(e -> formatRecords(writer, e));
        writer.writeln("");
    }

    public static void formatRecords(@NonNull final Writer writer, @NonNull final BBMPayerRecord record) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        payerRecordFieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, record));
        writer.writeln(fb);
    }

    @NonNull
    public static String formatHeader() {
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
