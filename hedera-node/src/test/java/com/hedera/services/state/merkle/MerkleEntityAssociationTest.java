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

import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;

class MerkleEntityAssociationTest {
	private static final long fromShard = 13;
	private static final long fromRealm = 25;
	private static final long fromNum = 7;
	private static final long toShard = 31;
	private static final long toRealm = 52;
	private static final long toNum = 0;

	MerkleEntityAssociation subject;

	@BeforeEach
	private void setup() {
		subject = new MerkleEntityAssociation(
				fromShard, fromRealm, fromNum,
				toShard, toRealm, toNum);
	}

	@Test
	void gettersWork() {
		assertEquals(fromNum, subject.getFromNum());
		assertEquals(toNum, subject.getToNum());
	}

	@Test
	void toAbbrevStringWorks() {
		assertEquals("13.25.7 <-> 31.52.0", subject.toAbbrevString());
	}

	@Test
	void objectContractMet() {
		final var one = new MerkleEntityAssociation();
		final var two = new MerkleEntityAssociation(toShard, toRealm, toNum, fromShard, fromRealm, fromNum);
		final var three = new MerkleEntityAssociation(fromShard, fromRealm, fromNum, toShard, toRealm, toNum);

		assertNotEquals(null, one);
		assertNotEquals(new Object(), one);
		assertNotEquals(two, one);
		assertEquals(subject, three);

		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(subject.hashCode(), three.hashCode());
	}

	@Test
	void factoryWorks() {
		assertEquals(
				EntityNumPair.fromLongs(7, 8),
				MerkleEntityAssociation.fromAccountTokenRel(Pair.of(asAccount("0.0.7"), asToken("0.0.8"))));
	}

	@Test
	void merkleMethodsWork() {
		assertEquals(MerkleEntityAssociation.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleEntityAssociation.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
	}

	@Test
	void serializeWorks() throws IOException {
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(out);

		subject.serialize(out);

		inOrder.verify(out).writeLong(fromShard);
		inOrder.verify(out).writeLong(fromRealm);
		inOrder.verify(out).writeLong(fromNum);
		inOrder.verify(out).writeLong(toShard);
		inOrder.verify(out).writeLong(toRealm);
		inOrder.verify(out).writeLong(toNum);
	}

	@Test
	void deserializeWorks() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		final var defaultSubject = new MerkleEntityAssociation();
		given(in.readLong())
				.willReturn(fromShard).willReturn(fromRealm).willReturn(fromNum)
				.willReturn(toShard).willReturn(toRealm).willReturn(toNum);

		defaultSubject.deserialize(in, MerkleEntityAssociation.MERKLE_VERSION);

		assertEquals(subject, defaultSubject);
	}

	@Test
	void toStringWorks() {
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
	void equalsWorksWithExtremes() {
		final var sameButDifferent = subject;
		assertEquals(subject, sameButDifferent);
		assertNotEquals(null, subject);
		assertNotEquals(subject, new Object());
	}

	@Test
	void copyWorks() {
		final var subjectCopy = subject.copy();

		assertNotSame(subjectCopy, subject);
		assertEquals(subject, subjectCopy);
		assertTrue(subject.isImmutable());
	}

	@Test
	void deleteIsNoop() {
		assertDoesNotThrow(subject::release);
	}
}
