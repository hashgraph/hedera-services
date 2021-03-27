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

import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.booleanThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

class MerkleNetworkContextTest {
	RichInstant consensusTimeOfLastHandledTxn;
	SequenceNumber seqNo;
	SequenceNumber seqNoCopy;
	ExchangeRates midnightRateSet;
	ExchangeRates midnightRateSetCopy;

	DomainSerdes serdes;
	FunctionalityThrottling throttling;

	MerkleNetworkContext subject;

	@BeforeEach
	public void setup() {
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

		serdes = mock(DomainSerdes.class);
		MerkleNetworkContext.serdes = serdes;

		subject = new MerkleNetworkContext(consensusTimeOfLastHandledTxn, seqNo, midnightRateSet);
	}

	@AfterEach
	public void cleanup() {
		MerkleNetworkContext.serdes = new DomainSerdes();
	}

	@Test
	public void copyWorksWithSyncedContext() {
		// setup:
		throttling = mock(FunctionalityThrottling.class);
		// and:
		var active = activeThrottles();

		given(throttling.allActiveThrottles()).willReturn(active);

		// when:
		subject.syncWithThrottles(throttling);
		// given:
		var subjectCopy = subject.copy();

		// expect:
		assertSame(subjectCopy.consensusTimeOfLastHandledTxn, subject.consensusTimeOfLastHandledTxn);
		assertEquals(seqNoCopy, subjectCopy.seqNo);
		assertEquals(midnightRateSetCopy, subjectCopy.midnightRates);
		// and:
		assertNull(subject.getThrottling());
		assertSame(throttling, subjectCopy.getThrottling());
		// and:
		assertSnapshotsMatch(subject);
	}

	@Test
	public void copyWorksBeforeSyncing() {
		// given:
		subject.throttleUsages = activeThrottles().stream()
				.map(DeterministicThrottle::usageSnapshot)
				.collect(Collectors.toList());

		// when:
		var subjectCopy = subject.copy();

		// expect:
		assertSame(subjectCopy.consensusTimeOfLastHandledTxn, subject.consensusTimeOfLastHandledTxn);
		assertEquals(seqNoCopy, subjectCopy.seqNo);
		assertEquals(midnightRateSetCopy, subjectCopy.midnightRates);
		// and:
		assertSnapshotsMatch(subjectCopy);
	}

	@Test
	public void toStringRendersUnsyncedAsExpected() {
		// setup:
		throttling = mock(FunctionalityThrottling.class);
		// and:
		var active = activeThrottles();

		given(throttling.allActiveThrottles()).willReturn(active);
		// and:
		subject.syncWithThrottles(throttling);
		// and:
		var desired = "The network context is,\n" +
				"  Consensus time of last handled transaction :: 1970-01-15T06:56:07.000054321Z\n" +
				"  Midnight rate set                          :: 1ℏ <-> 14¢ til 1234567 | 1ℏ <-> 15¢ til 2345678\n" +
				"  Next entity number                         :: 1234\n" +
				"  Throttle usage snapshots were              ::\n" +
				"    100 used (last decision time 1970-01-01T00:00:01.000000100Z)\n" +
				"    200 used (last decision time 1970-01-01T00:00:02.000000200Z)\n" +
				"    300 used (last decision time 1970-01-01T00:00:03.000000300Z)";


		// when:
		subject.copy();

		assertEquals(desired, subject.toString());
	}

	@Test
	public void toStringRendersSyncedAsExpected() {
		// setup:
		throttling = mock(FunctionalityThrottling.class);
		// and:
		var active = activeThrottles();

		given(throttling.allActiveThrottles()).willReturn(active);
		// and:
		subject.syncWithThrottles(throttling);
		// and:
		var desired = "The throttle-synced network context is,\n" +
				"  Consensus time of last handled transaction :: 1970-01-15T06:56:07.000054321Z\n" +
				"  Midnight rate set                          :: 1ℏ <-> 14¢ til 1234567 | 1ℏ <-> 15¢ til 2345678\n" +
				"  Next entity number                         :: 1234\n" +
				"  Usage statistics of active throttles       :: \n" +
				"    ThrottleA at 100/1000000000000 used (last decision time 1970-01-01T00:00:01.000000100Z)\n" +
				"    ThrottleB at 200/1000000000000 used (last decision time 1970-01-01T00:00:02.000000200Z)\n" +
				"    ThrottleC at 300/1000000000000 used (last decision time 1970-01-01T00:00:03.000000300Z)";

		assertEquals(desired, subject.toString());
	}

	@Test
	void updatesUsagesFromSavedWhenPresent() {
		// setup:
		var aThrottle = DeterministicThrottle.withTpsAndBurstPeriod(5, 2);
		aThrottle.allow(1);
		var subjectSnapshot = aThrottle.usageSnapshot();
		aThrottle.allow(2);

		throttling = mock(FunctionalityThrottling.class);
		// and:
		subject.syncWithThrottles(throttling);

		given(throttling.allActiveThrottles()).willReturn(List.of(aThrottle));
		// given:
		subject.throttleUsages = List.of(subjectSnapshot);

		// when:
		subject.updateSyncedThrottlesFromSavedState();

		// then:
		assertEquals(subjectSnapshot.used(), aThrottle.usageSnapshot().used());
		assertEquals(subjectSnapshot.lastDecisionTime(), aThrottle.usageSnapshot().lastDecisionTime());
	}

	@Test
	void failsFastIfThrottlingNotSynced() {
		// expect:
		assertThrows(IllegalStateException.class, subject::updateSyncedThrottlesFromSavedState);
	}

	@Test
	void doesNothingIfNoSavedUsageSnapshots() {
		// setup:
		throttling = mock(FunctionalityThrottling.class);
		// and:
		subject.syncWithThrottles(throttling);

		// when:
		subject.updateSyncedThrottlesFromSavedState();

		// then:
		verify(throttling, never()).allActiveThrottles();
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
		assertSame(Collections.emptyList(), subject.getThrottleUsages());
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
		var snapshots = snapshots();

		given(in.readInt()).willReturn(lastUseds.length);
		given(in.readLong())
				.willReturn(snapshots.get(0).used())
				.willReturn(snapshots.get(1).used())
				.willReturn(snapshots.get(2).used());
		given(serdes.readNullableInstant(in))
				.willReturn(consensusTimeOfLastHandledTxn)
				.willReturn(RichInstant.fromJava(lastUseds[0]))
				.willReturn(RichInstant.fromJava(lastUseds[1]))
				.willReturn(RichInstant.fromJava(lastUseds[2]));

		// when:
		subject.deserialize(in, MerkleNetworkContext.RELEASE_0130_VERSION);

		// then:
		assertSnapshotsMatch(subject);
		assertEquals(consensusTimeOfLastHandledTxn, subject.consensusTimeOfLastHandledTxn);
		// and:
		inOrder.verify(seqNo).deserialize(in);
		inOrder.verify(in).readSerializable(booleanThat(Boolean.TRUE::equals), any(Supplier.class));
	}

	@Test
	void cannotCallSerializeOnMutableCopy() {
		// setup:
		throttling = mock(FunctionalityThrottling.class);

		// when:
		subject.syncWithThrottles(throttling);

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.serialize(null));
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
		subject.syncWithThrottles(throttling);
		// and:
		subject.copy();
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
	}

	@Test
	public void sanityChecks() {
		assertEquals(MerkleNetworkContext.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleNetworkContext.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	private void assertSnapshotsMatch(MerkleNetworkContext subject) {
		var immutableUsages = subject.getThrottleUsages();
		assertArrayEquals(used, immutableUsages.stream()
				.mapToLong(DeterministicThrottle.UsageSnapshot::used)
				.toArray());
		assertEquals(List.of(lastUseds), immutableUsages.stream()
				.map(DeterministicThrottle.UsageSnapshot::lastDecisionTime)
				.collect(Collectors.toList()));
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
			var throttle = DeterministicThrottle.withTpsNamed(1, "Throttle" + (char)('A' + i));
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
}
