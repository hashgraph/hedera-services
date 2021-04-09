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

import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.state.merkle.MerkleNetworkContext.NO_CONGESTION_STARTS;
import static com.hedera.services.state.merkle.MerkleNetworkContext.NO_SNAPSHOTS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.booleanThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(LogCaptureExtension.class)
class MerkleNetworkContextTest {
	RichInstant consensusTimeOfLastHandledTxn;
	SequenceNumber seqNo;
	SequenceNumber seqNoCopy;
	ExchangeRates midnightRateSet;
	ExchangeRates midnightRateSetCopy;
	Instant[] congestionStarts;
	DeterministicThrottle.UsageSnapshot[] usageSnapshots;

	DomainSerdes serdes;
	FunctionalityThrottling throttling;
	FeeMultiplierSource feeMultiplierSource;

	private LogCaptor logCaptor;

	@LoggingSubject
	private MerkleNetworkContext subject;

	@BeforeEach
	public void setup() {
		congestionStarts = new Instant[] {
				Instant.ofEpochSecond(1_234_567L, 54321),
				Instant.ofEpochSecond(1_234_789L, 12345)
		};

		consensusTimeOfLastHandledTxn = RichInstant.fromJava(
				Instant.ofEpochSecond(1_234_567L, 54321));

		seqNo = mock(SequenceNumber.class);
		given(seqNo.current()).willReturn(1234L);
		seqNoCopy = mock(SequenceNumber.class);
		given(seqNo.copy()).willReturn(seqNoCopy);
		midnightRateSet = new ExchangeRates(
				1, 14, 1_234_567L,
				1, 15, 2_345_678L);
		midnightRateSetCopy = midnightRateSet.copy();
		usageSnapshots = new DeterministicThrottle.UsageSnapshot[] {
				new DeterministicThrottle.UsageSnapshot(
						123L, consensusTimeOfLastHandledTxn.toJava()),
				new DeterministicThrottle.UsageSnapshot(
						456L, consensusTimeOfLastHandledTxn.toJava().plusSeconds(1L))
		};

		serdes = mock(DomainSerdes.class);
		MerkleNetworkContext.serdes = serdes;

		subject = new MerkleNetworkContext(
				consensusTimeOfLastHandledTxn,
				seqNo,
				midnightRateSet,
				usageSnapshots,
				richCongestionStarts());
	}

	@AfterEach
	public void cleanup() {
		MerkleNetworkContext.serdes = new DomainSerdes();
	}

	@Test
	public void copyWorks() {
		// given:
		var subjectCopy = subject.copy();

		// expect:
		assertSame(subjectCopy.consensusTimeOfLastHandledTxn, subject.consensusTimeOfLastHandledTxn);
		assertEquals(seqNoCopy, subjectCopy.seqNo);
		assertEquals(midnightRateSetCopy, subjectCopy.midnightRates);
		assertSame(subjectCopy.usageSnapshots, subject.usageSnapshots);
		assertSame(subjectCopy.congestionLevelStarts, subject.congestionLevelStarts);
	}

	@Test
	public void toStringRendersAsExpected() {
		// setup:
		throttling = mock(FunctionalityThrottling.class);

		given(throttling.allActiveThrottles()).willReturn(activeThrottles());
		// and:
		subject.updateSnapshotsFrom(throttling);
		// and:
		var desired = "The network context is,\n" +
				"  Consensus time of last handled transaction :: 1970-01-15T06:56:07.000054321Z\n" +
				"  Midnight rate set                          :: 1ℏ <-> 14¢ til 1234567 | 1ℏ <-> 15¢ til 2345678\n" +
				"  Next entity number                         :: 1234\n" +
				"  Throttle usage snapshots are               ::\n" +
				"    100 used (last decision time 1970-01-01T00:00:01.000000100Z)\n" +
				"    200 used (last decision time 1970-01-01T00:00:02.000000200Z)\n" +
				"    300 used (last decision time 1970-01-01T00:00:03.000000300Z)\n" +
				"  Congestion level start times are           ::\n" +
				"    1970-01-15T06:56:07.000054321Z\n" +
				"    1970-01-15T06:59:49.000012345Z";

		// when:
		subject.copy();

		assertEquals(desired, subject.toString());
	}

	@Test
	void updatesEmptySnapshotsAsExpected() {
		// setup:
		throttling = mock(FunctionalityThrottling.class);

		given(throttling.allActiveThrottles()).willReturn(Collections.emptyList());

		// when:
		subject.updateSnapshotsFrom(throttling);

		// then:
		assertSame(NO_SNAPSHOTS, subject.usageSnapshots());
	}

	@Test
	void updatesEmptyLevelStartsAsExpected() {
		// setup:
		feeMultiplierSource = mock(FeeMultiplierSource.class);

		given(feeMultiplierSource.congestionLevelStarts()).willReturn(new Instant[0]);

		// when:
		subject.updateCongestionStartsFrom(feeMultiplierSource);

		// then:
		assertSame(NO_CONGESTION_STARTS, subject.congestionLevelStarts());
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

		given(throttling.allActiveThrottles()).willReturn(activeThrottles);

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
		assertArrayEquals(richCongestionStarts(), subject.congestionLevelStarts());
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

		given(throttling.allActiveThrottles()).willReturn(List.of(aThrottle, bThrottle));
		// and:
		subject.setUsageSnapshots(new DeterministicThrottle.UsageSnapshot[] { subjectSnapshotA, subjectSnapshotC });
		// and:
		var desired = "Saved usage snapshot #2 was not compatible with the corresponding active throttle " +
				"(Cannot use 20000000000000 units in a bucket of capacity 18000000000000!); not performing a reset!";

		// when:
		subject.resetWithSavedSnapshots(throttling);

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
		subject.setUsageSnapshots(new DeterministicThrottle.UsageSnapshot[] { subjectSnapshot });
		// and:
		var desired = "There are 2 active throttles, but 1 usage snapshots from saved state. Not performing a reset!";

		// when:
		subject.resetWithSavedSnapshots(throttling);

		// then:
		assertThat(logCaptor.warnLogs(), contains(desired));
		// and:
		assertNotEquals(subjectSnapshot.used(), aThrottle.usageSnapshot().used());
		assertNotEquals(subjectSnapshot.lastDecisionTime(), aThrottle.usageSnapshot().lastDecisionTime());
	}

	@Test
	void updatesFromMatchingSnapshotsAsExpected() {
		// setup:
		var aThrottle = DeterministicThrottle.withTpsAndBurstPeriod(5, 2);
		aThrottle.allow(1);
		var subjectSnapshot = aThrottle.usageSnapshot();
		aThrottle.allow(2);

		throttling = mock(FunctionalityThrottling.class);

		given(throttling.allActiveThrottles()).willReturn(List.of(aThrottle));
		// given:
		subject.setUsageSnapshots(new DeterministicThrottle.UsageSnapshot[]{ subjectSnapshot });

		// when:
		subject.resetWithSavedSnapshots(throttling);

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
		subject.congestionLevelStarts[1] = null;

		// when:
		subject.updateWithSavedCongestionStarts(feeMultiplierSource);

		// then:
		verify(feeMultiplierSource, times(1))
				.resetCongestionLevelStarts(congestionStarts);
	}

	@Test
	void updatesFromSavedCongestionStarts() {
		feeMultiplierSource = mock(FeeMultiplierSource.class);

		// when:
		subject.updateWithSavedCongestionStarts(feeMultiplierSource);
		// and:
		subject.congestionLevelStarts = NO_CONGESTION_STARTS;
		subject.updateWithSavedCongestionStarts(feeMultiplierSource);

		// then:
		verify(feeMultiplierSource, times(1)).resetCongestionLevelStarts(congestionStarts);
	}

	@Test
	public void deserializeWorksForPre0130() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		MerkleNetworkContext.ratesSupplier = () -> midnightRateSet;
		MerkleNetworkContext.seqNoSupplier = () -> seqNo;
		InOrder inOrder = inOrder(in, seqNo);

		given(serdes.readNullableInstant(in)).willReturn(consensusTimeOfLastHandledTxn);

		// when:
		subject.deserialize(in, MerkleNetworkContext.PRE_RELEASE_0130_VERSION);

		// then:
		assertEquals(consensusTimeOfLastHandledTxn, subject.consensusTimeOfLastHandledTxn);
		assertSame(usageSnapshots, subject.usageSnapshots());
		assertArrayEquals(richCongestionStarts(), subject.congestionLevelStarts());
		// and:
		inOrder.verify(seqNo).deserialize(in);
		inOrder.verify(in).readSerializable(booleanThat(Boolean.TRUE::equals), any(Supplier.class));
	}

	@Test
	public void deserializeWorksFor0130() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		MerkleNetworkContext.ratesSupplier = () -> midnightRateSet;
		MerkleNetworkContext.seqNoSupplier = () -> seqNo;
		InOrder inOrder = inOrder(in, seqNo);

		subject = new MerkleNetworkContext();

		given(in.readInt())
				.willReturn(usageSnapshots.length)
				.willReturn(congestionStarts.length)
				.willReturn(congestionStarts[0].getNano())
				.willReturn(congestionStarts[1].getNano());
		given(in.readLong())
				.willReturn(usageSnapshots[0].used())
				.willReturn(usageSnapshots[1].used());
		given(serdes.readNullableInstant(in))
				.willReturn(consensusTimeOfLastHandledTxn)
				.willReturn(RichInstant.fromJava(usageSnapshots[0].lastDecisionTime()))
				.willReturn(RichInstant.fromJava(usageSnapshots[1].lastDecisionTime()))
				.willReturn(RichInstant.fromJava(congestionStarts[0]))
				.willReturn(RichInstant.fromJava(congestionStarts[1]));

		// when:
		subject.deserialize(in, MerkleNetworkContext.RELEASE_0130_VERSION);

		// then:
		assertEquals(consensusTimeOfLastHandledTxn, subject.consensusTimeOfLastHandledTxn);
		assertArrayEquals(usageSnapshots, subject.usageSnapshots());
		assertArrayEquals(richCongestionStarts(), subject.congestionLevelStarts());
		// and:
		inOrder.verify(seqNo).deserialize(in);
		inOrder.verify(in).readSerializable(booleanThat(Boolean.TRUE::equals), any(Supplier.class));
	}

	@Test
	public void serializeWorks() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		InOrder inOrder = inOrder(out, seqNo, serdes);
		throttling = mock(FunctionalityThrottling.class);
		// and:
		var active = activeThrottles();

		given(throttling.allActiveThrottles()).willReturn(active);

		// when:
		subject.updateSnapshotsFrom(throttling);
		// and:
		subject.serialize(out);

		// expect:
		inOrder.verify(serdes).writeNullableInstant(consensusTimeOfLastHandledTxn, out);
		inOrder.verify(seqNo).serialize(out);
		inOrder.verify(out).writeSerializable(midnightRateSet, true);
		// and:
		inOrder.verify(out).writeInt(3);
		for (int i = 0; i < 3; i++) {
			inOrder.verify(out).writeLong(used[i]);
			inOrder.verify(serdes).writeNullableInstant(RichInstant.fromJava(lastUseds[i]), out);
		}
		// and:
		inOrder.verify(out).writeInt(2);
		for (int i = 0; i < 2; i++) {
			inOrder.verify(serdes).writeNullableInstant(richCongestionStarts()[i], out);
		}
	}

	@Test
	public void sanityChecks() {
		assertEquals(MerkleNetworkContext.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleNetworkContext.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	long[] used = new long[] { 100L, 200L, 300L };
	Instant[] lastUseds = new Instant[] {
			Instant.ofEpochSecond(1L, 100),
			Instant.ofEpochSecond(2L, 200),
			Instant.ofEpochSecond(3L, 300)
	};

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

	private RichInstant[] richCongestionStarts() {
		return Arrays.stream(congestionStarts).map(RichInstant::fromJava).toArray(RichInstant[]::new);
	}
}
