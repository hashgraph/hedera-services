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

import static com.hedera.services.cli.utils.Formatters.getListFormatter;
import static com.hedera.services.cli.utils.Formatters.getNullableFormatter;
import static com.hedera.services.cli.utils.Formatters.getOptionalFormatter;
import static com.hedera.services.cli.utils.ThingsToStrings.quoteForCsv;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.cli.utils.FieldBuilder;
import com.hedera.services.cli.utils.ThingsToStrings;
import com.hedera.services.cli.utils.Writer;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        final var scheduledTransactions = state.getScheduledTransactions().byId();
        System.out.printf("=== %d scheduled transactions ===%n", scheduledTransactions.size());

        final var allScheduledTxs = gatherScheduledTransactions(scheduledTransactions);

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
    record ScheduledTransaction(
            long number,
            @Nullable Key grpcAdminKey,
            @NonNull Optional<JKey> adminKey,
            @Nullable String memo,
            boolean deleted,
            boolean executed,
            boolean calculatedWaitForExpiry,
            boolean waitForExpiryProvided,
            @Nullable EntityId payer,
            @NonNull EntityId schedulingAccount,
            @NonNull RichInstant schedulingTXValidStart,
            @Nullable RichInstant expirationTimeProvided,
            @Nullable RichInstant calculatedExpirationTime,
            @Nullable RichInstant resolutionTime,
            byte[] bodyBytes,
            @NonNull TransactionBody ordinaryScheduledTxn,
            @NonNull SchedulableTransactionBody scheduledTxn,
            @NonNull List<byte[]> signatories,
            @NonNull List<byte[]> notary) {

        ScheduledTransaction(@NonNull final ScheduleVirtualValue scheduledTx) {
            this(
                    scheduledTx.getKey().getKeyAsLong(),
                    scheduledTx.grpcAdminKey(),
                    scheduledTx.adminKey(),
                    scheduledTx.memo().orElse(""),
                    scheduledTx.isDeleted(),
                    scheduledTx.isExecuted(),
                    scheduledTx.calculatedWaitForExpiry(),
                    scheduledTx.waitForExpiryProvided(),
                    scheduledTx.payer(),
                    scheduledTx.schedulingAccount(),
                    scheduledTx.schedulingTXValidStart(),
                    scheduledTx.expirationTimeProvided(),
                    scheduledTx.calculatedExpirationTime(),
                    scheduledTx.getResolutionTime(),
                    scheduledTx.bodyBytes(),
                    scheduledTx.ordinaryViewOfScheduledTxn(),
                    scheduledTx.scheduledTxn(),
                    scheduledTx.signatories(),
                    scheduledTx.notary().stream().map(ByteString::toByteArray).toList());
            Objects.requireNonNull(adminKey, "adminKey");
            Objects.requireNonNull(schedulingAccount, "schedulingAccount");
            Objects.requireNonNull(schedulingTXValidStart, "schedulingTXValidStart");
            Objects.requireNonNull(ordinaryScheduledTxn, "ordinaryScheduledTxn");
            Objects.requireNonNull(scheduledTxn, "scheduledTxn");
            Objects.requireNonNull(signatories, "signatories");
            Objects.requireNonNull(notary, "notary");
        }
    }

    @NonNull
    Map<Long, ScheduledTransaction> gatherScheduledTransactions(
            @NonNull final MerkleMapLike<EntityNumVirtualKey, ScheduleVirtualValue> scheduledTxsStore) {
        final var allScheduledTransactions = new TreeMap<Long, ScheduledTransaction>();
        scheduledTxsStore.forEachNode(
                (en, mt) -> allScheduledTransactions.put(en.getKeyAsLong(), new ScheduledTransaction(mt)));
        return allScheduledTransactions;
    }

    void reportSummary(@NonNull Writer writer, @NonNull Map<Long, ScheduledTransaction> scheduledTxs) {
        writer.writeln("=== %7d: scheduled transactions".formatted(scheduledTxs.size()));
        writer.writeln("");
    }

    void reportOnScheduledTransactions(@NonNull Writer writer, @NonNull Map<Long, ScheduledTransaction> scheduledTxs) {
        writer.writeln(formatHeader());
        scheduledTxs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> formatScheduledTransaction(writer, e.getValue()));
        writer.writeln("");
    }

    void formatScheduledTransaction(@NonNull final Writer writer, @NonNull final ScheduledTransaction scheduledTx) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, scheduledTx));
        writer.writeln(fb);
    }

    @NonNull
    String formatHeader() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    static final String FIELD_SEPARATOR = ";";
    static final String SUBFIELD_SEPARATOR = ",";
    static Function<Boolean, String> booleanFormatter = b -> b ? "T" : "";
    static Function<Object, String> csvQuote = s -> quoteForCsv(FIELD_SEPARATOR, (s == null) ? "" : s.toString());

    @NonNull
    static List<Pair<String, BiConsumer<FieldBuilder, ScheduledTransaction>>> fieldFormatters = List.of(
            Pair.of("number", getFieldFormatter(ScheduledTransaction::number, Object::toString)),
            Pair.of("grpcAdminKey", getFieldFormatter(ScheduledTransaction::grpcAdminKey, csvQuote)),
            Pair.of(
                    "adminKey",
                    getFieldFormatter(
                            ScheduledTransaction::adminKey, getOptionalFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("memo", getFieldFormatter(ScheduledTransaction::memo, csvQuote)),
            Pair.of("isDeleted", getFieldFormatter(ScheduledTransaction::deleted, booleanFormatter)),
            Pair.of("isExecuted", getFieldFormatter(ScheduledTransaction::executed, booleanFormatter)),
            Pair.of(
                    "calculatedWaitForExpiry",
                    getFieldFormatter(ScheduledTransaction::calculatedWaitForExpiry, booleanFormatter)),
            Pair.of(
                    "waitForExpiryProvided",
                    getFieldFormatter(ScheduledTransaction::waitForExpiryProvided, booleanFormatter)),
            Pair.of("payer", getFieldFormatter(ScheduledTransaction::payer, ThingsToStrings::toStringOfEntityId)),
            Pair.of(
                    "schedulingAccount",
                    getFieldFormatter(ScheduledTransaction::schedulingAccount, ThingsToStrings::toStringOfEntityId)),
            Pair.of(
                    "schedulingTXValidStart",
                    getFieldFormatter(
                            ScheduledTransaction::schedulingTXValidStart, ThingsToStrings::toStringOfRichInstant)),
            Pair.of(
                    "expirationTimeProvided",
                    getFieldFormatter(
                            ScheduledTransaction::expirationTimeProvided,
                            getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
            Pair.of(
                    "calculatedExpirationTime",
                    getFieldFormatter(
                            ScheduledTransaction::calculatedExpirationTime,
                            getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
            Pair.of(
                    "resolutionTime",
                    getFieldFormatter(
                            ScheduledTransaction::resolutionTime,
                            getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
            Pair.of(
                    "bodyBytes",
                    getFieldFormatter(ScheduledTransaction::bodyBytes, ThingsToStrings::toStringOfByteArray)),
            Pair.of("ordinaryScheduledTxn", getFieldFormatter(ScheduledTransaction::ordinaryScheduledTxn, csvQuote)),
            Pair.of("scheduledTxn", getFieldFormatter(ScheduledTransaction::scheduledTxn, csvQuote)),
            Pair.of(
                    "signatories",
                    getFieldFormatter(
                            ScheduledTransaction::signatories,
                            getListFormatter(ThingsToStrings::toStringOfByteArray, SUBFIELD_SEPARATOR))),
            Pair.of(
                    "notary",
                    getFieldFormatter(
                            ScheduledTransaction::notary,
                            getListFormatter(ThingsToStrings::toStringOfByteArray, SUBFIELD_SEPARATOR))));

    static <T> BiConsumer<FieldBuilder, ScheduledTransaction> getFieldFormatter(
            @NonNull final Function<ScheduledTransaction, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final ScheduledTransaction scheduledTransaction,
            @NonNull final Function<ScheduledTransaction, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(scheduledTransaction)));
    }
}
