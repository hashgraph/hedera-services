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

import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.state.submerkle.EntityId.fromGrpcAccountId;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.booleanThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.intThat;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.inOrder;

class CurrencyAdjustmentsTest {
	private static final AccountID a = IdUtils.asAccount("0.0.13257");
	private static final AccountID b = IdUtils.asAccount("0.0.13258");
	private static final AccountID c = IdUtils.asAccount("0.0.13259");

	private static final long aAmount = 1L;
	private static final long bAmount = 2L;
	private static final long cAmount = -3L;

	private static final TransferList grpcAdjustments = TxnUtils.withAdjustments(a, aAmount, b, bAmount, c, cAmount);
	private static final TransferList otherGrpcAdjustments =
			TxnUtils.withAdjustments(a, aAmount * 2, b, bAmount * 2, c, cAmount * 2);
	private final AliasManager aliasManager = new AliasManager();

	private CurrencyAdjustments subject;

	@BeforeEach
	void setup() {
		subject = new CurrencyAdjustments();
		subject.accountIds = List.of(fromGrpcAccountId(a), fromGrpcAccountId(b), fromGrpcAccountId(c));
		subject.hbars = new long[] { aAmount, bAmount, cAmount };
	}

	@Test
	void equalsWork() {
		var expectedAmounts = new long[] { 1, 2, 3 };
		var expectedParties = List.of(EntityId.fromGrpcAccountId(IdUtils.asAccount("0.0.1")));

		final var sameButDifferent = subject;
		final var anotherSubject = new CurrencyAdjustments(expectedAmounts, expectedParties);
		assertNotEquals(subject, anotherSubject);
		assertEquals(subject, sameButDifferent);
		assertNotEquals(null, subject);
	}

	@Test
	void isEmptyWorks() {
		assertFalse(subject.isEmpty());
		assertTrue(new CurrencyAdjustments().isEmpty());
	}

	@Test
	void toStringWorks() {
		assertEquals(
				"CurrencyAdjustments{readable=" + "[0.0.13257 <- +1, 0.0.13258 <- +2, 0.0.13259 -> -3]" + "}",
				subject.toString());
	}

	@Test
	void objectContractWorks() {
		final var one = subject;
		final var two = CurrencyAdjustments.fromGrpc(otherGrpcAdjustments);
		final var three = CurrencyAdjustments.fromGrpc(grpcAdjustments);

		assertNotEquals(null, one);
		assertNotEquals(new Object(), one);
		assertEquals(one, three);
		assertNotEquals(one, two);

		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(one.hashCode(), three.hashCode());
	}

	@Test
	void viewWorks() {
		assertEquals(grpcAdjustments, subject.toGrpc());
	}

	@Test
	void factoryWorks() {
		assertEquals(subject, CurrencyAdjustments.fromGrpc(grpcAdjustments));
	}

	@Test
	void serializableDetWorks() {
		assertEquals(CurrencyAdjustments.MERKLE_VERSION, subject.getVersion());
		assertEquals(CurrencyAdjustments.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	void deserializeWorks() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		given(in.readSerializableList(
				intThat(i -> i == CurrencyAdjustments.MAX_NUM_ADJUSTMENTS),
				booleanThat(Boolean.TRUE::equals),
				(Supplier<EntityId>) any())).willReturn(subject.accountIds);
		given(in.readLongArray(CurrencyAdjustments.MAX_NUM_ADJUSTMENTS)).willReturn(subject.hbars);

		final var readSubject = new CurrencyAdjustments();
		readSubject.deserialize(in, CurrencyAdjustments.MERKLE_VERSION);

		assertEquals(readSubject, subject);
	}

	@Test
	void serializeWorks() throws IOException {
		final var idsCaptor = ArgumentCaptor.forClass(List.class);
		final var amountsCaptor = ArgumentCaptor.forClass(long[].class);
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(out);

		subject.serialize(out);

		inOrder.verify(out).writeSerializableList(
				(List<EntityId>) idsCaptor.capture(),
				booleanThat(Boolean.TRUE::equals),
				booleanThat(Boolean.TRUE::equals));
		inOrder.verify(out).writeLongArray(amountsCaptor.capture());

		assertArrayEquals(subject.hbars, amountsCaptor.getValue());
		assertEquals(subject.accountIds, idsCaptor.getValue());
	}
}
