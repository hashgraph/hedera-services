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
import com.google.protobuf.BytesValue;
	import com.hedera.services.state.serdes.DomainSerdes;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractStateChange;
import com.hederahashgraph.api.proto.java.StorageChange;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.booleanThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.intThat;
import static org.mockito.BDDMockito.mock;

class SolidityFnResultTest {
	private static final long gasUsed = 1_234;
	private static final Address realContract = Address.fromHexString("0x57Ab8B73F33AFB95B876CFB209C9a97F72D00000");
	private static final byte[] contract = realContract.toArray();
	private static final byte[] slot = "slot".getBytes();
	private static final byte[] left = "left".getBytes();
	private static final byte[] right = "right".getBytes();
	private static final byte[] result = "abcdefgh".getBytes();
	private static final byte[] otherResult = "hgfedcba".getBytes();
	private static final byte[] bloom = "ijklmnopqrstuvwxyz".getBytes();
	private static final byte[] evmAddress = Address.BLAKE2B_F_COMPRESSION.toArray();
	private static final String error = "Oops!";
	private static final EntityId contractId = new EntityId(1L, 2L, 3L);
	private static final List<EntityId> createdContractIds = List.of(
			new EntityId(2L, 3L, 4L),
			new EntityId(3L, 4L, 5L));
	private final List<SolidityLog> logs = List.of(logFrom(0), logFrom(1));
	private final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges =
			Map.of(Address.fromHexString("0x6"),
					Map.of(Bytes.of(7), Pair.of(Bytes.of(8), null)),
					Address.fromHexString("0x9"),
					Map.of(Bytes.of(10), Pair.of(Bytes.of(11), Bytes.of(12))));
	private static Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> specialStateChanges =
			Map.of(realContract,
					Map.of(Bytes.of(slot), Pair.of(Bytes.of(left), Bytes.of(right))));
	private static final int RELEASE_022_VERSION = 3;

	private DomainSerdes serdes;
	private SolidityFnResult subject;

	@BeforeEach
	void setup() {
		serdes = mock(DomainSerdes.class);

		subject = new SolidityFnResult(
				contractId,
				result,
				error,
				bloom,
				gasUsed,
				logs,
				createdContractIds,
				evmAddress,
				stateChanges);

		SolidityFnResult.serdes = serdes;
	}

	@AfterEach
	void cleanup() {
		SolidityFnResult.serdes = new DomainSerdes();
	}

	@Test
	void gettersWork() {
		assertEquals(contractId, subject.getContractId());
		assertEquals(result, subject.getResult());
		assertEquals(error, subject.getError());
		assertEquals(bloom, subject.getBloom());
		assertEquals(gasUsed, subject.getGasUsed());
		assertEquals(logs, subject.getLogs());
		assertEquals(createdContractIds, subject.getCreatedContractIds());
		assertEquals(stateChanges, subject.getStateChanges());
		assertEquals(0x2055c5c03ff84eb4L, subject.getClassId());
		assertEquals(2, subject.getVersion());
	}

	@Test
	void objectContractWorks() {
		final var one = subject;
		final var two = new SolidityFnResult(
				contractId,
				otherResult,
				error,
				bloom,
				gasUsed,
				logs,
				createdContractIds,
				evmAddress,
				stateChanges);
		final var three = new SolidityFnResult(
				contractId,
				result,
				error,
				bloom,
				gasUsed,
				logs,
				createdContractIds,
				evmAddress,
				stateChanges);
		final var four = new SolidityFnResult(
				contractId,
				result,
				error,
				bloom,
				gasUsed,
				logs,
				createdContractIds,
				Address.ZERO.toArray(),
				stateChanges);

		assertNotEquals(null, one);
		assertNotEquals(new Object(), one);
		assertNotEquals(one, two);
		assertNotEquals(one, four);
		assertEquals(one, three);

		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(one.hashCode(), three.hashCode());
	}

	@Test
	void beanWorks() {
		assertEquals(
				new SolidityFnResult(
						subject.getContractId(),
						subject.getResult(),
						subject.getError(),
						subject.getBloom(),
						subject.getGasUsed(),
						subject.getLogs(),
						subject.getCreatedContractIds(),
						subject.getEvmAddress(),
						subject.getStateChanges()),
				subject
		);
	}

	@Test
	void toStringWorks() {
		assertEquals(
				"SolidityFnResult{" +
						"gasUsed=" + gasUsed + ", " +
						"bloom=" + CommonUtils.hex(bloom) + ", " +
						"result=" + CommonUtils.hex(result) + ", " +
						"error=" + error + ", " +
						"contractId=" + contractId + ", " +
						"createdContractIds=" + createdContractIds + ", " +
						"logs=" + logs + ", evmAddress=0000000000000000000000000000000000000009}",
				subject.toString());
	}

	@Test
	void nullEqualsWork() {
		assertEquals(subject, subject);
		assertNotEquals(null, subject);
		assertNotEquals(1, subject);
	}

	@Test
	void factoryWorks() {
		subject = new SolidityFnResult(
				contractId,
				result,
				error,
				bloom,
				gasUsed,
				logs,
				createdContractIds,
				evmAddress,
				specialStateChanges);

		final var grpc = ContractFunctionResult.newBuilder()
				.setGasUsed(gasUsed)
				.setContractCallResult(ByteString.copyFrom(result))
				.setBloom(ByteString.copyFrom(bloom))
				.setErrorMessage(error)
				.setContractID(contractId.toGrpcContractId())
				.addAllCreatedContractIDs(createdContractIds.stream().map(EntityId::toGrpcContractId).collect(toList()))
				.addAllLogInfo(logs.stream().map(SolidityLog::toGrpc).collect(toList()))
				.addStateChanges(ContractStateChange.newBuilder()
						.addStorageChanges(StorageChange.newBuilder()
								.setSlot(ByteString.copyFrom(slot))
								.setValueRead(ByteString.copyFrom(left))
								.setValueWritten(BytesValue.newBuilder().setValue(ByteString.copyFrom(right)).build())
								.build())
						.build())
				.setEvmAddress(BytesValue.newBuilder().setValue(ByteString.copyFrom(evmAddress)))
				.build();

		assertEquals(subject, SolidityFnResult.fromGrpc(grpc));
	}

	@Test
	void viewWorks() {
		final var actual = subject.toGrpc();
		final var expected = ContractFunctionResult.newBuilder()
				.setGasUsed(gasUsed)
				.setContractCallResult(ByteString.copyFrom(result))
				.setBloom(ByteString.copyFrom(bloom))
				.setErrorMessage(error)
				.setContractID(contractId.toGrpcContractId())
				.addAllCreatedContractIDs(createdContractIds.stream().map(EntityId::toGrpcContractId).collect(toList()))
				.addAllLogInfo(logs.stream().map(SolidityLog::toGrpc).collect(toList()))
				.addStateChanges(actual.getStateChanges(0))
				.addStateChanges(actual.getStateChanges(1))
				.setEvmAddress(BytesValue.newBuilder().setValue(ByteString.copyFrom(evmAddress)))
				.build();

		assertEquals(expected, actual);
	}

	@Test
	void deserializeWorksPre0230() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		subject.setEvmAddress(new byte[0]);
		final var readSubject = new SolidityFnResult();
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

		readSubject.deserialize(in, SolidityFnResult.PRE_RELEASE_0230_VERSION);

		assertEquals(subject, readSubject);
	}

	@Test
	void deserializeWorksPost0230() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		final var readSubject = new SolidityFnResult();
		given(in.readLong()).willReturn(gasUsed);
		given(in.readByteArray(SolidityLog.MAX_BLOOM_BYTES)).willReturn(bloom);
		given(in.readByteArray(SolidityFnResult.MAX_RESULT_BYTES)).willReturn(result);
		given(in.readByteArray(SolidityFnResult.MAX_ADDRESS_BYTES)).willReturn(evmAddress);
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

		readSubject.deserialize(in, SolidityFnResult.RELEASE_0230_VERSION);

		assertEquals(subject, readSubject);
	}

	@Test
	void deserializeVersion22Works() throws IOException {
		subject = new SolidityFnResult(
				contractId,
				result,
				error,
				bloom,
				gasUsed,
				logs,
				createdContractIds,
				evmAddress,
				specialStateChanges
		);

		final var in = mock(SerializableDataInputStream.class);
		final var readSubject = new SolidityFnResult();
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
		given(in.readInt()).willReturn(1);
		given(in.readInt()).willReturn(1);
		given(in.readByteArray(32))
				.willReturn(contract)
				.willReturn(slot)
				.willReturn(left)
				.willReturn(right);
		given(in.readBoolean()).willReturn(true);

		readSubject.deserialize(in, RELEASE_022_VERSION);

		subject.setEvmAddress(null);
		assertEquals(subject, readSubject);
	}

	@Test
	void serializeWorks() throws IOException {
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(serdes, out);

		subject.serialize(out);

		inOrder.verify(out).writeLong(gasUsed);
		inOrder.verify(out).writeByteArray(bloom);
		inOrder.verify(out).writeByteArray(result);
		inOrder.verify(serdes).writeNullableString(error, out);
		inOrder.verify(serdes).writeNullableSerializable(contractId, out);
		inOrder.verify(out).writeSerializableList(logs, true, true);
		inOrder.verify(out).writeSerializableList(createdContractIds, true, true);
		inOrder.verify(out).writeByteArray(evmAddress);
	}

	@Test
	void serializableDetWorks() {
		assertEquals(SolidityFnResult.MERKLE_VERSION, subject.getVersion());
		assertEquals(SolidityFnResult.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	private static final SolidityLog logFrom(final int s) {
		return new SolidityLog(
				contracts[s],
				blooms[s],
				List.of(topics[s], topics[s + 1 % 3]),
				data[s]);
	}

	private static final EntityId[] contracts = new EntityId[] {
			new EntityId(1L, 2L, 3L),
			new EntityId(2L, 3L, 4L),
			new EntityId(3L, 4L, 5L),
	};

	private static final byte[][] topics = new byte[][] {
			"alpha".getBytes(),
			"bravo".getBytes(),
			"charlie".getBytes(),
	};

	private static final byte[][] blooms = new byte[][] {
			"tulip".getBytes(),
			"lily".getBytes(),
			"cynthia".getBytes(),
	};

	private static final byte[][] data = new byte[][] {
			"one".getBytes(),
			"two".getBytes(),
			"three".getBytes(),
	};
}
