package com.hedera.services.state.merkle;

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

import com.hedera.test.utils.IdUtils;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.DataInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;

class MerkleEntityIdTest {
	long shard = 13;
	long realm = 25;
	long num = 7;

	MerkleEntityId subject;

	@BeforeEach
	private void setup() {
		subject = new MerkleEntityId(shard, realm, num);
	}

	@Test
	public void toAbbrevStringWorks() {
		// expect:
		assertEquals("13.25.7", subject.toAbbrevString());
	}

	@Test
	public void toAccountIdWorks() {
		// expect:
		assertEquals(IdUtils.asAccount("13.25.7"), subject.toAccountId());
	}

	@Test
	public void objectContractMet() {
		// given:
		var one = new MerkleEntityId();
		var two = new MerkleEntityId(1, 2, 3);
		var three = new MerkleEntityId();

		// when:
		three.setShard(1);
		three.setRealm(2);
		three.setNum(3);

		// then:
		assertNotEquals(null, one);
		assertNotEquals(two, one);
		assertEquals(one, one);
		assertEquals(two, three);
		// and:
		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(two.hashCode(), three.hashCode());
	}

	@Test
	public void merkleMethodsWork() {
		// expect;
		assertEquals(MerkleEntityId.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleEntityId.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
	}

	@Test
	public void serializeWorks() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		// and:
		InOrder inOrder = inOrder(out);

		// when:
		subject.serialize(out);

		// then:
		inOrder.verify(out).writeLong(shard);
		inOrder.verify(out).writeLong(realm);
		inOrder.verify(out).writeLong(num);
	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var defaultSubject = new MerkleEntityId();

		given(in.readLong()).willReturn(shard).willReturn(realm).willReturn(num);

		// when:
		defaultSubject.deserialize(in, MerkleEntityId.MERKLE_VERSION);

		// then:
		assertEquals(subject, defaultSubject);
	}

	@Test
	public void toStringWorks() {
		// expect:
		assertEquals(
				"MerkleEntityId{shard=" + shard + ", realm=" + realm + ", entity=" + num + "}",
				subject.toString());
	}

	@Test
	public void copyWorks() {
		// when:
		var subjectCopy = subject.copy();

		// then:
		assertTrue(subjectCopy != subject);
		assertEquals(subject, subjectCopy);
	}

	@Test
	public void deleteIsNoop() {
		// expect:
		assertDoesNotThrow(subject::release);
	}
}
