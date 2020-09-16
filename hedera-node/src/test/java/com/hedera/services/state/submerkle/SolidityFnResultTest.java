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

import com.google.protobuf.ByteString;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.serdes.IoReadingFunction;
import com.hedera.services.state.serdes.IoWritingConsumer;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.booleanThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.intThat;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;

@RunWith(JUnitPlatform.class)
class SolidityFnResultTest {
	long gasUsed = 1_234;
	byte[] result = "abcdefgh".getBytes();
	byte[] otherResult = "hgfedcba".getBytes();
	byte[] bloom = "ijklmnopqrstuvwxyz".getBytes();
	String error = "Oops!";
	EntityId contractId = new EntityId(1L, 2L, 3L);
	List<EntityId> createdContractIds = List.of(
			new EntityId(2L, 3L, 4L),
			new EntityId(3L, 4L, 5L));
	List<SolidityLog> logs = List.of(logFrom(0), logFrom(1));

	DomainSerdes serdes;
	DataInputStream din;
	EntityId.Provider idProvider;
	SolidityLog.Provider logProvider;
	SerializableDataInputStream in;

	SolidityFnResult subject;

	@BeforeEach
	public void setup() {
		din = mock(DataInputStream.class);
		in = mock(SerializableDataInputStream.class);
		serdes = mock(DomainSerdes.class);
		idProvider = mock(EntityId.Provider.class);
		logProvider = mock(SolidityLog.Provider.class);

		subject = new SolidityFnResult(
				contractId,
				result,
				error,
				bloom,
				gasUsed,
				logs,
				createdContractIds);

		SolidityFnResult.serdes = serdes;
		SolidityFnResult.legacyIdProvider = idProvider;
		SolidityFnResult.legacyLogProvider = logProvider;
	}

	@AfterEach
	public void cleanup() {
		SolidityFnResult.serdes = new DomainSerdes();
		SolidityFnResult.legacyIdProvider = EntityId.LEGACY_PROVIDER;
		SolidityFnResult.legacyLogProvider = SolidityLog.LEGACY_PROVIDER;
	}

	@Test
	public void objectContractWorks() {
		// given:
		var one = subject;
		var two = new SolidityFnResult(
				contractId,
				otherResult,
				error,
				bloom,
				gasUsed,
				logs,
				createdContractIds);
		var three = new SolidityFnResult(
				contractId,
				result,
				error,
				bloom,
				gasUsed,
				logs,
				createdContractIds);

		// then:
		assertNotEquals(one, null);
		assertNotEquals(one, new Object());
		assertNotEquals(two, one);
		assertEquals(three, one);
		assertEquals(one, one);
		// and:
		assertEquals(one.hashCode(), three.hashCode());
		assertNotEquals(one.hashCode(), two.hashCode());
	}

	@Test
	public void beanWorks() {
		// expect:
		assertEquals(
				new SolidityFnResult(
						subject.getContractId(),
						subject.getResult(),
						subject.getError(),
						subject.getBloom(),
						subject.getGasUsed(),
						subject.getLogs(),
						subject.getCreatedContractIds()),
				subject
		);
	}

	@Test
	public void toStringWorks() {
		// expect:
		assertEquals(
				"SolidityFnResult{" +
						"gasUsed=" + gasUsed + ", " +
						"bloom=" + Hex.encodeHexString(bloom) + ", " +
						"result=" + Hex.encodeHexString(result) + ", " +
						"error=" + error + ", " +
						"contractId=" + contractId + ", " +
						"createdContractIds=" + createdContractIds + ", " +
						"logs=" + logs + "}",
				subject.toString());
	}

	@Test
	public void factoryWorks() {
		// given:
		var grpc = ContractFunctionResult.newBuilder()
				.setGasUsed(gasUsed)
				.setContractCallResult(ByteString.copyFrom(result))
				.setBloom(ByteString.copyFrom(bloom))
				.setErrorMessage(error)
				.setContractID(contractId.toGrpcContractId())
				.addAllCreatedContractIDs(createdContractIds.stream().map(EntityId::toGrpcContractId).collect(toList()))
				.addAllLogInfo(logs.stream().map(SolidityLog::toGrpc).collect(toList()))
				.build();

		// expect:
		assertEquals(subject, SolidityFnResult.fromGrpc(grpc));
	}

	@Test
	public void viewWorks() {
		// setup:
		var expected = ContractFunctionResult.newBuilder()
				.setGasUsed(gasUsed)
				.setContractCallResult(ByteString.copyFrom(result))
				.setBloom(ByteString.copyFrom(bloom))
				.setErrorMessage(error)
				.setContractID(contractId.toGrpcContractId())
				.addAllCreatedContractIDs(createdContractIds.stream().map(EntityId::toGrpcContractId).collect(toList()))
				.addAllLogInfo(logs.stream().map(SolidityLog::toGrpc).collect(toList()))
				.build();

		// when:
		var actual = subject.toGrpc();

		// then:
		assertEquals(expected, actual);
	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var readSubject = new SolidityFnResult();
		// and:
		ArgumentCaptor<IoReadingFunction> captor = ArgumentCaptor.forClass(IoReadingFunction.class);

		given(in.readLong()).willReturn(gasUsed);
		given(in.readByteArray(SolidityLog.MAX_BLOOM_BYTES)).willReturn(bloom);
		given(in.readByteArray(SolidityFnResult.MAX_RESULT_BYTES)).willReturn(result);
		given(serdes.readNullableString(in, SolidityFnResult.MAX_ERROR_BYTES)).willReturn(error);
		given(serdes.readNullableSerializable(in)).willReturn(contractId);
		given(in.readSerializableList(
				intThat(i -> i == SolidityFnResult.MAX_LOGS),
				booleanThat(b -> b),
				any(Supplier.class))).willReturn(logs);
		given(in.readSerializableList(
				intThat(i -> i == SolidityFnResult.MAX_CREATED_IDS),
				booleanThat(b -> b),
				any(Supplier.class))).willReturn(createdContractIds);

		// when:
		readSubject.deserialize(in, SolidityFnResult.MERKLE_VERSION);

		// then:
		assertEquals(subject, readSubject);
	}

	@Test
	public void serializeWorks() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		// and:
		ArgumentCaptor<IoWritingConsumer> captor = ArgumentCaptor.forClass(IoWritingConsumer.class);
		InOrder inOrder = inOrder(serdes, out);

		// when:
		subject.serialize(out);

		// then:
		inOrder.verify(out).writeLong(gasUsed);
		inOrder.verify(out).writeByteArray(bloom);
		inOrder.verify(out).writeByteArray(result);
		inOrder.verify(serdes).writeNullableString(error, out);
		inOrder.verify(serdes).writeNullableSerializable(contractId, out);
		inOrder.verify(out).writeSerializableList(logs, true, true);
		inOrder.verify(out).writeSerializableList(createdContractIds, true, true);
	}

	@Test
	public void serializableDetWorks() {
		// expect;
		assertEquals(SolidityFnResult.MERKLE_VERSION, subject.getVersion());
		assertEquals(SolidityFnResult.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	public void legacyProviderWorks() throws IOException {
		// setup:
		var readCount = new AtomicInteger(0);

		given(din.readLong())
				.willReturn(3L)
				.willReturn(2L)
				.willReturn(gasUsed);
		given(din.readBoolean())
				.willReturn(true);
		given(din.readInt())
				.willReturn(result.length)
				.willReturn(error.length())
				.willReturn(bloom.length)
				.willReturn(logs.size())
				.willReturn(createdContractIds.size());
		given(logProvider.deserialize(din))
				.willReturn(logs.get(0))
				.willReturn(logs.get(1));
		given(idProvider.deserialize(din))
				.willReturn(contractId)
				.willReturn(createdContractIds.get(0))
				.willReturn(createdContractIds.get(1));

		willAnswer(invoke -> {
			byte[] arg = invoke.getArgument(0);
			int whichRead = readCount.getAndIncrement();
			if (whichRead == 0) {
				System.arraycopy(result, 0, arg, 0, result.length);
			} else if (whichRead == 1) {
				System.arraycopy(error.getBytes(), 0, arg, 0, error.length());
			} else {
				System.arraycopy(bloom, 0, arg, 0, bloom.length);
			}
			return null;
		}).given(din).readFully(any());

		// when:
		var readSubject = SolidityFnResult.LEGACY_PROVIDER.deserialize(din);

		// then:
		assertEquals(subject, readSubject);
	}

	public static SolidityLog logFrom(int s) {
		return new SolidityLog(
				contracts[s],
				blooms[s],
				List.of(topics[s], topics[s + 1 % 3]),
				data[s]);
	}

	static EntityId[] contracts = new EntityId[] {
			new EntityId(1L, 2L, 3L),
			new EntityId(2L, 3L, 4L),
			new EntityId(3L, 4L, 5L),
	};

	static byte[][] topics = new byte[][] {
			"alpha".getBytes(),
			"bravo".getBytes(),
			"charlie".getBytes(),
	};

	static byte[][] blooms = new byte[][] {
			"tulip".getBytes(),
			"lily".getBytes(),
			"cynthia".getBytes(),
	};

	static byte[][] data = new byte[][] {
			"one".getBytes(),
			"two".getBytes(),
			"three".getBytes(),
	};
}
