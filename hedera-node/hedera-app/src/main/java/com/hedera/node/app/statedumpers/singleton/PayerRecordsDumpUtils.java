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

package com.hedera.node.app.statedumpers.singleton;

import com.hedera.hapi.node.state.recordcache.TransactionRecordEntry;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.submerkle.TxnId;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.singleton.BBMPayerRecord;
import com.hedera.node.app.service.mono.statedumpers.utils.ThingsToStrings;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.node.app.state.merkle.queue.QueueNode;
import com.hedera.node.app.statedumpers.utils.FieldBuilder;
import com.swirlds.base.utility.Pair;
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

    @NonNull
    static List<Pair<String, BiConsumer<FieldBuilder, BBMPayerRecord>>> fieldFormatters = List.of(
            Pair.of("txnId", getFieldFormatter(BBMPayerRecord::transactionId, Object::toString)),
            Pair.of(
                    "consensusTime",
                    getFieldFormatter(BBMPayerRecord::consensusTime, ThingsToStrings::toStringOfRichInstant)),
            Pair.of("payer", getFieldFormatter(BBMPayerRecord::payer, ThingsToStrings::toStringOfEntityId)));

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

    static void reportOnTxnRecords(@NonNull Writer writer, @NonNull List<BBMPayerRecord> records) {
        writer.writeln(formatHeader());
        records.stream()
                .sorted(Comparator.comparing(BBMPayerRecord::consensusTime))
                .forEach(e -> formatRecords(writer, e));
        writer.writeln("");
    }

    static void formatRecords(@NonNull final Writer writer, @NonNull final BBMPayerRecord record) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, record));
        writer.writeln(fb);
    }

    @NonNull
    static String formatHeader() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    private static <T> BiConsumer<FieldBuilder, BBMPayerRecord> getFieldFormatter(
            @NonNull final Function<BBMPayerRecord, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final BBMPayerRecord transaction,
            @NonNull final Function<BBMPayerRecord, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(transaction)));
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
}
