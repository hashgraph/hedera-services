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

package com.hedera.node.app.statedumpers.scheduledtransactions;

import static com.hedera.node.app.service.schedule.impl.handlers.HandlerUtility.childAsOrdinary;
import static com.hedera.node.app.statedumpers.associations.BBMTokenAssociation.entityIdFrom;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.node.app.statedumpers.legacy.JKey;
import com.hedera.node.app.statedumpers.legacy.RichInstant;
import com.hedera.node.app.statedumpers.utils.FieldBuilder;
import com.hedera.node.app.statedumpers.utils.ThingsToStrings;
import com.hedera.node.app.statedumpers.utils.Writer;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.utility.Pair;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ScheduledTransactionsDumpUtils {

    public static void dumpModScheduledTransactions(
            @NonNull final Path path,
            @NonNull final VirtualMap scheduledTransactions,
            @NonNull final VirtualMap byEquality,
            @NonNull final VirtualMap byExpiry) {
        try (@NonNull final var writer = new Writer(path)) {
            System.out.printf("=== Dumping schedule transactions %n ======");

            final var byId = gatherModScheduledTransactionsById(scheduledTransactions);
            reportOnScheduledTransactionsById(writer, byId);
            System.out.println(
                    "Size of byId in State : " + scheduledTransactions.size() + " and gathered : " + byId.size());

            // Not sure how to compare Equality Virtual map in mono and mod
            final var byExpiryDump = gatherModScheduledTransactionsByExpiry(byExpiry);
            reportOnScheduledTransactionsByExpiry(writer, byExpiryDump);
            System.out.println(
                    "Size of byExpiry in State : " + byExpiry.size() + " and gathered : " + byExpiryDump.size());

            try {
                final var byEqualityDump = gatherModScheduledTransactionsByEquality(byEquality);
                reportOnScheduledTransactionsByEquality(writer, byEqualityDump);
                System.out.println("Size of byEquality in State : " + byEquality.size() + " and gathered : "
                        + byEqualityDump.size());
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error in gathering byEqualityDump");
            }
        }
    }

    private static List<BBMScheduledEqualityValue> gatherModScheduledTransactionsByEquality(final VirtualMap source) {
        final List<BBMScheduledEqualityValue> r = new ArrayList<>();
        final var scheduledTransactions = new ConcurrentLinkedQueue<BBMScheduledEqualityValue>();

        try {
            VirtualMapMigration.extractVirtualMapDataC(
                    getStaticThreadManager(),
                    source,
                    p -> scheduledTransactions.add(fromModProtoBytes(p.key(), p.value())),
                    1);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of scheduledTransactions by equality virtual map interrupted!");
            Thread.currentThread().interrupt();
        }

        while (!scheduledTransactions.isEmpty()) {
            final var mapping = scheduledTransactions.poll();
            r.add(mapping);
        }
        return r;
    }

    private static Map<BBMScheduledId, BBMScheduledSecondValue> gatherModScheduledTransactionsByExpiry(
            final VirtualMap source) {
        final var r = new HashMap<BBMScheduledId, BBMScheduledSecondValue>();
        final var scheduledTransactions = new ConcurrentLinkedQueue<Pair<BBMScheduledId, BBMScheduledSecondValue>>();

        try {
            VirtualMapMigration.extractVirtualMapDataC(
                    getStaticThreadManager(),
                    source,
                    p -> {
                        try {
                            final ProtoLong id = ProtoLong.PROTOBUF.parse(p.key());
                            scheduledTransactions.add(
                                    Pair.of(new BBMScheduledId(id.value()), fromModProtoLong(p.key(), p.value())));
                        } catch (final ParseException e) {
                            throw new RuntimeException("Failed to parse a long", e);
                        }
                    },
                    8);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of scheduledTransactions virtual map interrupted!");
            Thread.currentThread().interrupt();
        }
        while (!scheduledTransactions.isEmpty()) {
            final var mapping = scheduledTransactions.poll();
            r.put(mapping.key(), mapping.value());
        }
        return r;
    }

    @NonNull
    private static Map<BBMScheduledId, BBMScheduledTransaction> gatherModScheduledTransactionsById(VirtualMap source) {
        final var r = new HashMap<BBMScheduledId, BBMScheduledTransaction>();
        final var scheduledTransactions = new ConcurrentLinkedQueue<Pair<BBMScheduledId, BBMScheduledTransaction>>();

        try {
            VirtualMapMigration.extractVirtualMapDataC(
                    getStaticThreadManager(),
                    source,
                    p -> scheduledTransactions.add(Pair.of(fromModScheduleId(p.key()), fromModSchedule(p.value()))),
                    8);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of scheduledTransactions virtual map interrupted!");
            Thread.currentThread().interrupt();
        }
        while (!scheduledTransactions.isEmpty()) {
            final var mapping = scheduledTransactions.poll();
            r.put(mapping.key(), mapping.value());
        }
        return r;
    }

    static BBMScheduledSecondValue fromModProtoLong(final Bytes expiryBytes, @NonNull final Bytes valueBytes) {
        final ProtoLong expiry;
        try {
            expiry = ProtoLong.PROTOBUF.parse(expiryBytes);
        } catch (final ParseException e) {
            throw new RuntimeException("Failed to parse a long", e);
        }
        final ScheduleList value;
        try {
            value = ScheduleList.PROTOBUF.parse(valueBytes);
        } catch (final ParseException e) {
            throw new RuntimeException("Failed to parse a schedule list", e);
        }
        final var newMap = new TreeMap<Instant, List<Long>>();
        final var longsList = value.schedules().stream()
                .map(a -> a.scheduleId().scheduleNum())
                .toList();
        newMap.put(Instant.ofEpochSecond(expiry.value()), longsList);
        return new BBMScheduledSecondValue(newMap);
    }

    static BBMScheduledEqualityValue fromModProtoBytes(final Bytes hashBytes, @NonNull final Bytes valueBytes) {
        final ProtoBytes hash;
        try {
            hash = ProtoBytes.PROTOBUF.parse(hashBytes);
        } catch (final ParseException e) {
            throw new RuntimeException("Failed to parse a hash", e);
        }
        final ScheduleList value;
        try {
            value = ScheduleList.PROTOBUF.parse(valueBytes);
        } catch (final ParseException e) {
            throw new RuntimeException("Failed to parse a schedule list", e);
        }
        final var newMap = new TreeMap<String, Long>();
        final var longsList = value.schedules().stream()
                .map(a -> a.scheduleId().scheduleNum())
                .toList();
        newMap.put(hash.value().asUtf8String(), longsList.get(0));
        return new BBMScheduledEqualityValue(newMap);
    }

    static BBMScheduledTransaction fromModSchedule(@NonNull final Bytes scheduleBytes) {
        final Schedule value;
        try {
            value = Schedule.PROTOBUF.parse(scheduleBytes);
        } catch (final ParseException e) {
            throw new RuntimeException("Failed to parse a schedule", e);
        }
        Optional<JKey> adminKey;
        try {
            adminKey = value.adminKey() != null ? Optional.of(JKey.mapKey(value.adminKey())) : Optional.empty();
        } catch (InvalidKeyException e) {
            adminKey = Optional.empty();
        }

        return new BBMScheduledTransaction(
                value.scheduleId().scheduleNum(),
                adminKey,
                value.memo(),
                value.deleted(),
                value.executed(),
                // calculatedWaitForExpiry is the same as waitForExpiryProvided;
                // see ScheduleVirtualValue::from` - to.calculatedWaitForExpiry = to.waitForExpiryProvided;
                value.waitForExpiry(),
                value.waitForExpiry(),
                entityIdFrom(value.payerAccountId().accountNum()),
                entityIdFrom(value.schedulerAccountId().accountNum()),
                RichInstant.fromJava(Instant.ofEpochSecond(
                        value.scheduleValidStart().seconds(),
                        value.scheduleValidStart().nanos())),
                RichInstant.fromJava(Instant.ofEpochSecond(value.providedExpirationSecond())),
                RichInstant.fromJava(Instant.ofEpochSecond(value.calculatedExpirationSecond())),
                RichInstant.fromJava(Instant.ofEpochSecond(
                        value.resolutionTime().seconds(), value.resolutionTime().nanos())),
                CommonPbjConverters.fromPbj(value.originalCreateTransaction()).toByteArray(),
                CommonPbjConverters.fromPbj(childAsOrdinary(value)),
                CommonPbjConverters.fromPbj(value.scheduledTransaction()),
                value.signatories().stream().map(k -> toPrimitiveKey(k)).toList());
    }

    static BBMScheduledId fromModScheduleId(@NonNull final Bytes scheduleIdBytes) {
        final ScheduleID scheduleID;
        try {
            scheduleID = ScheduleID.PROTOBUF.parse(scheduleIdBytes);
        } catch (final ParseException e) {
            throw new RuntimeException("Failed to parse a schedule ID", e);
        }
        return new BBMScheduledId(scheduleID.scheduleNum());
    }

    static byte[] toPrimitiveKey(com.hedera.hapi.node.base.Key key) {
        if (key.hasEd25519()) {
            return key.ed25519().toByteArray();
        } else if (key.hasEcdsaSecp256k1()) {
            return key.ecdsaSecp256k1().toByteArray();
        } else {
            return new byte[] {};
        }
    }

    public static void reportOnScheduledTransactionsByEquality(
            final Writer writer, final List<BBMScheduledEqualityValue> source) {
        writer.writeln("=== Scheduled Transactions by Equality ===");
        source.stream().forEach(e -> writer.writeln(e.toString()));
        writer.writeln("");
    }

    public static void reportOnScheduledTransactionsById(
            @NonNull final Writer writer,
            @NonNull final Map<BBMScheduledId, BBMScheduledTransaction> scheduledTransactions) {
        writer.writeln("=== Scheduled Transactions by ID ===");
        writer.writeln(formatHeader());
        scheduledTransactions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> formatScheduledTransaction(writer, e.getValue()));
        writer.writeln("");
    }

    public static void reportOnScheduledTransactionsByExpiry(
            @NonNull final Writer writer,
            @NonNull final Map<BBMScheduledId, BBMScheduledSecondValue> scheduledTransactions) {
        writer.writeln("=== Scheduled Transactions by Expiry ===");
        scheduledTransactions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> writer.writeln(e.getValue().toString()));
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
    static <T> BiConsumer<FieldBuilder, BBMScheduledTransaction> getFieldFormatterForScheduledTxn(
            @NonNull final Function<BBMScheduledTransaction, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, u) -> fb.append(formatter.apply(fun.apply(u)));
    }

    private static void formatScheduledTransaction(
            @NonNull final Writer writer, @NonNull final BBMScheduledTransaction scheduledTransaction) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormattersForScheduleById.stream().map(Pair::right).forEach(ff -> ff.accept(fb, scheduledTransaction));
        writer.writeln(fb);
    }
}
