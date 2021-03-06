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

import com.google.protobuf.ByteString;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.booleanThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
class TxnIdTest {
	private AccountID payer = IdUtils.asAccount("0.0.75231");
	private EntityId fcPayer = EntityId.fromGrpcAccount(payer);
	private Timestamp validStart = Timestamp.newBuilder()
			.setSeconds(1_234_567L)
			.setNanos(89)
			.build();
	private ByteString nonce = ByteString.copyFrom("THIS_IS_NEW".getBytes());
	private RichInstant fcValidStart = RichInstant.fromGrpc(validStart);

	DomainSerdes serdes;
	SerializableDataInputStream din;
	SerializableDataOutputStream dout;

	TxnId subject;

	@Test
	void serializeWorksForScheduledNonce() throws IOException {
		// setup:
		subject = scheduledSubjectWithNonce();
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
		inOrder.verify(dout).writeByteArray(argThat((byte[] bytes) -> Arrays.equals(bytes, nonce.toByteArray())));

		// cleanup:
		TxnId.serdes = new DomainSerdes();
	}

	@Test
	void serializeWorksForScheduledNoNonce() throws IOException {
		// setup:
		subject = scheduledSubjectWithoutNonce();
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
		inOrder.verify(dout, never()).writeByteArray(any());

		// cleanup:
		TxnId.serdes = new DomainSerdes();
	}

	@Test
	public void preV0120DeserializeWorks() throws IOException {
		// setup:
		subject = unscheduledSubjectNoNonce();
		// and:
		din = mock(SerializableDataInputStream.class);
		serdes = mock(DomainSerdes.class);
		TxnId.serdes = serdes;

		given(din.readSerializable(booleanThat(Boolean.TRUE::equals), any(Supplier.class))).willReturn(fcPayer);
		given(serdes.deserializeTimestamp(din)).willReturn(fcValidStart);
		// and:
		var deserializedId = new TxnId();

		// when:
		deserializedId.deserialize(din, TxnId.PRE_RELEASE_0120_VERSION);

		// then:
		assertEquals(subject, deserializedId);
		verify(din, never()).readBoolean();
		verify(din, never()).readByteArray(TxnId.MAX_CONCEIVABLE_NONCE_BYTES);

		// cleanup:
		TxnId.serdes = new DomainSerdes();
	}

	@Test
	public void v0120DeserializeWorksWithoutNonce() throws IOException {
		// setup:
		subject = scheduledSubjectWithoutNonce();
		// and:
		din = mock(SerializableDataInputStream.class);
		serdes = mock(DomainSerdes.class);
		TxnId.serdes = serdes;

		given(din.readSerializable(booleanThat(Boolean.TRUE::equals), any(Supplier.class))).willReturn(fcPayer);
		given(serdes.deserializeTimestamp(din)).willReturn(fcValidStart);
		given(din.readBoolean()).willReturn(true).willReturn(false);
		// and:
		var deserializedId = new TxnId();

		// when:
		deserializedId.deserialize(din, TxnId.RELEASE_0120_VERSION);

		// then:
		assertEquals(subject, deserializedId);
		// and:
		verify(din, times(2)).readBoolean();
		verify(din, never()).readByteArray(anyInt());

		// cleanup:
		TxnId.serdes = new DomainSerdes();
	}

	@Test
	public void v0120DeserializeWorksWithNonce() throws IOException {
		// setup:
		subject = scheduledSubjectWithNonce();
		// and:
		din = mock(SerializableDataInputStream.class);
		serdes = mock(DomainSerdes.class);
		TxnId.serdes = serdes;

		given(din.readSerializable(booleanThat(Boolean.TRUE::equals), any(Supplier.class))).willReturn(fcPayer);
		given(serdes.deserializeTimestamp(din)).willReturn(fcValidStart);
		given(din.readBoolean()).willReturn(true).willReturn(true);
		given(din.readByteArray(TxnId.MAX_CONCEIVABLE_NONCE_BYTES)).willReturn(nonce.toByteArray());
		// and:
		var deserializedId = new TxnId();

		// when:
		deserializedId.deserialize(din, TxnId.RELEASE_0120_VERSION);

		// then:
		assertEquals(subject, deserializedId);
		// and:
		verify(din, times(2)).readBoolean();

		// cleanup:
		TxnId.serdes = new DomainSerdes();
	}

	@Test
	public void equalsWorks() {
		// given:
		subject = scheduledSubjectWithNonce();

		// expect:
		assertNotEquals(subject, unscheduledSubjectWithNonce());
		// and:
		assertNotEquals(subject, scheduledSubjectWithoutNonce());
	}

	@Test
	public void hashCodeWorks() {
		// given:
		subject = scheduledSubjectWithNonce();

		// expect:
		assertNotEquals(subject.hashCode(), unscheduledSubjectWithNonce().hashCode());
		// and:
		assertNotEquals(subject.hashCode(), scheduledSubjectWithoutNonce().hashCode());
	}

	@Test
	public void toStringWorks() {
		// given:
		subject = scheduledSubjectWithNonce();
		// and:
		String expRepr = "TxnId{payer=EntityId{shard=0, realm=0, num=75231}, " +
				"validStart=RichInstant{seconds=1234567, nanos=89}, " +
				"scheduled=true, " +
				"nonce=" + Hex.encodeHexString(nonce.toByteArray()) + "}";

		// expect:
		assertEquals(expRepr, subject.toString());
	}

	@Test
	public void toGrpcWorks() {
		// given:
		var subject = scheduledSubjectWithNonce();
		// and:
		var expected = base().setScheduled(true).setNonce(nonce).build();

		// expect:
		assertEquals(expected, subject.toGrpc());
	}

	@Test
	public void merkleWorks() {
		// given:
		var subject = new TxnId();

		// expect:
		assertEquals(TxnId.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertEquals(TxnId.RELEASE_0120_VERSION, subject.getVersion());
	}

	private TxnId unscheduledSubjectNoNonce() {
		return TxnId.fromGrpc(base().build());
	}

	private TxnId scheduledSubjectWithoutNonce() {
		return TxnId.fromGrpc(base()
				.setScheduled(true)
				.build());
	}

	private TxnId scheduledSubjectWithNonce() {
		return TxnId.fromGrpc(base()
				.setScheduled(true)
				.setNonce(nonce)
				.build());
	}

	private TxnId unscheduledSubjectWithNonce() {
		return TxnId.fromGrpc(base()
				.setNonce(nonce)
				.build());
	}

	private TransactionID.Builder base() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(validStart);
	}
}
