/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.ServicesState.EMPTY_HASH;
import static com.hedera.services.evm.contracts.execution.BlockMetaSource.UNAVAILABLE_BLOCK_HASH;
import static com.hedera.services.state.merkle.MerkleNetworkContext.ALL_PRE_EXISTING_ENTITIES_SCANNED;
import static com.hedera.services.state.merkle.MerkleNetworkContext.CURRENT_VERSION;
import static com.hedera.services.state.merkle.MerkleNetworkContext.NEVER_USED_SNAPSHOT;
import static com.hedera.services.state.merkle.MerkleNetworkContext.NO_CONGESTION_STARTS;
import static com.hedera.services.state.merkle.MerkleNetworkContext.NO_PREPARED_UPDATE_FILE_HASH;
import static com.hedera.services.state.merkle.MerkleNetworkContext.NO_PREPARED_UPDATE_FILE_NUM;
import static com.hedera.services.state.merkle.MerkleNetworkContext.NO_SNAPSHOTS;
import static com.hedera.services.state.merkle.MerkleNetworkContext.ethHashFrom;
import static com.hedera.services.sysfiles.domain.KnownBlockValues.MISSING_BLOCK_VALUES;
import static com.hedera.services.utils.Units.HBARS_TO_TINYBARS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.times;

import com.google.protobuf.ByteString;
import com.hedera.services.fees.congestion.MultiplierSources;
import com.hedera.services.state.DualStateAccessor;
import com.hedera.services.state.merkle.internals.BytesElement;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttles.GasLimitDeterministicThrottle;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.platform.state.DualStateImpl;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(LogCaptureExtension.class)
class MerkleNetworkContextTest {
    private final int stateVersion = 13;
    private final int nextTaskTodo = 666;
    private final long preparedUpdateFileNum = 150L;
    private final long lastScannedEntity = 1000L;
    private final byte preExistingEntityScanStatus = ALL_PRE_EXISTING_ENTITIES_SCANNED;
    private final long seqNoPostUpgrade = 1001L;
    private final long lastScannedPostUpgrade = 1234L;
    private final long entitiesTouchedThisSecond = 123L;
    private final long entitiesScannedThisSecond = 123_456L;
    private final byte[] preparedUpdateFileHash =
            "012345678901234567890123456789012345678901234567".getBytes(StandardCharsets.UTF_8);
    private final byte[] otherPreparedUpdateFileHash =
            "x123456789x123456789x123456789x123456789x1234567".getBytes(StandardCharsets.UTF_8);
    private Instant consensusTimeOfLastHandledTxn;
    private Instant firstConsTimeOfCurrentBlock;
    private SequenceNumber seqNo;
    private SequenceNumber seqNoCopy;
    private ExchangeRates midnightRateSet;
    private ExchangeRates midnightRateSetCopy;
    private Instant[] congestionStarts;
    private Instant[] evmCongestionStarts;
    private DeterministicThrottle.UsageSnapshot[] usageSnapshots;

    private FunctionalityThrottling throttling;
    private MultiplierSources multiplierSources;

    private GasLimitDeterministicThrottle gasLimitDeterministicThrottle;
    private DeterministicThrottle.UsageSnapshot gasLimitUsageSnapshot;
    private DeterministicThrottle.UsageSnapshot expiryUsageSnapshot =
            new DeterministicThrottle.UsageSnapshot(666L, Instant.MAX);

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private MerkleNetworkContext subject;

    @BeforeEach
    void setup() {
        congestionStarts =
                new Instant[] {
                    Instant.ofEpochSecond(1_234_567L, 54321L),
                    Instant.ofEpochSecond(1_234_789L, 12345L)
                };
        evmCongestionStarts =
                new Instant[] {
                    Instant.ofEpochSecond(2_234_567L, 54321L),
                    Instant.ofEpochSecond(2_234_789L, 12345L)
                };

        consensusTimeOfLastHandledTxn = Instant.ofEpochSecond(1_234_567L, 54321L);
        firstConsTimeOfCurrentBlock = Instant.ofEpochSecond(1_234_567L, 13579L);

        seqNo = mock(SequenceNumber.class);
        given(seqNo.current()).willReturn(1234L);
        seqNoCopy = mock(SequenceNumber.class);
        given(seqNo.copy()).willReturn(seqNoCopy);
        midnightRateSet = new ExchangeRates(1, 14, 1_234_567L, 1, 15, 2_345_678L);
        midnightRateSetCopy = midnightRateSet.copy();
        usageSnapshots =
                new DeterministicThrottle.UsageSnapshot[] {
                    new DeterministicThrottle.UsageSnapshot(123L, consensusTimeOfLastHandledTxn),
                    new DeterministicThrottle.UsageSnapshot(
                            456L, consensusTimeOfLastHandledTxn.plusSeconds(1L))
                };

        gasLimitDeterministicThrottle = new GasLimitDeterministicThrottle(1234);
        gasLimitUsageSnapshot =
                new DeterministicThrottle.UsageSnapshot(1234L, consensusTimeOfLastHandledTxn);

        subject =
                new MerkleNetworkContext(
                        consensusTimeOfLastHandledTxn, seqNo, lastScannedEntity, midnightRateSet);

        subject.setUsageSnapshots(usageSnapshots);
        subject.setGasThrottleUsageSnapshot(gasLimitUsageSnapshot);
        subject.setExpiryUsageSnapshot(expiryUsageSnapshot);
        subject.setCongestionLevelStarts(congestionStarts());
        subject.setEvmCongestionLevelStarts(evmCongestionStarts);
        subject.setStateVersion(stateVersion);
        subject.updateAutoRenewSummaryCounts(
                (int) entitiesScannedThisSecond, (int) entitiesTouchedThisSecond);
        subject.setPreparedUpdateFileNum(preparedUpdateFileNum);
        subject.setPreparedUpdateFileHash(preparedUpdateFileHash);
        subject.markMigrationRecordsStreamed();
        subject.setFirstConsTimeOfCurrentBlock(firstConsTimeOfCurrentBlock);
        subject.setBlockNo(0L);
        subject.setNextTaskTodo(nextTaskTodo);
        subject.setLastScannedPostUpgrade(lastScannedPostUpgrade);
        subject.setSeqNoPostUpgrade(seqNoPostUpgrade);
        subject.setPreExistingEntityScanStatus(preExistingEntityScanStatus);
    }

    @Test
    void isSelfHashing() {
        assertTrue(subject.isSelfHashing());
        assertNotNull(subject.getHash());
    }

    @Test
    @SuppressWarnings("unchecked")
    void logsAtErrorIfSomehowHashComputationFails() {
        final FCQueue<BytesElement> mockHashes = (FCQueue<BytesElement>) mock(FCQueue.class);

        subject.setBlockHashes(mockHashes);
        given(mockHashes.getHash()).willThrow(UncheckedIOException.class);
        final var hash = subject.getHash();
        assertSame(EMPTY_HASH, hash);

        assertThat(logCaptor.errorLogs(), contains(Matchers.startsWith("Hash computation failed")));
    }

    @Test
    void updatesExpectedFieldsWhenFinishingBlock() {
        final var newFirstConsTime = firstConsTimeOfCurrentBlock.plusSeconds(3);
        subject.setBlockNo(aBlockNo);

        final var newBlockNo = subject.finishBlock(ethHashFrom(aFullBlockHash), newFirstConsTime);

        assertEquals(aEthHash, subject.getBlockHashByNumber(aBlockNo));
        assertEquals(newFirstConsTime, subject.firstConsTimeOfCurrentBlock());
        assertEquals(aBlockNo + 1, newBlockNo);
    }

    @Test
    void doesntKeepMoreThanExpectedBlockHashes() {
        final var newFirstConsTime = firstConsTimeOfCurrentBlock.plusSeconds(3);
        for (int i = 0; i < 256; i++) {
            subject.finishBlock(bEthHash, newFirstConsTime.minusNanos(i + 1));
        }

        subject.setBlockNo(aBlockNo);
        subject.finishBlock(ethHashFrom(aFullBlockHash), newFirstConsTime);

        assertSame(UNAVAILABLE_BLOCK_HASH, subject.getBlockHashByNumber(0L));
        assertEquals(aEthHash, subject.getBlockHashByNumber(aBlockNo));
        assertEquals(newFirstConsTime, subject.firstConsTimeOfCurrentBlock());
    }

    @Test
    void knowsIfUpgradeIsPrepared() {
        assertTrue(subject.hasPreparedUpgrade());

        subject.discardPreparedUpgradeMeta();

        assertFalse(subject.hasPreparedUpgrade());
    }

    @Test
    void preparedHashValidIfMatchesOrAbsent() {
        final var fid = IdUtils.asFile("0.0.150");
        final var specialFiles = mock(MerkleSpecialFiles.class);

        given(specialFiles.hashMatches(fid, preparedUpdateFileHash)).willReturn(true);

        assertTrue(subject.isPreparedFileHashValidGiven(specialFiles));

        subject.setPreparedUpdateFileNum(NO_PREPARED_UPDATE_FILE_NUM);

        assertTrue(subject.isPreparedFileHashValidGiven(specialFiles));
    }

    @Test
    @SuppressWarnings("java:S5961")
    void copyWorks() {
        // given:
        var subjectCopy = subject.copy();

        // expect:
        assertSame(
                subjectCopy.getConsensusTimeOfLastHandledTxn(),
                subject.getConsensusTimeOfLastHandledTxn());
        assertEquals(seqNoCopy, subjectCopy.seqNo());
        assertEquals(subjectCopy.lastScannedEntity(), subject.lastScannedEntity());
        assertEquals(midnightRateSetCopy, subjectCopy.getMidnightRates());
        assertSame(subjectCopy.usageSnapshots(), subject.usageSnapshots());
        assertSame(subjectCopy.expiryUsageSnapshot(), subject.expiryUsageSnapshot());
        assertSame(subjectCopy.getCongestionLevelStarts(), subject.getCongestionLevelStarts());
        assertEquals(subjectCopy.getStateVersion(), stateVersion);
        assertEquals(subjectCopy.idsScannedThisSecond(), entitiesScannedThisSecond);
        assertEquals(subjectCopy.getEntitiesTouchedThisSecond(), entitiesTouchedThisSecond);
        assertEquals(subjectCopy.getPreparedUpdateFileNum(), preparedUpdateFileNum);
        assertSame(subjectCopy.getPreparedUpdateFileHash(), subject.getPreparedUpdateFileHash());
        assertEquals(subjectCopy.getBlockHashes(), subject.getBlockHashes());
        assertNotSame(subject.getBlockHashes(), subjectCopy.getBlockHashes());
        assertSame(
                subjectCopy.firstConsTimeOfCurrentBlock(), subject.firstConsTimeOfCurrentBlock());
        assertEquals(
                subjectCopy.areMigrationRecordsStreamed(), subject.areMigrationRecordsStreamed());
        assertEquals(subjectCopy.areRewardsActivated(), subject.areRewardsActivated());
        assertEquals(subjectCopy.getTotalStakedRewardStart(), subject.getTotalStakedRewardStart());
        assertEquals(subjectCopy.getTotalStakedStart(), subject.getTotalStakedStart());
        assertEquals(subjectCopy.lastScannedPostUpgrade(), subject.lastScannedPostUpgrade());
        assertEquals(subjectCopy.seqNoPostUpgrade(), subject.seqNoPostUpgrade());
        assertEquals(
                subjectCopy.getPreExistingEntityScanStatus(),
                subject.getPreExistingEntityScanStatus());
        // and:
        assertTrue(subject.isImmutable());
        assertFalse(subjectCopy.isImmutable());
        assertNull(subject.getThrottling());
        assertNull(subject.getMultiplierSources());
        assertNull(subjectCopy.getThrottling());
        assertNull(subjectCopy.getMultiplierSources());
        // and:
        assertEquals(subject.getHash(), subjectCopy.getHash());
        subjectCopy.setBlockNo(subject.getAlignmentBlockNo() + 1L);
        assertNotEquals(subject.getHash(), subjectCopy.getHash());
        subjectCopy.setBlockNo(subject.getAlignmentBlockNo());
        subjectCopy.finishBlock(aEthHash, firstConsTimeOfCurrentBlock.plusSeconds(2));
        assertNotEquals(subject.getHash(), subjectCopy.getHash());
    }

    @Test
    void copyWorksWithSyncedThrottles() {
        // setup:
        throttling = mock(FunctionalityThrottling.class);
        final var expiryThrottle = mock(ExpiryThrottle.class);
        multiplierSources = mock(MultiplierSources.class);
        gasLimitDeterministicThrottle = mock(GasLimitDeterministicThrottle.class);

        final var someThrottle = DeterministicThrottle.withTpsAndBurstPeriod(1, 23);
        someThrottle.allow(1, Instant.now());
        final var someStart = Instant.ofEpochSecond(7_654_321L, 0);
        final var syncedStarts = new Instant[] {someStart};
        final var syncedGasStarts = new Instant[] {someStart};

        given(throttling.allActiveThrottles()).willReturn(List.of(someThrottle));

        given(throttling.gasLimitThrottle()).willReturn(gasLimitDeterministicThrottle);
        given(expiryThrottle.getThrottleSnapshot()).willReturn(expiryUsageSnapshot);
        given(gasLimitDeterministicThrottle.usageSnapshot()).willReturn(gasLimitUsageSnapshot);
        given(multiplierSources.genericCongestionStarts()).willReturn(syncedStarts);
        given(multiplierSources.gasCongestionStarts()).willReturn(syncedGasStarts);
        // and:
        subject.syncThrottling(throttling);
        subject.syncExpiryThrottle(expiryThrottle);
        subject.syncMultiplierSources(multiplierSources);

        // when:
        var subjectCopy = subject.copy();

        // expect:
        assertSame(
                subjectCopy.getConsensusTimeOfLastHandledTxn(),
                subject.getConsensusTimeOfLastHandledTxn());
        assertEquals(seqNoCopy, subjectCopy.seqNo());
        assertEquals(subjectCopy.lastScannedEntity(), subject.lastScannedEntity());
        assertEquals(midnightRateSetCopy, subjectCopy.getMidnightRates());
        // and:
        assertEquals(someThrottle.usageSnapshot(), subject.usageSnapshots()[0]);
        assertSame(subjectCopy.usageSnapshots(), subject.usageSnapshots());
        assertSame(subjectCopy.expiryUsageSnapshot(), subject.expiryUsageSnapshot());
        // and:
        assertEquals(syncedStarts, subject.getCongestionLevelStarts());
        assertSame(subject.getCongestionLevelStarts(), subjectCopy.getCongestionLevelStarts());
        // and:
        assertEquals(subjectCopy.getStateVersion(), stateVersion);
        assertEquals(subjectCopy.idsScannedThisSecond(), entitiesScannedThisSecond);
        assertEquals(subjectCopy.getEntitiesTouchedThisSecond(), entitiesTouchedThisSecond);
        assertEquals(subjectCopy.getPreparedUpdateFileNum(), preparedUpdateFileNum);
        assertSame(subjectCopy.getPreparedUpdateFileHash(), subject.getPreparedUpdateFileHash());
        // and:
        assertTrue(subject.isImmutable());
        assertFalse(subjectCopy.isImmutable());
        // and:
        assertNull(subject.getThrottling());
        assertNull(subject.getExpiryThrottle());
        assertNull(subject.getMultiplierSources());
        assertNull(subjectCopy.getThrottling());
        assertNull(subjectCopy.getMultiplierSources());
        assertNull(subjectCopy.getExpiryThrottle());
    }

    @Test
    void copyWorksWithSyncedThrottlesButNoExpiry() {
        // setup:
        throttling = mock(FunctionalityThrottling.class);
        gasLimitDeterministicThrottle = mock(GasLimitDeterministicThrottle.class);

        final var someThrottle = DeterministicThrottle.withTpsAndBurstPeriod(1, 23);
        someThrottle.allow(1, Instant.now());

        given(throttling.allActiveThrottles()).willReturn(List.of(someThrottle));
        given(throttling.gasLimitThrottle()).willReturn(gasLimitDeterministicThrottle);

        given(gasLimitDeterministicThrottle.usageSnapshot()).willReturn(gasLimitUsageSnapshot);
        subject.syncThrottling(throttling);

        assertDoesNotThrow(subject::copy);
    }

    public static void assertEqualContexts(
            final MerkleNetworkContext a, final MerkleNetworkContext b) {
        assertEquals(a.getConsensusTimeOfLastHandledTxn(), b.getConsensusTimeOfLastHandledTxn());
        assertEquals(a.seqNo().current(), b.seqNo().current());
        assertEquals(a.lastScannedEntity(), b.lastScannedEntity());
        assertEquals(a.midnightRates(), b.getMidnightRates());
        assertArrayEquals(a.usageSnapshots(), b.usageSnapshots());
        assertEquals(a.expiryUsageSnapshot(), b.expiryUsageSnapshot());
        assertArrayEquals(a.getCongestionLevelStarts(), b.getCongestionLevelStarts());
        assertArrayEquals(a.getEvmCongestionLevelStarts(), b.getEvmCongestionLevelStarts());
        assertEquals(a.getStateVersion(), b.getStateVersion());
        assertEquals(a.idsScannedThisSecond(), b.idsScannedThisSecond());
        assertEquals(a.getEntitiesTouchedThisSecond(), b.getEntitiesTouchedThisSecond());
        assertEquals(a.getPreparedUpdateFileNum(), b.getPreparedUpdateFileNum());
        assertArrayEquals(a.getPreparedUpdateFileHash(), b.getPreparedUpdateFileHash());
        assertEquals(a.areMigrationRecordsStreamed(), b.areMigrationRecordsStreamed());
        assertEquals(a.getAlignmentBlockNo(), b.getAlignmentBlockNo());
        assertEquals(a.firstConsTimeOfCurrentBlock(), b.firstConsTimeOfCurrentBlock());
        assertEquals(a.getBlockHashes(), b.getBlockHashes());
        assertEquals(a.nextTaskTodo(), b.nextTaskTodo());
        assertEquals(a.lastScannedPostUpgrade(), b.lastScannedPostUpgrade());
        assertEquals(a.seqNoPostUpgrade(), b.seqNoPostUpgrade());
        assertEquals(a.getPreExistingEntityScanStatus(), b.getPreExistingEntityScanStatus());
    }

    @Test
    void canResetScanStatusAfterUpgrade() {
        subject.markPostUpgradeScanStatus();
        assertEquals(seqNo.current(), subject.seqNoPostUpgrade());
        assertEquals(lastScannedEntity, subject.lastScannedEntity());
        assertFalse(subject.areAllPreUpgradeEntitiesScanned());
    }

    @Test
    void mutatorsThrowOnImmutableCopy() {
        // when:
        subject.copy();

        // then:
        assertThrows(MutabilityException.class, () -> subject.updateLastScannedEntity(1L));
        assertThrows(MutabilityException.class, () -> subject.updateAutoRenewSummaryCounts(1, 2));
        assertThrows(MutabilityException.class, () -> subject.clearAutoRenewSummaryCounts());
        assertThrows(MutabilityException.class, () -> subject.updateCongestionStartsFrom(null));
        assertThrows(MutabilityException.class, () -> subject.updateSnapshotsFrom(null));
        assertThrows(MutabilityException.class, () -> subject.updateExpirySnapshotFrom(null));
        assertThrows(MutabilityException.class, () -> subject.setStateVersion(1));
        assertThrows(
                MutabilityException.class, () -> subject.setConsensusTimeOfLastHandledTxn(null));
        assertThrows(MutabilityException.class, () -> subject.setPreparedUpdateFileNum(123));
        assertThrows(MutabilityException.class, () -> subject.setNextTaskTodo(123));
        assertThrows(MutabilityException.class, () -> subject.setLastScannedPostUpgrade(4321));
        assertThrows(MutabilityException.class, subject::markPostUpgradeScanStatus);
        assertThrows(
                MutabilityException.class, () -> subject.setPreExistingEntityScanStatus((byte) 1));
        assertThrows(
                MutabilityException.class,
                () -> subject.setPreparedUpdateFileHash(NO_PREPARED_UPDATE_FILE_HASH));
        assertThrows(MutabilityException.class, () -> subject.recordPreparedUpgrade(null));
        assertThrows(MutabilityException.class, () -> subject.discardPreparedUpgradeMeta());
        assertThrows(MutabilityException.class, () -> subject.finishBlock(null, null));
        assertThrows(
                MutabilityException.class,
                () -> subject.renumberBlocksToMatch(MISSING_BLOCK_VALUES));
    }

    @Test
    void canSetPreparedUpdateFileMeta() {
        subject.setPreparedUpdateFileNum(789);
        subject.setPreparedUpdateFileHash(otherPreparedUpdateFileHash);

        assertEquals(789, subject.getPreparedUpdateFileNum());
        assertSame(otherPreparedUpdateFileHash, subject.getPreparedUpdateFileHash());
    }

    @Test
    void recordsPreparedUpgrade() {
        final var op =
                FreezeTransactionBody.newBuilder()
                        .setUpdateFile(IdUtils.asFile("0.0.789"))
                        .setFileHash(ByteString.copyFrom(otherPreparedUpdateFileHash))
                        .build();

        subject.recordPreparedUpgrade(op);

        assertEquals(789, subject.getPreparedUpdateFileNum());
        assertArrayEquals(otherPreparedUpdateFileHash, subject.getPreparedUpdateFileHash());
    }

    @Test
    void rollbackWorksOnPreparedUpgrade() {
        subject.discardPreparedUpgradeMeta();

        assertEquals(NO_PREPARED_UPDATE_FILE_NUM, subject.getPreparedUpdateFileNum());
        assertSame(NO_PREPARED_UPDATE_FILE_HASH, subject.getPreparedUpdateFileHash());
    }

    @Test
    void syncsWork() {
        // setup:
        throttling = mock(FunctionalityThrottling.class);
        multiplierSources = mock(MultiplierSources.class);

        // when:
        subject.syncThrottling(throttling);
        subject.syncMultiplierSources(multiplierSources);

        // then:
        assertSame(throttling, subject.getThrottling());
        assertSame(multiplierSources, subject.getMultiplierSources());
    }

    @Test
    void summarizesUnavailableAsExpected() {
        subject.setCongestionLevelStarts(new Instant[0]);
        subject.setEvmCongestionLevelStarts(new Instant[0]);
        subject.setUsageSnapshots(new DeterministicThrottle.UsageSnapshot[0]);
        subject.setExpiryUsageSnapshot(NEVER_USED_SNAPSHOT);

        final var desired =
                "The network context (state version 13) is,\n"
                    + "  Consensus time of last handled transaction ::"
                    + " 1970-01-15T06:56:07.000054321Z\n"
                    + "  Pending maintenance                        :: <N/A>\n"
                    + "    w/ NMT upgrade prepped                   :: from 0.0.150 # 30313233\n"
                    + "  Midnight rate set                          :: 1ℏ <-> 14¢ til 1234567 | 1ℏ"
                    + " <-> 15¢ til 2345678\n"
                    + "  Next entity number                         :: 1234\n"
                    + "  Last scanned entity                        :: 1000\n"
                    + "  Entities scanned last consensus second     :: 123456\n"
                    + "  Entities touched last consensus second     :: 123\n"
                    + "  Expiry work usage snapshot is              :: <N/A>\n"
                    + "  Throttle usage snapshots are               :: <N/A>\n"
                    + "  Generic congestion level start times are   :: <N/A>\n"
                    + "  EVM congestion level start times are       :: <N/A>\n"
                    + "  Block number is                            :: 0\n"
                    + "  Block timestamp is                         ::"
                    + " 1970-01-15T06:56:07.000013579Z\n"
                    + "  Trailing block hashes are                  :: []\n"
                    + "  Staking rewards activated                  :: false\n"
                    + "  Total stake reward start this period       :: 0\n"
                    + "  Total stake start this period              :: 0";

        assertEquals(desired, subject.summarized());
    }

    @Test
    void summarizesHashesAsExpected() {
        subject.setCongestionLevelStarts(new Instant[0]);
        subject.setEvmCongestionLevelStarts(new Instant[0]);
        subject.setUsageSnapshots(new DeterministicThrottle.UsageSnapshot[0]);
        final var aFixedHash =
                org.hyperledger.besu.datatypes.Hash.wrap(
                        Bytes32.wrap("abcdabcdabcdabcdabcdabcdabcdabcd".getBytes()));
        final var bFixedHash =
                org.hyperledger.besu.datatypes.Hash.wrap(
                        Bytes32.wrap("ffcdffcdffcdffcdffcdffcdffcdffcd".getBytes()));
        subject.finishBlock(aFixedHash, firstConsTimeOfCurrentBlock.plusSeconds(2));
        subject.finishBlock(bFixedHash, firstConsTimeOfCurrentBlock.plusSeconds(4));

        final var desired =
                "The network context (state version 13) is,\n"
                    + "  Consensus time of last handled transaction ::"
                    + " 1970-01-15T06:56:07.000054321Z\n"
                    + "  Pending maintenance                        :: <N/A>\n"
                    + "    w/ NMT upgrade prepped                   :: from 0.0.150 # 30313233\n"
                    + "  Midnight rate set                          :: 1ℏ <-> 14¢ til 1234567 | 1ℏ"
                    + " <-> 15¢ til 2345678\n"
                    + "  Next entity number                         :: 1234\n"
                    + "  Last scanned entity                        :: 1000\n"
                    + "  Entities scanned last consensus second     :: 123456\n"
                    + "  Entities touched last consensus second     :: 123\n"
                    + "  Expiry work usage snapshot is              :: \n"
                    + "    666 used (last decision time +1000000000-12-31T23:59:59.999999999Z)\n"
                    + "  Throttle usage snapshots are               :: <N/A>\n"
                    + "  Generic congestion level start times are   :: <N/A>\n"
                    + "  EVM congestion level start times are       :: <N/A>\n"
                    + "  Block number is                            :: 2\n"
                    + "  Block timestamp is                         ::"
                    + " 1970-01-15T06:56:11.000013579Z\n"
                    + "  Trailing block hashes are                  :: [{\"num\": 0, \"hash\":"
                    + " \"6162636461626364616263646162636461626364616263646162636461626364\"},"
                    + " {\"num\": 1, \"hash\":"
                    + " \"6666636466666364666663646666636466666364666663646666636466666364\"}]\n"
                    + "  Staking rewards activated                  :: false\n"
                    + "  Total stake reward start this period       :: 0\n"
                    + "  Total stake start this period              :: 0";

        assertEquals(desired, subject.summarized());
    }

    @Test
    void summarizesStateVersionAsExpected() {
        throttling = mock(FunctionalityThrottling.class);
        final var accessor = mock(DualStateAccessor.class);

        given(throttling.allActiveThrottles()).willReturn(activeThrottles());
        given(throttling.gasLimitThrottle()).willReturn(gasLimitDeterministicThrottle);
        // and:
        subject.updateSnapshotsFrom(throttling);
        subject.setExpiryUsageSnapshot(expiryUsageSnapshot);
        subject.setPreparedUpdateFileNum(NO_PREPARED_UPDATE_FILE_NUM);
        // and:
        var desiredWithStateVersion =
                "The network context (state version 13) is,\n"
                    + "  Consensus time of last handled transaction ::"
                    + " 1970-01-15T06:56:07.000054321Z\n"
                    + "  Pending maintenance                        :: <N/A>\n"
                    + "    w/ NMT upgrade prepped                   :: <NONE>\n"
                    + "  Midnight rate set                          :: 1ℏ <-> 14¢ til 1234567 | 1ℏ"
                    + " <-> 15¢ til 2345678\n"
                    + "  Next entity number                         :: 1234\n"
                    + "  Last scanned entity                        :: 1000\n"
                    + "  Entities scanned last consensus second     :: 123456\n"
                    + "  Entities touched last consensus second     :: 123\n"
                    + "  Expiry work usage snapshot is              :: \n"
                    + "    666 used (last decision time +1000000000-12-31T23:59:59.999999999Z)\n"
                    + "  Throttle usage snapshots are               :: \n"
                    + "    100 used (last decision time 1970-01-01T00:00:01.000000100Z)\n"
                    + "    200 used (last decision time 1970-01-01T00:00:02.000000200Z)\n"
                    + "    300 used (last decision time 1970-01-01T00:00:03.000000300Z)\n"
                    + "    0 gas used (last decision time <N/A>)\n"
                    + "  Generic congestion level start times are   :: \n"
                    + "    1970-01-15T06:56:07.000054321Z\n"
                    + "    1970-01-15T06:59:49.000012345Z\n"
                    + "  EVM congestion level start times are       :: \n"
                    + "    1970-01-26T20:42:47.000054321Z\n"
                    + "    1970-01-26T20:46:29.000012345Z\n"
                    + "  Block number is                            :: 0\n"
                    + "  Block timestamp is                         ::"
                    + " 1970-01-15T06:56:07.000013579Z\n"
                    + "  Trailing block hashes are                  :: []\n"
                    + "  Staking rewards activated                  :: false\n"
                    + "  Total stake reward start this period       :: 0\n"
                    + "  Total stake start this period              :: 0";
        var desiredWithoutStateVersion =
                "The network context (state version <N/A>) is,\n"
                    + "  Consensus time of last handled transaction ::"
                    + " 1970-01-15T06:56:07.000054321Z\n"
                    + "  Pending maintenance                        :: <N/A>\n"
                    + "    w/ NMT upgrade prepped                   :: <NONE>\n"
                    + "  Midnight rate set                          :: 1ℏ <-> 14¢ til 1234567 | 1ℏ"
                    + " <-> 15¢ til 2345678\n"
                    + "  Next entity number                         :: 1234\n"
                    + "  Last scanned entity                        :: 1000\n"
                    + "  Entities scanned last consensus second     :: 123456\n"
                    + "  Entities touched last consensus second     :: 123\n"
                    + "  Expiry work usage snapshot is              :: \n"
                    + "    666 used (last decision time +1000000000-12-31T23:59:59.999999999Z)\n"
                    + "  Throttle usage snapshots are               :: \n"
                    + "    100 used (last decision time 1970-01-01T00:00:01.000000100Z)\n"
                    + "    200 used (last decision time 1970-01-01T00:00:02.000000200Z)\n"
                    + "    300 used (last decision time 1970-01-01T00:00:03.000000300Z)\n"
                    + "    0 gas used (last decision time <N/A>)\n"
                    + "  Generic congestion level start times are   :: \n"
                    + "    1970-01-15T06:56:07.000054321Z\n"
                    + "    1970-01-15T06:59:49.000012345Z\n"
                    + "  EVM congestion level start times are       :: \n"
                    + "    1970-01-26T20:42:47.000054321Z\n"
                    + "    1970-01-26T20:46:29.000012345Z\n"
                    + "  Block number is                            :: 0\n"
                    + "  Block timestamp is                         ::"
                    + " 1970-01-15T06:56:07.000013579Z\n"
                    + "  Trailing block hashes are                  :: []\n"
                    + "  Staking rewards activated                  :: false\n"
                    + "  Total stake reward start this period       :: 0\n"
                    + "  Total stake start this period              :: 0";
        var desiredWithNoStateVersionOrHandledTxn =
                "The network context (state version <N/A>) is,\n"
                    + "  Consensus time of last handled transaction :: <N/A>\n"
                    + "  Pending maintenance                        :: <N/A>\n"
                    + "    w/ NMT upgrade prepped                   :: <NONE>\n"
                    + "  Midnight rate set                          :: 1ℏ <-> 14¢ til 1234567 | 1ℏ"
                    + " <-> 15¢ til 2345678\n"
                    + "  Next entity number                         :: 1234\n"
                    + "  Last scanned entity                        :: 1000\n"
                    + "  Entities scanned last consensus second     :: 123456\n"
                    + "  Entities touched last consensus second     :: 123\n"
                    + "  Expiry work usage snapshot is              :: \n"
                    + "    666 used (last decision time +1000000000-12-31T23:59:59.999999999Z)\n"
                    + "  Throttle usage snapshots are               :: \n"
                    + "    100 used (last decision time 1970-01-01T00:00:01.000000100Z)\n"
                    + "    200 used (last decision time 1970-01-01T00:00:02.000000200Z)\n"
                    + "    300 used (last decision time 1970-01-01T00:00:03.000000300Z)\n"
                    + "    0 gas used (last decision time <N/A>)\n"
                    + "  Generic congestion level start times are   :: \n"
                    + "    1970-01-15T06:56:07.000054321Z\n"
                    + "    1970-01-15T06:59:49.000012345Z\n"
                    + "  EVM congestion level start times are       :: \n"
                    + "    1970-01-26T20:42:47.000054321Z\n"
                    + "    1970-01-26T20:46:29.000012345Z\n"
                    + "  Block number is                            :: 0\n"
                    + "  Block timestamp is                         ::"
                    + " 1970-01-15T06:56:07.000013579Z\n"
                    + "  Trailing block hashes are                  :: []\n"
                    + "  Staking rewards activated                  :: false\n"
                    + "  Total stake reward start this period       :: 0\n"
                    + "  Total stake start this period              :: 0";

        // then:
        assertEquals(desiredWithStateVersion, subject.summarized());
        assertEquals(desiredWithStateVersion, subject.summarizedWith(accessor));

        // and when:
        subject.setStateVersion(MerkleNetworkContext.UNRECORDED_STATE_VERSION);
        // then:
        assertEquals(desiredWithoutStateVersion, subject.summarized());

        // and when:
        subject.setConsensusTimeOfLastHandledTxn(null);
        // then:
        assertEquals(desiredWithNoStateVersionOrHandledTxn, subject.summarized());
    }

    @Test
    void summarizesPendingUpdateAsExpected() {
        final var someTime = Instant.ofEpochSecond(1_234_567L, 890);

        throttling = mock(FunctionalityThrottling.class);
        final var dualState = mock(DualStateImpl.class);
        final var accessor = mock(DualStateAccessor.class);

        given(accessor.getDualState()).willReturn(dualState);

        // and:
        var desiredWithPreparedUnscheduledMaintenance =
                "The network context (state version 13) is,\n"
                    + "  Consensus time of last handled transaction ::"
                    + " 1970-01-15T06:56:07.000054321Z\n"
                    + "  Pending maintenance                        :: <NONE>\n"
                    + "    w/ NMT upgrade prepped                   :: from 0.0.150 # 30313233\n"
                    + "  Midnight rate set                          :: 1ℏ <-> 14¢ til 1234567 | 1ℏ"
                    + " <-> 15¢ til 2345678\n"
                    + "  Next entity number                         :: 1234\n"
                    + "  Last scanned entity                        :: 1000\n"
                    + "  Entities scanned last consensus second     :: 123456\n"
                    + "  Entities touched last consensus second     :: 123\n"
                    + "  Expiry work usage snapshot is              :: \n"
                    + "    666 used (last decision time +1000000000-12-31T23:59:59.999999999Z)\n"
                    + "  Throttle usage snapshots are               :: \n"
                    + "    123 used (last decision time 1970-01-15T06:56:07.000054321Z)\n"
                    + "    456 used (last decision time 1970-01-15T06:56:08.000054321Z)\n"
                    + "    1234 gas used (last decision time 1970-01-15T06:56:07.000054321Z)\n"
                    + "  Generic congestion level start times are   :: \n"
                    + "    1970-01-15T06:56:07.000054321Z\n"
                    + "    1970-01-15T06:59:49.000012345Z\n"
                    + "  EVM congestion level start times are       :: \n"
                    + "    1970-01-26T20:42:47.000054321Z\n"
                    + "    1970-01-26T20:46:29.000012345Z\n"
                    + "  Block number is                            :: 0\n"
                    + "  Block timestamp is                         ::"
                    + " 1970-01-15T06:56:07.000013579Z\n"
                    + "  Trailing block hashes are                  :: []\n"
                    + "  Staking rewards activated                  :: false\n"
                    + "  Total stake reward start this period       :: 0\n"
                    + "  Total stake start this period              :: 0";
        // and:
        var desiredWithPreparedAndScheduledMaintenance =
                "The network context (state version 13) is,\n"
                    + "  Consensus time of last handled transaction ::"
                    + " 1970-01-15T06:56:07.000054321Z\n"
                    + "  Pending maintenance                        ::"
                    + " 1970-01-15T06:56:07.000000890Z\n"
                    + "    w/ NMT upgrade prepped                   :: from 0.0.150 # 30313233\n"
                    + "  Midnight rate set                          :: 1ℏ <-> 14¢ til 1234567 | 1ℏ"
                    + " <-> 15¢ til 2345678\n"
                    + "  Next entity number                         :: 1234\n"
                    + "  Last scanned entity                        :: 1000\n"
                    + "  Entities scanned last consensus second     :: 123456\n"
                    + "  Entities touched last consensus second     :: 123\n"
                    + "  Expiry work usage snapshot is              :: \n"
                    + "    666 used (last decision time +1000000000-12-31T23:59:59.999999999Z)\n"
                    + "  Throttle usage snapshots are               :: \n"
                    + "    123 used (last decision time 1970-01-15T06:56:07.000054321Z)\n"
                    + "    456 used (last decision time 1970-01-15T06:56:08.000054321Z)\n"
                    + "    1234 gas used (last decision time 1970-01-15T06:56:07.000054321Z)\n"
                    + "  Generic congestion level start times are   :: \n"
                    + "    1970-01-15T06:56:07.000054321Z\n"
                    + "    1970-01-15T06:59:49.000012345Z\n"
                    + "  EVM congestion level start times are       :: \n"
                    + "    1970-01-26T20:42:47.000054321Z\n"
                    + "    1970-01-26T20:46:29.000012345Z\n"
                    + "  Block number is                            :: 0\n"
                    + "  Block timestamp is                         ::"
                    + " 1970-01-15T06:56:07.000013579Z\n"
                    + "  Trailing block hashes are                  :: []\n"
                    + "  Staking rewards activated                  :: false\n"
                    + "  Total stake reward start this period       :: 0\n"
                    + "  Total stake start this period              :: 0";

        // then:
        assertEquals(desiredWithPreparedUnscheduledMaintenance, subject.summarizedWith(accessor));

        given(dualState.getFreezeTime()).willReturn(someTime);
        assertEquals(desiredWithPreparedAndScheduledMaintenance, subject.summarizedWith(accessor));
    }

    @Test
    void addsWork() {
        // when:
        subject.updateAutoRenewSummaryCounts(
                (int) entitiesScannedThisSecond, (int) entitiesTouchedThisSecond);

        // then:
        assertEquals(2 * entitiesScannedThisSecond, subject.idsScannedThisSecond());
        assertEquals(2 * entitiesTouchedThisSecond, subject.getEntitiesTouchedThisSecond());
    }

    @Test
    void resetsWork() {
        // when:
        subject.clearAutoRenewSummaryCounts();

        // then:
        assertEquals(0, subject.getEntitiesTouchedThisSecond());
        assertEquals(0, subject.idsScannedThisSecond());
    }

    @Test
    void updatesEmptySnapshotsAsExpected() {
        // setup:
        throttling = mock(FunctionalityThrottling.class);
        gasLimitDeterministicThrottle = mock(GasLimitDeterministicThrottle.class);

        given(throttling.allActiveThrottles()).willReturn(Collections.emptyList());
        given(throttling.gasLimitThrottle()).willReturn(gasLimitDeterministicThrottle);
        given(gasLimitDeterministicThrottle.usageSnapshot()).willReturn(gasLimitUsageSnapshot);

        // when:
        subject.updateSnapshotsFrom(throttling);

        // then:
        assertSame(NO_SNAPSHOTS, subject.usageSnapshots());
    }

    @Test
    void updatesEmptyLevelStartsAsExpected() {
        // setup:
        multiplierSources = mock(MultiplierSources.class);

        given(multiplierSources.gasCongestionStarts()).willReturn(NO_CONGESTION_STARTS);
        given(multiplierSources.genericCongestionStarts()).willReturn(NO_CONGESTION_STARTS);

        // when:
        subject.updateCongestionStartsFrom(multiplierSources);

        // then:
        assertEquals(NO_CONGESTION_STARTS, subject.getCongestionLevelStarts());
    }

    @Test
    void updatesNullCongestionLevelStartsAsExpected() {
        // setup:
        multiplierSources = mock(MultiplierSources.class);

        given(multiplierSources.gasCongestionStarts()).willReturn(null);
        given(multiplierSources.genericCongestionStarts()).willReturn(null);

        // when:
        subject.updateCongestionStartsFrom(multiplierSources);

        // then:
        assertEquals(NO_CONGESTION_STARTS, subject.getCongestionLevelStarts());
    }

    @Test
    void updatesSnapshotsAsExpected() {
        // setup:
        var aThrottle = DeterministicThrottle.withTpsAndBurstPeriod(5, 2);
        var bThrottle = DeterministicThrottle.withTpsAndBurstPeriod(6, 3);
        var cThrottle = DeterministicThrottle.withTpsAndBurstPeriod(7, 4);
        aThrottle.allow(1, Instant.now());
        bThrottle.allow(1, Instant.now());
        cThrottle.allow(20, Instant.now());
        var activeThrottles = List.of(aThrottle, bThrottle, cThrottle);
        var expectedSnapshots =
                activeThrottles.stream()
                        .map(DeterministicThrottle::usageSnapshot)
                        .toArray(DeterministicThrottle.UsageSnapshot[]::new);

        throttling = mock(FunctionalityThrottling.class);
        gasLimitDeterministicThrottle = mock(GasLimitDeterministicThrottle.class);

        given(throttling.allActiveThrottles()).willReturn(activeThrottles);
        given(throttling.gasLimitThrottle()).willReturn(gasLimitDeterministicThrottle);
        given(gasLimitDeterministicThrottle.usageSnapshot()).willReturn(gasLimitUsageSnapshot);

        // when:
        subject.updateSnapshotsFrom(throttling);

        // then:
        assertArrayEquals(expectedSnapshots, subject.usageSnapshots());
    }

    @Test
    void updatesCongestionStartsAsExpected() {
        // setup:
        subject = new MerkleNetworkContext();

        multiplierSources = mock(MultiplierSources.class);

        given(multiplierSources.genericCongestionStarts()).willReturn(congestionStarts);
        given(multiplierSources.gasCongestionStarts()).willReturn(evmCongestionStarts);

        // when:
        subject.updateCongestionStartsFrom(multiplierSources);

        // then:
        assertArrayEquals(congestionStarts(), subject.getCongestionLevelStarts());
        assertArrayEquals(evmCongestionStarts, subject.getEvmCongestionLevelStarts());
    }

    @Test
    void resetsExpiryThrottleIfNonNullOnly() {
        final var throttle = mock(ExpiryThrottle.class);
        subject.resetExpiryThrottleFromSavedSnapshot(throttle);
        verify(throttle).resetToSnapshot(expiryUsageSnapshot);
    }

    @Test
    void canSyncExpiryThrottling() {
        final var throttle = mock(ExpiryThrottle.class);
        subject.syncExpiryThrottle(throttle);
        assertSame(throttle, subject.getExpiryThrottle());
    }

    @Test
    void warnsIfSavedUsageNotCompatibleWithActiveThrottles() {
        // setup:
        var aThrottle = DeterministicThrottle.withTpsAndBurstPeriod(5, 2);
        var bThrottle = DeterministicThrottle.withTpsAndBurstPeriod(6, 3);
        var cThrottle = DeterministicThrottle.withTpsAndBurstPeriod(7, 4);
        aThrottle.allow(1, Instant.now());
        bThrottle.allow(1, Instant.now());
        cThrottle.allow(20, Instant.now());
        // and:
        var subjectSnapshotA = aThrottle.usageSnapshot();
        aThrottle.allow(2, Instant.now());
        var subjectSnapshotC = cThrottle.usageSnapshot();

        throttling = mock(FunctionalityThrottling.class);
        gasLimitDeterministicThrottle = mock(GasLimitDeterministicThrottle.class);

        given(throttling.allActiveThrottles()).willReturn(List.of(aThrottle, bThrottle));
        given(throttling.gasLimitThrottle()).willReturn(gasLimitDeterministicThrottle);
        given(gasLimitDeterministicThrottle.usageSnapshot()).willReturn(gasLimitUsageSnapshot);
        // and:
        subject.setUsageSnapshots(
                new DeterministicThrottle.UsageSnapshot[] {subjectSnapshotA, subjectSnapshotC});

        // when:
        subject.resetThrottlingFromSavedSnapshots(throttling);

        // then:

        // and:
        assertNotEquals(subjectSnapshotA.used(), aThrottle.usageSnapshot().used());
        assertNotEquals(
                subjectSnapshotA.lastDecisionTime(), aThrottle.usageSnapshot().lastDecisionTime());
    }

    @Test
    void warnsIfDifferentNumOfActiveThrottles() {
        // setup:
        var aThrottle = DeterministicThrottle.withTpsAndBurstPeriod(5, 2);
        var bThrottle = DeterministicThrottle.withTpsAndBurstPeriod(6, 3);
        aThrottle.allow(1, Instant.now());
        bThrottle.allow(1, Instant.now());
        // and:
        var subjectSnapshot = aThrottle.usageSnapshot();
        aThrottle.allow(2, Instant.now());

        throttling = mock(FunctionalityThrottling.class);

        given(throttling.allActiveThrottles()).willReturn(List.of(aThrottle, bThrottle));
        // and:
        subject.setUsageSnapshots(new DeterministicThrottle.UsageSnapshot[] {subjectSnapshot});
        // and:
        var desired =
                "There are 2 active throttles, but 1 usage snapshots from saved state. Not"
                        + " performing a reset!";

        // when:
        subject.resetThrottlingFromSavedSnapshots(throttling);

        // then:
        assertThat(logCaptor.warnLogs(), contains(desired));
        // and:
        assertNotEquals(subjectSnapshot.used(), aThrottle.usageSnapshot().used());
        assertNotEquals(
                subjectSnapshot.lastDecisionTime(), aThrottle.usageSnapshot().lastDecisionTime());
    }

    @Test
    void warnsIfCannotResetGasLimitUsage() {
        // setup:
        var aThrottle = DeterministicThrottle.withTpsAndBurstPeriod(5, 2);
        aThrottle.allow(1, Instant.now());
        var subjectSnapshot = aThrottle.usageSnapshot();
        aThrottle.allow(2, Instant.now());

        throttling = mock(FunctionalityThrottling.class);

        given(throttling.allActiveThrottles()).willReturn(List.of(aThrottle));
        given(throttling.gasLimitThrottle()).willReturn(gasLimitDeterministicThrottle);
        subject.setUsageSnapshots(new DeterministicThrottle.UsageSnapshot[] {subjectSnapshot});

        var desired =
                "Saved gas throttle usage snapshot was not compatible with the corresponding active"
                        + " throttle (Cannot use -1 units in a bucket of capacity 1234!); not"
                        + " performing a reset!";
        // when:
        subject.setGasThrottleUsageSnapshot(new DeterministicThrottle.UsageSnapshot(-1, null));
        subject.resetThrottlingFromSavedSnapshots(throttling);

        // then:
        assertThat(logCaptor.warnLogs(), contains(desired));
    }

    @Test
    void updatesFromMatchingSnapshotsAsExpected() {
        // setup:
        var aThrottle = DeterministicThrottle.withTpsAndBurstPeriod(5, 2);
        aThrottle.allow(1, Instant.now());
        var subjectSnapshot = aThrottle.usageSnapshot();
        aThrottle.allow(2, Instant.now());

        throttling = mock(FunctionalityThrottling.class);
        gasLimitDeterministicThrottle = mock(GasLimitDeterministicThrottle.class);

        given(throttling.allActiveThrottles()).willReturn(List.of(aThrottle));
        given(throttling.gasLimitThrottle()).willReturn(gasLimitDeterministicThrottle);
        given(gasLimitDeterministicThrottle.usageSnapshot()).willReturn(gasLimitUsageSnapshot);
        // given:
        subject.setUsageSnapshots(new DeterministicThrottle.UsageSnapshot[] {subjectSnapshot});

        // when:
        subject.resetThrottlingFromSavedSnapshots(throttling);

        // then:
        assertEquals(subjectSnapshot.used(), aThrottle.usageSnapshot().used());
        assertEquals(
                subjectSnapshot.lastDecisionTime(), aThrottle.usageSnapshot().lastDecisionTime());
    }

    @Test
    void updatesFromSavedCongestionStartsEvenIfNull() {
        // setup:
        multiplierSources = mock(MultiplierSources.class);
        congestionStarts[1] = null;
        evmCongestionStarts[1] = null;

        // given:
        subject.getCongestionLevelStarts()[1] = null;
        subject.getEvmCongestionLevelStarts()[1] = null;

        // when:
        subject.resetMultiplierSourceFromSavedCongestionStarts(multiplierSources);

        // then:
        verify(multiplierSources, times(1)).resetGenericCongestionLevelStarts(congestionStarts);
        verify(multiplierSources, times(1)).resetGasCongestionLevelStarts(evmCongestionStarts);
    }

    @Test
    void gasThrottleUsageSnapshotTest() {
        subject.setGasThrottleUsageSnapshot(gasLimitUsageSnapshot);
        assertEquals(gasLimitUsageSnapshot, subject.getGasThrottleUsageSnapshot());
    }

    @Test
    void updatesFromSavedCongestionStarts() {
        multiplierSources = mock(MultiplierSources.class);
        given(multiplierSources.genericCongestionStarts()).willReturn(congestionStarts);
        given(multiplierSources.gasCongestionStarts()).willReturn(evmCongestionStarts);

        // when:
        subject.resetMultiplierSourceFromSavedCongestionStarts(multiplierSources);
        // and:
        subject.setCongestionLevelStarts(NO_CONGESTION_STARTS);
        subject.setEvmCongestionLevelStarts(NO_CONGESTION_STARTS);
        subject.resetMultiplierSourceFromSavedCongestionStarts(multiplierSources);

        // then:
        verify(multiplierSources, times(1)).resetGenericCongestionLevelStarts(congestionStarts);
        verify(multiplierSources, times(1)).resetGasCongestionLevelStarts(evmCongestionStarts);
    }

    @Test
    void sanityChecks() {
        assertEquals(CURRENT_VERSION, subject.getVersion());
        assertEquals(MerkleNetworkContext.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }

    @Test
    void canIncreaseAndDecreasePendingRewards() {
        final var localSub = new MerkleNetworkContext();
        localSub.increasePendingRewards(123);
        localSub.increasePendingRewards(234);
        localSub.decreasePendingRewards(99);
        assertEquals(123 + 234 - 99, localSub.pendingRewards());
    }

    @Test
    void pendingRewardsAdjustmentsAreSanityChecked() {
        final var localSub = new MerkleNetworkContext();
        localSub.setPendingRewards(100);
        assertThrows(IllegalArgumentException.class, () -> localSub.increasePendingRewards(-1));
        assertThrows(IllegalArgumentException.class, () -> localSub.decreasePendingRewards(-1));
    }

    @Test
    void pendingRewardsAreKeptInRangeAtHighEnd() {
        final var localSub = new MerkleNetworkContext();
        localSub.setPendingRewards(100);
        localSub.increasePendingRewards(Long.MAX_VALUE);
        assertEquals(50_000_000_000L * HBARS_TO_TINYBARS, localSub.pendingRewards());
    }

    @Test
    void pendingRewardsAreKeptInRangeAtLowEnd() {
        final var localSub = new MerkleNetworkContext();
        localSub.setPendingRewards(100);
        localSub.decreasePendingRewards(101);
        assertEquals(0L, localSub.pendingRewards());
    }

    long[] used = new long[] {100L, 200L, 300L};
    Instant[] lastUseds =
            new Instant[] {
                Instant.ofEpochSecond(1L, 100),
                Instant.ofEpochSecond(2L, 200),
                Instant.ofEpochSecond(3L, 300)
            };

    @Test
    void updateLastScannedEntityWorks() {
        subject.updateLastScannedEntity(2000L);
        assertEquals(2000L, subject.lastScannedEntity());
    }

    private List<DeterministicThrottle> activeThrottles() {
        var snapshots = snapshots();
        List<DeterministicThrottle> active = new ArrayList<>();
        for (int i = 0; i < used.length; i++) {
            var throttle = DeterministicThrottle.withTpsNamed(1, "Throttle" + (char) ('A' + i));
            throttle.resetUsageTo(snapshots.get(i));
            active.add(throttle);
        }
        return active;
    }

    private List<DeterministicThrottle.UsageSnapshot> snapshots() {
        List<DeterministicThrottle.UsageSnapshot> cur = new ArrayList<>();
        for (int i = 0; i < used.length; i++) {
            var usageSnapshot = new DeterministicThrottle.UsageSnapshot(used[i], lastUseds[i]);
            cur.add(usageSnapshot);
        }
        return cur;
    }

    private Instant[] congestionStarts() {
        return congestionStarts;
    }

    private static final long aBlockNo = 123_456L;
    private static final Hash aFullBlockHash = new Hash(TxnUtils.randomUtf8Bytes(48));
    private static final Hash bFullBlockHash = new Hash(TxnUtils.randomUtf8Bytes(48));
    private static final org.hyperledger.besu.datatypes.Hash aEthHash = ethHashFrom(aFullBlockHash);
    private static final org.hyperledger.besu.datatypes.Hash bEthHash = ethHashFrom(bFullBlockHash);
}
