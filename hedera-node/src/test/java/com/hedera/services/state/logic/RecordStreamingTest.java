package com.hedera.services.state.logic;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.TransactionContext;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.stream.NonBlockingHandoff;
import com.hedera.services.stream.RecordStreamObject;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHash;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import static com.hedera.services.state.merkle.MerkleNetworkContext.NULL_CONSENSUS_TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@ExtendWith(MockitoExtension.class)
class RecordStreamingTest {
    private static final Instant topLevelConsTime = Instant.ofEpochSecond(1_234_567L, 890);
    private static final Hash INITIAL_RANDOM_HASH = new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength()));

	@Mock
	private TransactionContext txnCtx;
	@Mock
	private NonBlockingHandoff nonBlockingHandoff;
	@Mock
	private Consumer<RunningHash> runningHashUpdate;
	@Mock
	private RecordsHistorian recordsHistorian;
	@Mock
	private SignedTxnAccessor accessor;
	@Mock
	private RecordStreamObject firstFollowingChildRso;
	@Mock
	private RecordStreamObject secondFollowingChildRso;
	@Mock
	private RecordStreamObject firstPrecedingChildRso;
	@Mock
	private RecordsRunningHashLeaf recordsRunningHashLeaf;

    private static final MerkleNetworkContext merkleNetworkContext = new MerkleNetworkContext(
            NULL_CONSENSUS_TIME,
            new SequenceNumber(2),
            1,
            new ExchangeRates());
    private RecordStreaming subject;
    private static final Hash EMPTY_HASH = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);
    private static final RunningHash genesisRunningHash = new RunningHash(EMPTY_HASH);

    @BeforeEach
    void setUp() {
        subject = new RecordStreaming(txnCtx, nonBlockingHandoff, runningHashUpdate, recordsHistorian, () -> merkleNetworkContext, () -> recordsRunningHashLeaf);
    }

    @AfterEach
    void clear() {
        merkleNetworkContext.clearBlockData();
    }

    @Test
    void streamsChildRecordsAtExpectedTimes() throws InterruptedException {
        given(recordsHistorian.hasPrecedingChildRecords()).willReturn(true);
        given(recordsHistorian.getPrecedingChildRecords()).willReturn(List.of(
                firstPrecedingChildRso));
        given(recordsHistorian.hasFollowingChildRecords()).willReturn(true);
        given(recordsHistorian.getFollowingChildRecords()).willReturn(List.of(
                firstFollowingChildRso, secondFollowingChildRso));
        given(nonBlockingHandoff.offer(firstPrecedingChildRso)).willReturn(true);
        given(nonBlockingHandoff.offer(firstFollowingChildRso)).willReturn(true);
        given(nonBlockingHandoff.offer(secondFollowingChildRso)).willReturn(true);
        given(recordsRunningHashLeaf.getLatestBlockHash()).willReturn(new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength())));

        subject.run();

        verify(nonBlockingHandoff).offer(firstPrecedingChildRso);
        verify(nonBlockingHandoff).offer(firstFollowingChildRso);
        verify(nonBlockingHandoff).offer(secondFollowingChildRso);
    }

    @Test
    void doesNothingIfNoRecord() {
        // when:
        subject.run();

        // then:
        verifyNoInteractions(txnCtx, nonBlockingHandoff, runningHashUpdate);
    }

    @Test
    void streamsWhenAvail() throws InterruptedException {
        final var txn = Transaction.getDefaultInstance();
        final var lastRecord = ExpirableTxnRecord.newBuilder().build();
        final var expectedRso = new RecordStreamObject(lastRecord, txn, topLevelConsTime);

        given(accessor.getSignedTxnWrapper()).willReturn(txn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(topLevelConsTime);
        given(recordsRunningHashLeaf.getLatestBlockHash()).willReturn(INITIAL_RANDOM_HASH);
        given(recordsHistorian.lastCreatedTopLevelRecord()).willReturn(lastRecord);
        given(nonBlockingHandoff.offer(expectedRso))
                .willReturn(false)
                .willReturn(true);

        subject.run();

        verify(nonBlockingHandoff, times(2)).offer(expectedRso);
    }

    @Test
    void checkNewBlockCreationWithNullConsTimeOfCurrentBlock() throws InterruptedException {
        final var txn = Transaction.getDefaultInstance();
        final var lastRecord = ExpirableTxnRecord.newBuilder().build();
        final var expectedRso = new RecordStreamObject(lastRecord, txn, topLevelConsTime);

        given(accessor.getSignedTxnWrapper()).willReturn(txn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(topLevelConsTime);
        given(recordsRunningHashLeaf.getLatestBlockHash()).willReturn(INITIAL_RANDOM_HASH);
        given(recordsHistorian.lastCreatedTopLevelRecord()).willReturn(lastRecord);
        given(nonBlockingHandoff.offer(expectedRso))
                .willReturn(false)
                .willReturn(true);
        merkleNetworkContext.setFirstConsTimeOfCurrentBlock(null);

        subject.run();

        verify(nonBlockingHandoff, times(2)).offer(expectedRso);

        assertEquals(1, merkleNetworkContext.getBlockHashCache().size());
        assertEquals(0L, merkleNetworkContext.getBlockHashCache().entrySet().stream().findFirst().get().getKey());
        assertEquals(MerkleNetworkContext.convertSwirldsHashToBesuHash(INITIAL_RANDOM_HASH), merkleNetworkContext.getBlockHashCache().entrySet().stream().findFirst().get().getValue());
        assertEquals(MerkleNetworkContext.convertSwirldsHashToBesuHash(INITIAL_RANDOM_HASH), merkleNetworkContext.getBlockHashByNumber(0));
        assertEquals(1, merkleNetworkContext.getBlockNo());
    }

    @Test
    void checkNewBlockCreationWithMinimumInterval() throws InterruptedException {
        final Map<Long, org.hyperledger.besu.datatypes.Hash> blockNumberToHash = new TreeMap<>();
        final var numberOfTransactions = 3;
        final var numberOfBlocks = 3;

        var consTime = topLevelConsTime;
        for (int i = 0; i < numberOfTransactions; i++) {
            consTime = consTime.plusSeconds(3);
            final var txn = Transaction.getDefaultInstance();
            final var lastRecord = ExpirableTxnRecord.newBuilder().build();
            final var expectedRso = new RecordStreamObject(lastRecord, txn, consTime);

            given(accessor.getSignedTxnWrapper()).willReturn(txn);
            given(txnCtx.accessor()).willReturn(accessor);
            given(txnCtx.consensusTime()).willReturn(consTime);

            if (i != 0) {
                final var randomHash = new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength()));
                blockNumberToHash.put((long) i, MerkleNetworkContext.convertSwirldsHashToBesuHash(randomHash));
                given(recordsRunningHashLeaf.getLatestBlockHash()).willReturn(randomHash);
            } else {
                blockNumberToHash.put((long) i, MerkleNetworkContext.convertSwirldsHashToBesuHash(EMPTY_HASH));
                given(recordsRunningHashLeaf.getLatestBlockHash()).willReturn(EMPTY_HASH);
            }

            given(recordsHistorian.lastCreatedTopLevelRecord()).willReturn(lastRecord);
            given(nonBlockingHandoff.offer(expectedRso))
                    .willReturn(true);

            subject.run();
        }


        assertEquals(numberOfBlocks, merkleNetworkContext.getBlockHashCache().size());
        assertEquals(numberOfBlocks, merkleNetworkContext.getBlockNo());

        for (int i = 0; i < numberOfBlocks; i++) {
            assertEquals(blockNumberToHash.get((long) i), merkleNetworkContext.getBlockHashByNumber(i));
        }
    }

    @Test
    void checkMaxBlockCacheLimit() throws InterruptedException {
        final Map<Long, org.hyperledger.besu.datatypes.Hash> blockNumberToHash = new TreeMap<>();
        final var numberOfTransactions = 256;
        final var numberOfBlocks = 256;

        var consTime = topLevelConsTime;
        for (int i = 0; i < numberOfTransactions; i++) {
            consTime = consTime.plusSeconds(3);
            final var txn = Transaction.getDefaultInstance();
            final var lastRecord = ExpirableTxnRecord.newBuilder().build();
            final var expectedRso = new RecordStreamObject(lastRecord, txn, consTime);

            given(accessor.getSignedTxnWrapper()).willReturn(txn);
            given(txnCtx.accessor()).willReturn(accessor);
            given(txnCtx.consensusTime()).willReturn(consTime);

            if (i != 0) {
                final var randomHash = new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength()));
                blockNumberToHash.put((long) i, MerkleNetworkContext.convertSwirldsHashToBesuHash(randomHash));
                given(recordsRunningHashLeaf.getLatestBlockHash()).willReturn(randomHash);
            } else {
                blockNumberToHash.put((long) i, MerkleNetworkContext.convertSwirldsHashToBesuHash(EMPTY_HASH));
                given(recordsRunningHashLeaf.getLatestBlockHash()).willReturn(EMPTY_HASH);
            }

            given(recordsHistorian.lastCreatedTopLevelRecord()).willReturn(lastRecord);
            given(nonBlockingHandoff.offer(expectedRso))
                    .willReturn(true);

            subject.run();
        }


        assertEquals(numberOfBlocks, merkleNetworkContext.getBlockHashCache().size());
        assertEquals(numberOfBlocks, merkleNetworkContext.getBlockNo());

        for (int i = 0; i < numberOfBlocks; i++) {
            assertEquals(blockNumberToHash.get((long) i), merkleNetworkContext.getBlockHashByNumber(i));
        }
    }

    @Test
    void checkExceedingBlockCacheLimit() throws InterruptedException {
        final Map<Long, org.hyperledger.besu.datatypes.Hash> blockNumberToHash = new TreeMap<>();
        final var maxBlockCacheSize = 256;
        final var numberOfTransactions = 800;
        final var numberOfBlocks = 800;
        final var startCacheBlockAfterStreaming = numberOfBlocks - maxBlockCacheSize;

        var consTime = topLevelConsTime;
        for (int i = 0; i < numberOfTransactions; i++) {
            consTime = consTime.plusSeconds(3);
            final var txn = Transaction.getDefaultInstance();
            final var lastRecord = ExpirableTxnRecord.newBuilder().build();
            final var expectedRso = new RecordStreamObject(lastRecord, txn, consTime);

            given(accessor.getSignedTxnWrapper()).willReturn(txn);
            given(txnCtx.accessor()).willReturn(accessor);
            given(txnCtx.consensusTime()).willReturn(consTime);

            if (i != 0) {
                final var randomHash = new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength()));
                blockNumberToHash.put((long) i, MerkleNetworkContext.convertSwirldsHashToBesuHash(randomHash));
                given(recordsRunningHashLeaf.getLatestBlockHash()).willReturn(randomHash);
            } else {
                blockNumberToHash.put((long) i, MerkleNetworkContext.convertSwirldsHashToBesuHash(EMPTY_HASH));
                given(recordsRunningHashLeaf.getLatestBlockHash()).willReturn(EMPTY_HASH);
            }

            given(recordsHistorian.lastCreatedTopLevelRecord()).willReturn(lastRecord);
            given(nonBlockingHandoff.offer(expectedRso))
                    .willReturn(true);

            subject.run();
        }

        for (int i = 0; i < startCacheBlockAfterStreaming; i++) {
            blockNumberToHash.remove((long) i);
        }

        assertEquals(maxBlockCacheSize, merkleNetworkContext.getBlockHashCache().size());
        assertEquals(numberOfBlocks, merkleNetworkContext.getBlockNo());

        for (int i = startCacheBlockAfterStreaming; i < numberOfBlocks; i++) {
            assertEquals(blockNumberToHash.get((long) i), merkleNetworkContext.getBlockHashByNumber(i));
        }
    }

    @Test
    void checkNewBlockCreationWithMinimumIntervalAndMinimumLastTxnNsPeriod() throws InterruptedException {
        final Map<Long, org.hyperledger.besu.datatypes.Hash> blockNumberToHash = new TreeMap<>();
        final var numberOfTransactions = 6;
        final var numberOfBlocks = 3;

        var consTime = topLevelConsTime;
        for (int i = 0, j = 0; i < numberOfTransactions; i++) {
            final var randomHash = new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength()));
            if (i % 2 == 0) {
                consTime = consTime.plusSeconds(2);
                blockNumberToHash.put((long) j, MerkleNetworkContext.convertSwirldsHashToBesuHash(randomHash));
                j++;
            } else {
                consTime = consTime.plusNanos(10);
            }

            final var txn = Transaction.getDefaultInstance();
            final var lastRecord = ExpirableTxnRecord.newBuilder().build();
            final var expectedRso = new RecordStreamObject(lastRecord, txn, consTime);

            given(accessor.getSignedTxnWrapper()).willReturn(txn);
            given(txnCtx.accessor()).willReturn(accessor);
            given(txnCtx.consensusTime()).willReturn(consTime);

            if (i != 0 && i % 2 == 0) {
                given(recordsRunningHashLeaf.getLatestBlockHash()).willReturn(randomHash);
            } else if (i == 0) {
                blockNumberToHash.put((long) i, MerkleNetworkContext.convertSwirldsHashToBesuHash(EMPTY_HASH));
                given(recordsRunningHashLeaf.getLatestBlockHash()).willReturn(EMPTY_HASH);
            }

            given(recordsHistorian.lastCreatedTopLevelRecord()).willReturn(lastRecord);
            given(nonBlockingHandoff.offer(expectedRso))
                    .willReturn(true);

            subject.run();
        }

        assertEquals(numberOfBlocks, merkleNetworkContext.getBlockHashCache().size());
        assertEquals(numberOfBlocks, merkleNetworkContext.getBlockNo());

        for (int i = 0; i < numberOfBlocks; i++) {
            assertEquals(blockNumberToHash.get((long) i), merkleNetworkContext.getBlockHashByNumber(i));
        }
    }

    @Test
    void checkNewBlockIsNotCreatedWhenMinimumLastTxnNsPeriodIsNotMet() throws InterruptedException {
        final Map<Long, org.hyperledger.besu.datatypes.Hash> blockNumberToHash = new TreeMap<>();
        final var numberOfTransactions = 3;
        final var numberOfBlocks = 1;

        var consTime = topLevelConsTime;
        for (int i = 0; i < numberOfTransactions; i++) {
            consTime = consTime.plusNanos(1);
            final var txn = Transaction.getDefaultInstance();
            final var lastRecord = ExpirableTxnRecord.newBuilder().build();
            final var expectedRso = new RecordStreamObject(lastRecord, txn, consTime);

            given(accessor.getSignedTxnWrapper()).willReturn(txn);
            given(txnCtx.accessor()).willReturn(accessor);
            given(txnCtx.consensusTime()).willReturn(consTime);

			if (i != 0) {
				final var randomHash = new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength()));
				blockNumberToHash.put((long) i, MerkleNetworkContext.convertSwirldsHashToBesuHash(randomHash));
			} else {
				blockNumberToHash.put((long) i, MerkleNetworkContext.convertSwirldsHashToBesuHash(EMPTY_HASH));
                given(recordsRunningHashLeaf.getLatestBlockHash()).willReturn(EMPTY_HASH);
			}

            given(recordsHistorian.lastCreatedTopLevelRecord()).willReturn(lastRecord);
            given(nonBlockingHandoff.offer(expectedRso))
                    .willReturn(true);

            subject.run();
        }


        assertEquals(numberOfBlocks, merkleNetworkContext.getBlockHashCache().size());
        assertEquals(numberOfBlocks, merkleNetworkContext.getBlockNo());

        for (int i = 0; i < numberOfBlocks; i++) {
            assertEquals(blockNumberToHash.get((long) i), merkleNetworkContext.getBlockHashByNumber(i));
        }
    }

    @Test
    void checkBlockIsNotCreatedWhenFutureHashGetsInterrupted() throws InterruptedException {
        final var txn = Transaction.getDefaultInstance();
        final var lastRecord = ExpirableTxnRecord.newBuilder().build();

        given(accessor.getSignedTxnWrapper()).willReturn(txn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(topLevelConsTime);
        given(recordsRunningHashLeaf.getLatestBlockHash()).willThrow(InterruptedException.class);
        given(recordsHistorian.lastCreatedTopLevelRecord()).willReturn(lastRecord);
        merkleNetworkContext.setFirstConsTimeOfCurrentBlock(null);

        subject.run();

        assertEquals(0, merkleNetworkContext.getBlockHashCache().size());
        assertEquals(0, merkleNetworkContext.getBlockNo());
    }

    @Test
    void checkBlockIsCreatedWhenFirstConsAndLastConsBlockTimesAreNull() throws InterruptedException {
        final var txn = Transaction.getDefaultInstance();
        final var lastRecord = ExpirableTxnRecord.newBuilder().build();
        final var expectedRso = new RecordStreamObject(lastRecord, txn, topLevelConsTime);

        given(accessor.getSignedTxnWrapper()).willReturn(txn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(topLevelConsTime);
        given(recordsRunningHashLeaf.getLatestBlockHash()).willReturn(INITIAL_RANDOM_HASH);
        given(recordsHistorian.lastCreatedTopLevelRecord()).willReturn(lastRecord);
        given(nonBlockingHandoff.offer(expectedRso))
                .willReturn(false)
                .willReturn(true);
        merkleNetworkContext.setFirstConsTimeOfCurrentBlock(null);
        merkleNetworkContext.setLatestConsTimeOfCurrentBlock(null);

        subject.run();

        verify(nonBlockingHandoff, times(2)).offer(expectedRso);
    }

    @Test
    void checkNewBlockIsNotCreatedWhenMinimumIntervalIsNotMetAndMinimumLastTxnNsPeriodIsMet() throws InterruptedException {
        final Map<Long, org.hyperledger.besu.datatypes.Hash> blockNumberToHash = new TreeMap<>();
        final var numberOfTransactions = 5;
        final var numberOfBlocks = 2;

        var consTime = topLevelConsTime;
        blockNumberToHash.put((long) 0, MerkleNetworkContext.convertSwirldsHashToBesuHash(EMPTY_HASH));
        for (int i = 0, j = 0; i < numberOfTransactions; i++) {
            final var randomHash = new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength()));
            if (i % 2 == 0) {
                consTime = consTime.plusSeconds(1);
                blockNumberToHash.put((long) j, MerkleNetworkContext.convertSwirldsHashToBesuHash(randomHash));
                if (i != 2) {
                    j++;
                }
            } else {
                consTime = consTime.plusNanos(1001);
            }

            final var txn = Transaction.getDefaultInstance();
            final var lastRecord = ExpirableTxnRecord.newBuilder().build();
            final var expectedRso = new RecordStreamObject(lastRecord, txn, consTime);

            given(accessor.getSignedTxnWrapper()).willReturn(txn);
            given(txnCtx.accessor()).willReturn(accessor);
            given(txnCtx.consensusTime()).willReturn(consTime);

            if (i != 0 && i % 2 == 0) {
                given(recordsRunningHashLeaf.getLatestBlockHash()).willReturn(randomHash);
            } else if (i == 0) {
                blockNumberToHash.put((long) i, MerkleNetworkContext.convertSwirldsHashToBesuHash(EMPTY_HASH));
                given(recordsRunningHashLeaf.getLatestBlockHash()).willReturn(EMPTY_HASH);
            }

            given(recordsHistorian.lastCreatedTopLevelRecord()).willReturn(lastRecord);
            given(nonBlockingHandoff.offer(expectedRso))
                    .willReturn(true);

            subject.run();
        }


        assertEquals(numberOfBlocks, merkleNetworkContext.getBlockHashCache().size());
        assertEquals(numberOfBlocks, merkleNetworkContext.getBlockNo());

        for (int i = 0; i < numberOfBlocks; i++) {
            assertEquals(blockNumberToHash.get((long) i), merkleNetworkContext.getBlockHashByNumber(i));
        }
    }
}