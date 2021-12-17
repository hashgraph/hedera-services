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

import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.booleanThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class TxnIdTest {
	private static final int nonce = 123;
	private final AccountID payer = IdUtils.asAccount("0.0.75231");
	private final EntityId fcPayer = EntityId.fromGrpcAccountId(payer);
	private final Timestamp validStart = Timestamp.newBuilder()
			.setSeconds(1_234_567L)
			.setNanos(89)
			.build();
	private final RichInstant fcValidStart = RichInstant.fromGrpc(validStart);

	private DomainSerdes serdes;
	private SerializableDataInputStream din;
	private SerializableDataOutputStream dout;

	TxnId subject;

	@Test
	void gettersWork() {
		subject = scheduledSubject();
		assertEquals(fcPayer, subject.getPayerAccount());
		assertEquals(fcValidStart, subject.getValidStart());
	}

	@Test
	void sameAndNullEqualsWork() {
		subject = scheduledSubject();
		final var same = subject;
		assertEquals(subject, same);
		assertNotEquals(null, subject);
	}

	@Test
	void serializeWorksForScheduled() throws IOException {
		// setup:
		subject = scheduledSubject();
		// and:
		dout = mock(SerializableDataOutputStream.class);
		serdes = mock(DomainSerdes.class);
		TxnId.serdes = serdes;

		// given:
		InOrder inOrder = Mockito.inOrder(serdes, dout);

		// when:
		subject.serialize(dout);

		// then:
		inOrder.verify(dout).writeSerializable(fcPayer, Boolean.TRUE);
		inOrder.verify(serdes).serializeTimestamp(fcValidStart, dout);
		inOrder.verify(dout, times(2)).writeBoolean(true);
		inOrder.verify(dout).writeInt(nonce);

		// cleanup:
		TxnId.serdes = new DomainSerdes();
	}

	@Test
	void serializeWorksForUserNonce() throws IOException {
		// setup:
		subject = scheduledSubjectUserNonce();
		// and:
		dout = mock(SerializableDataOutputStream.class);
		serdes = mock(DomainSerdes.class);
		TxnId.serdes = serdes;

		// given:
		InOrder inOrder = Mockito.inOrder(serdes, dout);

		// when:
		subject.serialize(dout);

		// then:
		inOrder.verify(dout).writeSerializable(fcPayer, Boolean.TRUE);
		inOrder.verify(serdes).serializeTimestamp(fcValidStart, dout);
		inOrder.verify(dout).writeBoolean(true);
		inOrder.verify(dout).writeBoolean(false);
		verifyNoMoreInteractions(dout);

		// cleanup:
		TxnId.serdes = new DomainSerdes();
	}

	@Test
	void v0130DeserializeWorks() throws IOException {
		// setup:
		subject = scheduledSubjectUserNonce();
		// and:
		din = mock(SerializableDataInputStream.class);
		serdes = mock(DomainSerdes.class);
		TxnId.serdes = serdes;

		given(din.readSerializable(booleanThat(Boolean.TRUE::equals), any(Supplier.class))).willReturn(fcPayer);
		given(serdes.deserializeTimestamp(din)).willReturn(fcValidStart);
		given(din.readBoolean()).willReturn(true);
		// and:
		var deserializedId = new TxnId();

		// when:
		deserializedId.deserialize(din, TxnId.RELEASE_0130_VERSION);

		// then:
		assertEquals(subject, deserializedId);
		// and:
		verify(din, times(1)).readBoolean();

		// cleanup:
		TxnId.serdes = new DomainSerdes();
	}

	@Test
	void v0210DeserializeWorksWithNonUserNonce() throws IOException {
		// setup:
		subject = scheduledSubject();
		// and:
		din = mock(SerializableDataInputStream.class);
		serdes = mock(DomainSerdes.class);
		TxnId.serdes = serdes;

		given(din.readSerializable(booleanThat(Boolean.TRUE::equals), any(Supplier.class))).willReturn(fcPayer);
		given(serdes.deserializeTimestamp(din)).willReturn(fcValidStart);
		given(din.readBoolean()).willReturn(true);
		given(din.readInt()).willReturn(nonce);
		// and:
		var deserializedId = new TxnId();

		// when:
		deserializedId.deserialize(din, TxnId.RELEASE_0210_VERSION);

		// then:
		assertEquals(subject, deserializedId);
		// and:
		verify(din, times(2)).readBoolean();
		verify(din).readInt();

		// cleanup:
		TxnId.serdes = new DomainSerdes();
	}

	@Test
	void v0210DeserializeWorksWithUserNonce() throws IOException {
		// setup:
		subject = scheduledSubjectUserNonce();
		// and:
		din = mock(SerializableDataInputStream.class);
		serdes = mock(DomainSerdes.class);
		TxnId.serdes = serdes;

		given(din.readSerializable(booleanThat(Boolean.TRUE::equals), any(Supplier.class))).willReturn(fcPayer);
		given(serdes.deserializeTimestamp(din)).willReturn(fcValidStart);
		given(din.readBoolean())
				.willReturn(true)
				.willReturn(false);
		// and:
		var deserializedId = new TxnId();

		// when:
		deserializedId.deserialize(din, TxnId.RELEASE_0210_VERSION);

		// then:
		assertEquals(subject, deserializedId);
		// and:
		verify(din, times(2)).readBoolean();
		verify(din, never()).readInt();

		// cleanup:
		TxnId.serdes = new DomainSerdes();
	}

	@Test
	void equalsWorks() {
		// given:
		subject = scheduledSubject();
		var subjectUserNonce = scheduledSubjectUserNonce();

		// expect:
		assertNotEquals(subject, unscheduledSubject());
		assertNotEquals(subject, subjectUserNonce);
	}

	@Test
	void hashCodeWorks() {
		// given:
		subject = scheduledSubject();

		// expect:
		assertNotEquals(subject.hashCode(), unscheduledSubject().hashCode());
	}

	@Test
	void toStringWorks() {
		// given:
		subject = scheduledSubject();
		// and:
		final var desired = "TxnId{payer=EntityId{shard=0, realm=0, num=75231}, validStart=RichInstant" +
				"{seconds=1234567, nanos=89}, scheduled=true, nonce=123}";

		// expect:
		assertEquals(desired, subject.toString());
	}

	@Test
	void toGrpcWorks() {
		// given:
		var subject = scheduledSubject();
		// and:
		var expected = base().setScheduled(true).build();

		// expect:
		assertEquals(expected, subject.toGrpc());
	}

	@Test
	void merkleWorks() {
		// given:
		var subject = new TxnId();

		// expect:
		assertEquals(TxnId.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertEquals(TxnId.RELEASE_0210_VERSION, subject.getVersion());
	}

	private TxnId unscheduledSubject() {
		return TxnId.fromGrpc(base().build());
	}

	private TxnId scheduledSubject() {
		return TxnId.fromGrpc(base()
				.setScheduled(true)
				.build());
	}

	private TxnId scheduledSubjectUserNonce() {
		return TxnId.fromGrpc(base()
				.setScheduled(true)
				.setNonce(TxnId.USER_TRANSACTION_NONCE)
				.build());
	}

	private TransactionID.Builder base() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setNonce(nonce)
				.setTransactionValidStart(validStart);
	}
}
