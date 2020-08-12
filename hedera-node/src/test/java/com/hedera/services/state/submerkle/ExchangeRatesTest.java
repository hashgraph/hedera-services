package com.hedera.services.state.submerkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.io.DataInputStream;
import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class ExchangeRatesTest {
	private int expCurrentHbarEquiv = 25;
	private int expCurrentCentEquiv = 1;
	private long expCurrentExpiry = Instant.now().getEpochSecond() + 1_234L;

	private int expNextHbarEquiv = 45;
	private int expNextCentEquiv = 2;
	private long expNextExpiry = Instant.now().getEpochSecond() + 5_678L;

	ExchangeRateSet grpc = ExchangeRateSet.newBuilder()
			.setCurrentRate(ExchangeRate.newBuilder()
					.setHbarEquiv(expCurrentHbarEquiv)
					.setCentEquiv(expCurrentCentEquiv)
					.setExpirationTime(TimestampSeconds.newBuilder().setSeconds(expCurrentExpiry)))
			.setNextRate(ExchangeRate.newBuilder()
					.setHbarEquiv(expNextHbarEquiv)
					.setCentEquiv(expNextCentEquiv)
					.setExpirationTime(TimestampSeconds.newBuilder().setSeconds(expNextExpiry)))
			.build();

	DataInputStream din;

	ExchangeRates subject;

	@BeforeEach
	private void setup() {
		din = mock(DataInputStream.class);

		subject = new ExchangeRates(
				expCurrentHbarEquiv, expCurrentCentEquiv, expCurrentExpiry,
				expNextHbarEquiv, expNextCentEquiv, expNextExpiry);
	}

	@Test
	public void notAutoInitialized() {
		// given:
		subject = new ExchangeRates();

		// expect:
		assertFalse(subject.isInitialized());
	}

	@Test
	public void legacyProviderWorks() throws IOException {
		given(din.readBoolean()).willReturn(true);
		given(din.readLong())
				.willReturn(-1L).willReturn(-2L)
				.willReturn(-1L).willReturn(-2L).willReturn(expCurrentExpiry)
				.willReturn(-1L).willReturn(-2L).willReturn(expNextExpiry);
		given(din.readInt())
				.willReturn(expCurrentHbarEquiv).willReturn(expCurrentCentEquiv)
				.willReturn(expNextHbarEquiv).willReturn(expNextCentEquiv);

		// when:
		var subjectRead = ExchangeRates.LEGACY_PROVIDER.deserialize(din);

		// then:
		assertEquals(subject, subjectRead);
	}

	@Test
	public void copyWorks() {
		// given:
		var subjectCopy = subject.copy();

		// expect:
		assertEquals(expCurrentHbarEquiv, subjectCopy.getCurrHbarEquiv());
		assertEquals(expCurrentCentEquiv, subjectCopy.getCurrCentEquiv());
		assertEquals(expCurrentExpiry, subjectCopy.getCurrExpiry());
		assertEquals(expNextHbarEquiv, subjectCopy.getNextHbarEquiv());
		assertEquals(expNextCentEquiv, subjectCopy.getNextCentEquiv());
		assertEquals(expNextExpiry, subjectCopy.getNextExpiry());
		assertTrue(subjectCopy.isInitialized());
	}

	@Test
	public void serializesAsExpected() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		InOrder inOrder = inOrder(out);

		// when:
		subject.serialize(out);

		// then:
		inOrder.verify(out).writeInt(expCurrentHbarEquiv);
		inOrder.verify(out).writeInt(expCurrentCentEquiv);
		inOrder.verify(out).writeLong(expCurrentExpiry);
		// and:
		inOrder.verify(out).writeInt(expNextHbarEquiv);
		inOrder.verify(out).writeInt(expNextCentEquiv);
		inOrder.verify(out).writeLong(expNextExpiry);
	}

	@Test
	public void deserializesAsExpected() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		subject = new ExchangeRates();

		given(in.readLong())
				.willReturn(expCurrentExpiry)
				.willReturn(expNextExpiry);
		given(in.readInt())
				.willReturn(expCurrentHbarEquiv)
				.willReturn(expCurrentCentEquiv)
				.willReturn(expNextHbarEquiv)
				.willReturn(expNextCentEquiv);

		// when:
		subject.deserialize(in, ExchangeRates.MERKLE_VERSION);

		// then:
		assertEquals(expCurrentHbarEquiv, subject.getCurrHbarEquiv());
		assertEquals(expCurrentCentEquiv, subject.getCurrCentEquiv());
		assertEquals(expCurrentExpiry, subject.getCurrExpiry());
		assertEquals(expNextHbarEquiv, subject.getNextHbarEquiv());
		assertEquals(expNextCentEquiv, subject.getNextCentEquiv());
		assertEquals(expNextExpiry, subject.getNextExpiry());
		assertTrue(subject.isInitialized());
	}

	@Test
	public void sanityChecks() {
		// expect:
		assertEquals(expCurrentHbarEquiv, subject.getCurrHbarEquiv());
		assertEquals(expCurrentCentEquiv, subject.getCurrCentEquiv());
		assertEquals(expCurrentExpiry, subject.getCurrExpiry());
		assertEquals(expNextHbarEquiv, subject.getNextHbarEquiv());
		assertEquals(expNextCentEquiv, subject.getNextCentEquiv());
		assertEquals(expNextExpiry, subject.getNextExpiry());
		assertTrue(subject.isInitialized());
	}

	@Test
	public void replaces() {
		// setup:
		var newRates = ExchangeRateSet.newBuilder()
				.setCurrentRate(
						ExchangeRate.newBuilder()
								.setHbarEquiv(expCurrentHbarEquiv)
								.setCentEquiv(expCurrentCentEquiv)
								.setExpirationTime(TimestampSeconds.newBuilder().setSeconds(expCurrentExpiry)))
				.setNextRate(
						ExchangeRate.newBuilder()
								.setHbarEquiv(expNextHbarEquiv)
								.setCentEquiv(expNextCentEquiv)
								.setExpirationTime(TimestampSeconds.newBuilder().setSeconds(expNextExpiry)))
				.build();

		// given:
		subject = new ExchangeRates();

		// when:
		subject.replaceWith(newRates);

		// expect:
		assertEquals(expCurrentHbarEquiv, subject.getCurrHbarEquiv());
		assertEquals(expCurrentCentEquiv, subject.getCurrCentEquiv());
		assertEquals(expCurrentExpiry, subject.getCurrExpiry());
		assertEquals(expNextHbarEquiv, subject.getNextHbarEquiv());
		assertEquals(expNextCentEquiv, subject.getNextCentEquiv());
		assertEquals(expNextExpiry, subject.getNextExpiry());
		assertTrue(subject.isInitialized());
	}

	@Test
	public void toStringWorks() {
		// expect:
		assertEquals(
				"ExchangeRates{currHbarEquiv=" + expCurrentHbarEquiv +
						", currCentEquiv=" + expCurrentCentEquiv +
						", currExpiry=" + expCurrentExpiry +
						", nextHbarEquiv=" + expNextHbarEquiv +
						", nextCentEquiv=" + expNextCentEquiv +
						", nextExpiry=" + expNextExpiry + "}",
			subject.toString());
	}

	@Test
	public void viewWorks() {
		// expect:
		assertEquals(grpc, subject.toGrpc());
	}

	@Test
	public void factoryWorks() {
		// expect:
		assertEquals(subject, ExchangeRates.fromGrpc(grpc));
	}
}
