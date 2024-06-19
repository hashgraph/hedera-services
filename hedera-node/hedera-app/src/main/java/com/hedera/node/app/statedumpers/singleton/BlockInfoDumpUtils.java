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

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.records.impl.BlockRecordInfoUtils;
import com.hedera.node.app.statedumpers.DumpCheckpoint;
import com.hedera.node.app.statedumpers.legacy.RichInstant;
import com.hedera.node.app.statedumpers.utils.FieldBuilder;
import com.hedera.node.app.statedumpers.utils.ThingsToStrings;
import com.hedera.node.app.statedumpers.utils.Writer;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.utility.Pair;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BlockInfoDumpUtils {
    static Function<Boolean, String> booleanFormatter = b -> b ? "T" : "";

    @NonNull
    static List<Pair<String, BiConsumer<FieldBuilder, BBMBlockInfoAndRunningHashes>>> fieldFormatters = List.of(
            Pair.of(
                    "lastBlockNumber",
                    getFieldFormatter(BBMBlockInfoAndRunningHashes::lastBlockNumber, Object::toString)),
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
            Pair.of(
                    "runningHash",
                    getFieldFormatter(
                            BBMBlockInfoAndRunningHashes::runningHash, getNullableFormatter(Object::toString))),
            Pair.of(
                    "nMinus1RunningHash",
                    getFieldFormatter(
                            BBMBlockInfoAndRunningHashes::nMinus1RunningHash, getNullableFormatter(Object::toString))),
            Pair.of(
                    "nMinus2RunningHash",
                    getFieldFormatter(
                            BBMBlockInfoAndRunningHashes::nMinus2RunningHash, getNullableFormatter(Object::toString))),
            Pair.of(
                    "nMinus3RunningHas",
                    getFieldFormatter(
                            BBMBlockInfoAndRunningHashes::nMinus3RunningHash, getNullableFormatter(Object::toString))));

    public static void dumpModBlockInfo(
            @NonNull final Path path,
            @NonNull final RunningHashes runningHashes,
            @NonNull final BlockInfo blockInfo,
            @NonNull final EntityNumber entityNumber,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            var combined = combineFromMod(blockInfo, runningHashes, entityNumber.number());
            reportOnBlockInfo(writer, combined);
            System.out.printf(
                    "=== mod running hashes and block info report is %d bytes at checkpoint %s%n",
                    writer.getSize(), checkpoint.name());
        }
    }

    public static BBMBlockInfoAndRunningHashes combineFromMod(
            @NonNull final BlockInfo blockInfo, @NonNull final RunningHashes runningHashes, final long entityId) {

        // convert all TimeStamps fields from blockInfo to RichInstant
        var consTimeOfLastHandledTxn = blockInfo.consTimeOfLastHandledTxn() == null
                ? RichInstant.fromJava(Instant.EPOCH)
                : new RichInstant(
                        blockInfo.consTimeOfLastHandledTxn().seconds(),
                        blockInfo.consTimeOfLastHandledTxn().nanos());
        var firstConsTimeOfCurrentBlock = blockInfo.firstConsTimeOfCurrentBlock() == null
                ? RichInstant.fromJava(Instant.EPOCH)
                : new RichInstant(
                        blockInfo.firstConsTimeOfCurrentBlock().seconds(),
                        blockInfo.firstConsTimeOfCurrentBlock().nanos());

        var runningHash = Bytes.EMPTY.equals(runningHashes.runningHash())
                ? null
                : new Hash(runningHashes.runningHash().toByteArray());
        var nMinus1RunningHash = Bytes.EMPTY.equals(runningHashes.nMinus1RunningHash())
                ? null
                : new Hash(runningHashes.nMinus1RunningHash().toByteArray());
        var nMinus2RunningHash = Bytes.EMPTY.equals(runningHashes.nMinus2RunningHash())
                ? null
                : new Hash(runningHashes.nMinus2RunningHash().toByteArray());
        var nMinus3RunningHash = Bytes.EMPTY.equals(runningHashes.nMinus3RunningHash())
                ? null
                : new Hash(runningHashes.nMinus3RunningHash().toByteArray());

        return new BBMBlockInfoAndRunningHashes(
                blockInfo.lastBlockNumber(),
                stringifiedBlockHashes(blockInfo),
                consTimeOfLastHandledTxn,
                blockInfo.migrationRecordsStreamed(),
                firstConsTimeOfCurrentBlock,
                entityId,
                runningHash,
                nMinus1RunningHash,
                nMinus2RunningHash,
                nMinus3RunningHash);
    }

    // generate same string format for hashes, as MerkelNetworkContext.stringifiedBlockHashes() for mod
    static String stringifiedBlockHashes(BlockInfo blockInfo) {
        final var jsonSb = new StringBuilder("[");
        final var blockNo = blockInfo.lastBlockNumber();
        final var blockHashes = blockInfo.blockHashes();
        final var availableBlocksCount = blockHashes.length() / BlockRecordInfoUtils.HASH_SIZE;
        final var firstAvailable = blockNo - availableBlocksCount;

        for (int i = 0; i < availableBlocksCount; i++) {
            final var nextBlockNo = firstAvailable + i;
            final var blockHash =
                    blockHashes.toByteArray(i * BlockRecordInfoUtils.HASH_SIZE, BlockRecordInfoUtils.HASH_SIZE);
            jsonSb.append("{\"num\": ")
                    .append(nextBlockNo)
                    .append(", ")
                    .append("\"hash\": \"")
                    .append(CommonUtils.hex(blockHash))
                    .append("\"}")
                    .append(i < availableBlocksCount ? ", " : "");
        }
        return jsonSb.append("]").toString();
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
