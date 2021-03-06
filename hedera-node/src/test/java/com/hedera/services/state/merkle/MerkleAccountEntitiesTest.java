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
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;

class MerkleAccountEntitiesTest {
	TokenID a = IdUtils.asToken("0.0.2");
	TokenID b = IdUtils.asToken("0.1.2");
	TokenID c = IdUtils.asToken("1.1.2");
	TokenID d = IdUtils.asToken("0.0.3");
	TokenID e = IdUtils.asToken("0.0.1");
	long[] initialIds = new long[] {
		2, 0, 0, 2, 1, 0, 2, 1, 1
	};

	MerkleAccountEntities subject;

	@BeforeEach
	private void setup() {
		subject = new MerkleAccountEntities(initialIds);
	}

	@Test
	void rejectsIndivisibleParts() {
		// expect:
		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> new MerkleAccountEntities(new long[MerkleAccountEntities.NUM_ID_PARTS + 1]));
	}

	@Test
	public void asIdsWorks() {
		// expect:
		assertEquals(
				List.of(a, b, c),
				subject.asTokenIds());
		// and when:
		subject = new MerkleAccountEntities();
		// then:
		assertSame(Collections.emptyList(), subject.asTokenIds());
	}

	@Test
	public void dissociateAllWorks() {
		// when:
		subject.dissociateAllTokens(Set.of(a, e));

		// then:
		assertArrayEquals(new long[] {2, 1, 0}, Arrays.copyOfRange(subject.getEntityIds(), 0, 3));
		// and:
		assertFalse(subject.includes(a));
	}

	@Test
	public void associateAllWorks() {
		// when:
		subject.associateAllTokens(Set.of(d, e));

		// then:
		assertArrayEquals(new long[] {1, 0, 0}, Arrays.copyOfRange(subject.getEntityIds(), 0, 3));
		// and:
		assertArrayEquals(new long[] {3, 0, 0}, Arrays.copyOfRange(subject.getEntityIds(), 12, 15));
	}

	@Test
	public void objectContractMet() {
		// given:
		var one = new MerkleAccountEntities();
		var two = new MerkleAccountEntities();
		two.associateAllTokens(Set.of(a, b, c));

		// then:
		assertNotEquals(one, null);
		assertNotEquals(one, new Object());
		assertEquals(subject, two);
		// and:
		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(subject.hashCode(), two.hashCode());
	}

	@Test
	public void merkleMethodsWork() {
		// expect;
		assertEquals(MerkleAccountEntities.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleAccountEntities.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
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
		inOrder.verify(out).writeLongArray(initialIds);
	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var defaultSubject = new MerkleAccountEntities();

		given(in.readLongArray(MerkleAccountEntities.MAX_CONCEIVABLE_ENTITY_ID_PARTS)).willReturn(initialIds);

		// when:
		defaultSubject.deserialize(in, MerkleAccountEntities.MERKLE_VERSION);

		// then:
		assertEquals(subject, defaultSubject);
	}

	@Test
	public void toStringWorks() {
		// expect:
		assertEquals(
				"MerkleAccountEntities{entities=[0.0.2, 0.1.2, 1.1.2]}",
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
}
