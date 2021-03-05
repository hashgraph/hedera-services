package com.hedera.services.state.submerkle;

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

import com.hederahashgraph.api.proto.java.Timestamp;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

class RichInstantTest {
	long seconds = 1_234_567L;
	int nanos = 890;

	RichInstant subject;

	DataInputStream din;
	SerializableDataInputStream in;

	@BeforeEach
	public void setup() {
		in = mock(SerializableDataInputStream.class);
		din = mock(DataInputStream.class);

		subject = new RichInstant(seconds, nanos);
	}

	@Test
	public void serializeWorks() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);

		// when:
		subject.serialize(out);

		// then:
		verify(out).writeLong(seconds);
		verify(out).writeInt(nanos);
	}

	@Test
	public void factoryWorks() throws IOException {
		given(in.readLong()).willReturn(seconds);
		given(in.readInt()).willReturn(nanos);

		// when:
		var readSubject = RichInstant.from(in);

		// expect:
		assertEquals(subject, readSubject);
	}

	@Test
	public void beanWorks() {
		assertEquals(subject, new RichInstant(subject.getSeconds(), subject.getNanos()));
	}

	@Test
	public void viewWorks() {
		// given:
		var grpc = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();

		// expect:
		assertEquals(grpc, subject.toGrpc());
	}

	@Test
	public void knowsIfMissing() {
		// expect:
		assertFalse(subject.isMissing());
		assertTrue(RichInstant.MISSING_INSTANT.isMissing());
	}

	@Test
	public void toStringWorks() {
		// expect:
		assertEquals(
				"RichInstant{seconds=" + seconds + ", nanos=" + nanos + "}",
				subject.toString());
	}

	@Test
	public void factoryWorksForMissing() {
		// expect:
		assertTrue(RichInstant.MISSING_INSTANT == RichInstant.fromGrpc(Timestamp.getDefaultInstance()));
		assertEquals(subject, RichInstant.fromGrpc(subject.toGrpc()));
	}

	@Test
	public void objectContractWorks() {
		// given:
		var one = subject;
		var two = new RichInstant(seconds - 1, nanos - 1);
		var three = new RichInstant(subject.getSeconds(), subject.getNanos());

		// when:

		// then:
		assertEquals(one, one);
		assertNotEquals(one, null);
		assertNotEquals(one, new Object());
		assertNotEquals(one, two);
		assertEquals(one, three);
		// and:
		assertEquals(one.hashCode(), three.hashCode());
		assertNotEquals(one.hashCode(), two.hashCode());
	}

	@Test
	public void orderingWorks() {
		assertTrue(subject.isAfter(new RichInstant(seconds - 1, nanos)));
		assertTrue(subject.isAfter(new RichInstant(seconds, nanos - 1)));
		assertFalse(subject.isAfter(new RichInstant(seconds, nanos + 1)));
	}

	@Test
	public void javaFactoryWorks() {
		// expect:
		assertEquals(subject, RichInstant.fromJava(Instant.ofEpochSecond(subject.getSeconds(), subject.getNanos())));
	}

	@Test
	public void javaViewWorks() {
		// expect:
		assertEquals(Instant.ofEpochSecond(subject.getSeconds(), subject.getNanos()), subject.toJava());
	}
}
