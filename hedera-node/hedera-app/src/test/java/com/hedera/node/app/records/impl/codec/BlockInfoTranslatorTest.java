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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.node.app.service.mono.state.merkle.internals.BytesElement;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.fcqueue.FCQueue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockInfoTranslatorTest {
    private static final Timestamp CONSENSUS_TIME =
            Timestamp.newBuilder().seconds(1_234_567L).nanos(13579).build();

    private com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext subject;

    @BeforeEach
    void setup() {
        final FCQueue<BytesElement> hashes = new FCQueue<>();
        hashes.add(new BytesElement("hash1".getBytes()));
        hashes.add(new BytesElement("hash2".getBytes()));
        hashes.add(new BytesElement("hash3".getBytes()));
        subject = new com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext();
        subject.setFirstConsTimeOfCurrentBlock(Instant.ofEpochSecond(1_234_567L, 13579L));
        subject.setBlockNo(5L);
        subject.setBlockHashes(hashes);
    }

    @Test
    void createBlockInfoFromMerkleNetworkContext() throws IOException {
        final BlockInfo blockInfo = BlockInfoTranslator.blockInfoFromMerkleNetworkContext(subject);

        assertEquals(getBaseExpectedBlockInfo().build(), blockInfo);
    }

    @Test
    void createBlockInfoFromMerkleNetworkContextWithEmptyTime() throws IOException {
        subject.setFirstConsTimeOfCurrentBlock(null);
        final BlockInfo blockInfo = BlockInfoTranslator.blockInfoFromMerkleNetworkContext(subject);

        assertEquals(getExpectedBlockInfoWithoutTime().build(), blockInfo);
    }

    @Test
    void createBlockInfoFromMerkleNetworkContextWithLastHandledTime() throws IOException {
        subject.setConsensusTimeOfLastHandledTxn(
                Instant.ofEpochSecond(CONSENSUS_TIME.seconds(), CONSENSUS_TIME.nanos()));
        final BlockInfo blockInfo = BlockInfoTranslator.blockInfoFromMerkleNetworkContext(subject);

        assertEquals(
                getBaseExpectedBlockInfo()
                        .consTimeOfLastHandledTxn(CONSENSUS_TIME)
                        .build(),
                blockInfo);
    }

    @Test
    void createBlockInfoFromMerkleNetworkContextWithMigrationRecordsStreamed() throws IOException {
        subject.setMigrationRecordsStreamed(true);
        final BlockInfo blockInfo = BlockInfoTranslator.blockInfoFromMerkleNetworkContext(subject);

        assertEquals(getBaseExpectedBlockInfo().migrationRecordsStreamed(true).build(), blockInfo);
    }

    private BlockInfo.Builder getBaseExpectedBlockInfo() {
        byte[] result = ByteBuffer.allocate(
                        "hash1".getBytes().length + "hash2".getBytes().length + "hash3".getBytes().length)
                .put("hash1".getBytes())
                .put("hash2".getBytes())
                .put("hash3".getBytes())
                .array();
        return BlockInfo.newBuilder()
                .lastBlockNumber(5L)
                .firstConsTimeOfLastBlock(CONSENSUS_TIME)
                .blockHashes(Bytes.wrap(result));
    }

    private BlockInfo.Builder getExpectedBlockInfoWithoutTime() {
        return getBaseExpectedBlockInfo().firstConsTimeOfLastBlock((Timestamp) null);
    }
}
