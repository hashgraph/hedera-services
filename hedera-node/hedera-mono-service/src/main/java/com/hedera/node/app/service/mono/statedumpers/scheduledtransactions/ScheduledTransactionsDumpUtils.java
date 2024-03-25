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
    private static Map<BBMScheduledTransactionId, BBMScheduledTransaction> gatherMonoScheduledTransactionsByID(
            MerkleMapLike<EntityNumVirtualKey, ScheduleVirtualValue> source) {
        final var r = new HashMap<BBMScheduledTransactionId, BBMScheduledTransaction>();
        source.forEach((k, v) -> r.put(BBMScheduledTransactionId.fromMono(k), BBMScheduledTransaction.fromMono(v)));
        return r;
    }

    @NonNull
    private static Map<BBMScheduledTransactionId, BBMScheduledEqualityValue> gatherMonoScheduledTransactionsByEquality(
            MerkleMapLike<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue> source) {
        final var r = new HashMap<BBMScheduledTransactionId, BBMScheduledEqualityValue>();
        source.forEach((k, v) -> r.put(BBMScheduledTransactionId.fromMono(k), BBMScheduledEqualityValue.fromMono(v)));
        return r;
    }

    @NonNull
    private static Map<BBMScheduledTransactionId, BBMScheduledSecondValue> gatherMonoScheduledTransactionsByExpiry(
            MerkleMapLike<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue> source) {
        final var r = new HashMap<BBMScheduledTransactionId, BBMScheduledSecondValue>();
        source.forEach((k, v) -> r.put(BBMScheduledTransactionId.fromMono(k), BBMScheduledSecondValue.fromMono(v)));
        return r;
    }

    public static void reportOnScheduledTransactionsById(
            @NonNull final Writer writer,
            @NonNull final Map<BBMScheduledTransactionId, BBMScheduledTransaction> scheduledTransactions) {
        writer.writeln(formatHeader());
        scheduledTransactions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> formatScheduledTransaction(writer, e.getValue()));
        writer.writeln("");
    }

    private static void reportOnScheduledTransactionsByEquality(
            @NonNull final Writer writer,
            @NonNull final Map<BBMScheduledTransactionId, BBMScheduledEqualityValue> scheduledTransactions) {
        writer.writeln(formatHeader());
        scheduledTransactions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> formatScheduledTransaction(writer, e.getValue()));
        writer.writeln("");
    }

    private static void reportOnScheduledTransactionsByExpiry(
            @NonNull final Writer writer,
            @NonNull final Map<BBMScheduledTransactionId, BBMScheduledSecondValue> scheduledTransactions) {
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
    private static final List<Pair<String, BiConsumer<FieldBuilder, BBMScheduledTransaction>>>
            fieldFormattersForScheduleById = List.of(
                    Pair.of(
                            "number",
                            getFieldFormatterForScheduledTxn(BBMScheduledTransaction::number, Object::toString)),
                    Pair.of(
                            "adminKey",
                            getFieldFormatterForScheduledTxn(
                                    BBMScheduledTransaction::adminKey,
                                    getOptionalFormatter(ThingsToStrings::toStringOfJKey))),
                    Pair.of("memo", getFieldFormatterForScheduledTxn(BBMScheduledTransaction::memo, csvQuote)),
                    Pair.of(
                            "isDeleted",
                            getFieldFormatterForScheduledTxn(BBMScheduledTransaction::deleted, booleanFormatter)),
                    Pair.of(
                            "isExecuted",
                            getFieldFormatterForScheduledTxn(BBMScheduledTransaction::executed, booleanFormatter)),
                    Pair.of(
                            "calculatedWaitForExpiry",
                            getFieldFormatterForScheduledTxn(
                                    BBMScheduledTransaction::calculatedWaitForExpiry, booleanFormatter)),
                    Pair.of(
                            "waitForExpiryProvided",
                            getFieldFormatterForScheduledTxn(
                                    BBMScheduledTransaction::waitForExpiryProvided, booleanFormatter)),
                    Pair.of(
                            "payer",
                            getFieldFormatterForScheduledTxn(
                                    BBMScheduledTransaction::payer, ThingsToStrings::toStringOfEntityId)),
                    Pair.of(
                            "schedulingAccount",
                            getFieldFormatterForScheduledTxn(
                                    BBMScheduledTransaction::schedulingAccount, ThingsToStrings::toStringOfEntityId)),
                    Pair.of(
                            "schedulingTXValidStart",
                            getFieldFormatterForScheduledTxn(
                                    BBMScheduledTransaction::schedulingTXValidStart,
                                    ThingsToStrings::toStringOfRichInstant)),
                    Pair.of(
                            "expirationTimeProvided",
                            getFieldFormatterForScheduledTxn(
                                    BBMScheduledTransaction::expirationTimeProvided,
                                    getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
                    Pair.of(
                            "calculatedExpirationTime",
                            getFieldFormatterForScheduledTxn(
                                    BBMScheduledTransaction::calculatedExpirationTime,
                                    getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
                    Pair.of(
                            "resolutionTime",
                            getFieldFormatterForScheduledTxn(
                                    BBMScheduledTransaction::resolutionTime,
                                    getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
                    Pair.of(
                            "bodyBytes",
                            getFieldFormatterForScheduledTxn(
                                    BBMScheduledTransaction::bodyBytes, ThingsToStrings::toStringOfByteArray)),
                    Pair.of(
                            "ordinaryScheduledTxn",
                            getFieldFormatterForScheduledTxn(BBMScheduledTransaction::ordinaryScheduledTxn, csvQuote)),
                    Pair.of(
                            "scheduledTxn",
                            getFieldFormatterForScheduledTxn(BBMScheduledTransaction::scheduledTxn, csvQuote)),
                    Pair.of(
                            "signatories",
                            getFieldFormatterForScheduledTxn(
                                    BBMScheduledTransaction::signatories,
                                    getListFormatter(ThingsToStrings::toStringOfByteArray, SUBFIELD_SEPARATOR))));

    @NonNull
    private static final List<Pair<String, BiConsumer<FieldBuilder, BBMScheduledEqualityValue>>>
            fieldFormattersForScheduleByEquality = List.of(
                    Pair.of(
                            "number",
                            getFieldFormatterForScheduledByEquality(
                                    BBMScheduledEqualityValue::number, Object::toString)),
                    Pair.of(
                            "ids",
                            getFieldFormatterForScheduledByEquality(
                                    BBMScheduledEqualityValue::ids,
                                    getMapFormatter(e -> e.getKey() + "=" + e.getValue(), SUBFIELD_SEPARATOR))));

    @NonNull
    private static final List<Pair<String, BiConsumer<FieldBuilder, BBMScheduledSecondValue>>>
            fieldFormattersForScheduleByExpiry = List.of(
                    Pair.of(
                            "number",
                            getFieldFormatterForScheduledByExpiry(BBMScheduledSecondValue::number, Object::toString)),
                    Pair.of(
                            "ids",
                            getFieldFormatterForScheduledByExpiry(
                                    BBMScheduledSecondValue::ids,
                                    getMapFormatter(
                                            e -> ThingsToStrings.toStringOfRichInstant(e.getKey()) + "="
                                                    + getListFormatter(Object::toString, SUBFIELD_SEPARATOR),
                                            SUBFIELD_SEPARATOR))));

    @NonNull
    static <T> BiConsumer<FieldBuilder, BBMScheduledTransaction> getFieldFormatterForScheduledTxn(
            @NonNull final Function<BBMScheduledTransaction, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, u) -> fb.append(formatter.apply(fun.apply(u)));
    }

    @NonNull
    static <T> BiConsumer<FieldBuilder, BBMScheduledEqualityValue> getFieldFormatterForScheduledByEquality(
            @NonNull final Function<BBMScheduledEqualityValue, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, u) -> fb.append(formatter.apply(fun.apply(u)));
    }

    @NonNull
    static <T> BiConsumer<FieldBuilder, BBMScheduledSecondValue> getFieldFormatterForScheduledByExpiry(
            @NonNull final Function<BBMScheduledSecondValue, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, u) -> fb.append(formatter.apply(fun.apply(u)));
    }

    private static void formatScheduledTransaction(
            @NonNull final Writer writer, @NonNull final BBMScheduledTransaction scheduledTransaction) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormattersForScheduleById.stream().map(Pair::right).forEach(ff -> ff.accept(fb, scheduledTransaction));
        writer.writeln(fb);
    }

    private static void formatScheduledTransaction(
            @NonNull final Writer writer, @NonNull final BBMScheduledSecondValue scheduledTransaction) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormattersForScheduleByExpiry.stream().map(Pair::right).forEach(ff -> ff.accept(fb, scheduledTransaction));
        writer.writeln(fb);
    }

    private static void formatScheduledTransaction(
            @NonNull final Writer writer, @NonNull final BBMScheduledEqualityValue scheduledTransaction) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormattersForScheduleByEquality.stream()
                .map(Pair::right)
                .forEach(ff -> ff.accept(fb, scheduledTransaction));
        writer.writeln(fb);
    }
}
