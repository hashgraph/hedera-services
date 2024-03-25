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

import static com.hedera.node.app.service.mono.statedumpers.singleton.BlockInfoDumpUtils.reportOnBlockInfo;

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.records.impl.BlockRecordInfoUtils;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.singleton.BBMBlockInfoAndRunningHashes;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.time.Instant;

public class BlockInfoDumpUtils {

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
                    .append(nextBlockNo + 1)
                    .append(", ")
                    .append("\"hash\": \"")
                    .append(CommonUtils.hex(blockHash))
                    .append("\"}")
                    .append(i < availableBlocksCount ? ", " : "");
        }
        return jsonSb.append("]").toString();
    }
}
