package com.hedera.services.state.serdes;

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
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.test.utils.SerdeUtils;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.time.Instant;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.SerdeUtils.deOutcome;
import static com.hedera.test.utils.SerdeUtils.serOutcome;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.verifyNoInteractions;
import static org.mockito.BDDMockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.times;

public class DomainSerdesTest {
	private DomainSerdes subject = new DomainSerdes();

	@BeforeAll
	public static void setupAll() throws ConstructableRegistryException {
		/* Per Cody, this will be unnecessary at some point. */
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(ExpirableTxnRecord.class, ExpirableTxnRecord::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(EntityId.class, EntityId::new));
	}

	@Test
	void readsExpectedForNonNullableSerializable() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var data = new EntityId(1L, 2L, 3L);

		given(in.readBoolean()).willReturn(true);
		given(in.readSerializable()).willReturn(data);

		// when:
		var dataIn = subject.readNullableSerializable(in);

		// then:
		verify(in).readBoolean();
		assertEquals(data, dataIn);
	}

	@Test
	void readsNullForNullableSerializable() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		EntityId data;

		given(in.readBoolean()).willReturn(false);

		// when:
		data = subject.readNullableSerializable(in);

		// then:
		verify(in).readBoolean();
		verifyNoMoreInteractions(in);
		// and:
		assertNull(data);
	}

	@Test
	void writesFalseForNullString() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);

		// when:
		subject.writeNullableString(null, out);

		// then:
		verify(out).writeBoolean(false);
		verifyNoMoreInteractions(out);
	}

	@Test
	void writesForNonNullString() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);

		// given:
		var msg = "Hi!";

		// when:
		subject.writeNullableString(msg, out);

		// then:
		verify(out).writeBoolean(true);
		verify(out).writeNormalisedString(msg);
	}

	@Test
	void writesExpectedForNonNullInstant() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);

		// given:
		var at = new RichInstant(123L, 456);

		// when:
		subject.writeNullableInstant(at, out);

		// then:
		verify(out).writeBoolean(true);
		verify(out).writeLong(123L);
		verify(out).writeInt(456);
		verifyNoMoreInteractions(out);
	}

	@Test
	void writesFalseForNullInstant() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);

		// when:
		subject.writeNullableInstant(null, out);

		// then:
		verify(out).writeBoolean(false);
		verifyNoMoreInteractions(out);
	}

	@Test
	void writesFalseForNullWritable() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		// and:
		var writer = mock(IoWritingConsumer.class);

		// when:
		subject.writeNullable(null, out, (IoWritingConsumer<? extends Object>) writer);

		// then:
		verify(out).writeBoolean(false);
		verifyNoMoreInteractions(out);
		verifyNoInteractions(writer);
	}

	@Test
	void readsExpectedForNonNullString() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var msg = "Hi!";

		given(in.readBoolean()).willReturn(true);
		given(in.readNormalisedString(123)).willReturn(msg);

		// when:
		String data = subject.readNullableString(in, 123);

		// then:
		assertEquals(data, msg);
	}

	@Test
	void readsLegacyTimestamp() throws IOException {
		// setup:
		var in = mock(DataInputStream.class);
		// and:
		var expected = new RichInstant(123L, 456);

		given(in.readLong()).willReturn(1L).willReturn(2L).willReturn(123L);
		given(in.readInt()).willReturn(456);

		// when:
		var fromStream = subject.deserializeLegacyTimestamp(in);

		// then:
		verify(in, times(3)).readLong();
		verify(in).readInt();
		// and:
		assertEquals(expected, fromStream);
	}

	@Test
	void readsExpectedForNonNullInstant() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var expected = new RichInstant(123L, 456);

		given(in.readBoolean()).willReturn(true);
		given(in.readLong()).willReturn(123L);
		given(in.readInt()).willReturn(456);

		// when:
		RichInstant data = subject.readNullableInstant(in);

		// then:
		assertEquals(expected, data);
	}

	@Test
	void readsNullForNullInstant() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);

		given(in.readBoolean()).willReturn(false);

		// when:
		RichInstant data = subject.readNullableInstant(in);

		// then:
		verify(in).readBoolean();
		verifyNoMoreInteractions(in);
		// and:
		assertNull(data);
	}

	@Test
	void readsNullForNullString() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);

		given(in.readBoolean()).willReturn(false);

		// when:
		String data = subject.readNullableString(in, 123);

		// then:
		verify(in).readBoolean();
		verifyNoMoreInteractions(in);
		// and:
		assertNull(data);
	}

	@Test
	void readsNullForNullReadable() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var reader = mock(IoReadingFunction.class);

		given(in.readBoolean()).willReturn(false);

		// when:
		Object data = subject.readNullable(in, reader);

		// then:
		verify(in).readBoolean();
		verifyNoMoreInteractions(in);
		// and:
		assertNull(data);
	}

	@Test
	void readsExpectedForNonNullReadable() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var reader = (IoReadingFunction<EntityId>) mock(IoReadingFunction.class);
		var data = new EntityId(1L, 2L, 3L);

		given(in.readBoolean()).willReturn(true);
		given(reader.read(in)).willReturn(data);

		// when:
		EntityId dataIn = subject.readNullable(in, reader);

		// then:
		verify(in).readBoolean();
		assertEquals(data, dataIn);
	}

	@Test
	void writesForNonNullWritable() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		// and:
		var writer = mock(IoWritingConsumer.class);

		// given:
		var data = new EntityId(1L, 2L, 3L);

		// when:
		subject.writeNullable(data, out, writer);

		// then:
		verify(out).writeBoolean(true);
		verify(writer).write(data, out);
	}

	@Test
	void writesFalseForNullSerializable() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);

		// when:
		subject.writeNullableSerializable(null, out);

		// then:
		verify(out).writeBoolean(false);
		verifyNoMoreInteractions(out);
	}

	@Test
	void writesExpectedForNonNullSerializable() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		// and:
		var data = new EntityId(1L, 2L, 3L);

		// when:
		subject.writeNullableSerializable(data, out);

		// then:
		verify(out).writeBoolean(true);
		verify(out).writeSerializable(data, true);
	}


	@Test
	void idSerdesWork() throws Exception {
		// given:
		EntityId idIn = new EntityId(1, 2, 3);

		// when:
		byte[] repr = serOutcome(out -> subject.serializeId(idIn, out));
		// and:
		EntityId idOut = deOutcome(in -> subject.deserializeId(in), repr);

		// then:
		assertEquals(idIn, idOut);
	}

	@Test
	void keySerdesWork() throws Exception {
		// given:
		JKey keyIn = COMPLEX_KEY_ACCOUNT_KT.asJKey();

		// when:
		byte[] repr = serOutcome(out -> subject.serializeKey(keyIn, out));
		// and:
		JKey keyOut = deOutcome(in -> subject.deserializeKey(in), repr);

		// then:
		assertEquals(JKey.mapJKey(keyIn), JKey.mapJKey(keyOut));
	}

	public static ExpirableTxnRecord recordOne() {
		return ExpirableTxnRecord.newBuilder()
				.setReceipt(TxnReceipt.newBuilder()
						.setStatus(INVALID_ACCOUNT_ID.name())
						.setAccountId(EntityId.fromGrpcAccountId(asAccount("0.0.3"))).build())
				.setTxnId(TxnId.fromGrpc(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder().setSeconds(9_999_999_999L)).build()))
				.setMemo("Alpha bravo charlie")
				.setConsensusTime(RichInstant.fromJava(Instant.ofEpochSecond(9_999_999_999L)))
				.setFee(555L)
				.setHbarAdjustments(
						CurrencyAdjustments.fromChanges(new long[] { -4L, 2L, 2L }, new long[] { 2L, 1001L, 1002L }))
				.setContractCallResult(SerdeUtils.fromGrpc(ContractFunctionResult.newBuilder()
						.setContractID(asContract("1.2.3"))
						.setErrorMessage("Couldn't figure it out!")
						.setGasUsed(55L)
						.addLogInfo(ContractLoginfo.newBuilder()
								.setData(ByteString.copyFrom("Nonsense!".getBytes()))).build()))
				.build();
	}

	public static ExpirableTxnRecord recordTwo() {
		return ExpirableTxnRecord.newBuilder()
				.setReceipt(TxnReceipt.newBuilder()
						.setStatus(INVALID_CONTRACT_ID.name())
						.setAccountId(EntityId.fromGrpcAccountId(asAccount("0.0.4"))).build())
				.setTxnId(TxnId.fromGrpc(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder().setSeconds(7_777_777_777L)).build()))
				.setMemo("Alpha bravo charlie")
				.setConsensusTime(RichInstant.fromJava(Instant.ofEpochSecond(7_777_777_777L)))
				.setFee(556L)
				.setHbarAdjustments(
						CurrencyAdjustments.fromChanges(new long[] { -6L, 3L, 3L }, new long[] { 2L, 1001L, 1002L }))
				.setContractCallResult(SerdeUtils.fromGrpc(ContractFunctionResult.newBuilder()
						.setContractID(asContract("4.3.2"))
						.setErrorMessage("Couldn't figure it out immediately!")
						.setGasUsed(55L)
						.addLogInfo(ContractLoginfo.newBuilder()
								.setData(ByteString.copyFrom("Nonsensical!".getBytes())))
						.setGas(1_000_000L)
						.setFunctionParameters(ByteString.copyFrom("Sensible!".getBytes())).build()))
				.build();
	}
}
