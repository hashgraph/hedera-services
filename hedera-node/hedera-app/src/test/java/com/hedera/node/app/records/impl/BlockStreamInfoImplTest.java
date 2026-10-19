// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.records.impl;

import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_STATE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.blocks.impl.BlockStreamManagerImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.State;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import java.util.List;
import org.hiero.base.crypto.DigestType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link BlockStreamInfoImpl}, the {@code streamMode == BLOCKS} source of
 * {@link com.hedera.node.app.spi.records.BlockRecordInfo}. Beyond covering each accessor, these tests assert
 * <b>full parity</b> with {@link BlockRecordInfoImpl} on {@code blockNo()} and {@code blockHashByBlockNumber()} —
 * including that the last completed block's hash (absent from the state-resident trailing hashes) is reconstructed
 * so {@code blockhash(block.number - 1)} resolves exactly as in records mode.
 */
@ExtendWith(MockitoExtension.class)
class BlockStreamInfoImplTest {
    private static final Timestamp BLOCK_TIME =
            Timestamp.newBuilder().seconds(1_234_567L).nanos(890).build();

    @Mock
    private State state;

    @Mock
    private ReadableStates readableStates;

    @Mock
    private ReadableSingletonState<BlockStreamInfo> singletonState;

    /** A distinct, deterministic 48-byte (SHA-384-length) hash filled with the given byte. */
    private static Bytes hash(final int seed) {
        final var bytes = new byte[DigestType.SHA_384.digestLength()];
        java.util.Arrays.fill(bytes, (byte) seed);
        return Bytes.wrap(bytes);
    }

    // trailingOutputHashes are chained running hashes whose size follows the configured digest type.
    private static Bytes runningHash(final int seed) {
        return runningHash(seed, DigestType.SHA_384);
    }

    private static Bytes runningHash(final int seed, final DigestType digestType) {
        final var bytes = new byte[digestType.digestLength()];
        java.util.Arrays.fill(bytes, (byte) seed);
        return Bytes.wrap(bytes);
    }

    private static Bytes concat(final Bytes... parts) {
        var total = 0;
        for (final var p : parts) {
            total += (int) p.length();
        }
        final var out = new byte[total];
        var off = 0;
        for (final var p : parts) {
            p.getBytes(0, out, off, (int) p.length());
            off += (int) p.length();
        }
        return Bytes.wrap(out);
    }

    /** A fully-populated {@link BlockStreamInfo} whose subtree roots let {@code reconstructLastBlockHash} run. */
    private static BlockStreamInfo info(final long blockNumber, final Bytes trailingBlockHashes) {
        return BlockStreamInfo.newBuilder()
                .blockNumber(blockNumber)
                .blockTime(BLOCK_TIME)
                .blockEndTime(BLOCK_TIME)
                .trailingBlockHashes(trailingBlockHashes)
                .trailingOutputHashes(Bytes.EMPTY)
                .intermediatePreviousBlockRootHashes(List.of())
                .intermediateBlockRootsLeafCount(0L)
                .rightmostPrecedingStateChangesTreeHashes(List.of())
                .numPrecedingStateChangesItems(0L)
                .startOfBlockStateHash(hash(11))
                .consensusHeaderRootHash(hash(12))
                .inputTreeRootHash(hash(13))
                .outputItemRootHash(hash(14))
                .traceDataRootHash(hash(15))
                .build();
    }

    @Test
    void blockNoIsBlockNumberPlusOne() {
        assertEquals(
                0L,
                new BlockStreamInfoImpl(info(-1L, Bytes.EMPTY), DigestType.SHA_384).blockNo(),
                "genesis matches records");
        assertEquals(1L, new BlockStreamInfoImpl(info(0L, Bytes.EMPTY), DigestType.SHA_384).blockNo());
        assertEquals(667L, new BlockStreamInfoImpl(info(666L, hash(1)), DigestType.SHA_384).blockNo());
    }

    @Test
    void blockTimestampUsesBlockTimeWithDefaultFallback() {
        assertEquals(BLOCK_TIME, new BlockStreamInfoImpl(info(5L, hash(1)), DigestType.SHA_384).blockTimestamp());
        final var noTime = BlockStreamInfo.newBuilder().blockNumber(5L).build();
        assertEquals(Timestamp.DEFAULT, new BlockStreamInfoImpl(noTime, DigestType.SHA_384).blockTimestamp());
    }

    @Test
    void prngSeedIsLeftmostTrailingOutputHashOnceFourArePresent() {
        // Four or more output hashes: seed is the leftmost (n-minus-3 running hash).
        final var four = concat(runningHash(1), runningHash(2), runningHash(3), runningHash(4));
        assertEquals(
                runningHash(1),
                new BlockStreamInfoImpl(
                                BlockStreamInfo.newBuilder()
                                        .trailingOutputHashes(four)
                                        .build(),
                                DigestType.SHA_384)
                        .prngSeed());
        // Fewer than four: not yet available.
        final var three = concat(runningHash(1), runningHash(2), runningHash(3));
        assertNull(new BlockStreamInfoImpl(
                        BlockStreamInfo.newBuilder().trailingOutputHashes(three).build(), DigestType.SHA_384)
                .prngSeed());
        assertNull(new BlockStreamInfoImpl(
                        BlockStreamInfo.newBuilder()
                                .trailingOutputHashes(Bytes.EMPTY)
                                .build(),
                        DigestType.SHA_384)
                .prngSeed());
    }

    @Test
    void prngSeedUsesConfiguredDigestLength() {
        final var sha256 = DigestType.SHA_256;
        final var four =
                concat(runningHash(1, sha256), runningHash(2, sha256), runningHash(3, sha256), runningHash(4, sha256));
        assertEquals(
                runningHash(1, sha256),
                new BlockStreamInfoImpl(
                                BlockStreamInfo.newBuilder()
                                        .trailingOutputHashes(four)
                                        .build(),
                                sha256)
                        .prngSeed());
        final var three = concat(runningHash(1, sha256), runningHash(2, sha256), runningHash(3, sha256));
        assertNull(new BlockStreamInfoImpl(
                        BlockStreamInfo.newBuilder().trailingOutputHashes(three).build(), sha256)
                .prngSeed());
    }

    @Test
    void prngSeedDecodesMixedCutoverWindow() {
        // After a cutover block closes with <4 results, the output window is a mix of pre-cutover 48-byte
        // SHA-384 seeds and post-cutover 32-byte SHA-256 hashes. Here the two leftmost entries are still
        // 48-byte seeds, so the PRNG seed (the leftmost running hash) is the full 48-byte value.
        final var sha256 = DigestType.SHA_256;
        final var mixed = concat(
                runningHash(1, DigestType.SHA_384),
                runningHash(2, DigestType.SHA_384),
                runningHash(3, sha256),
                runningHash(4, sha256));
        assertEquals(160, mixed.length(), "Two 48-byte seeds plus two 32-byte hashes");
        assertEquals(
                runningHash(1, DigestType.SHA_384),
                new BlockStreamInfoImpl(
                                BlockStreamInfo.newBuilder()
                                        .trailingOutputHashes(mixed)
                                        .build(),
                                sha256)
                        .prngSeed(),
                "prngSeed must return the full 48-byte leftmost pre-cutover running hash");
    }

    /** A 32-byte SHA-256 hash left-padded with 16 leading zeros to the uniform 48-byte stored size. */
    private static Bytes paddedSha256Hash(final int seed) {
        final var out = new byte[DigestType.SHA_384.digestLength()];
        final var real = new byte[DigestType.SHA_256.digestLength()];
        java.util.Arrays.fill(real, (byte) seed);
        System.arraycopy(real, 0, out, out.length - real.length, real.length);
        return Bytes.wrap(out);
    }

    @Test
    void blockHashByBlockNumberDecodesMixedPaddedWindowWithSha256() {
        // After a cutover, trailingBlockHashes is a uniform 48-byte-per-entry window mixing genuine 48-byte SHA-384
        // record hashes (pre-cutover, left) with left-padded 32-byte SHA-256 block hashes (post-cutover, right).
        // blockNumber = 3, trailing covers blocks 0, 1 (SHA-384) and 2 (padded SHA-256); block 3 is reconstructed.
        // The window is genesis-consistent (3 entries for blockNumber 3) so the per-entry stride resolves to 48.
        final var trailing = concat(hash(1), hash(2), paddedSha256Hash(3));
        final var blockStreamInfo = info(3L, trailing);
        final var subject = new BlockStreamInfoImpl(blockStreamInfo, DigestType.SHA_256);
        final var reconstructedLast =
                BlockStreamManagerImpl.reconstructLastBlockHash(blockStreamInfo, DigestType.SHA_256, 256);

        // Pre-cutover blocks keep their full 48-byte SHA-384 record hash.
        assertEquals(hash(1), subject.blockHashByBlockNumber(0L), "pre-cutover block 0 is a full 48-byte hash");
        assertEquals(48, (int) subject.blockHashByBlockNumber(0L).length());
        assertEquals(hash(2), subject.blockHashByBlockNumber(1L));
        // Post-cutover block's padding is stripped back to its real 32-byte SHA-256 hash.
        final var realSha256Block2 = runningHash(3, DigestType.SHA_256);
        assertEquals(
                realSha256Block2, subject.blockHashByBlockNumber(2L), "post-cutover block 2 is stripped to 32 bytes");
        assertEquals(32, (int) subject.blockHashByBlockNumber(2L).length());
        // The reconstructed last block's hash resolves and is 32 bytes under SHA-256.
        assertEquals(reconstructedLast, subject.blockHashByBlockNumber(3L));
        assertEquals(32, (int) subject.blockHashByBlockNumber(3L).length());

        assertNull(subject.blockHashByBlockNumber(-1L), "older than the available window");
        assertNull(subject.blockHashByBlockNumber(4L), "future block");
    }

    @Test
    void blockHashByBlockNumberDecodesCompactedNativeWindowWithSha256() {
        // Post-compaction: once no genuine pre-cutover hashes remain, trailingBlockHashes is a uniform
        // 32-byte-per-entry
        // native window (no padding). blockNumber = 3, trailing covers blocks 0, 1, 2; block 3 is reconstructed.
        final var block0 = runningHash(1, DigestType.SHA_256);
        final var block1 = runningHash(2, DigestType.SHA_256);
        final var block2 = runningHash(3, DigestType.SHA_256);
        final var trailing = concat(block0, block1, block2);
        assertEquals(96, (int) trailing.length(), "three native 32-byte entries");
        final var blockStreamInfo = info(3L, trailing);
        final var subject = new BlockStreamInfoImpl(blockStreamInfo, DigestType.SHA_256);
        final var reconstructedLast =
                BlockStreamManagerImpl.reconstructLastBlockHash(blockStreamInfo, DigestType.SHA_256, 256);

        assertEquals(block0, subject.blockHashByBlockNumber(0L));
        assertEquals(32, (int) subject.blockHashByBlockNumber(0L).length());
        assertEquals(block1, subject.blockHashByBlockNumber(1L));
        assertEquals(block2, subject.blockHashByBlockNumber(2L));
        assertEquals(reconstructedLast, subject.blockHashByBlockNumber(3L));
        assertEquals(32, (int) subject.blockHashByBlockNumber(3L).length());
        assertNull(subject.blockHashByBlockNumber(-1L), "older than the available window");
        assertNull(subject.blockHashByBlockNumber(4L), "future block");
    }

    @Test
    void blockHashByBlockNumberIsNullAtGenesis() {
        final var subject = new BlockStreamInfoImpl(info(-1L, Bytes.EMPTY), DigestType.SHA_384);
        assertNull(subject.blockHashByBlockNumber(0L));
        assertNull(subject.blockHashByBlockNumber(-1L));
    }

    @Test
    void blockHashByBlockNumberReconstructsLastBlockAndResolvesTrailing() {
        // blockNumber = 2, trailing covers blocks 0 and 1 (genesis-consistent); block 2's own hash is not in state.
        final var trailing = concat(hash(1), hash(2));
        final var blockStreamInfo = info(2L, trailing);
        final var subject = new BlockStreamInfoImpl(blockStreamInfo, DigestType.SHA_384);
        final var reconstructedLast =
                BlockStreamManagerImpl.reconstructLastBlockHash(blockStreamInfo, DigestType.SHA_384, 256);

        assertEquals(reconstructedLast, subject.blockHashByBlockNumber(2L), "last block hash is reconstructed");
        assertEquals(hash(2), subject.blockHashByBlockNumber(1L), "previous block comes from trailing hashes");
        assertEquals(hash(1), subject.blockHashByBlockNumber(0L));
        assertNull(subject.blockHashByBlockNumber(-1L), "older than the available window");
        assertNull(subject.blockHashByBlockNumber(3L), "future block");
    }

    @Test
    void blockHashByBlockNumberReconstructsTheVeryFirstBlock() {
        // blockNumber = 0: no prior trailing hashes; block 0's hash is reconstructed from genesis.
        final var blockStreamInfo = info(0L, Bytes.EMPTY);
        final var subject = new BlockStreamInfoImpl(blockStreamInfo, DigestType.SHA_384);
        final var reconstructedLast =
                BlockStreamManagerImpl.reconstructLastBlockHash(blockStreamInfo, DigestType.SHA_384, 256);

        assertEquals(reconstructedLast, subject.blockHashByBlockNumber(0L));
        assertNull(subject.blockHashByBlockNumber(1L));
        assertNull(subject.blockHashByBlockNumber(-1L));
    }

    @Test
    void extendedHashesAreStableAcrossRepeatedCalls() {
        // Genesis-consistent window: blockNumber = 2, trailing covers blocks 0 and 1.
        final var subject = new BlockStreamInfoImpl(info(2L, concat(hash(1), hash(2))), DigestType.SHA_384);
        assertEquals(subject.blockHashByBlockNumber(2L), subject.blockHashByBlockNumber(2L));
        assertEquals(subject.blockHashByBlockNumber(1L), subject.blockHashByBlockNumber(1L));
    }

    @Test
    void blockHashByBlockNumberLengthFollowsDigestType() {
        // At blockNumber == 0 the single resolvable hash is the reconstructed last-block hash, whose digest
        // algorithm (and therefore byte length) is selected by digestType: 48 bytes for SHA-384, 32 for SHA-256.
        final var blockStreamInfo = info(0L, Bytes.EMPTY);
        final var sha384 = new BlockStreamInfoImpl(blockStreamInfo, DigestType.SHA_384);
        final var sha256 = new BlockStreamInfoImpl(blockStreamInfo, DigestType.SHA_256);

        assertEquals(48, (int) sha384.blockHashByBlockNumber(0L).length(), "SHA-384 block hash is 48 bytes");
        assertEquals(32, (int) sha256.blockHashByBlockNumber(0L).length(), "SHA-256 block hash is 32 bytes");
        assertNotEquals(
                sha384.blockHashByBlockNumber(0L),
                sha256.blockHashByBlockNumber(0L),
                "changing digestType changes the reconstructed block hash");
    }

    @Test
    void fromStateReadsBlockStreamInfoSingleton() {
        given(state.getReadableStates(BlockStreamService.NAME)).willReturn(readableStates);
        given(readableStates.<BlockStreamInfo>getSingleton(BLOCK_STREAM_INFO_STATE_ID))
                .willReturn(singletonState);
        given(singletonState.get()).willReturn(info(10L, hash(1)));

        final var subject = BlockStreamInfoImpl.from(state, DigestType.SHA_384);

        assertEquals(11L, subject.blockNo());
        assertEquals(BLOCK_TIME, subject.blockTimestamp());
    }

    @Test
    void blockNoHasFullParityWithRecordsAcrossLifecycle() {
        for (final long lastCompleted : new long[] {-1L, 0L, 5L, 665L}) {
            final var blocks = new BlockStreamInfoImpl(
                    info(lastCompleted, lastCompleted < 0 ? Bytes.EMPTY : hash(1)), DigestType.SHA_384);
            final var records = new BlockRecordInfoImpl(
                    BlockInfo.newBuilder()
                            .lastBlockNumber(lastCompleted)
                            .blockHashes(Bytes.EMPTY)
                            .firstConsTimeOfCurrentBlock(BLOCK_TIME)
                            .build(),
                    RunningHashes.DEFAULT);
            assertEquals(records.blockNo(), blocks.blockNo(), "blockNo parity at lastCompleted=" + lastCompleted);
        }
    }

    @Test
    void blockHashByBlockNumberHasFullParityWithRecords() {
        // Blocks-mode view: blockNumber = 2, trailing covers blocks 0 and 1 (genesis-consistent).
        final var trailing = concat(hash(1), hash(2));
        final var blockStreamInfo = info(2L, trailing);
        final var blocks = new BlockStreamInfoImpl(blockStreamInfo, DigestType.SHA_384);
        final var lastBlockHash =
                BlockStreamManagerImpl.reconstructLastBlockHash(blockStreamInfo, DigestType.SHA_384, 256);

        // Equivalent records-mode view: lastBlockNumber = 2 and blockHashes include block 2's hash directly.
        final var records = new BlockRecordInfoImpl(
                BlockInfo.newBuilder()
                        .lastBlockNumber(2L)
                        .blockHashes(concat(hash(1), hash(2), lastBlockHash))
                        .firstConsTimeOfCurrentBlock(BLOCK_TIME)
                        .build(),
                RunningHashes.DEFAULT);

        for (long n = -1; n <= 3; n++) {
            assertEquals(
                    records.blockHashByBlockNumber(n),
                    blocks.blockHashByBlockNumber(n),
                    "blockHashByBlockNumber parity at block " + n);
        }
        assertEquals(records.blockNo(), blocks.blockNo());
    }
}
