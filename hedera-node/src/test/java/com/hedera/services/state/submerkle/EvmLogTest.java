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
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;

class EvmLogTest {
	private static final byte[] data = "hgfedcba".getBytes();
	private static final byte[] otherData = "abcdefgh".getBytes();
	private static final byte[] bloom = "ijklmnopqrstuvwxyz".getBytes();
	private static final EntityNum aSourceNum = EntityNum.fromLong(3L);
	private static final EntityId aLoggerId = aSourceNum.toId().asEntityId();
	private static final List<byte[]> aTopics = List.of(
			"first000000000000000000000000000".getBytes(),
			"second00000000000000000000000000".getBytes(),
			"third000000000000000000000000000".getBytes());

	private DomainSerdes serdes;
	private EvmLog subject;

	@BeforeEach
	void setup() {
		serdes = mock(DomainSerdes.class);

		subject = new EvmLog(aLoggerId, bloom, aTopics, data);

		EvmLog.serdes = serdes;
	}

	@AfterEach
	public void cleanup() {
		EvmLog.serdes = new DomainSerdes();
	}

	@Test
	void convertsFromBesuAsExpected() {
		final var aSource = new Log(
				aSourceNum.toEvmAddress(),
				Bytes.wrap(data),
				aTopics.stream().map(bytes -> LogTopic.of(Bytes.wrap(bytes))).toList());
		final var aBloom = bloomFor(aSource);
		subject.setBloom(aBloom);

		final var converted = EvmLog.fromBesu(aSource);

		assertEquals(subject, converted);
	}

	@Test
	void convertsFromTwoBesuAsExpected() {
		final var aSource = new Log(
				aSourceNum.toEvmAddress(),
				Bytes.wrap(data),
				aTopics.stream().map(bytes -> LogTopic.of(Bytes.wrap(bytes))).toList());
		final var aBloom = bloomFor(aSource);
		final var bSourceNum = EntityNum.fromLong(666);
		final var bSource = new Log(
				bSourceNum.toEvmAddress(),
				Bytes.wrap(otherData),
				aTopics.stream().map(bytes -> LogTopic.of(Bytes.wrap(bytes))).toList());
		final var bBloom = bloomFor(bSource);

		final var expected = List.of(
				new EvmLog(aSourceNum.toId().asEntityId(), aBloom, aTopics, data),
				new EvmLog(bSourceNum.toId().asEntityId(), bBloom, aTopics, otherData));

		final var converted = EvmLog.fromBesu(List.of(aSource, bSource));

		assertEquals(expected, converted);
	}

	@Test
	void convertsEmptyLogs() {
		assertEquals(List.of(), EvmLog.fromBesu(Collections.emptyList()));
	}

	@Test
	void equalsSame() {
		final var sameButDifferent = subject;
		assertEquals(subject, sameButDifferent);
	}

	@Test
	void areSameTopicsBadScenarios() {
		List<byte[]> differentTopics = List.of("first".getBytes(), "second".getBytes());
		List<byte[]> sameButDifferentTopics = List.of("first".getBytes(), "second".getBytes(), "thirds".getBytes());

		EvmLog copy = new EvmLog(aLoggerId, bloom, differentTopics, data);
		EvmLog sameButDifferentCopy = new EvmLog(aLoggerId, bloom, sameButDifferentTopics, data);

		assertNotEquals(subject, copy);
		assertNotEquals(subject, sameButDifferentCopy);
	}

	@Test
	void toStringWorks() {
		assertEquals(
				"EvmLog{data=" + CommonUtils.hex(data) + ", " +
						"bloom=" + CommonUtils.hex(bloom) + ", " +
						"contractId=" + aLoggerId + ", " +
						"topics=" + aTopics.stream().map(CommonUtils::hex).collect(toList()) + "}",
				subject.toString()
		);
	}

	@Test
	void objectContractWorks() {
		final var one = subject;
		final var two = new EvmLog(aLoggerId, bloom, aTopics, otherData);
		final var three = new EvmLog(aLoggerId, bloom, aTopics, data);

		assertNotEquals(null, one);
		assertNotEquals(new Object(), one);
		assertNotEquals(one, two);
		assertEquals(one, three);

		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(one.hashCode(), three.hashCode());
	}

	@Test
	void beanWorks() {
		assertEquals(
				new EvmLog(
						subject.getContractId(),
						subject.getBloom(),
						subject.getTopics(),
						subject.getData()),
				subject);
	}

	@Test
	void serializableDetWorks() {
		assertEquals(EvmLog.MERKLE_VERSION, subject.getVersion());
		assertEquals(EvmLog.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	void serializeWorks() throws IOException {
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(serdes, out);

		subject.serialize(out);

		inOrder.verify(out).writeByteArray(argThat((byte[] bytes) -> Arrays.equals(bytes, data)));
		inOrder.verify(out).writeByteArray(argThat((byte[] bytes) -> Arrays.equals(bytes, bloom)));
		inOrder.verify(serdes).writeNullableSerializable(aLoggerId, out);
		inOrder.verify(out).writeInt(aTopics.size());
		inOrder.verify(out).writeByteArray(argThat((byte[] bytes) -> Arrays.equals(bytes, aTopics.get(0))));
		inOrder.verify(out).writeByteArray(argThat((byte[] bytes) -> Arrays.equals(bytes, aTopics.get(1))));
		inOrder.verify(out).writeByteArray(argThat((byte[] bytes) -> Arrays.equals(bytes, aTopics.get(2))));
	}

	@Test
	void deserializeWorks() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		final var readSubject = new EvmLog();
		given(in.readByteArray(EvmLog.MAX_BLOOM_BYTES)).willReturn(bloom);
		given(in.readByteArray(EvmLog.MAX_DATA_BYTES)).willReturn(data);
		given(serdes.readNullableSerializable(in)).willReturn(aLoggerId);
		given(in.readInt()).willReturn(aTopics.size());
		given(in.readByteArray(EvmLog.MAX_TOPIC_BYTES))
				.willReturn(aTopics.get(0))
				.willReturn(aTopics.get(1))
				.willReturn(aTopics.get(2));

		readSubject.deserialize(in, EvmLog.MERKLE_VERSION);

		assertEquals(subject, readSubject);
	}

	static byte[] bloomFor(final Log log) {
		return LogsBloomFilter.builder().insertLog(log).build().toArray();
	}
}
