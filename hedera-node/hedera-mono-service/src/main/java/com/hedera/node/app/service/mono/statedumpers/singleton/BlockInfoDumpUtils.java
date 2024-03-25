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

package com.hedera.node.app.service.mono.statedumpers.singleton;

import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.utils.FieldBuilder;
import com.hedera.node.app.service.mono.statedumpers.utils.ThingsToStrings;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BlockInfoDumpUtils {
    static Function<Boolean, String> booleanFormatter = b -> b ? "T" : "";

    // spotless:off
    @NonNull
    static List<Pair<String, BiConsumer<FieldBuilder, BBMBlockInfoAndRunningHashes>>> fieldFormatters = List.of(
            Pair.of("lastBlockNumber", getFieldFormatter(BBMBlockInfoAndRunningHashes::lastBlockNumber, Object::toString)),
            Pair.of("blockHashes", getFieldFormatter(BBMBlockInfoAndRunningHashes::blockHashes, Object::toString)),
            Pair.of(
                    "consTimeOfLastHandledTxn",
                    getFieldFormatter(
                            BBMBlockInfoAndRunningHashes::consTimeOfLastHandledTxn,
                            getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
            Pair.of(
                    "migrationRecordsStreamed",
                    getFieldFormatter(BBMBlockInfoAndRunningHashes::migrationRecordsStreamed, booleanFormatter)),
            Pair.of(
                    "firstConsTimeOfCurrentBlock",
                    getFieldFormatter(
                            BBMBlockInfoAndRunningHashes::firstConsTimeOfCurrentBlock,
                            getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
            Pair.of("entityId", getFieldFormatter(BBMBlockInfoAndRunningHashes::entityId, Object::toString)),
            Pair.of("runningHash", getFieldFormatter(BBMBlockInfoAndRunningHashes::runningHash, getNullableFormatter(Object::toString))),
            Pair.of("nMinus1RunningHash", getFieldFormatter(BBMBlockInfoAndRunningHashes::nMinus1RunningHash, getNullableFormatter(Object::toString))),
            Pair.of("nMinus2RunningHash", getFieldFormatter(BBMBlockInfoAndRunningHashes::nMinus2RunningHash, getNullableFormatter(Object::toString))),
            Pair.of("nMinus3RunningHas", getFieldFormatter(BBMBlockInfoAndRunningHashes::nMinus3RunningHash, getNullableFormatter(Object::toString))));
    // spotless:on

    public static void dumpMonoBlockInfo(
            @NonNull final Path path,
            @NonNull final MerkleNetworkContext merkleNetworkContext,
            @NonNull final RecordsRunningHashLeaf recordsRunningHashLeaf,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            final var combined =
                    BBMBlockInfoAndRunningHashes.combineFromMono(merkleNetworkContext, recordsRunningHashLeaf);
            reportOnBlockInfo(writer, combined);

            System.out.printf(
                    "=== mono running hashes and block info report is %d bytes at checkpoint %s%n",
                    writer.getSize(), checkpoint.name());
        }
    }

    public static void reportOnBlockInfo(
            @NonNull final Writer writer,
            @NonNull final BBMBlockInfoAndRunningHashes combinedBlockInfoAndRunningHashes) {
        writer.writeln(formatHeaderForBlockInfo());
        formatBlockInfo(writer, combinedBlockInfoAndRunningHashes);
        writer.writeln("");
    }

    @NonNull
    private static String formatHeaderForBlockInfo() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(Writer.FIELD_SEPARATOR));
    }

    @NonNull
    static <T> BiConsumer<FieldBuilder, BBMBlockInfoAndRunningHashes> getFieldFormatter(
            @NonNull final Function<BBMBlockInfoAndRunningHashes, T> fun,
            @NonNull final Function<T, String> formatter) {
        return (fb, u) -> formatField(fb, u, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final BBMBlockInfoAndRunningHashes info,
            @NonNull final Function<BBMBlockInfoAndRunningHashes, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(info)));
    }

    private static void formatBlockInfo(
            @NonNull final Writer writer,
            @NonNull final BBMBlockInfoAndRunningHashes combinedBlockInfoAndRunningHashes) {
        final var fb = new FieldBuilder(Writer.FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, combinedBlockInfoAndRunningHashes));
        writer.writeln(fb);
    }

    static <T> Function<T, String> getNullableFormatter(@NonNull final Function<T, String> formatter) {
        return t -> null != t ? formatter.apply(t) : "";
    }
}
