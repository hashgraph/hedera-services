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

import static com.hedera.node.app.service.mono.statedumpers.utils.ThingsToStrings.quoteForCsv;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.scheduledtransactions.BBMScheduledTransaction;
import com.hedera.node.app.service.mono.statedumpers.scheduledtransactions.BBMScheduledTransactionId;
import com.hedera.node.app.service.mono.statedumpers.utils.ThingsToStrings;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.hedera.node.app.statedumpers.utils.FieldBuilder;
import com.swirlds.base.utility.Pair;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ScheduledTransactionsDumpUtils {

    public static void dumpModScheduledTransactions(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<ScheduleID>, OnDiskValue<Schedule>> scheduledTransactions,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            final var dumpableScheduledTransactions = gatherModScheduledTransactions(scheduledTransactions);
            reportOnScheduledTransactions(writer, dumpableScheduledTransactions);
            System.out.printf(
                    "=== mod scheduled transactions report is %d bytes at checkpoint %s%n",
                    writer.getSize(), checkpoint.name());
        }
    }

    @NonNull
    private static Map<BBMScheduledTransactionId, BBMScheduledTransaction> gatherModScheduledTransactions(
            VirtualMap<OnDiskKey<ScheduleID>, OnDiskValue<Schedule>> source) {
        final var r = new HashMap<BBMScheduledTransactionId, BBMScheduledTransaction>();
        final var scheduledTransactions =
                new ConcurrentLinkedQueue<Pair<BBMScheduledTransactionId, BBMScheduledTransaction>>();

        try {
            VirtualMapLike.from(source)
                    .extractVirtualMapDataC(
                            getStaticThreadManager(),
                            p -> {
                                try {
                                    scheduledTransactions.add(Pair.of(
                                            fromMod(p.key().getKey()),
                                            fromMod(p.value().getValue())));
                                } catch (InvalidKeyException e) {
                                    throw new RuntimeException(e);
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

    private static void reportOnScheduledTransactions(
            @NonNull final Writer writer,
            @NonNull final Map<BBMScheduledTransactionId, BBMScheduledTransaction> scheduledTransactions) {
        writer.writeln(formatHeader());
        scheduledTransactions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> formatTokenAssociation(writer, e.getValue()));
        writer.writeln("");
    }

    @NonNull
    private static String formatHeader() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    static final String FIELD_SEPARATOR = ";";
    static final String SUBFIELD_SEPARATOR = ",";
    static Function<Boolean, String> booleanFormatter = b -> b ? "T" : "";
    static Function<Object, String> csvQuote = s -> quoteForCsv(FIELD_SEPARATOR, (s == null) ? "" : s.toString());

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
    private static final List<Pair<String, BiConsumer<FieldBuilder, BBMScheduledTransaction>>> fieldFormatters = List.of(
            Pair.of("number", getFieldFormatter(BBMScheduledTransaction::number, Object::toString)),
            Pair.of(
                    "adminKey",
                    getFieldFormatter(
                            BBMScheduledTransaction::adminKey, getOptionalFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("memo", getFieldFormatter(BBMScheduledTransaction::memo, csvQuote)),
            Pair.of("isDeleted", getFieldFormatter(BBMScheduledTransaction::deleted, booleanFormatter)),
            Pair.of("isExecuted", getFieldFormatter(BBMScheduledTransaction::executed, booleanFormatter)),
            Pair.of(
                    "calculatedWaitForExpiry",
                    getFieldFormatter(BBMScheduledTransaction::calculatedWaitForExpiry, booleanFormatter)),
            Pair.of(
                    "waitForExpiryProvided",
                    getFieldFormatter(BBMScheduledTransaction::waitForExpiryProvided, booleanFormatter)),
            Pair.of("payer", getFieldFormatter(BBMScheduledTransaction::payer, ThingsToStrings::toStringOfEntityId)),
            Pair.of(
                    "schedulingAccount",
                    getFieldFormatter(BBMScheduledTransaction::schedulingAccount, ThingsToStrings::toStringOfEntityId)),
            Pair.of(
                    "schedulingTXValidStart",
                    getFieldFormatter(
                            BBMScheduledTransaction::schedulingTXValidStart, ThingsToStrings::toStringOfRichInstant)),
            Pair.of(
                    "expirationTimeProvided",
                    getFieldFormatter(
                            BBMScheduledTransaction::expirationTimeProvided,
                            getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
            Pair.of(
                    "calculatedExpirationTime",
                    getFieldFormatter(
                            BBMScheduledTransaction::calculatedExpirationTime,
                            getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
            Pair.of(
                    "resolutionTime",
                    getFieldFormatter(
                            BBMScheduledTransaction::resolutionTime,
                            getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
            Pair.of(
                    "bodyBytes",
                    getFieldFormatter(BBMScheduledTransaction::bodyBytes, ThingsToStrings::toStringOfByteArray)),
            Pair.of("ordinaryScheduledTxn", getFieldFormatter(BBMScheduledTransaction::ordinaryScheduledTxn, csvQuote)),
            Pair.of("scheduledTxn", getFieldFormatter(BBMScheduledTransaction::scheduledTxn, csvQuote)),
            Pair.of(
                    "signatories",
                    getFieldFormatter(
                            BBMScheduledTransaction::signatories,
                            getListFormatter(ThingsToStrings::toStringOfByteArray, SUBFIELD_SEPARATOR))));
    // spotless:on

    @NonNull
    static <T> BiConsumer<FieldBuilder, BBMScheduledTransaction> getFieldFormatter(
            @NonNull final Function<BBMScheduledTransaction, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, u) -> formatField(fb, u, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final BBMScheduledTransaction scheduledTransaction,
            @NonNull final Function<BBMScheduledTransaction, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(scheduledTransaction)));
    }

    private static void formatTokenAssociation(
            @NonNull final Writer writer, @NonNull final BBMScheduledTransaction scheduledTransaction) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, scheduledTransaction));
        writer.writeln(fb);
    }

    static BBMScheduledTransaction fromMod(@NonNull final Schedule value) throws InvalidKeyException {
        return new BBMScheduledTransaction(
                value.scheduleId().scheduleNum(),
                value.adminKey() != null ? Optional.of(JKey.mapKey(value.adminKey())) : Optional.empty(),
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
                PbjConverter.fromPbj(value.originalCreateTransaction()).toByteArray(),
                PbjConverter.fromPbj(value.originalCreateTransaction()),
                PbjConverter.fromPbj(value.scheduledTransaction()),
                value.signatories().stream().map(k -> toPrimitiveKey(k)).toList());
    }

    static EntityId entityIdFrom(long num) {
        return new EntityId(0L, 0L, num);
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

    static BBMScheduledTransactionId fromMod(@NonNull final ScheduleID scheduleID) {
        return new BBMScheduledTransactionId(scheduleID.scheduleNum());
    }
}
