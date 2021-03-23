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
import com.hedera.services.throttling.DeterministicThrottle;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.booleanThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;

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
		consensusTimeOfLastHandledTxn = RichInstant.fromJava(Instant.now());

		seqNo = mock(SequenceNumber.class);
		seqNoCopy = mock(SequenceNumber.class);
		given(seqNo.copy()).willReturn(seqNoCopy);
		midnightRateSet = mock(ExchangeRates.class);
		midnightRateSetCopy = mock(ExchangeRates.class);
		given(midnightRateSet.copy()).willReturn(midnightRateSetCopy);

		serdes = mock(DomainSerdes.class);
		MerkleNetworkContext.serdes = serdes;

		subject = new MerkleNetworkContext(consensusTimeOfLastHandledTxn, seqNo, midnightRateSet);
	}

	@AfterEach
	public void cleanup() {
		MerkleNetworkContext.serdes = new DomainSerdes();
	}

	@Test
	public void copyWorks() {
		throttling = mock(FunctionalityThrottling.class);
		// and:
		var current = expectedThrottles();

		given(throttling.currentThrottles()).willReturn(current);

		// when:
		subject.syncWithThrottles(throttling);
		// given:
		var subjectCopy = subject.copy();

		// expect:
		assertTrue(subjectCopy.consensusTimeOfLastHandledTxn == subject.consensusTimeOfLastHandledTxn);
		assertEquals(seqNoCopy, subjectCopy.seqNo);
		assertEquals(midnightRateSetCopy, subjectCopy.midnightRates);
		// and:
		assertNull(subject.getThrottling());
		assertSame(throttling, subjectCopy.getThrottling());
		// and:
		var immutableInternals = subject.getThrottleInternals();
		assertEquals(List.of(throttleNames), immutableInternals.stream()
				.map(MerkleNetworkContext.ThrottleInternals::getName)
				.collect(Collectors.toList()));
		assertArrayEquals(used, immutableInternals.stream()
				.mapToLong(MerkleNetworkContext.ThrottleInternals::getUsed)
				.toArray());
		assertEquals(List.of(lastUseds), immutableInternals.stream()
				.map(MerkleNetworkContext.ThrottleInternals::getLastUsed)
				.collect(Collectors.toList()));
	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		MerkleNetworkContext.ratesSupplier = () -> midnightRateSet;
		MerkleNetworkContext.seqNoSupplier = () -> seqNo;
		InOrder inOrder = inOrder(in, midnightRateSet, seqNo);

		given(serdes.readNullableInstant(in)).willReturn(consensusTimeOfLastHandledTxn);

		// when:
		subject.deserialize(in, MerkleNetworkContext.MERKLE_VERSION);

		// then:
		assertEquals(consensusTimeOfLastHandledTxn, subject.consensusTimeOfLastHandledTxn);
		// and:
		inOrder.verify(seqNo).deserialize(in);
		inOrder.verify(in).readSerializable(booleanThat(Boolean.TRUE::equals), any(Supplier.class));
	}

	@Test
	public void serializeWorks() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		InOrder inOrder = inOrder(out, seqNo, midnightRateSet, serdes);
		throttling = mock(FunctionalityThrottling.class);
		// and:
		var current = expectedThrottles();

		given(throttling.currentThrottles()).willReturn(current);

		// when:
		subject.syncWithThrottles(throttling);
		// and:
		subject.serialize(out);

		// expect:
		inOrder.verify(serdes).writeNullableInstant(consensusTimeOfLastHandledTxn, out);
		inOrder.verify(seqNo).serialize(out);
		inOrder.verify(out).writeSerializable(midnightRateSet, true);
		// and:
		inOrder.verify(out).writeInt(3);
		for (int i = 0; i < 3; i++) {
			inOrder.verify(out).writeNormalisedString(throttleNames[i]);
			inOrder.verify(out).writeLong(used[i]);
			inOrder.verify(out).writeLong(lastUseds[i].getEpochSecond());
			inOrder.verify(out).writeInt(lastUseds[i].getNano());
		}
	}

	@Test
	public void sanityChecks() {
		assertEquals(MerkleNetworkContext.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleNetworkContext.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	long[] used = new long[] { 100L, 200L, 300L };
	String[] throttleNames = new String[] { "a", "b", "c" };
	Instant[] lastUseds = new Instant[] {
			Instant.ofEpochSecond(1L, 100),
			Instant.ofEpochSecond(2L, 200),
			Instant.ofEpochSecond(3L, 300)
	};

	private Map<String, DeterministicThrottle> expectedThrottles() {
		Map<String, DeterministicThrottle> expected = new HashMap<>();

		for (int i = 0; i < used.length; i++) {
			var throttle = new DeterministicThrottle(
					new DeterministicThrottle.StateSnapshot(used[i], 0L, lastUseds[i]));
			expected.put(throttleNames[i], throttle);
		}

		return expected;
	}

	private List<MerkleNetworkContext.ThrottleInternals> expectedInternals() {
		List<MerkleNetworkContext.ThrottleInternals> exp = new ArrayList<>();
		for (int i = 0; i < used.length; i++) {
			exp.add(new MerkleNetworkContext.ThrottleInternals(used[i], throttleNames[i], lastUseds[i]));
		}
		return exp;
	}
}
