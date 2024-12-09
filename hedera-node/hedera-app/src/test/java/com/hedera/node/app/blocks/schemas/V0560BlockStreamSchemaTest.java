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

package com.hedera.node.app.blocks.schemas;

import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_KEY;
import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class V0560BlockStreamSchemaTest {
    @Mock
    private MigrationContext migrationContext;

    @Mock
    private WritableStates writableStates;

    @Mock
    private Consumer<Bytes> migratedBlockHashConsumer;

    @Mock
    private WritableSingletonState<BlockStreamInfo> state;

    private V0560BlockStreamSchema subject;

    @BeforeEach
    void setUp() {
        subject = new V0560BlockStreamSchema(migratedBlockHashConsumer);
    }

    @Test
    void versionIsV0560() {
        assertEquals(new SemanticVersion(0, 56, 0, "", ""), subject.getVersion());
    }

    @Test
    void createsOneSingleton() {
        final var stateDefs = subject.statesToCreate(DEFAULT_CONFIG);
        assertEquals(1, stateDefs.size());
        final var def = stateDefs.iterator().next();
        assertTrue(def.singleton());
        assertEquals(BLOCK_STREAM_INFO_KEY, def.stateKey());
    }

    @Test
    void createsDefaultInfoAtGenesis() {
        given(migrationContext.newStates()).willReturn(writableStates);
        given(writableStates.<BlockStreamInfo>getSingleton(BLOCK_STREAM_INFO_KEY))
                .willReturn(state);

        subject.restart(migrationContext);

        verify(state).put(BlockStreamInfo.DEFAULT);
    }

    @Test
    void assumesMigrationIfNotGenesisAndStateIsNull() {
        final var blockInfo = new BlockInfo(
                666L,
                new Timestamp(1_234_567L, 0),
                Bytes.fromHex("abcd".repeat(24 * 256)),
                new Timestamp(1_234_567L, 890),
                false,
                new Timestamp(1_234_567L, 123));
        final var sharedValues = Map.<String, Object>of(
                "SHARED_BLOCK_RECORD_INFO",
                blockInfo,
                "SHARED_RUNNING_HASHES",
                new RunningHashes(
                        Bytes.fromHex("aa".repeat(48)),
                        Bytes.fromHex("bb".repeat(48)),
                        Bytes.fromHex("cc".repeat(48)),
                        Bytes.fromHex("dd".repeat(48))));
        given(migrationContext.newStates()).willReturn(writableStates);
        given(migrationContext.previousVersion()).willReturn(SemanticVersion.DEFAULT);
        given(writableStates.<BlockStreamInfo>getSingleton(BLOCK_STREAM_INFO_KEY))
                .willReturn(state);
        given(migrationContext.sharedValues()).willReturn(sharedValues);

        subject.restart(migrationContext);

        verify(migratedBlockHashConsumer).accept(Bytes.fromHex("abcd".repeat(24)));
        final var expectedInfo = new BlockStreamInfo(
                blockInfo.lastBlockNumber(),
                blockInfo.firstConsTimeOfLastBlock(),
                Bytes.fromHex("dd".repeat(48) + "cc".repeat(48) + "bb".repeat(48) + "aa".repeat(48)),
                Bytes.fromHex("abcd".repeat(24 * 255)),
                Bytes.EMPTY,
                Bytes.EMPTY,
                0,
                List.of(),
                blockInfo.consTimeOfLastHandledTxn(),
                false,
                SemanticVersion.DEFAULT,
                blockInfo.consTimeOfLastHandledTxn(),
                blockInfo.consTimeOfLastHandledTxn());
        verify(state).put(expectedInfo);
    }

    @Test
    void migrationIsNoopIfNotGenesisAndInfoIsNonNull() {
        given(migrationContext.newStates()).willReturn(writableStates);
        given(migrationContext.previousVersion()).willReturn(SemanticVersion.DEFAULT);
        given(writableStates.<BlockStreamInfo>getSingleton(BLOCK_STREAM_INFO_KEY))
                .willReturn(state);
        given(state.get()).willReturn(BlockStreamInfo.DEFAULT);

        subject.restart(migrationContext);

        verifyNoInteractions(migratedBlockHashConsumer);
    }
}
