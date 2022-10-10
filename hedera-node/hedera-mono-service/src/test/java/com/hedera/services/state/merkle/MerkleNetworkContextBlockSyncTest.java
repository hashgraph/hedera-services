/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.merkle;

import static com.hedera.services.contracts.execution.BlockMetaSource.UNAVAILABLE_BLOCK_HASH;
import static com.hedera.services.state.merkle.MerkleNetworkContext.NUM_BLOCKS_TO_LOG_AFTER_RENUMBERING;
import static com.hedera.services.state.merkle.MerkleNetworkContext.ethHashFrom;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.sysfiles.domain.KnownBlockValues;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.swirlds.common.crypto.Hash;
import java.time.Instant;
import java.util.SplittableRandom;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(LogCaptureExtension.class)
class MerkleNetworkContextBlockSyncTest {
    private static final int m = 64;
    private static final byte[] unmatchedHash = new byte[32];
    private static final byte[][] blockHashes = new byte[m][];
    private static final byte[][] swirldHashes = new byte[m][];
    private static final long knownBlockNo = 666;
    private static final Instant then = Instant.ofEpochSecond(1_234_567, 890);

    static {
        final var r = new SplittableRandom(123456789);
        for (int i = 0; i < m; i++) {
            swirldHashes[i] = new byte[48];
            blockHashes[i] = new byte[32];
            r.nextBytes(swirldHashes[i]);
            System.arraycopy(swirldHashes[i], 0, blockHashes[i], 0, 32);
        }
        r.nextBytes(unmatchedHash);
    }

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private MerkleNetworkContext subject;

    @BeforeEach
    void setUp() {
        subject = new MerkleNetworkContext();
    }

    @Test
    void finishingWithoutKnownBlockNumberUsesNegativeBlockNumbers() {
        final var miniN = m / 16;
        finishNBlocks(miniN);
        for (int i = 0; i < miniN; i++) {
            assertSame(UNAVAILABLE_BLOCK_HASH, subject.getBlockHashByNumber(Long.MIN_VALUE + i));
        }
    }

    @Test
    void unmatchedHashIsNoop() {
        subject.renumberBlocksToMatch(new KnownBlockValues(unmatchedHash, knownBlockNo));
        assertSame(UNAVAILABLE_BLOCK_HASH, subject.getBlockHashByNumber(knownBlockNo));
    }

    @Test
    void renumberingWithUnknownValuesIsNoop() {
        assertDoesNotThrow(
                () -> subject.renumberBlocksToMatch(KnownBlockValues.MISSING_BLOCK_VALUES));
    }

    @Test
    void renumbersAndLogsAsExpected() {
        finishNBlocks(m);
        final var blockOffset = m / 4;
        subject.renumberBlocksToMatch(new KnownBlockValues(blockHashes[blockOffset], knownBlockNo));
        assertSame(
                UNAVAILABLE_BLOCK_HASH,
                subject.getBlockHashByNumber(knownBlockNo - blockOffset - 1));
        for (int i = 0; i < m; i++) {
            assertArrayEquals(
                    blockHashes[i],
                    subject.getBlockHashByNumber(knownBlockNo + i - blockOffset).toArray());
        }
        final var newCurNo = knownBlockNo + m - blockOffset;
        assertSame(UNAVAILABLE_BLOCK_HASH, subject.getBlockHashByNumber(newCurNo));
        assertEquals(newCurNo, subject.getAlignmentBlockNo());
        // and:
        assertEquals(NUM_BLOCKS_TO_LOG_AFTER_RENUMBERING, subject.getBlocksToLog());
        for (int j = 0; j < NUM_BLOCKS_TO_LOG_AFTER_RENUMBERING + 1; j++) {
            subject.finishBlock(
                    ethHashFrom(new Hash(swirldHashes[j % swirldHashes.length])),
                    then.plusSeconds(2 * j));
        }
        assertThat(
                logCaptor.infoLogs(),
                contains(
                        Matchers.startsWith("Renumbered 64 trailing block hashes"),
                        Matchers.startsWith("--- BLOCK UPDATE ---\n  Finished: #" + newCurNo),
                        Matchers.startsWith("--- BLOCK UPDATE ---\n  Finished: #" + (newCurNo + 1)),
                        Matchers.startsWith("--- BLOCK UPDATE ---\n  Finished: #" + (newCurNo + 2)),
                        Matchers.startsWith("--- BLOCK UPDATE ---\n  Finished: #" + (newCurNo + 3)),
                        Matchers.startsWith(
                                "--- BLOCK UPDATE ---\n  Finished: #" + (newCurNo + 4))));
    }

    @Test
    void unknownBlockValuesHaveExpectedDefaults() {
        assertSame(Instant.EPOCH, subject.firstConsTimeOfCurrentBlock());
        subject.setBlockNo(Long.MIN_VALUE + 1);
        assertEquals(Long.MIN_VALUE + 1, subject.getAlignmentBlockNo());
    }

    private void finishNBlocks(final int n) {
        for (int i = 0; i < n; i++) {
            subject.finishBlock(ethHashFrom(new Hash(swirldHashes[i])), then.plusNanos(i));
        }
    }
}
