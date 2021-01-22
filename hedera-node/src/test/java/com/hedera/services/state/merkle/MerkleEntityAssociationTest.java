package com.hedera.services.state.merkle;

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

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;

class MerkleEntityAssociationTest {
	long fromShard = 13;
	long fromRealm = 25;
	long fromNum = 7;
	long toShard = 31;
	long toRealm = 52;
	long toNum = 0;

	MerkleEntityAssociation subject;

	@BeforeEach
	private void setup() {
		subject = new MerkleEntityAssociation(
				fromShard, fromRealm, fromNum,
				toShard, toRealm, toNum);
	}

	@Test
	public void toAbbrevStringWorks() {
		// expect:
		assertEquals("13.25.7 <-> 31.52.0", subject.toAbbrevString());
	}

	@Test
	public void objectContractMet() {
		// given:
		var one = new MerkleEntityAssociation();
		var two = new MerkleEntityAssociation(toShard, toRealm, toNum, fromShard, fromRealm, fromNum);
		var three = new MerkleEntityAssociation(fromShard, fromRealm, fromNum, toShard, toRealm, toNum);

		// then:
		assertNotEquals(one, null);
		assertNotEquals(one, new Object());
		assertNotEquals(two, one);
		assertEquals(subject, three);
		// and:
		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(subject.hashCode(), three.hashCode());
	}

	@Test
	public void factoryWorks() {
		// expect:
		assertEquals(
				subject,
				MerkleEntityAssociation.fromAccountTokenRel(asAccount("13.25.7"), asToken("31.52.0")));
	}

	@Test
	public void merkleMethodsWork() {
		// expect;
		assertEquals(MerkleEntityAssociation.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleEntityAssociation.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
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
		inOrder.verify(out).writeLong(fromShard);
		inOrder.verify(out).writeLong(fromRealm);
		inOrder.verify(out).writeLong(fromNum);
		inOrder.verify(out).writeLong(toShard);
		inOrder.verify(out).writeLong(toRealm);
		inOrder.verify(out).writeLong(toNum);
	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var defaultSubject = new MerkleEntityAssociation();

		given(in.readLong())
				.willReturn(fromShard).willReturn(fromRealm).willReturn(fromNum)
				.willReturn(toShard).willReturn(toRealm).willReturn(toNum);

		// when:
		defaultSubject.deserialize(in, MerkleEntityAssociation.MERKLE_VERSION);

		// then:
		assertEquals(subject, defaultSubject);
	}

	@Test
	public void toStringWorks() {
		// expect:
		assertEquals(
				"MerkleEntityAssociation{fromShard=" + fromShard
						+ ", fromRealm=" + fromRealm
						+ ", fromNum=" + fromNum
						+ ", toShard=" + toShard
						+ ", toRealm=" + toRealm
						+ ", toNum=" + toNum
						+ "}",
				subject.toString());
	}

	@Test
	public void copyWorks() {
		// when:
		var subjectCopy = subject.copy();

		// then:
		assertNotSame(subjectCopy, subject);
		assertEquals(subject, subjectCopy);
	}

	@Test
	public void deleteIsNoop() {
		// expect:
		assertDoesNotThrow(subject::release);
	}
}
