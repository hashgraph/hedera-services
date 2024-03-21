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

package com.hedera.node.app.service.mono.statedumpers.scheduledtransactions;

import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.utils.FieldBuilder;
import com.hedera.node.app.service.mono.statedumpers.utils.ThingsToStrings;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ScheduledTransactionsDumpUtils {
    public static void dumpMonoScheduledTransactions(
            @NonNull final Path path,
            @NonNull final MerkleScheduledTransactions scheduledTransactions,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            final var byId = scheduledTransactions.byId();
            final var byEquality = scheduledTransactions.byEquality();
            final var byExpirationSecond = scheduledTransactions.byExpirationSecond();

            final var byIdBump = gatherMonoScheduledTransactionsByID(byId);
            reportOnScheduledTransactionsById(writer, byIdBump);
            System.out.printf(
                    "=== mono scheduled transactions report is %d bytes at checkpoint %s%n",
                    writer.getSize(), checkpoint.name());

            final var byEqualityDump = gatherMonoScheduledTransactionsByEquality(byEquality);
            reportOnScheduledTransactionsByEquality(writer, byEqualityDump);
            System.out.printf(
                    "=== mono scheduled transactions report is %d bytes at checkpoint %s%n",
                    writer.getSize(), checkpoint.name());

            final var byExpiryDump = gatherMonoScheduledTransactionsByExpiry(byExpirationSecond);
            reportOnScheduledTransactionsByExpiry(writer, byExpiryDump);
            System.out.printf(
                    "=== mono scheduled transactions report is %d bytes at checkpoint %s%n",
                    writer.getSize(), checkpoint.name());
        }
    }

    @NonNull
    private static Map<ScheduledTransactionId, ScheduledTransaction> gatherMonoScheduledTransactionsByID(
            MerkleMapLike<EntityNumVirtualKey, ScheduleVirtualValue> source) {
        final var r = new HashMap<ScheduledTransactionId, ScheduledTransaction>();
        source.forEach((k, v) -> r.put(ScheduledTransactionId.fromMono(k), ScheduledTransaction.fromMono(v)));
        return r;
    }

    @NonNull
    private static Map<ScheduledTransactionId, ScheduledEqualityValue> gatherMonoScheduledTransactionsByEquality(
            MerkleMapLike<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue> source) {
        final var r = new HashMap<ScheduledTransactionId, ScheduledEqualityValue>();
        source.forEach((k, v) -> r.put(ScheduledTransactionId.fromMono(k), ScheduledEqualityValue.fromMono(v)));
        return r;
    }

    @NonNull
    private static Map<ScheduledTransactionId, ScheduledSecondValue> gatherMonoScheduledTransactionsByExpiry(
            MerkleMapLike<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue> source) {
        final var r = new HashMap<ScheduledTransactionId, ScheduledSecondValue>();
        source.forEach((k, v) -> r.put(ScheduledTransactionId.fromMono(k), ScheduledSecondValue.fromMono(v)));
        return r;
    }

    private static void reportOnScheduledTransactionsById(
            @NonNull final Writer writer,
            @NonNull final Map<ScheduledTransactionId, ScheduledTransaction> scheduledTransactions) {
        writer.writeln(formatHeader());
        scheduledTransactions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> formatScheduledTransaction(writer, e.getValue()));
        writer.writeln("");
    }

    private static void reportOnScheduledTransactionsByEquality(
            @NonNull final Writer writer,
            @NonNull final Map<ScheduledTransactionId, ScheduledEqualityValue> scheduledTransactions) {
        writer.writeln(formatHeader());
        scheduledTransactions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> formatScheduledTransaction(writer, e.getValue()));
        writer.writeln("");
    }

    private static void reportOnScheduledTransactionsByExpiry(
            @NonNull final Writer writer,
            @NonNull final Map<ScheduledTransactionId, ScheduledSecondValue> scheduledTransactions) {
        writer.writeln(formatHeader());
        scheduledTransactions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> formatScheduledTransaction(writer, e.getValue()));
        writer.writeln("");
    }

    @NonNull
    private static String formatHeader() {
        return fieldFormattersForScheduleById.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    static final String FIELD_SEPARATOR = ";";
    static final String SUBFIELD_SEPARATOR = ",";
    static Function<Boolean, String> booleanFormatter = b -> b ? "T" : "";
    static Function<Object, String> csvQuote =
            s -> ThingsToStrings.quoteForCsv(FIELD_SEPARATOR, (s == null) ? "" : s.toString());

    static <T> Function<Optional<T>, String> getOptionalFormatter(@NonNull final Function<T, String> formatter) {
        return ot -> ot.isPresent() ? formatter.apply(ot.get()) : "";
    }

    static <T> Function<T, String> getNullableFormatter(@NonNull final Function<T, String> formatter) {
        return t -> null != t ? formatter.apply(t) : "";
    }

    static <T> Function<List<T>, String> getListFormatter(
            @NonNull final Function<T, String> formatter, @NonNull final String subfieldSeparator) {
        return lt -> {
            if (!lt.isEmpty()) {
                final var sb = new StringBuilder();
                for (@NonNull final var e : lt) {
                    final var v = formatter.apply(e);
                    sb.append(v);
                    sb.append(subfieldSeparator);
                }
                // Remove last subfield separator
                if (sb.length() >= subfieldSeparator.length()) sb.setLength(sb.length() - subfieldSeparator.length());
                return sb.toString();
            } else return "";
        };
    }

    public static <K, V> Function<Map<K, V>, String> getMapFormatter(
            final Function<Map.Entry<K, V>, String> formatter, final String entrySeparator) {
        return map -> {
            if (!map.isEmpty()) {
                final StringBuilder sb = new StringBuilder();
                for (final Map.Entry<K, V> entry : map.entrySet()) {
                    final String formattedEntry = formatter.apply(entry);
                    sb.append(formattedEntry).append(entrySeparator);
                }
                if (sb.length() >= entrySeparator.length()) {
                    sb.setLength(sb.length() - entrySeparator.length());
                }
                return sb.toString();
            }
            return "";
        };
    }

    // spotless:off
    @NonNull
    private static final List<Pair<String, BiConsumer<FieldBuilder, ScheduledTransaction>>>
            fieldFormattersForScheduleById = List.of(
                    Pair.of("number", getFieldFormatterForScheduledTxn(ScheduledTransaction::number, Object::toString)),
                    Pair.of(
                            "adminKey",
                            getFieldFormatterForScheduledTxn(
                                    ScheduledTransaction::adminKey,
                                    getOptionalFormatter(ThingsToStrings::toStringOfJKey))),
                    Pair.of("memo", getFieldFormatterForScheduledTxn(ScheduledTransaction::memo, csvQuote)),
                    Pair.of(
                            "isDeleted",
                            getFieldFormatterForScheduledTxn(ScheduledTransaction::deleted, booleanFormatter)),
                    Pair.of(
                            "isExecuted",
                            getFieldFormatterForScheduledTxn(ScheduledTransaction::executed, booleanFormatter)),
                    Pair.of(
                            "calculatedWaitForExpiry",
                            getFieldFormatterForScheduledTxn(
                                    ScheduledTransaction::calculatedWaitForExpiry, booleanFormatter)),
                    Pair.of(
                            "waitForExpiryProvided",
                            getFieldFormatterForScheduledTxn(
                                    ScheduledTransaction::waitForExpiryProvided, booleanFormatter)),
                    Pair.of(
                            "payer",
                            getFieldFormatterForScheduledTxn(
                                    ScheduledTransaction::payer, ThingsToStrings::toStringOfEntityId)),
                    Pair.of(
                            "schedulingAccount",
                            getFieldFormatterForScheduledTxn(
                                    ScheduledTransaction::schedulingAccount, ThingsToStrings::toStringOfEntityId)),
                    Pair.of(
                            "schedulingTXValidStart",
                            getFieldFormatterForScheduledTxn(
                                    ScheduledTransaction::schedulingTXValidStart,
                                    ThingsToStrings::toStringOfRichInstant)),
                    Pair.of(
                            "expirationTimeProvided",
                            getFieldFormatterForScheduledTxn(
                                    ScheduledTransaction::expirationTimeProvided,
                                    getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
                    Pair.of(
                            "calculatedExpirationTime",
                            getFieldFormatterForScheduledTxn(
                                    ScheduledTransaction::calculatedExpirationTime,
                                    getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
                    Pair.of(
                            "resolutionTime",
                            getFieldFormatterForScheduledTxn(
                                    ScheduledTransaction::resolutionTime,
                                    getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
                    Pair.of(
                            "bodyBytes",
                            getFieldFormatterForScheduledTxn(
                                    ScheduledTransaction::bodyBytes, ThingsToStrings::toStringOfByteArray)),
                    Pair.of(
                            "ordinaryScheduledTxn",
                            getFieldFormatterForScheduledTxn(ScheduledTransaction::ordinaryScheduledTxn, csvQuote)),
                    Pair.of(
                            "scheduledTxn",
                            getFieldFormatterForScheduledTxn(ScheduledTransaction::scheduledTxn, csvQuote)),
                    Pair.of(
                            "signatories",
                            getFieldFormatterForScheduledTxn(
                                    ScheduledTransaction::signatories,
                                    getListFormatter(ThingsToStrings::toStringOfByteArray, SUBFIELD_SEPARATOR))));

    @NonNull
    private static final List<Pair<String, BiConsumer<FieldBuilder, ScheduledEqualityValue>>>
            fieldFormattersForScheduleByEquality = List.of(
                    Pair.of(
                            "number",
                            getFieldFormatterForScheduledByEquality(ScheduledEqualityValue::number, Object::toString)),
                    Pair.of(
                            "ids",
                            getFieldFormatterForScheduledByEquality(
                                    ScheduledEqualityValue::ids,
                                    getMapFormatter(e -> e.getKey() + "=" + e.getValue(), SUBFIELD_SEPARATOR))));

    @NonNull
    private static final List<Pair<String, BiConsumer<FieldBuilder, ScheduledSecondValue>>>
            fieldFormattersForScheduleByExpiry = List.of(
                    Pair.of(
                            "number",
                            getFieldFormatterForScheduledByExpiry(ScheduledSecondValue::number, Object::toString)),
                    Pair.of(
                            "ids",
                            getFieldFormatterForScheduledByExpiry(
                                    ScheduledSecondValue::ids,
                                    getMapFormatter(
                                            e -> ThingsToStrings.toStringOfRichInstant(e.getKey()) + "="
                                                    + getListFormatter(Object::toString, SUBFIELD_SEPARATOR),
                                            SUBFIELD_SEPARATOR))));

    @NonNull
    static <T> BiConsumer<FieldBuilder, ScheduledTransaction> getFieldFormatterForScheduledTxn(
            @NonNull final Function<ScheduledTransaction, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, u) -> fb.append(formatter.apply(fun.apply(u)));
    }

    @NonNull
    static <T> BiConsumer<FieldBuilder, ScheduledEqualityValue> getFieldFormatterForScheduledByEquality(
            @NonNull final Function<ScheduledEqualityValue, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, u) -> fb.append(formatter.apply(fun.apply(u)));
    }

    @NonNull
    static <T> BiConsumer<FieldBuilder, ScheduledSecondValue> getFieldFormatterForScheduledByExpiry(
            @NonNull final Function<ScheduledSecondValue, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, u) -> fb.append(formatter.apply(fun.apply(u)));
    }

    private static void formatScheduledTransaction(
            @NonNull final Writer writer, @NonNull final ScheduledTransaction scheduledTransaction) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormattersForScheduleById.stream().map(Pair::right).forEach(ff -> ff.accept(fb, scheduledTransaction));
        writer.writeln(fb);
    }

    private static void formatScheduledTransaction(
            @NonNull final Writer writer, @NonNull final ScheduledSecondValue scheduledTransaction) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormattersForScheduleByExpiry.stream().map(Pair::right).forEach(ff -> ff.accept(fb, scheduledTransaction));
        writer.writeln(fb);
    }

    private static void formatScheduledTransaction(
            @NonNull final Writer writer, @NonNull final ScheduledEqualityValue scheduledTransaction) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormattersForScheduleByEquality.stream()
                .map(Pair::right)
                .forEach(ff -> ff.accept(fb, scheduledTransaction));
        writer.writeln(fb);
    }
}
