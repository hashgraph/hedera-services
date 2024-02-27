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

package com.hedera.node.app.bbm.singleton;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.internals.BytesElement;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.fcqueue.FCQueue;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

record BlockInfoAndRunningHashes(
        long lastBlockNumber,
        @Nullable Timestamp firstConsTimeOfLastBlock,
        @NonNull Bytes blockHashes,
        @Nullable Timestamp consTimeOfLastHandledTxn,
        boolean migrationRecordsStreamed,
        @Nullable Timestamp firstConsTimeOfCurrentBlock,
        long entityId,
        @NonNull Bytes runningHash,
        @NonNull Bytes nMinus1RunningHash,
        @NonNull Bytes nMinus2RunningHash,
        @NonNull Bytes nMinus3RunningHash) {

    public static BlockInfoAndRunningHashes combineFromMono(
            @NonNull final MerkleNetworkContext merkleNetworkContext,
            @NonNull final RecordsRunningHashLeaf recordsRunningHashLeaf)
            throws IOException {
        requireNonNull(merkleNetworkContext);
        requireNonNull(recordsRunningHashLeaf);

        // build block info
        final var blockInfoBuilder = new BlockInfo.Builder()
                .lastBlockNumber(merkleNetworkContext.getAlignmentBlockNo())
                .blockHashes(getBlockHashes(merkleNetworkContext.getBlockHashes()));
        if (merkleNetworkContext.firstConsTimeOfCurrentBlock().getEpochSecond() > 0) {
            blockInfoBuilder.firstConsTimeOfCurrentBlock(Timestamp.newBuilder()
                    .seconds(merkleNetworkContext.firstConsTimeOfCurrentBlock().getEpochSecond())
                    .nanos(merkleNetworkContext.firstConsTimeOfCurrentBlock().getNano())
                    .build());
        }
        if (merkleNetworkContext.consensusTimeOfLastHandledTxn() != null) {
            final var lastHandledTxn = merkleNetworkContext.consensusTimeOfLastHandledTxn();
            blockInfoBuilder.consTimeOfLastHandledTxn(Timestamp.newBuilder()
                    .seconds(lastHandledTxn.getEpochSecond())
                    .nanos(lastHandledTxn.getNano())
                    .build());
        }
        blockInfoBuilder.migrationRecordsStreamed(merkleNetworkContext.areMigrationRecordsStreamed());

        // build running hashes
        var runningHashesBuilder = new RunningHashes.Builder()
                .runningHash(Bytes.wrap(
                        recordsRunningHashLeaf.getRunningHash().getHash().getValue()))
                .nMinus1RunningHash(Bytes.wrap(
                        recordsRunningHashLeaf.getNMinus1RunningHash().getHash().getValue()))
                .nMinus2RunningHash(Bytes.wrap(
                        recordsRunningHashLeaf.getNMinus2RunningHash().getHash().getValue()))
                .nMinus3RunningHash(Bytes.wrap(
                        recordsRunningHashLeaf.getNMinus3RunningHash().getHash().getValue()));

        var entityId = merkleNetworkContext.seqNo().current();

        return combineFromMod(blockInfoBuilder.build(), runningHashesBuilder.build(), entityId);
    }

    public static BlockInfoAndRunningHashes combineFromMod(
            @NonNull final BlockInfo blockInfo, @NonNull final RunningHashes runningHashes, final long entityId) {
        return new BlockInfoAndRunningHashes(
                blockInfo.lastBlockNumber(),
                blockInfo.firstConsTimeOfLastBlock(),
                blockInfo.blockHashes(),
                Objects.requireNonNull(blockInfo.consTimeOfLastHandledTxn(), "consTimeOfLastHandledTxn"),
                blockInfo.migrationRecordsStreamed(),
                blockInfo.firstConsTimeOfCurrentBlock(),
                entityId,
                runningHashes.runningHash(),
                runningHashes.nMinus1RunningHash(),
                runningHashes.nMinus2RunningHash(),
                runningHashes.nMinus3RunningHash());
    }

    private static Bytes getBlockHashes(FCQueue<BytesElement> queue) throws IOException {
        ByteArrayOutputStream collector = new ByteArrayOutputStream();
        final var iterator = queue.iterator();
        while (iterator.hasNext()) {
            final var element = iterator.next();
            collector.write(element.getData());
        }
        return Bytes.wrap(collector.toByteArray());
    }
}
