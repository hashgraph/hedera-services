//package com.hedera.services.state.submerkle;
//
///*-
// * ‌
// * Hedera Services Node
// * ​
// * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
// * ​
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// * ‍
// */
//
//import com.google.protobuf.ByteString;
//import com.hedera.services.state.serdes.DomainSerdes;
//import com.hederahashgraph.api.proto.java.ContractFunctionResult;
//import com.hederahashgraph.api.proto.java.ContractID;
//import com.hederahashgraph.api.proto.java.ContractStateChange;
//import com.hederahashgraph.api.proto.java.StorageChange;
//import com.swirlds.common.CommonUtils;
//import com.swirlds.common.io.SerializableDataInputStream;
//import com.swirlds.common.io.SerializableDataOutputStream;
//import org.apache.commons.lang3.tuple.Pair;
//import org.apache.tuweni.bytes.Bytes;
//import org.hyperledger.besu.datatypes.Address;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import java.io.IOException;
//import java.util.List;
//import java.util.Map;
//import java.util.function.Supplier;
//
//import static java.util.stream.Collectors.toList;
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertNotEquals;
//import static org.mockito.BDDMockito.any;
//import static org.mockito.BDDMockito.booleanThat;
//import static org.mockito.BDDMockito.given;
//import static org.mockito.BDDMockito.inOrder;
//import static org.mockito.BDDMockito.intThat;
//import static org.mockito.BDDMockito.mock;
//
//class SolidityFnResultTest {
//	private static final long gasUsed = 1_234;
//	private static final byte[] result = "abcdefgh".getBytes();
//	private static final byte[] otherResult = "hgfedcba".getBytes();
//	private static final byte[] bloom = "ijklmnopqrstuvwxyz".getBytes();
//	private static final String error = "Oops!";
//	private static final EntityId contractId = new EntityId(1L, 2L, 3L);
//	private static final List<EntityId> createdContractIds = List.of(
//			new EntityId(2L, 3L, 4L),
//			new EntityId(3L, 4L, 5L));
//	private final List<SolidityLog> logs = List.of(logFrom(0), logFrom(1));
//	private final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges =
//			Map.of(Address.fromHexString("0x6"),
//					Map.of(Bytes.of(7), Pair.of(Bytes.of(8), null)),
//					Address.fromHexString("0x9"),
//					Map.of(Bytes.of(10), Pair.of(Bytes.of(11), Bytes.of(12))));
//
//	private DomainSerdes serdes;
//	private SolidityFnResult subject;
//
//	@BeforeEach
//	void setup() {
//		serdes = mock(DomainSerdes.class);
//
//		subject = new SolidityFnResult(
//				contractId,
//				result,
//				error,
//				bloom,
//				gasUsed,
//				logs,
//				createdContractIds,
//				stateChanges);
//
//		SolidityFnResult.serdes = serdes;
//	}
//
//	@AfterEach
//	void cleanup() {
//		SolidityFnResult.serdes = new DomainSerdes();
//	}
//
//	@Test
//	void objectContractWorks() {
//		final var one = subject;
//		final var two = new SolidityFnResult(
//				contractId,
//				otherResult,
//				error,
//				bloom,
//				gasUsed,
//				logs,
//				createdContractIds,
//				stateChanges);
//		final var three = new SolidityFnResult(
//				contractId,
//				result,
//				error,
//				bloom,
//				gasUsed,
//				logs,
//				createdContractIds,
//				stateChanges);
//
//		assertNotEquals(null, one);
//		assertNotEquals(new Object(), one);
//		assertNotEquals(one, two);
//		assertEquals(one, three);
//
//		assertNotEquals(one.hashCode(), two.hashCode());
//		assertEquals(one.hashCode(), three.hashCode());
//	}
//
//	@Test
//	void beanWorks() {
//		assertEquals(
//				new SolidityFnResult(
//						subject.getContractId(),
//						subject.getResult(),
//						subject.getError(),
//						subject.getBloom(),
//						subject.getGasUsed(),
//						subject.getLogs(),
//						subject.getCreatedContractIds(),
//						subject.getStateChanges()),
//				subject
//		);
//	}
//
//	@Test
//	void toStringWorks() {
//		assertEquals(
//				"SolidityFnResult{" +
//						"gasUsed=" + gasUsed + ", " +
//						"bloom=" + CommonUtils.hex(bloom) + ", " +
//						"result=" + CommonUtils.hex(result) + ", " +
//						"error=" + error + ", " +
//						"contractId=" + contractId + ", " +
//						"createdContractIds=" + createdContractIds + ", " +
//						"logs=" + logs + "}",
//				subject.toString());
//	}
//
//	@Test
//	void factoryWorks() {
//		final var grpc = ContractFunctionResult.newBuilder()
//				.setGasUsed(gasUsed)
//				.setContractCallResult(ByteString.copyFrom(result))
//				.setBloom(ByteString.copyFrom(bloom))
//				.setErrorMessage(error)
//				.setContractID(contractId.toGrpcContractId())
//				.addAllCreatedContractIDs(createdContractIds.stream().map(EntityId::toGrpcContractId).collect(toList()))
//				.addAllLogInfo(logs.stream().map(SolidityLog::toGrpc).collect(toList()))
//				.build();
//
//		assertEquals(subject, SolidityFnResult.fromGrpc(grpc));
//	}
//
//	@Test
//	void viewWorks() {
//		final var expected = ContractFunctionResult.newBuilder()
//				.setGasUsed(gasUsed)
//				.setContractCallResult(ByteString.copyFrom(result))
//				.setBloom(ByteString.copyFrom(bloom))
//				.setErrorMessage(error)
//				.setContractID(contractId.toGrpcContractId())
//				.addAllCreatedContractIDs(createdContractIds.stream().map(EntityId::toGrpcContractId).collect(toList()))
//				.addAllLogInfo(logs.stream().map(SolidityLog::toGrpc).collect(toList()))
//				.addStateChanges(ContractStateChange.newBuilder()
//						.setContractID(ContractID.newBuilder().setContractNum(6).build())
//						.addStorageChanges(
//								StorageChange.newBuilder()
//										.setSlot(ByteString.copyFrom(new byte[] {7}))
//										.setValueRead(ByteString.copyFrom(new byte[] {8}))
//										.setReadOnly(true)
//										.build())
//						.build())
//				.addStateChanges(ContractStateChange.newBuilder()
//						.setContractID(ContractID.newBuilder().setContractNum(9).build())
//						.addStorageChanges(
//								StorageChange.newBuilder()
//										.setSlot(ByteString.copyFrom(new byte[] {10}))
//										.setValueRead(ByteString.copyFrom(new byte[] {11}))
//										.setValueWritten(ByteString.copyFrom(new byte[] {12}))
//										.build())
//						.build())
//				.build();
//
//		final var actual = subject.toGrpc();
//
//		assertEquals(expected, actual);
//	}
//
//	@Test
//	void deserializeWorks() throws IOException {
//		final var in = mock(SerializableDataInputStream.class);
//		final var readSubject = new SolidityFnResult();
//		given(in.readLong()).willReturn(gasUsed);
//		given(in.readByteArray(SolidityLog.MAX_BLOOM_BYTES)).willReturn(bloom);
//		given(in.readByteArray(SolidityFnResult.MAX_RESULT_BYTES)).willReturn(result);
//		given(serdes.readNullableString(in, SolidityFnResult.MAX_ERROR_BYTES)).willReturn(error);
//		given(serdes.readNullableSerializable(in)).willReturn(contractId);
//		given(in.readSerializableList(
//				intThat(i -> i == SolidityFnResult.MAX_LOGS),
//				booleanThat(b -> b),
//				any(Supplier.class))).willReturn(logs);
//		given(in.readSerializableList(
//				intThat(i -> i == SolidityFnResult.MAX_CREATED_IDS),
//				booleanThat(b -> b),
//				any(Supplier.class))).willReturn(createdContractIds);
//
//		readSubject.deserialize(in, SolidityFnResult.MERKLE_VERSION);
//
//		assertEquals(subject, readSubject);
//	}
//
//	@Test
//	void serializeWorks() throws IOException {
//		final var out = mock(SerializableDataOutputStream.class);
//		final var inOrder = inOrder(serdes, out);
//
//		subject.serialize(out);
//
//		inOrder.verify(out).writeLong(gasUsed);
//		inOrder.verify(out).writeByteArray(bloom);
//		inOrder.verify(out).writeByteArray(result);
//		inOrder.verify(serdes).writeNullableString(error, out);
//		inOrder.verify(serdes).writeNullableSerializable(contractId, out);
//		inOrder.verify(out).writeSerializableList(logs, true, true);
//		inOrder.verify(out).writeSerializableList(createdContractIds, true, true);
//	}
//
//	@Test
//	void serializableDetWorks() {
//		assertEquals(SolidityFnResult.MERKLE_VERSION, subject.getVersion());
//		assertEquals(SolidityFnResult.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
//	}
//
//	private static final SolidityLog logFrom(final int s) {
//		return new SolidityLog(
//				contracts[s],
//				blooms[s],
//				List.of(topics[s], topics[s + 1 % 3]),
//				data[s]);
//	}
//
//	private static final EntityId[] contracts = new EntityId[] {
//			new EntityId(1L, 2L, 3L),
//			new EntityId(2L, 3L, 4L),
//			new EntityId(3L, 4L, 5L),
//	};
//
//	private static final byte[][] topics = new byte[][] {
//			"alpha".getBytes(),
//			"bravo".getBytes(),
//			"charlie".getBytes(),
//	};
//
//	private static final byte[][] blooms = new byte[][] {
//			"tulip".getBytes(),
//			"lily".getBytes(),
//			"cynthia".getBytes(),
//	};
//
//	private static final byte[][] data = new byte[][] {
//			"one".getBytes(),
//			"two".getBytes(),
//			"three".getBytes(),
//	};
//}
