/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.records.impl.codec;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.node.app.service.mono.state.merkle.internals.BytesElement;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.fcqueue.FCQueue;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class BlockInfoTranslator {
    private BlockInfoTranslator() {
        throw new IllegalStateException("Utility class");
    }

    @NonNull
    /**
     * Converts a Part of {@link com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext} to {@link BlockInfo}.
     * @param merkleNetworkContext the {@link com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext}
     * @return the {@link BlockInfo} converted from the {@link com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext}
     */
    public static BlockInfo blockInfoFromMerkleNetworkContext(
            @NonNull final com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext merkleNetworkContext)
            throws IOException {
        requireNonNull(merkleNetworkContext);
        final var blockInfoBuilder = new BlockInfo.Builder()
                .lastBlockNumber(merkleNetworkContext.getAlignmentBlockNo() - 1)
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

        return blockInfoBuilder.build();
    }

    private static Bytes getBlockHashes(FCQueue<BytesElement> queue) throws IOException {
        ByteArrayOutputStream collector = new ByteArrayOutputStream();
        final var iterator = queue.iterator();
        while (iterator.hasNext()) {
            final byte[] hashes = new byte[48];
            final var element = iterator.next();
            System.arraycopy(element.getData(), 0, hashes, 0, 32);
            collector.write(hashes);
        }
        return Bytes.wrap(collector.toByteArray());
    }
}
