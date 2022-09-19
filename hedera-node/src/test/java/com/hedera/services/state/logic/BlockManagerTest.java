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
package com.hedera.services.state.logic;

import static com.hedera.services.context.properties.PropertyNames.HEDERA_RECORD_STREAM_LOG_PERIOD;
import static com.hedera.services.state.merkle.MerkleNetworkContext.ethHashFrom;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.test.utils.TxnUtils;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockManagerTest {
    private static final long blockPeriodSecs = 2L;

    @Mock private BootstrapProperties bootstrapProperties;
    @Mock private MerkleNetworkContext networkContext;
    @Mock private RecordsRunningHashLeaf runningHashLeaf;

    private BlockManager subject;

    @BeforeEach
    void setUp() {
        given(bootstrapProperties.getLongProperty(HEDERA_RECORD_STREAM_LOG_PERIOD))
                .willReturn(blockPeriodSecs);
        subject =
                new BlockManager(bootstrapProperties, () -> networkContext, () -> runningHashLeaf);
    }

    @Test
    void requiresProvisionalValuesToBeComputedBeforeReturningHash() {
        assertThrows(IllegalStateException.class, () -> subject.getBlockHash(1));
    }

    @Test
    void delegatesCurrentBlockNumberToContextIfNot() {
        given(networkContext.getAlignmentBlockNo()).willReturn(someBlockNo);

        assertEquals(someBlockNo, subject.getAlignmentBlockNumber());
    }

    @Test
    void continuesWithCurrentBlockIfInSamePeriod() {
        given(networkContext.firstConsTimeOfCurrentBlock()).willReturn(aTime);
        given(networkContext.getAlignmentBlockNo()).willReturn(someBlockNo);

        final var newBlockNo = subject.updateAndGetAlignmentBlockNumber(someTime);

        assertEquals(someBlockNo, newBlockNo);
        verify(networkContext, never()).setFirstConsTimeOfCurrentBlock(any());
        verify(networkContext, never()).finishBlock(any(), any());
    }

    @Test
    void resetClearsBlockNo() {
        given(networkContext.firstConsTimeOfCurrentBlock()).willReturn(aTime);
        given(networkContext.getAlignmentBlockNo()).willReturn(someBlockNo);

        subject.updateAndGetAlignmentBlockNumber(someTime);
        subject.reset();

        assertThrows(IllegalStateException.class, () -> subject.getBlockHash(1));
    }

    @Test
    void finishesBlockIfUnknownFirstConsTime() throws InterruptedException {
        given(networkContext.finishBlock(ethHashFrom(aFullBlockHash), anotherTime))
                .willReturn(someBlockNo);
        given(runningHashLeaf.currentRunningHash()).willReturn(aFullBlockHash);

        final var newBlockNo = subject.updateAndGetAlignmentBlockNumber(anotherTime);

        assertEquals(someBlockNo, newBlockNo);
    }

    @Test
    void finishesBlockIfNotInSamePeriod() throws InterruptedException {
        given(networkContext.firstConsTimeOfCurrentBlock()).willReturn(aTime);
        given(networkContext.finishBlock(ethHashFrom(aFullBlockHash), anotherTime))
                .willReturn(someBlockNo);
        given(runningHashLeaf.currentRunningHash()).willReturn(aFullBlockHash);

        final var newBlockNo = subject.updateAndGetAlignmentBlockNumber(anotherTime);

        assertEquals(someBlockNo, newBlockNo);
    }

    @Test
    void returnsCurrentBlockNoIfSomehowInterrupted() throws InterruptedException {
        given(networkContext.firstConsTimeOfCurrentBlock()).willReturn(aTime);
        given(runningHashLeaf.currentRunningHash()).willThrow(InterruptedException.class);
        given(networkContext.getAlignmentBlockNo()).willReturn(someBlockNo);

        final var newBlockNo = subject.updateAndGetAlignmentBlockNumber(anotherTime);

        assertEquals(someBlockNo, newBlockNo);
    }

    @Test
    void updatesRunningHashAsExpected() {
        final var someHash = new RunningHash();
        subject.updateCurrentBlockHash(someHash);
        verify(runningHashLeaf).setRunningHash(someHash);
    }

    @Test
    void knowsIfNewBlockNowIsTheTimestampAndNumberIncrements() throws InterruptedException {
        given(networkContext.firstConsTimeOfCurrentBlock()).willReturn(aTime);
        given(runningHashLeaf.currentRunningHash()).willReturn(aFullBlockHash);
        given(networkContext.getAlignmentBlockNo()).willReturn(someBlockNo);

        final var values = subject.computeBlockValues(anotherTime, gasLimit);

        assertEquals(gasLimit, values.getGasLimit());
        assertEquals(someBlockNo + 1, values.getNumber());
        assertEquals(anotherTime.getEpochSecond(), values.getTimestamp());
    }

    @Test
    void knowsIfBlockIsSameThenNetworkCtxApplies() {
        given(networkContext.firstConsTimeOfCurrentBlock()).willReturn(aTime);
        given(networkContext.getAlignmentBlockNo()).willReturn(someBlockNo);

        final var values = subject.computeBlockValues(someTime, gasLimit);

        assertEquals(gasLimit, values.getGasLimit());
        assertEquals(someBlockNo, values.getNumber());
        assertEquals(aTime.getEpochSecond(), values.getTimestamp());
    }

    @Test
    void alwaysDelegatesHashLookupIfNotNewBlock() {
        given(networkContext.firstConsTimeOfCurrentBlock()).willReturn(aTime);
        given(networkContext.getAlignmentBlockNo()).willReturn(someBlockNo);
        given(networkContext.getBlockHashByNumber(someBlockNo)).willReturn(aSuffixHash);

        subject.ensureProvisionalBlockMeta(someTime);
        final var hash = subject.getBlockHash(someBlockNo);

        assertSame(aSuffixHash, hash);
    }

    @Test
    void stillDelegatesHashLookupInNewBlockIfNotPrevBlockNo() throws InterruptedException {
        given(networkContext.firstConsTimeOfCurrentBlock()).willReturn(aTime);
        given(runningHashLeaf.currentRunningHash()).willReturn(aFullBlockHash);
        given(networkContext.getAlignmentBlockNo()).willReturn(someBlockNo);
        given(networkContext.getBlockHashByNumber(someBlockNo + 1)).willReturn(aSuffixHash);

        subject.ensureProvisionalBlockMeta(anotherTime);
        final var hash = subject.getBlockHash(someBlockNo + 1);

        assertSame(aSuffixHash, hash);
    }

    @Test
    void answersHashLookupProvisionallyInNewBlockIfPrevBlockNo() throws InterruptedException {
        given(networkContext.firstConsTimeOfCurrentBlock()).willReturn(aTime);
        given(runningHashLeaf.currentRunningHash()).willReturn(aFullBlockHash);
        given(networkContext.getAlignmentBlockNo()).willReturn(someBlockNo);

        subject.ensureProvisionalBlockMeta(anotherTime);
        final var hash = subject.getBlockHash(someBlockNo);

        assertArrayEquals(aSuffixHash.toArrayUnsafe(), hash.toArrayUnsafe());
        assertNotSame(aSuffixHash, hash);
    }

    @Test
    void shouldLogEveryTransactionStandardCase() {
        final var result = subject.shouldLogEveryTransaction();

        assertFalse(result);
    }

    private static final long gasLimit = 1000;
    private static final long someBlockNo = 123_456;
    private static final Instant aTime = Instant.ofEpochSecond(1_234_567L, 890);
    private static final Instant someTime = Instant.ofEpochSecond(1_234_567L, 890_000);
    private static final Instant anotherTime = Instant.ofEpochSecond(1_234_568L, 890);
    private static final Hash aFullBlockHash = new Hash(TxnUtils.randomUtf8Bytes(48));
    private static final org.hyperledger.besu.datatypes.Hash aSuffixHash =
            ethHashFrom(aFullBlockHash);
}
