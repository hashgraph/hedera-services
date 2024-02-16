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

import static com.hedera.services.cli.utils.ThingsToStrings.quoteForCsv;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.services.cli.signedstate.DumpTokensSubcommand.FieldBuilder;
import com.hedera.services.cli.utils.ThingsToStrings;
import com.hedera.services.cli.utils.Writer;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Dump block info from a signed state file to a text file in a deterministic order  */
public class DumpBlockInfoSubcommand {

    static void doit(@NonNull final SignedStateHolder state, @NonNull final Path blockInfoPath) {
        new DumpBlockInfoSubcommand(state, blockInfoPath).doit();
    }

    @NonNull
    final SignedStateHolder state;

    @NonNull
    final Path blockInfoPath;

    DumpBlockInfoSubcommand(@NonNull final SignedStateHolder state, @NonNull final Path blockInfoPath) {
        requireNonNull(state, "state");
        requireNonNull(blockInfoPath, "blockInfoPath");

        this.state = state;
        this.blockInfoPath = blockInfoPath;
    }

    void doit() {
        final var networkContext = state.getNetworkContext();
        System.out.printf("=== block info ===%n");

        final var blockInfo = BlockInfo.fromMerkleNetworkContext(networkContext);

        int reportSize;
        try (@NonNull final var writer = new Writer(blockInfoPath)) {
            reportOnBlockInfo(writer, blockInfo);
            reportSize = writer.getSize();
        }

        final var runningHashLeaf = state.getRunningHashLeaf();

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
        static BlockInfo fromMerkleNetworkContext(@NonNull final MerkleNetworkContext networkContext) {
            return new BlockInfo(
                    networkContext.getAlignmentBlockNo(),
                    networkContext.stringifiedBlockHashes(),
                    RichInstant.fromJava(networkContext.consensusTimeOfLastHandledTxn()),
                    networkContext.areMigrationRecordsStreamed(),
                    RichInstant.fromJava(networkContext.firstConsTimeOfCurrentBlock()));
        }
    }

    void reportOnBlockInfo(@NonNull Writer writer, @NonNull BlockInfo blockInfo) {
        writer.writeln(formatHeader());
        formatBlockInfo(writer, blockInfo);
        writer.writeln("");
    }

    void formatBlockInfo(@NonNull final Writer writer, @NonNull final BlockInfo blockInfo) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, blockInfo));
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
            Pair.of("lastBlockNumber", getFieldFormatter(BlockInfo::lastBlockNumber, Object::toString)),
            Pair.of("blockHashes", getFieldFormatter(BlockInfo::blockHashes, Object::toString)),
            Pair.of(
                    "consTimeOfLastHandledTxn",
                    getFieldFormatter(
                            BlockInfo::consTimeOfLastHandledTxn,
                            getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
            Pair.of(
                    "migrationRecordsStreamed",
                    getFieldFormatter(BlockInfo::migrationRecordsStreamed, booleanFormatter)),
            Pair.of(
                    "firstConsTimeOfCurrentBlock",
                    getFieldFormatter(
                            BlockInfo::firstConsTimeOfCurrentBlock,
                            getNullableFormatter(ThingsToStrings::toStringOfRichInstant))));

    static <T> BiConsumer<FieldBuilder, BlockInfo> getFieldFormatter(
            @NonNull final Function<BlockInfo, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> Function<T, String> getNullableFormatter(@NonNull final Function<T, String> formatter) {
        return t -> null != t ? formatter.apply(t) : "";
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final BlockInfo blockInfo,
            @NonNull final Function<BlockInfo, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(blockInfo)));
    }
}
