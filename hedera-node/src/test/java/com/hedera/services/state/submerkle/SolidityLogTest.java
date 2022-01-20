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
import com.swirlds.common.CommonUtils;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;

class SolidityLogTest {
	private static final byte[] data = "hgfedcba".getBytes();
	private static final byte[] otherData = "abcdefgh".getBytes();
	private static final byte[] bloom = "ijklmnopqrstuvwxyz".getBytes();
	private static final EntityId contractId = new EntityId(1L, 2L, 3L);
	private static final List<byte[]> topics = List.of("first".getBytes(), "second".getBytes(), "third".getBytes());

	private DomainSerdes serdes;
	private SolidityLog subject;

	@BeforeEach
	void setup() {
		serdes = mock(DomainSerdes.class);

		subject = new SolidityLog(contractId, bloom, topics, data);

		SolidityLog.serdes = serdes;
	}

	@AfterEach
	public void cleanup() {
		SolidityLog.serdes = new DomainSerdes();
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

		SolidityLog copy = new SolidityLog(contractId, bloom, differentTopics, data);
		SolidityLog sameButDifferentCopy = new SolidityLog(contractId, bloom, sameButDifferentTopics, data);

		assertNotEquals(subject, copy);
		assertNotEquals(subject, sameButDifferentCopy);
	}

	@Test
	void toStringWorks() {
		assertEquals(
				"SolidityLog{data=" + CommonUtils.hex(data) + ", " +
						"bloom=" + CommonUtils.hex(bloom) + ", " +
						"contractId=" + contractId + ", " +
						"topics=" + topics.stream().map(CommonUtils::hex).collect(toList()) + "}",
				subject.toString()
		);
	}

	@Test
	void objectContractWorks() {
		final var one = subject;
		final var two = new SolidityLog(contractId, bloom, topics, otherData);
		final var three = new SolidityLog(contractId, bloom, topics, data);

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
				new SolidityLog(
						subject.getContractId(),
						subject.getBloom(),
						subject.getTopics(),
						subject.getData()),
				subject);
	}

	@Test
	void serializableDetWorks() {
		assertEquals(SolidityLog.MERKLE_VERSION, subject.getVersion());
		assertEquals(SolidityLog.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	void serializeWorks() throws IOException {
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(serdes, out);

		subject.serialize(out);

		inOrder.verify(out).writeByteArray(argThat((byte[] bytes) -> Arrays.equals(bytes, data)));
		inOrder.verify(out).writeByteArray(argThat((byte[] bytes) -> Arrays.equals(bytes, bloom)));
		inOrder.verify(serdes).writeNullableSerializable(contractId, out);
		inOrder.verify(out).writeInt(topics.size());
		inOrder.verify(out).writeByteArray(argThat((byte[] bytes) -> Arrays.equals(bytes, topics.get(0))));
		inOrder.verify(out).writeByteArray(argThat((byte[] bytes) -> Arrays.equals(bytes, topics.get(1))));
		inOrder.verify(out).writeByteArray(argThat((byte[] bytes) -> Arrays.equals(bytes, topics.get(2))));
	}

	@Test
	void deserializeWorks() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		final var readSubject = new SolidityLog();
		given(in.readByteArray(SolidityLog.MAX_BLOOM_BYTES)).willReturn(bloom);
		given(in.readByteArray(SolidityLog.MAX_DATA_BYTES)).willReturn(data);
		given(serdes.readNullableSerializable(in)).willReturn(contractId);
		given(in.readInt()).willReturn(topics.size());
		given(in.readByteArray(SolidityLog.MAX_TOPIC_BYTES))
				.willReturn(topics.get(0))
				.willReturn(topics.get(1))
				.willReturn(topics.get(2));

		readSubject.deserialize(in, SolidityLog.MERKLE_VERSION);

		assertEquals(subject, readSubject);
	}
}
