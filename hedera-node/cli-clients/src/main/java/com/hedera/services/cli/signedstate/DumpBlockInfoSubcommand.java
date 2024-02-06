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

import static com.hedera.node.app.service.mono.state.submerkle.RichInstant.fromJava;
import static com.hedera.services.cli.utils.ThingsToStrings.getMaybeStringifyByteString;
import static com.hedera.services.cli.utils.ThingsToStrings.quoteForCsv;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.services.cli.signedstate.DumpStateCommand.EmitSummary;
import com.hedera.services.cli.signedstate.DumpTokensSubcommand.FieldBuilder;
import com.hedera.services.cli.signedstate.SignedStateCommand.Verbosity;
import com.hedera.services.cli.utils.ThingsToStrings;
import com.hedera.services.cli.utils.Writer;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Dump all block info from a signed state file to a text file in a deterministic order  */
public class DumpBlockInfoSubcommand {

    static void doit(
            @NonNull final SignedStateHolder state,
            @NonNull final Path blockInfoPath,
            @NonNull final EmitSummary emitSummary,
            @NonNull final Verbosity verbosity) {
        new DumpBlockInfoSubcommand(state, blockInfoPath, emitSummary, verbosity).doit();
    }

    @NonNull
    final SignedStateHolder state;

    @NonNull
    final Path blockInfoPath;

    @NonNull
    final EmitSummary emitSummary;

    @NonNull
    final Verbosity verbosity;

    DumpBlockInfoSubcommand(
            @NonNull final SignedStateHolder state,
            @NonNull final Path blockInfoPath,
            @NonNull final EmitSummary emitSummary,
            @NonNull final Verbosity verbosity) {
        requireNonNull(state, "state");
        requireNonNull(blockInfoPath, "blockInfoPath");
        requireNonNull(emitSummary, "emitSummary");
        requireNonNull(verbosity, "verbosity");

        this.state = state;
        this.blockInfoPath = blockInfoPath;
        this.emitSummary = emitSummary;
        this.verbosity = verbosity;
    }

    void doit() {
        final var networkContext = state.getNetworkContext();

        System.out.printf("=== block info ===%n");

        final var blockInfo = gatherBlockInfo(networkContext);

        int reportSize;
        try (@NonNull final var writer = new Writer(blockInfoPath)) {
            if (emitSummary == EmitSummary.YES) reportSummary(writer, blockInfo);
            reportOnBlockInfo(writer, blockInfo);
            reportSize = writer.getSize();
        }

        System.out.printf("=== block info report is %d bytes%n", reportSize);
    }

    @SuppressWarnings(
            "java:S6218") // "Equals/hashcode method should be overridden in records containing array fields" - this
    record BlockInfo(
            long lastBlockNumber,
            @NonNull String blockHashes,
            @Nullable RichInstant consTimeOfLastHandledTxn,
            boolean migrationRecordsStreamed,
            @Nullable RichInstant firstConsTimeOfCurrentBlock) {
        BlockInfo(@NonNull final MerkleNetworkContext networkContext) {
            this(
                    networkContext.getAlignmentBlockNo(),
                    networkContext.stringifiedBlockHashes(),
                    fromJava(networkContext.consensusTimeOfLastHandledTxn()),
                    networkContext.areMigrationRecordsStreamed(),
                    fromJava(networkContext.firstConsTimeOfCurrentBlock()));
            Objects.requireNonNull(blockHashes, "blockHashes");
        }
    }

    @NonNull
    Map<Long, BlockInfo> gatherBlockInfo(@NonNull final MerkleNetworkContext networkContext) {
        final var blockInfo = new TreeMap<Long, BlockInfo>();
        networkContext.forEachNode((en, mt) -> blockInfo.put(en.longValue(), new BlockInfo(mt)));
        return blockInfo;
    }

    void reportSummary(@NonNull Writer writer, @NonNull Map<Long, BlockInfo> topics) {
        writer.writeln("=== %7d: topics".formatted(topics.size()));
        writer.writeln("");
    }

    void reportOnBlockInfo(@NonNull Writer writer, @NonNull Map<Long, BlockInfo> topics) {
        writer.writeln(formatHeader());
        topics.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> formatTopic(writer, e.getValue()));
        writer.writeln("");
    }

    void formatTopic(@NonNull final Writer writer, @NonNull final BlockInfo topic) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, topic));
        writer.writeln(fb);
    }

    @NonNull
    String formatHeader() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    static final String FIELD_SEPARATOR = ";";
    static Function<Boolean, String> booleanFormatter = b -> b ? "T" : "";
    static Function<String, String> csvQuote = s -> quoteForCsv(FIELD_SEPARATOR, s);

    @NonNull
    static List<Pair<String, BiConsumer<FieldBuilder, BlockInfo>>> fieldFormatters = List.of(
            Pair.of("number", getFieldFormatter(BlockInfo::number, Object::toString)),
            Pair.of("memo", getFieldFormatter(BlockInfo::memo, csvQuote)),
            Pair.of(
                    "expiry",
                    getFieldFormatter(BlockInfo::expirationTimestamp, ThingsToStrings::toStringOfRichInstant)),
            Pair.of("deleted", getFieldFormatter(BlockInfo::deleted, booleanFormatter)),
            Pair.of(
                    "adminKey",
                    getFieldFormatter(BlockInfo::adminKey, getNullableFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of(
                    "submitKey",
                    getFieldFormatter(BlockInfo::submitKey, getNullableFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of(
                    "runningHash",
                    getFieldFormatter(BlockInfo::runningHash, getMaybeStringifyByteString(FIELD_SEPARATOR))),
            Pair.of("sequenceNumber", getFieldFormatter(BlockInfo::sequenceNumber, Object::toString)),
            Pair.of("autoRenewSecs", getFieldFormatter(BlockInfo::autoRenewDurationSeconds, Object::toString)),
            Pair.of(
                    "autoRenewAccount",
                    getFieldFormatter(
                            BlockInfo::autoRenewAccountId, getNullableFormatter(ThingsToStrings::toStringOfEntityId))));

    static <T> BiConsumer<FieldBuilder, BlockInfo> getFieldFormatter(
            @NonNull final Function<BlockInfo, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> Function<T, String> getNullableFormatter(@NonNull final Function<T, String> formatter) {
        return t -> null != t ? formatter.apply(t) : "";
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final BlockInfo topic,
            @NonNull final Function<BlockInfo, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(topic)));
    }
}
