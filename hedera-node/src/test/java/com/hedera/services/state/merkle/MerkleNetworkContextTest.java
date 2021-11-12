package com.hedera.services.state.merkle;

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

import com.google.protobuf.ByteString;
import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.state.DualStateAccessor;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttles.GasLimitDeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.swirlds.common.MutabilityException;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.platform.state.DualStateImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.state.merkle.MerkleNetworkContext.CURRENT_VERSION;
import static com.hedera.services.state.merkle.MerkleNetworkContext.NO_CONGESTION_STARTS;
import static com.hedera.services.state.merkle.MerkleNetworkContext.NO_GAS_THROTTLE_SNAPSHOT;
import static com.hedera.services.state.merkle.MerkleNetworkContext.NO_PREPARED_UPDATE_FILE_HASH;
import static com.hedera.services.state.merkle.MerkleNetworkContext.NO_PREPARED_UPDATE_FILE_NUM;
import static com.hedera.services.state.merkle.MerkleNetworkContext.NO_SNAPSHOTS;
import static com.hedera.services.state.submerkle.RichInstant.fromJava;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.booleanThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.times;

@ExtendWith(LogCaptureExtension.class)
class MerkleNetworkContextTest {
	private final int stateVersion = 13;
	private final long preparedUpdateFileNum = 150L;
	private final long lastScannedEntity = 1000L;
	private final long entitiesTouchedThisSecond = 123L;
	private final long entitiesScannedThisSecond = 123_456L;
	private final byte[] preparedUpdateFileHash =
			"012345678901234567890123456789012345678901234567".getBytes(StandardCharsets.UTF_8);
	private final byte[] otherPreparedUpdateFileHash =
			"x123456789x123456789x123456789x123456789x1234567".getBytes(StandardCharsets.UTF_8);
	private Instant lastMidnightBoundaryCheck;
	private Instant consensusTimeOfLastHandledTxn;
	private SequenceNumber seqNo;
	private SequenceNumber seqNoCopy;
	private ExchangeRates midnightRateSet;
	private ExchangeRates midnightRateSetCopy;
	private Instant[] congestionStarts;
	private DeterministicThrottle.UsageSnapshot[] usageSnapshots;

	private DomainSerdes serdes;
	private FunctionalityThrottling throttling;
	private FeeMultiplierSource feeMultiplierSource;

	private GasLimitDeterministicThrottle gasLimitDeterministicThrottle;
	private DeterministicThrottle.UsageSnapshot gasLimitUsageSnapshot;

	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private MerkleNetworkContext subject;

	@BeforeEach
	void setup() {
		congestionStarts = new Instant[]{
				Instant.ofEpochSecond(1_234_567L, 54321L),
				Instant.ofEpochSecond(1_234_789L, 12345L)
		};

		consensusTimeOfLastHandledTxn = Instant.ofEpochSecond(1_234_567L, 54321L);
		lastMidnightBoundaryCheck = consensusTimeOfLastHandledTxn.minusSeconds(123L);

		seqNo = mock(SequenceNumber.class);
		given(seqNo.current()).willReturn(1234L);
		seqNoCopy = mock(SequenceNumber.class);
		given(seqNo.copy()).willReturn(seqNoCopy);
		midnightRateSet = new ExchangeRates(
				1, 14, 1_234_567L,
				1, 15, 2_345_678L);
		midnightRateSetCopy = midnightRateSet.copy();
		usageSnapshots = new DeterministicThrottle.UsageSnapshot[]{
				new DeterministicThrottle.UsageSnapshot(
						123L, consensusTimeOfLastHandledTxn),
				new DeterministicThrottle.UsageSnapshot(
						456L, consensusTimeOfLastHandledTxn.plusSeconds(1L))
		};

		gasLimitDeterministicThrottle = new GasLimitDeterministicThrottle(1234);
		gasLimitUsageSnapshot = new DeterministicThrottle.UsageSnapshot(1234L, consensusTimeOfLastHandledTxn);

		serdes = mock(DomainSerdes.class, RETURNS_DEEP_STUBS);
		MerkleNetworkContext.serdes = serdes;

		subject = new MerkleNetworkContext(consensusTimeOfLastHandledTxn, seqNo, lastScannedEntity, midnightRateSet);

		subject.setUsageSnapshots(usageSnapshots);
		subject.setGasThrottleUsageSnapshot(gasLimitUsageSnapshot);
		subject.setCongestionLevelStarts(congestionStarts());
		subject.setStateVersion(stateVersion);
		subject.updateAutoRenewSummaryCounts((int) entitiesScannedThisSecond, (int) entitiesTouchedThisSecond);
		subject.setLastMidnightBoundaryCheck(lastMidnightBoundaryCheck);
		subject.setPreparedUpdateFileNum(preparedUpdateFileNum);
		subject.setPreparedUpdateFileHash(preparedUpdateFileHash);
	}

	@AfterEach
	void cleanup() {
		MerkleNetworkContext.serdes = new DomainSerdes();
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
	void copyWorks() {
		// given:
		var subjectCopy = subject.copy();

		// expect:
		assertSame(subjectCopy.lastMidnightBoundaryCheck(), subject.lastMidnightBoundaryCheck());
		assertSame(subjectCopy.getConsensusTimeOfLastHandledTxn(), subject.getConsensusTimeOfLastHandledTxn());
		assertEquals(seqNoCopy, subjectCopy.seqNo());
		assertEquals(subjectCopy.lastScannedEntity(), subject.lastScannedEntity());
		assertEquals(midnightRateSetCopy, subjectCopy.getMidnightRates());
		assertSame(subjectCopy.usageSnapshots(), subject.usageSnapshots());
		assertSame(subjectCopy.getCongestionLevelStarts(), subject.getCongestionLevelStarts());
		assertEquals(subjectCopy.getStateVersion(), stateVersion);
		assertEquals(subjectCopy.getEntitiesScannedThisSecond(), entitiesScannedThisSecond);
		assertEquals(subjectCopy.getEntitiesTouchedThisSecond(), entitiesTouchedThisSecond);
		assertEquals(subjectCopy.getPreparedUpdateFileNum(), preparedUpdateFileNum);
		assertSame(subjectCopy.getPreparedUpdateFileHash(), subject.getPreparedUpdateFileHash());
		// and:
		assertTrue(subject.isImmutable());
		assertFalse(subjectCopy.isImmutable());
		assertNull(subject.getThrottling());
		assertNull(subject.getMultiplierSource());
		assertNull(subjectCopy.getThrottling());
		assertNull(subjectCopy.getMultiplierSource());
	}

	@Test
	void copyWorksWithSyncedThrottles() {
		// setup:
		throttling = mock(FunctionalityThrottling.class);
		feeMultiplierSource = mock(FeeMultiplierSource.class);
		gasLimitDeterministicThrottle = mock(GasLimitDeterministicThrottle.class);

		final var someThrottle = DeterministicThrottle.withTpsAndBurstPeriod(1, 23);
		someThrottle.allow(1);
		final var someStart = Instant.ofEpochSecond(7_654_321L, 0);
		final var syncedStarts = new Instant[]{someStart};

		given(throttling.allActiveThrottles()).willReturn(List.of(someThrottle));

		given(throttling.gasLimitThrottle()).willReturn(gasLimitDeterministicThrottle);
		given(gasLimitDeterministicThrottle.usageSnapshot()).willReturn(gasLimitUsageSnapshot);
		given(feeMultiplierSource.congestionLevelStarts()).willReturn(syncedStarts);
		// and:
		subject.syncThrottling(throttling);
		subject.syncMultiplierSource(feeMultiplierSource);

		// when:
		var subjectCopy = subject.copy();

		// expect:
		assertSame(subjectCopy.lastMidnightBoundaryCheck(), subject.lastMidnightBoundaryCheck());
		assertSame(subjectCopy.getConsensusTimeOfLastHandledTxn(), subject.getConsensusTimeOfLastHandledTxn());
		assertEquals(seqNoCopy, subjectCopy.seqNo());
		assertEquals(subjectCopy.lastScannedEntity(), subject.lastScannedEntity());
		assertEquals(midnightRateSetCopy, subjectCopy.getMidnightRates());
		// and:
		assertEquals(someThrottle.usageSnapshot(), subject.usageSnapshots()[0]);
		assertSame(subjectCopy.usageSnapshots(), subject.usageSnapshots());
		// and:
		assertEquals(syncedStarts, subject.getCongestionLevelStarts());
		assertSame(subject.getCongestionLevelStarts(), subjectCopy.getCongestionLevelStarts());
		// and:
		assertEquals(subjectCopy.getStateVersion(), stateVersion);
		assertEquals(subjectCopy.getEntitiesScannedThisSecond(), entitiesScannedThisSecond);
		assertEquals(subjectCopy.getEntitiesTouchedThisSecond(), entitiesTouchedThisSecond);
		assertEquals(subjectCopy.getPreparedUpdateFileNum(), preparedUpdateFileNum);
		assertSame(subjectCopy.getPreparedUpdateFileHash(), subject.getPreparedUpdateFileHash());
		// and:
		assertTrue(subject.isImmutable());
		assertFalse(subjectCopy.isImmutable());
		// and:
		assertNull(subject.getThrottling());
		assertNull(subject.getMultiplierSource());
		assertNull(subjectCopy.getThrottling());
		assertNull(subjectCopy.getMultiplierSource());
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
		assertThrows(MutabilityException.class, () -> subject.setStateVersion(1));
		assertThrows(MutabilityException.class, () -> subject.setLastMidnightBoundaryCheck(null));
		assertThrows(MutabilityException.class, () -> subject.setConsensusTimeOfLastHandledTxn(null));
		assertThrows(MutabilityException.class, () -> subject.setPreparedUpdateFileNum(123));
		assertThrows(MutabilityException.class, () -> subject.setPreparedUpdateFileHash(NO_PREPARED_UPDATE_FILE_HASH));
		assertThrows(MutabilityException.class, () -> subject.recordPreparedUpgrade(null));
		assertThrows(MutabilityException.class, () -> subject.discardPreparedUpgradeMeta());
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
		final var op = FreezeTransactionBody.newBuilder()
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
	void canSetLastMidnightBoundaryCheck() {
		final var newLmbc = lastMidnightBoundaryCheck.plusSeconds(1L);

		// when:
		subject.setLastMidnightBoundaryCheck(newLmbc);

		// then:
		assertSame(newLmbc, subject.lastMidnightBoundaryCheck());
	}

	@Test
	void syncsWork() {
		// setup:
		throttling = mock(FunctionalityThrottling.class);
		feeMultiplierSource = mock(FeeMultiplierSource.class);

		// when:
		subject.syncThrottling(throttling);
		subject.syncMultiplierSource(feeMultiplierSource);

		// then:
		assertSame(throttling, subject.getThrottling());
		assertSame(feeMultiplierSource, subject.getMultiplierSource());
	}

	@Test
	void summarizesUnavailableAsExpected() {
		subject.setCongestionLevelStarts(new Instant[0]);
		subject.setUsageSnapshots(new DeterministicThrottle.UsageSnapshot[0]);

		final var desired = "The network context (state version 13) is,\n" +
				"  Consensus time of last handled transaction :: 1970-01-15T06:56:07.000054321Z\n" +
				"  Pending maintenance                        :: <N/A>\n" +
				"    w/ NMT upgrade prepped                   :: from 0.0.150 # 30313233\n" +
				"  Midnight rate set                          :: 1ℏ <-> 14¢ til 1234567 | 1ℏ <-> 15¢ til 2345678\n" +
				"  Last midnight boundary check               :: 1970-01-15T06:54:04.000054321Z\n" +
				"  Next entity number                         :: 1234\n" +
				"  Last scanned entity                        :: 1000\n" +
				"  Entities scanned last consensus second     :: 123456\n" +
				"  Entities touched last consensus second     :: 123\n" +
				"  Throttle usage snapshots are               :: <N/A>\n" +
				"  Congestion level start times are           :: <N/A>";

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
		subject.setPreparedUpdateFileNum(NO_PREPARED_UPDATE_FILE_NUM);
		// and:
		var desiredWithStateVersion = "The network context (state version 13) is,\n" +
				"  Consensus time of last handled transaction :: 1970-01-15T06:56:07.000054321Z\n" +
				"  Pending maintenance                        :: <N/A>\n" +
				"    w/ NMT upgrade prepped                   :: <NONE>\n" +
				"  Midnight rate set                          :: 1ℏ <-> 14¢ til 1234567 | 1ℏ <-> 15¢ til 2345678\n" +
				"  Last midnight boundary check               :: 1970-01-15T06:54:04.000054321Z\n" +
				"  Next entity number                         :: 1234\n" +
				"  Last scanned entity                        :: 1000\n" +
				"  Entities scanned last consensus second     :: 123456\n" +
				"  Entities touched last consensus second     :: 123\n" +
				"  Throttle usage snapshots are               ::\n" +
				"    100 used (last decision time 1970-01-01T00:00:01.000000100Z)\n" +
				"    200 used (last decision time 1970-01-01T00:00:02.000000200Z)\n" +
				"    300 used (last decision time 1970-01-01T00:00:03.000000300Z)\n" +
				"    0 gas used (last decision time <N/A>)\n" +
				"  Congestion level start times are           ::\n" +
				"    1970-01-15T06:56:07.000054321Z\n" +
				"    1970-01-15T06:59:49.000012345Z";
		var desiredWithoutStateVersion = "The network context (state version <N/A>) is,\n" +
				"  Consensus time of last handled transaction :: 1970-01-15T06:56:07.000054321Z\n" +
				"  Pending maintenance                        :: <N/A>\n" +
				"    w/ NMT upgrade prepped                   :: <NONE>\n" +
				"  Midnight rate set                          :: 1ℏ <-> 14¢ til 1234567 | 1ℏ <-> 15¢ til 2345678\n" +
				"  Last midnight boundary check               :: 1970-01-15T06:54:04.000054321Z\n" +
				"  Next entity number                         :: 1234\n" +
				"  Last scanned entity                        :: 1000\n" +
				"  Entities scanned last consensus second     :: 123456\n" +
				"  Entities touched last consensus second     :: 123\n" +
				"  Throttle usage snapshots are               ::\n" +
				"    100 used (last decision time 1970-01-01T00:00:01.000000100Z)\n" +
				"    200 used (last decision time 1970-01-01T00:00:02.000000200Z)\n" +
				"    300 used (last decision time 1970-01-01T00:00:03.000000300Z)\n" +
				"    0 gas used (last decision time <N/A>)\n" +
				"  Congestion level start times are           ::\n" +
				"    1970-01-15T06:56:07.000054321Z\n" +
				"    1970-01-15T06:59:49.000012345Z";
		var desiredWithNoStateVersionOrHandledTxn = "The network context (state version <N/A>) is,\n" +
				"  Consensus time of last handled transaction :: <N/A>\n" +
				"  Pending maintenance                        :: <N/A>\n" +
				"    w/ NMT upgrade prepped                   :: <NONE>\n" +
				"  Midnight rate set                          :: 1ℏ <-> 14¢ til 1234567 | 1ℏ <-> 15¢ til 2345678\n" +
				"  Last midnight boundary check               :: 1970-01-15T06:54:04.000054321Z\n" +
				"  Next entity number                         :: 1234\n" +
				"  Last scanned entity                        :: 1000\n" +
				"  Entities scanned last consensus second     :: 123456\n" +
				"  Entities touched last consensus second     :: 123\n" +
				"  Throttle usage snapshots are               ::\n" +
				"    100 used (last decision time 1970-01-01T00:00:01.000000100Z)\n" +
				"    200 used (last decision time 1970-01-01T00:00:02.000000200Z)\n" +
				"    300 used (last decision time 1970-01-01T00:00:03.000000300Z)\n" +
				"    0 gas used (last decision time <N/A>)\n" +
				"  Congestion level start times are           ::\n" +
				"    1970-01-15T06:56:07.000054321Z\n" +
				"    1970-01-15T06:59:49.000012345Z";

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
		var desiredWithPreparedUnscheduledMaintenance = "The network context (state version 13) is,\n" +
				"  Consensus time of last handled transaction :: 1970-01-15T06:56:07.000054321Z\n" +
				"  Pending maintenance                        :: <NONE>\n" +
				"    w/ NMT upgrade prepped                   :: from 0.0.150 # 30313233\n" +
				"  Midnight rate set                          :: 1ℏ <-> 14¢ til 1234567 | 1ℏ <-> 15¢ til 2345678\n" +
				"  Last midnight boundary check               :: 1970-01-15T06:54:04.000054321Z\n" +
				"  Next entity number                         :: 1234\n" +
				"  Last scanned entity                        :: 1000\n" +
				"  Entities scanned last consensus second     :: 123456\n" +
				"  Entities touched last consensus second     :: 123\n" +
				"  Throttle usage snapshots are               ::\n" +
				"    123 used (last decision time 1970-01-15T06:56:07.000054321Z)\n" +
				"    456 used (last decision time 1970-01-15T06:56:08.000054321Z)\n" +
				"    1234 gas used (last decision time 1970-01-15T06:56:07.000054321Z)\n" +
				"  Congestion level start times are           ::\n" +
				"    1970-01-15T06:56:07.000054321Z\n" +
				"    1970-01-15T06:59:49.000012345Z";
		// and:
		var desiredWithPreparedAndScheduledMaintenance = "The network context (state version 13) is,\n" +
				"  Consensus time of last handled transaction :: 1970-01-15T06:56:07.000054321Z\n" +
				"  Pending maintenance                        :: 1970-01-15T06:56:07.000000890Z\n" +
				"    w/ NMT upgrade prepped                   :: from 0.0.150 # 30313233\n" +
				"  Midnight rate set                          :: 1ℏ <-> 14¢ til 1234567 | 1ℏ <-> 15¢ til 2345678\n" +
				"  Last midnight boundary check               :: 1970-01-15T06:54:04.000054321Z\n" +
				"  Next entity number                         :: 1234\n" +
				"  Last scanned entity                        :: 1000\n" +
				"  Entities scanned last consensus second     :: 123456\n" +
				"  Entities touched last consensus second     :: 123\n" +
				"  Throttle usage snapshots are               ::\n" +
				"    123 used (last decision time 1970-01-15T06:56:07.000054321Z)\n" +
				"    456 used (last decision time 1970-01-15T06:56:08.000054321Z)\n" +
				"    1234 gas used (last decision time 1970-01-15T06:56:07.000054321Z)\n" +
				"  Congestion level start times are           ::\n" +
				"    1970-01-15T06:56:07.000054321Z\n" +
				"    1970-01-15T06:59:49.000012345Z";

		// then:
		assertEquals(desiredWithPreparedUnscheduledMaintenance, subject.summarizedWith(accessor));

		given(dualState.getFreezeTime()).willReturn(someTime);
		assertEquals(desiredWithPreparedAndScheduledMaintenance, subject.summarizedWith(accessor));
	}

	@Test
	void addsWork() {
		// when:
		subject.updateAutoRenewSummaryCounts((int) entitiesScannedThisSecond, (int) entitiesTouchedThisSecond);

		// then:
		assertEquals(2 * entitiesScannedThisSecond, subject.getEntitiesScannedThisSecond());
		assertEquals(2 * entitiesTouchedThisSecond, subject.getEntitiesTouchedThisSecond());
	}

	@Test
	void resetsWork() {
		// when:
		subject.clearAutoRenewSummaryCounts();

		// then:
		assertEquals(0, subject.getEntitiesTouchedThisSecond());
		assertEquals(0, subject.getEntitiesScannedThisSecond());
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
		feeMultiplierSource = mock(FeeMultiplierSource.class);

		given(feeMultiplierSource.congestionLevelStarts()).willReturn(NO_CONGESTION_STARTS);

		// when:
		subject.updateCongestionStartsFrom(feeMultiplierSource);

		// then:
		assertEquals(NO_CONGESTION_STARTS, subject.getCongestionLevelStarts());
	}

	@Test
	void updatesNullCongestionLevelStartsAsExpected() {
		// setup:
		feeMultiplierSource = mock(FeeMultiplierSource.class);

		given(feeMultiplierSource.congestionLevelStarts()).willReturn(null);

		// when:
		subject.updateCongestionStartsFrom(feeMultiplierSource);

		// then:
		assertEquals(NO_CONGESTION_STARTS, subject.getCongestionLevelStarts());
	}

	@Test
	void updatesSnapshotsAsExpected() {
		// setup:
		var aThrottle = DeterministicThrottle.withTpsAndBurstPeriod(5, 2);
		var bThrottle = DeterministicThrottle.withTpsAndBurstPeriod(6, 3);
		var cThrottle = DeterministicThrottle.withTpsAndBurstPeriod(7, 4);
		aThrottle.allow(1);
		bThrottle.allow(1);
		cThrottle.allow(20);
		var activeThrottles = List.of(aThrottle, bThrottle, cThrottle);
		var expectedSnapshots = activeThrottles.stream()
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

		feeMultiplierSource = mock(FeeMultiplierSource.class);

		given(feeMultiplierSource.congestionLevelStarts()).willReturn(congestionStarts);

		// when:
		subject.updateCongestionStartsFrom(feeMultiplierSource);

		// then:
		assertArrayEquals(congestionStarts(), subject.getCongestionLevelStarts());
	}

	@Test
	void warnsIfSavedUsageNotCompatibleWithActiveThrottles() {
		// setup:
		var aThrottle = DeterministicThrottle.withTpsAndBurstPeriod(5, 2);
		var bThrottle = DeterministicThrottle.withTpsAndBurstPeriod(6, 3);
		var cThrottle = DeterministicThrottle.withTpsAndBurstPeriod(7, 4);
		aThrottle.allow(1);
		bThrottle.allow(1);
		cThrottle.allow(20);
		// and:
		var subjectSnapshotA = aThrottle.usageSnapshot();
		aThrottle.allow(2);
		var subjectSnapshotC = cThrottle.usageSnapshot();

		throttling = mock(FunctionalityThrottling.class);
		gasLimitDeterministicThrottle = mock(GasLimitDeterministicThrottle.class);

		given(throttling.allActiveThrottles()).willReturn(List.of(aThrottle, bThrottle));
		given(throttling.gasLimitThrottle()).willReturn(gasLimitDeterministicThrottle);
		given(gasLimitDeterministicThrottle.usageSnapshot()).willReturn(gasLimitUsageSnapshot);
		// and:
		subject.setUsageSnapshots(new DeterministicThrottle.UsageSnapshot[]{subjectSnapshotA, subjectSnapshotC});

		// when:
		subject.resetThrottlingFromSavedSnapshots(throttling);

		// then:

		// and:
		assertNotEquals(subjectSnapshotA.used(), aThrottle.usageSnapshot().used());
		assertNotEquals(subjectSnapshotA.lastDecisionTime(), aThrottle.usageSnapshot().lastDecisionTime());
	}

	@Test
	void warnsIfDifferentNumOfActiveThrottles() {
		// setup:
		var aThrottle = DeterministicThrottle.withTpsAndBurstPeriod(5, 2);
		var bThrottle = DeterministicThrottle.withTpsAndBurstPeriod(6, 3);
		aThrottle.allow(1);
		bThrottle.allow(1);
		// and:
		var subjectSnapshot = aThrottle.usageSnapshot();
		aThrottle.allow(2);

		throttling = mock(FunctionalityThrottling.class);

		given(throttling.allActiveThrottles()).willReturn(List.of(aThrottle, bThrottle));
		// and:
		subject.setUsageSnapshots(new DeterministicThrottle.UsageSnapshot[]{subjectSnapshot});
		// and:
		var desired = "There are 2 active throttles, but 1 usage snapshots from saved state. Not performing a reset!";

		// when:
		subject.resetThrottlingFromSavedSnapshots(throttling);

		// then:
		assertThat(logCaptor.warnLogs(), contains(desired));
		// and:
		assertNotEquals(subjectSnapshot.used(), aThrottle.usageSnapshot().used());
		assertNotEquals(subjectSnapshot.lastDecisionTime(), aThrottle.usageSnapshot().lastDecisionTime());
	}

	@Test
	void warnsIfCannotResetGasLimitUsage() {
		// setup:
		var aThrottle = DeterministicThrottle.withTpsAndBurstPeriod(5, 2);
		aThrottle.allow(1);
		var subjectSnapshot = aThrottle.usageSnapshot();
		aThrottle.allow(2);

		throttling = mock(FunctionalityThrottling.class);

		given(throttling.allActiveThrottles()).willReturn(List.of(aThrottle));
		given(throttling.gasLimitThrottle()).willReturn(gasLimitDeterministicThrottle);
		subject.setUsageSnapshots(new DeterministicThrottle.UsageSnapshot[]{subjectSnapshot});

		var desired = "Saved gas throttle usage snapshot was not compatible with the corresponding " +
				"active throttle (Cannot use -1 units in a bucket of capacity 1234!); not performing a reset!";
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
		aThrottle.allow(1);
		var subjectSnapshot = aThrottle.usageSnapshot();
		aThrottle.allow(2);

		throttling = mock(FunctionalityThrottling.class);
		gasLimitDeterministicThrottle = mock(GasLimitDeterministicThrottle.class);

		given(throttling.allActiveThrottles()).willReturn(List.of(aThrottle));
		given(throttling.gasLimitThrottle()).willReturn(gasLimitDeterministicThrottle);
		given(gasLimitDeterministicThrottle.usageSnapshot()).willReturn(gasLimitUsageSnapshot);
		// given:
		subject.setUsageSnapshots(new DeterministicThrottle.UsageSnapshot[]{subjectSnapshot});

		// when:
		subject.resetThrottlingFromSavedSnapshots(throttling);

		// then:
		assertEquals(subjectSnapshot.used(), aThrottle.usageSnapshot().used());
		assertEquals(subjectSnapshot.lastDecisionTime(), aThrottle.usageSnapshot().lastDecisionTime());
	}

	@Test
	void updatesFromSavedCongestionStartsEvenIfNull() {
		// setup:
		feeMultiplierSource = mock(FeeMultiplierSource.class);
		congestionStarts[1] = null;

		// given:
		subject.getCongestionLevelStarts()[1] = null;

		// when:
		subject.resetMultiplierSourceFromSavedCongestionStarts(feeMultiplierSource);

		// then:
		verify(feeMultiplierSource, times(1))
				.resetCongestionLevelStarts(congestionStarts);
	}

	@Test
	void gasThrottleUsageSnapshotTest() {
		subject.setGasThrottleUsageSnapshot(gasLimitUsageSnapshot);
		assertEquals(gasLimitUsageSnapshot, subject.getGasThrottleUsageSnapshot());
	}

	@Test
	void updatesFromSavedCongestionStarts() {
		feeMultiplierSource = mock(FeeMultiplierSource.class);

		// when:
		subject.resetMultiplierSourceFromSavedCongestionStarts(feeMultiplierSource);
		// and:
		subject.setCongestionLevelStarts(NO_CONGESTION_STARTS);
		subject.resetMultiplierSourceFromSavedCongestionStarts(feeMultiplierSource);

		// then:
		verify(feeMultiplierSource, times(1)).resetCongestionLevelStarts(congestionStarts);
	}

	@Test
	void deserializeWorksFor0130() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		MerkleNetworkContext.ratesSupplier = () -> midnightRateSet;
		MerkleNetworkContext.seqNoSupplier = () -> seqNo;
		InOrder inOrder = inOrder(in, seqNo);

		subject = new MerkleNetworkContext();

		given(in.readInt())
				.willReturn(usageSnapshots.length)
				.willReturn(congestionStarts.length);
		given(in.readLong())
				.willReturn(usageSnapshots[0].used())
				.willReturn(usageSnapshots[1].used());
		given(serdes.readNullableInstant(in).toJava())
				.willReturn(consensusTimeOfLastHandledTxn)
				.willReturn(usageSnapshots[0].lastDecisionTime())
				.willReturn(usageSnapshots[1].lastDecisionTime())
				.willReturn(congestionStarts[0])
				.willReturn(congestionStarts[1]);

		// when:
		subject.deserialize(in, MerkleNetworkContext.RELEASE_0130_VERSION);

		// then:
		assertEquals(consensusTimeOfLastHandledTxn, subject.getConsensusTimeOfLastHandledTxn());
		assertArrayEquals(usageSnapshots, subject.usageSnapshots());
		assertArrayEquals(congestionStarts(), subject.getCongestionLevelStarts());
		// and:
		inOrder.verify(seqNo).deserialize(in);
		inOrder.verify(in).readSerializable(booleanThat(Boolean.TRUE::equals), any(Supplier.class));
		// and:
		assertEquals(MerkleNetworkContext.UNRECORDED_STATE_VERSION, subject.getStateVersion());
	}

	@Test
	void deserializeWorksFor0140() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		MerkleNetworkContext.ratesSupplier = () -> midnightRateSet;
		MerkleNetworkContext.seqNoSupplier = () -> seqNo;
		InOrder inOrder = inOrder(in, seqNo);

		subject = new MerkleNetworkContext();

		given(in.readInt())
				.willReturn(usageSnapshots.length)
				.willReturn(congestionStarts.length)
				.willReturn(stateVersion);
		given(in.readLong())
				.willReturn(usageSnapshots[0].used())
				.willReturn(usageSnapshots[1].used())
				.willReturn(lastScannedEntity)
				.willReturn(entitiesScannedThisSecond)
				.willReturn(entitiesTouchedThisSecond);
		given(serdes.readNullableInstant(in))
				.willReturn(fromJava(consensusTimeOfLastHandledTxn))
				.willReturn(fromJava(usageSnapshots[0].lastDecisionTime()))
				.willReturn(fromJava(usageSnapshots[1].lastDecisionTime()))
				.willReturn(fromJava(congestionStarts[0]))
				.willReturn(fromJava(congestionStarts[1]));

		// when:
		subject.deserialize(in, MerkleNetworkContext.RELEASE_0140_VERSION);

		// then:
		assertEquals(consensusTimeOfLastHandledTxn, subject.getConsensusTimeOfLastHandledTxn());
		assertEquals(entitiesScannedThisSecond, subject.getEntitiesScannedThisSecond());
		assertEquals(entitiesTouchedThisSecond, subject.getEntitiesTouchedThisSecond());
		assertArrayEquals(usageSnapshots, subject.usageSnapshots());
		assertArrayEquals(congestionStarts(), subject.getCongestionLevelStarts());
		// and:
		inOrder.verify(seqNo).deserialize(in);
		inOrder.verify(in).readSerializable(booleanThat(Boolean.TRUE::equals), any(Supplier.class));
		// and:
		assertEquals(lastScannedEntity, subject.lastScannedEntity());
		assertEquals(stateVersion, subject.getStateVersion());
	}

	@Test
	void deserializeWorksFor0150() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		MerkleNetworkContext.ratesSupplier = () -> midnightRateSet;
		MerkleNetworkContext.seqNoSupplier = () -> seqNo;
		InOrder inOrder = inOrder(in, seqNo);

		subject = new MerkleNetworkContext();

		given(in.readInt())
				.willReturn(usageSnapshots.length)
				.willReturn(congestionStarts.length)
				.willReturn(stateVersion);
		given(in.readLong())
				.willReturn(usageSnapshots[0].used())
				.willReturn(usageSnapshots[1].used())
				.willReturn(lastScannedEntity)
				.willReturn(entitiesScannedThisSecond)
				.willReturn(entitiesTouchedThisSecond);
		given(serdes.readNullableInstant(in))
				.willReturn(fromJava(consensusTimeOfLastHandledTxn))
				.willReturn(fromJava(usageSnapshots[0].lastDecisionTime()))
				.willReturn(fromJava(usageSnapshots[1].lastDecisionTime()))
				.willReturn(fromJava(congestionStarts[0]))
				.willReturn(fromJava(congestionStarts[1]))
				.willReturn(fromJava(lastMidnightBoundaryCheck));

		// when:
		subject.deserialize(in, MerkleNetworkContext.RELEASE_0150_VERSION);

		// then:
		assertEquals(lastMidnightBoundaryCheck, subject.lastMidnightBoundaryCheck());
		assertEquals(consensusTimeOfLastHandledTxn, subject.getConsensusTimeOfLastHandledTxn());
		assertEquals(entitiesScannedThisSecond, subject.getEntitiesScannedThisSecond());
		assertEquals(entitiesTouchedThisSecond, subject.getEntitiesTouchedThisSecond());
		assertArrayEquals(usageSnapshots, subject.usageSnapshots());
		assertArrayEquals(congestionStarts(), subject.getCongestionLevelStarts());
		// and:
		inOrder.verify(seqNo).deserialize(in);
		inOrder.verify(in).readSerializable(booleanThat(Boolean.TRUE::equals), any(Supplier.class));
		// and:
		assertEquals(lastScannedEntity, subject.lastScannedEntity());
		assertEquals(stateVersion, subject.getStateVersion());
	}

	@Test
	void deserializeWorksFor0190() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		MerkleNetworkContext.ratesSupplier = () -> midnightRateSet;
		MerkleNetworkContext.seqNoSupplier = () -> seqNo;
		InOrder inOrder = inOrder(in, seqNo);

		subject = new MerkleNetworkContext();

		given(in.readInt())
				.willReturn(usageSnapshots.length)
				.willReturn(congestionStarts.length)
				.willReturn(stateVersion);
		given(in.readLong())
				.willReturn(usageSnapshots[0].used())
				.willReturn(usageSnapshots[1].used())
				.willReturn(lastScannedEntity)
				.willReturn(entitiesScannedThisSecond)
				.willReturn(entitiesTouchedThisSecond)
				.willReturn(preparedUpdateFileNum);
		given(serdes.readNullableInstant(in))
				.willReturn(fromJava(consensusTimeOfLastHandledTxn))
				.willReturn(fromJava(usageSnapshots[0].lastDecisionTime()))
				.willReturn(fromJava(usageSnapshots[1].lastDecisionTime()))
				.willReturn(fromJava(congestionStarts[0]))
				.willReturn(fromJava(congestionStarts[1]))
				.willReturn(fromJava(lastMidnightBoundaryCheck));
		given(in.readByteArray(48)).willReturn(preparedUpdateFileHash);

		// when:
		subject.deserialize(in, MerkleNetworkContext.RELEASE_0190_VERSION);

		// then:
		assertEquals(lastMidnightBoundaryCheck, subject.lastMidnightBoundaryCheck());
		assertEquals(consensusTimeOfLastHandledTxn, subject.getConsensusTimeOfLastHandledTxn());
		assertEquals(entitiesScannedThisSecond, subject.getEntitiesScannedThisSecond());
		assertEquals(entitiesTouchedThisSecond, subject.getEntitiesTouchedThisSecond());
		assertArrayEquals(usageSnapshots, subject.usageSnapshots());
		assertArrayEquals(congestionStarts(), subject.getCongestionLevelStarts());
		// and:
		inOrder.verify(seqNo).deserialize(in);
		inOrder.verify(in).readSerializable(booleanThat(Boolean.TRUE::equals), any(Supplier.class));
		// and:
		assertEquals(lastScannedEntity, subject.lastScannedEntity());
		assertEquals(stateVersion, subject.getStateVersion());
		assertEquals(preparedUpdateFileNum, subject.getPreparedUpdateFileNum());
		assertArrayEquals(preparedUpdateFileHash, subject.getPreparedUpdateFileHash());
	}

	@Test
	void deserializeWorksFor0200() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		MerkleNetworkContext.ratesSupplier = () -> midnightRateSet;
		MerkleNetworkContext.seqNoSupplier = () -> seqNo;
		InOrder inOrder = inOrder(in, seqNo);

		subject = new MerkleNetworkContext();

		given(in.readInt())
				.willReturn(usageSnapshots.length)
				.willReturn(congestionStarts.length)
				.willReturn(stateVersion);
		given(in.readLong())
				.willReturn(usageSnapshots[0].used())
				.willReturn(usageSnapshots[1].used())
				.willReturn(lastScannedEntity)
				.willReturn(entitiesScannedThisSecond)
				.willReturn(entitiesTouchedThisSecond)
				.willReturn(preparedUpdateFileNum)
				.willReturn(gasLimitUsageSnapshot.used());
		given(serdes.readNullableInstant(in))
				.willReturn(fromJava(consensusTimeOfLastHandledTxn))
				.willReturn(fromJava(usageSnapshots[0].lastDecisionTime()))
				.willReturn(fromJava(usageSnapshots[1].lastDecisionTime()))
				.willReturn(fromJava(congestionStarts[0]))
				.willReturn(fromJava(congestionStarts[1]))
				.willReturn(fromJava(lastMidnightBoundaryCheck))
				.willReturn(fromJava(gasLimitUsageSnapshot.lastDecisionTime()));
		given(in.readByteArray(48)).willReturn(preparedUpdateFileHash);

		// when:
		subject.deserialize(in, MerkleNetworkContext.RELEASE_0200_VERSION);

		// then:
		assertEquals(lastMidnightBoundaryCheck, subject.lastMidnightBoundaryCheck());
		assertEquals(consensusTimeOfLastHandledTxn, subject.getConsensusTimeOfLastHandledTxn());
		assertEquals(entitiesScannedThisSecond, subject.getEntitiesScannedThisSecond());
		assertEquals(entitiesTouchedThisSecond, subject.getEntitiesTouchedThisSecond());
		assertArrayEquals(usageSnapshots, subject.usageSnapshots());
		assertArrayEquals(congestionStarts(), subject.getCongestionLevelStarts());
		// and:
		inOrder.verify(seqNo).deserialize(in);
		inOrder.verify(in).readSerializable(booleanThat(Boolean.TRUE::equals), any(Supplier.class));
		// and:
		assertEquals(lastScannedEntity, subject.lastScannedEntity());
		assertEquals(stateVersion, subject.getStateVersion());
		assertEquals(preparedUpdateFileNum, subject.getPreparedUpdateFileNum());
		assertArrayEquals(preparedUpdateFileHash, subject.getPreparedUpdateFileHash());
		assertEquals(gasLimitUsageSnapshot, subject.getGasThrottleUsageSnapshot());
	}


	@Test
	void deserializeWorksForNullInstants() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		MerkleNetworkContext.ratesSupplier = () -> midnightRateSet;
		MerkleNetworkContext.seqNoSupplier = () -> seqNo;
		InOrder inOrder = inOrder(in, seqNo);

		subject = new MerkleNetworkContext();

		given(in.readInt())
				.willReturn(usageSnapshots.length)
				.willReturn(congestionStarts.length)
				.willReturn(stateVersion);
		given(in.readLong())
				.willReturn(usageSnapshots[0].used())
				.willReturn(usageSnapshots[1].used())
				.willReturn(lastScannedEntity)
				.willReturn(entitiesScannedThisSecond)
				.willReturn(entitiesTouchedThisSecond);
		given(serdes.readNullableInstant(in)).willReturn(null);

		// when:
		subject.deserialize(in, MerkleNetworkContext.RELEASE_0150_VERSION);

		// then:
		assertNull(subject.getConsensusTimeOfLastHandledTxn());
		assertNull(subject.lastMidnightBoundaryCheck());
		assertEquals(entitiesScannedThisSecond, subject.getEntitiesScannedThisSecond());
		assertEquals(entitiesTouchedThisSecond, subject.getEntitiesTouchedThisSecond());
		assertArrayEquals(new DeterministicThrottle.UsageSnapshot[]{
				new DeterministicThrottle.UsageSnapshot(usageSnapshots[0].used(), null),
				new DeterministicThrottle.UsageSnapshot(usageSnapshots[1].used(), null)
		}, subject.usageSnapshots());
		assertArrayEquals(new Instant[]{null, null}, subject.getCongestionLevelStarts());
		// and:
		inOrder.verify(seqNo).deserialize(in);
		inOrder.verify(in).readSerializable(booleanThat(Boolean.TRUE::equals), any(Supplier.class));
		// and:
		assertEquals(lastScannedEntity, subject.lastScannedEntity());
		assertEquals(stateVersion, subject.getStateVersion());
	}

	@Test
	void serializeWorks() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		InOrder inOrder = inOrder(out, seqNo, serdes);
		throttling = mock(FunctionalityThrottling.class);
		gasLimitDeterministicThrottle = mock(GasLimitDeterministicThrottle.class);

		// and:
		var active = activeThrottles();

		given(throttling.allActiveThrottles()).willReturn(active);
		given(throttling.gasLimitThrottle()).willReturn(gasLimitDeterministicThrottle);
		given(gasLimitDeterministicThrottle.usageSnapshot()).willReturn(gasLimitUsageSnapshot);

		// when:
		subject.updateSnapshotsFrom(throttling);
		// and:
		subject.serialize(out);

		// expect:
		inOrder.verify(serdes).writeNullableInstant(fromJava(consensusTimeOfLastHandledTxn), out);
		inOrder.verify(seqNo).serialize(out);
		inOrder.verify(out).writeSerializable(midnightRateSet, true);
		// and:
		inOrder.verify(out).writeInt(3);
		for (int i = 0; i < 3; i++) {
			inOrder.verify(out).writeLong(used[i]);
			inOrder.verify(serdes).writeNullableInstant(fromJava(lastUseds[i]), out);
		}
		// and:
		inOrder.verify(out).writeInt(2);
		for (int i = 0; i < 2; i++) {
			inOrder.verify(serdes).writeNullableInstant(fromJava(congestionStarts()[i]), out);
		}
		inOrder.verify(out).writeLong(lastScannedEntity);
		inOrder.verify(out).writeLong(entitiesScannedThisSecond);
		inOrder.verify(out).writeLong(entitiesTouchedThisSecond);
		inOrder.verify(out).writeInt(stateVersion);
		inOrder.verify(serdes).writeNullableInstant(fromJava(lastMidnightBoundaryCheck), out);
		inOrder.verify(out).writeLong(preparedUpdateFileNum);
		inOrder.verify(out).writeByteArray(preparedUpdateFileHash);
	}

	@Test
	void canSerializeNullCongestionLevelStarts() {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		feeMultiplierSource = mock(FeeMultiplierSource.class);

		given(feeMultiplierSource.congestionLevelStarts()).willReturn(new Instant[]{null, null});

		// when:
		subject.updateCongestionStartsFrom(feeMultiplierSource);
		// and:
		Assertions.assertDoesNotThrow(() -> subject.serialize(out));
	}

	@Test
	void sanityChecks() {
		assertEquals(CURRENT_VERSION, subject.getVersion());
		assertEquals(MerkleNetworkContext.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	long[] used = new long[]{100L, 200L, 300L};
	Instant[] lastUseds = new Instant[]{
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
}
