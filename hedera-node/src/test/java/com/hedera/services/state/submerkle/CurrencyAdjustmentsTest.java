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

import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.state.submerkle.EntityId.ofNullableAccountId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class CurrencyAdjustmentsTest {
	AccountID a = IdUtils.asAccount("0.0.13257");
	EntityId aEntity = EntityId.ofNullableAccountId(a);
	AccountID b = IdUtils.asAccount("0.0.13258");
	EntityId bEntity = EntityId.ofNullableAccountId(b);
	AccountID c = IdUtils.asAccount("0.0.13259");
	EntityId cEntity = EntityId.ofNullableAccountId(c);

	long aAmount = 1L, bAmount = 2L, cAmount = -3L;

	TransferList grpcAdjustments = TxnUtils.withAdjustments(a, aAmount, b, bAmount, c, cAmount);
	TransferList otherGrpcAdjustments = TxnUtils.withAdjustments(a, aAmount * 2, b, bAmount * 2, c, cAmount * 2);

	DataInputStream din;
	EntityId.Provider idProvider;

	CurrencyAdjustments subject;

	@BeforeEach
	public void setup() {
		din = mock(DataInputStream.class);
		idProvider = mock(EntityId.Provider.class);

		CurrencyAdjustments.legacyIdProvider = idProvider;

		subject = new CurrencyAdjustments();
		subject.accountIds = List.of(ofNullableAccountId(a), ofNullableAccountId(b), ofNullableAccountId(c));
		subject.hbars = new long[] { aAmount, bAmount, cAmount };
	}

	@AfterEach
	public void cleanup() {
		CurrencyAdjustments.legacyIdProvider = EntityId.LEGACY_PROVIDER;
	}

	@Test
	public void toStringWorks() {
		// expect:
		assertEquals(
				"CurrencyAdjustments{readable=" + "[0.0.13257 <- +1, 0.0.13258 <- +2, 0.0.13259 -> -3]" + "}",
				subject.toString());
	}

	@Test
	public void objectContractWorks() {
		// given:
		var one = subject;
		var two = CurrencyAdjustments.fromGrpc(otherGrpcAdjustments);
		var three = CurrencyAdjustments.fromGrpc(grpcAdjustments);

		// when:
		assertEquals(one, one);
		assertNotEquals(one, null);
		assertNotEquals(one, new Object());
		assertEquals(one, three);
		assertNotEquals(one, two);
		// and:
		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(one.hashCode(), three.hashCode());
	}

	@Test
	public void legacyProviderWorks() throws IOException {
		given(din.readLong())
				.willReturn(-1L).willReturn(-2L)
				.willReturn(-1L).willReturn(-2L).willReturn(aAmount)
				.willReturn(-1L).willReturn(-2L).willReturn(bAmount)
				.willReturn(-1L).willReturn(-2L).willReturn(cAmount);
		given(din.readInt()).willReturn(3);
		given(idProvider.deserialize(din))
				.willReturn(aEntity)
				.willReturn(bEntity)
				.willReturn(cEntity);

		// when:
		var subjectRead = CurrencyAdjustments.LEGACY_PROVIDER.deserialize(din);

		// then:
		assertEquals(subject, subjectRead);
	}

	@Test
	public void viewWorks() {
		// expect:
		assertEquals(grpcAdjustments, subject.toGrpc());
	}

	@Test
	public void factoryWorks() {
		// expect:
		assertEquals(subject, CurrencyAdjustments.fromGrpc(grpcAdjustments));
	}

	@Test
	public void serializableDetWorks() {
		// expect;
		assertEquals(CurrencyAdjustments.MERKLE_VERSION, subject.getVersion());
		assertEquals(CurrencyAdjustments.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);

		given(in.readSerializableList(
						intThat(i -> i == CurrencyAdjustments.MAX_NUM_ADJUSTMENTS),
						booleanThat(Boolean.TRUE::equals),
						(Supplier<EntityId>)any())).willReturn(subject.accountIds);
		given(in.readLongArray(CurrencyAdjustments.MAX_NUM_ADJUSTMENTS)).willReturn(subject.hbars);

		// when:
		var readSubject = new CurrencyAdjustments();
		// and:
		readSubject.deserialize(in, CurrencyAdjustments.MERKLE_VERSION);

		// expect:
		assertEquals(readSubject, subject);
	}

	@Test
	public void serializeWorks() throws IOException {
		// setup:
		ArgumentCaptor idsCaptor = ArgumentCaptor.forClass(List.class);
		ArgumentCaptor amountsCaptor = ArgumentCaptor.forClass(long[].class);
		// and:
		var out = mock(SerializableDataOutputStream.class);

		// when:
		subject.serialize(out);

		// then:
		verify(out).writeSerializableList(
				(List<EntityId>)idsCaptor.capture(),
				booleanThat(Boolean.TRUE::equals),
				booleanThat(Boolean.TRUE::equals));
		verify(out).writeLongArray((long[])amountsCaptor.capture());
		// and:
		assertArrayEquals(subject.hbars, (long[])amountsCaptor.getValue());
		assertEquals(subject.accountIds, idsCaptor.getValue());
	}
}
