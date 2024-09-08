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

package com.hedera.node.app.blocks.impl;

import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static com.hedera.node.app.blocks.BlockStreamManager.ZERO_BLOCK_HASH;
import static com.hedera.node.app.blocks.BlockStreamService.FAKE_RESTART_BLOCK_HASH;
import static com.hedera.node.app.blocks.impl.BlockImplUtils.appendHash;
import static com.hedera.node.app.blocks.impl.BlockImplUtils.combine;
import static com.hedera.node.app.blocks.schemas.V0540BlockStreamSchema.BLOCK_STREAM_INFO_KEY;
import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema.PLATFORM_STATE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.node.app.blocks.BlockItemWriter;
import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.system.Round;
import com.swirlds.state.State;
import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockStreamManagerImplTest {
    private static final long ROUND_NO = 123L;
    private static final long N_MINUS_2_BLOCK_NO = 664L;
    private static final long N_MINUS_1_BLOCK_NO = 665L;
    private static final long N_BLOCK_NO = 666L;
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L);
    private static final Bytes N_MINUS_2_BLOCK_HASH = Bytes.wrap(noThrowSha384HashOf(new byte[] {(byte) 0xAA}));
    private static final Bytes FAKE_SIGNATURE = Bytes.fromHex("ff".repeat(48));
    private static final BlockItem FAKE_EVENT_TRANSACTION =
            BlockItem.newBuilder().eventTransaction(EventTransaction.DEFAULT).build();
    private static final BlockItem FAKE_TRANSACTION_RESULT =
            BlockItem.newBuilder().transactionResult(TransactionResult.DEFAULT).build();
    private static final Bytes FAKE_RESULT_HASH = noThrowSha384HashOfItem(FAKE_TRANSACTION_RESULT);
    private static final BlockItem FAKE_STATE_CHANGES =
            BlockItem.newBuilder().stateChanges(StateChanges.DEFAULT).build();

    @Mock
    private TssBaseService tssBaseService;

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private BoundaryStateChangeListener boundaryStateChangeListener;

    @Mock
    private BlockItemWriter aWriter;

    @Mock
    private BlockItemWriter bWriter;

    @Mock
    private ReadableStates readableStates;

    private WritableStates writableStates;

    @Mock
    private Round round;

    @Mock
    private State state;

    private final AtomicReference<Bytes> lastAItem = new AtomicReference<>();
    private final AtomicReference<PlatformState> stateRef = new AtomicReference<>();
    private final AtomicReference<BlockStreamInfo> infoRef = new AtomicReference<>();

    private BlockStreamManagerImpl subject;

    @BeforeEach
    void setUp() {
        given(round.getConsensusTimestamp()).willReturn(CONSENSUS_NOW);
        //        given(round.getRoundNum()).willReturn(ROUND_NO);
        given(boundaryStateChangeListener.flushChanges()).willReturn(FAKE_STATE_CHANGES);
        doAnswer(invocationOnMock -> {
                    lastAItem.set(invocationOnMock.getArgument(0));
                    return aWriter;
                })
                .when(aWriter)
                .writeItem(any());
        writableStates = mock(WritableStates.class, withSettings().extraInterfaces(CommittableWritableStates.class));
    }

    @Test
    void startsAndEndsBlockWithSingleRoundPerBlockAsExpected() throws ParseException {
        givenSubjectWith(
                1, blockStreamInfoWith(N_MINUS_1_BLOCK_NO, N_MINUS_2_BLOCK_HASH), platformStateWith(), aWriter);
        final ArgumentCaptor<byte[]> blockHashCaptor = ArgumentCaptor.forClass(byte[].class);

        // Initialize the last (N-1) block hash
        subject.initLastBlockHash(FAKE_RESTART_BLOCK_HASH);

        // Start the round that will be block N
        subject.startRound(round, state);

        // Assert the internal state of the subject has changed as expected and the writer has been opened
        verify(boundaryStateChangeListener).setLastUsedConsensusTime(CONSENSUS_NOW);
        verify(aWriter).openBlock(N_BLOCK_NO);
        assertEquals(N_MINUS_2_BLOCK_HASH, subject.blockHashByBlockNumber(N_MINUS_2_BLOCK_NO));
        assertEquals(FAKE_RESTART_BLOCK_HASH, subject.blockHashByBlockNumber(N_MINUS_1_BLOCK_NO));

        // Write some items to the block
        subject.writeItem(FAKE_EVENT_TRANSACTION);
        subject.writeItem(FAKE_TRANSACTION_RESULT);
        subject.writeItem(FAKE_STATE_CHANGES);

        // End the round
        subject.endRound(state, ROUND_NO);

        // Assert the internal state of the subject has changed as expected and the writer has been closed
        final var expectedBlockInfo = new BlockStreamInfo(
                N_BLOCK_NO,
                asTimestamp(CONSENSUS_NOW),
                appendHash(combine(ZERO_BLOCK_HASH, FAKE_RESULT_HASH), appendHash(ZERO_BLOCK_HASH, Bytes.EMPTY, 4), 4),
                appendHash(FAKE_RESTART_BLOCK_HASH, appendHash(N_MINUS_2_BLOCK_HASH, Bytes.EMPTY, 256), 256));
        final var actualBlockInfo = infoRef.get();
        assertEquals(expectedBlockInfo, actualBlockInfo);
        verify(tssBaseService).requestLedgerSignature(blockHashCaptor.capture());

        // Provide the ledger signature to the subject
        subject.accept(blockHashCaptor.getValue(), FAKE_SIGNATURE.toByteArray());

        // Assert the block proof was written
        final var proofItem = lastAItem.get();
        assertNotNull(proofItem);
        final var item = BlockItem.PROTOBUF.parse(proofItem);
        assertTrue(item.hasBlockProof());
        final var proof = item.blockProofOrThrow();
        assertEquals(N_BLOCK_NO, proof.block());
        assertEquals(FAKE_SIGNATURE, proof.blockSignature());
    }

    private void givenSubjectWith(
            final int roundsPerBlock,
            @NonNull final BlockStreamInfo blockStreamInfo,
            @NonNull final PlatformState platformState,
            @NonNull final BlockItemWriter... writers) {
        final AtomicInteger nextWriter = new AtomicInteger(0);
        final var config = HederaTestConfigBuilder.create()
                .withValue("blockStream.roundsPerBlock", roundsPerBlock)
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(config, 1L));
        subject = new BlockStreamManagerImpl(
                () -> writers[nextWriter.getAndIncrement()],
                ForkJoinPool.commonPool(),
                configProvider,
                tssBaseService,
                boundaryStateChangeListener);
        subject.appendRealHashes();
        given(state.getReadableStates(BlockStreamService.NAME)).willReturn(readableStates);
        given(state.getReadableStates(PlatformStateService.NAME)).willReturn(readableStates);
        infoRef.set(blockStreamInfo);
        stateRef.set(platformState);
        final var blockStreamInfoState =
                new WritableSingletonStateBase<>(BLOCK_STREAM_INFO_KEY, infoRef::get, infoRef::set);
        given(readableStates.<BlockStreamInfo>getSingleton(BLOCK_STREAM_INFO_KEY))
                .willReturn(blockStreamInfoState);
        given(readableStates.<PlatformState>getSingleton(PLATFORM_STATE_KEY))
                .willReturn(new WritableSingletonStateBase<>(PLATFORM_STATE_KEY, stateRef::get, stateRef::set));
        given(state.getWritableStates(BlockStreamService.NAME)).willReturn(writableStates);
        given(writableStates.<BlockStreamInfo>getSingleton(BLOCK_STREAM_INFO_KEY))
                .willReturn(blockStreamInfoState);
        doAnswer(invocationOnMock -> {
                    blockStreamInfoState.commit();
                    return null;
                })
                .when((CommittableWritableStates) writableStates)
                .commit();
    }

    private BlockStreamInfo blockStreamInfoWith(final long blockNumber, @NonNull final Bytes nMinus2Hash) {
        return BlockStreamInfo.newBuilder()
                .blockNumber(blockNumber)
                .trailingBlockHashes(appendHash(nMinus2Hash, Bytes.EMPTY, 256))
                .build();
    }

    private PlatformState platformStateWith() {
        return PlatformState.newBuilder().build();
    }

    private static Bytes noThrowSha384HashOfItem(@NonNull final BlockItem item) {
        return Bytes.wrap(noThrowSha384HashOf(BlockItem.PROTOBUF.toBytes(item).toByteArray()));
    }
}
