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

import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hedera.services.store.models.Id;
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

import static com.hedera.test.utils.IdUtils.asToken;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;

class MerkleAccountTokensTest {
	TokenID a = asToken("0.0.2");
	TokenID b = asToken("0.1.2");
	TokenID c = asToken("1.1.2");

	private Id aId = new Id(0, 0, 2);
	private Id bId = new Id(0, 1, 2);
	private Id cId = new Id(1, 1, 2);
	private Id dId = new Id(0, 0, 3);
	private Id eId = new Id(0, 0, 1);

	private long[] initialIds = new long[] {
			2, 0, 0, 2, 1, 0, 2, 1, 1
	};

	MerkleAccountTokens subject;

	@BeforeEach
	private void setup() {
		subject = new MerkleAccountTokens(new CopyOnWriteIds(initialIds));
	}

	@Test
	void copiedSubjectBecomesImmutable() {
		// given:
		final var someSet = Set.of(asToken("1.2.3"));

		// when:
		subject.copy();

		// then:
		assertTrue(subject.isImmutable());
		Assertions.assertThrows(IllegalStateException.class, () -> subject.associateAll(someSet));
		Assertions.assertThrows(IllegalStateException.class, () -> subject.dissociateAll(someSet));
	}

	@Test
	void rejectsIndivisibleParts() {
		// expect:
		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> new MerkleAccountTokens(new CopyOnWriteIds(new long[MerkleAccountTokens.NUM_ID_PARTS + 1])));
	}

	@Test
	void asTokenIdsWorks() {
		// expect:
		assertEquals(
				List.of(a, b, c),
				subject.asTokenIds());
		// and when:
		subject = new MerkleAccountTokens();
		// then:
		assertEquals(Collections.emptyList(), subject.asTokenIds());
	}

	@Test
	void dissociateWorks() {
		// when:
		subject.dissociate(Set.of(aId, eId));

		// then:
		assertArrayEquals(new long[] { 2, 1, 0 }, Arrays.copyOfRange(subject.getRawIds(), 0, 3));
		// and:
		assertFalse(subject.includes(aId));
	}

	@Test
	void associateWorks() {
		// when:
		subject.associate(Set.of(dId, eId));

		// then:
		assertArrayEquals(new long[] { 1, 0, 0 }, Arrays.copyOfRange(subject.getRawIds(), 0, 3));
		// and:
		assertArrayEquals(new long[] { 3, 0, 0 }, Arrays.copyOfRange(subject.getRawIds(), 12, 15));
	}

	@Test
	void objectContractMet() {
		// given:
		var one = new MerkleAccountTokens();
		var two = new MerkleAccountTokens();
		two.associate(Set.of(aId, bId, cId));

		// then:
		assertNotEquals(one, null);
		assertNotEquals(one, new Object());
		assertEquals(subject, two);
		// and:
		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(subject.hashCode(), two.hashCode());
	}

	@Test
	void merkleMethodsWork() {
		// expect;
		assertEquals(MerkleAccountTokens.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleAccountTokens.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
	}

	@Test
	void serializeWorks() throws IOException {
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
	void deserializeWorks() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var defaultSubject = new MerkleAccountTokens();

		given(in.readLongArray(MerkleAccountTokens.MAX_CONCEIVABLE_TOKEN_ID_PARTS)).willReturn(initialIds);

		// when:
		defaultSubject.deserialize(in, MerkleAccountTokens.MERKLE_VERSION);

		// then:
		assertEquals(subject, defaultSubject);
	}

	@Test
	void toStringWorks() {
		// expect:
		assertEquals(
				"MerkleAccountTokens{tokens=[0.0.2, 0.1.2, 1.1.2]}",
				subject.toString());
	}

	@Test
	void copyWorks() {
		// when:
		var subjectCopy = subject.copy();

		// then:
		assertNotSame(subjectCopy, subject);
		assertEquals(subject, subjectCopy);
	}

	@Test
	void updateAssociationsWorks() {
		// setup:
		final var expectedUpdate = "[0.0.1, 0.0.3]";

		// given:
		final var newIds = new CopyOnWriteIds();
		newIds.addAllIds(Set.of(dId, eId));

		// when:
		subject.updateAssociationsFrom(newIds);

		// then:
		assertEquals(expectedUpdate, subject.readableTokenIds());
	}
}
