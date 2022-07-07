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
import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.hedera.test.utils.IdUtils.asModelId;
import static com.hedera.test.utils.IdUtils.asToken;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.verify;

class MerkleAccountTokensTest {
	private static final TokenID a = asToken("0.0.2");
	private static final TokenID b = asToken("0.1.2");
	private static final TokenID c = asToken("1.1.2");
	private static final TokenID d = asToken("0.0.3");
	private static final TokenID e = asToken("0.0.1");
	private static final long[] initialIds = new long[] { 2, 0, 0, 2, 1, 0, 2, 1, 1 };

	private static final Id aId = new Id(0, 0, 2);
	private static final Id dId = new Id(0, 0, 3);
	private static final Id eId = new Id(0, 0, 1);

	private MerkleAccountTokens subject;

	@BeforeEach
	private void setup() {
		subject = new MerkleAccountTokens(new CopyOnWriteIds(initialIds));
	}

	@Test
	void copiedSubjectBecomesImmutable() {
		final var someSet = Set.of(asToken("1.2.3"));
		final var someModelSet = Set.of(aId);
		final var someIds = subject.getIds();

		final var subjectCopy = subject.copy();

		assertTrue(subject.isImmutable());
		Assertions.assertThrows(MutabilityException.class, () -> subject.associateAll(someSet));
		Assertions.assertThrows(MutabilityException.class, () -> subject.associate(someModelSet));
		Assertions.assertThrows(MutabilityException.class, () -> subject.dissociate(someModelSet));
		Assertions.assertThrows(MutabilityException.class, () -> subject.shareTokensOf(subjectCopy));
		Assertions.assertThrows(MutabilityException.class, () -> subject.updateAssociationsFrom(someIds));
	}

	@Test
	void nonMerkleCopiedSubjectNotImmutable() {
		final var someSet = Set.of(asToken("1.2.3"));

		final var subjectCopy = subject.tmpNonMerkleCopy();

		assertFalse(subject.isImmutable());
		Assertions.assertDoesNotThrow(() -> subject.associateAll(someSet));
		Assertions.assertDoesNotThrow(() -> subject.shareTokensOf(subjectCopy));
	}

	@Test
	void shareTokensUsesStructuralSharing() {
		final var other = new MerkleAccountTokens();

		other.shareTokensOf(subject);

		assertSame(subject.getRawIds(), other.getRawIds());
	}

	@Test
	void associationsWorks() {
		assertEquals(3, subject.numAssociations());
		assertTrue(subject.includes(asToken("0.0.2")));
	}

	@Test
	void associateDissociate() {
		subject.associate(Set.of(asModelId("0.0.123")));

		assertTrue(subject.includes(asToken("0.0.123")));

		subject.dissociate(Set.of(asModelId("0.0.123")));

		assertFalse(subject.includes(asToken("0.0.123")));
	}

	@Test
	void nullEqualsWorks() {
		final var sameButDifferent = subject;
		assertNotEquals(null, subject);
		assertEquals(subject, sameButDifferent);
	}

	@Test
	void rejectsIndivisibleParts() {
		final var idLength = new long[MerkleAccountTokens.NUM_ID_PARTS + 1];
		Assertions.assertThrows(IllegalArgumentException.class, () -> new CopyOnWriteIds(idLength));
	}

	@Test
	void asTokenIdsWorks() {
		assertEquals(
				List.of(a, b, c),
				subject.asTokenIds());

		subject = new MerkleAccountTokens();

		assertEquals(Collections.emptyList(), subject.asTokenIds());
	}

	@Test
	void associateAllWorks() {
		subject.associateAll(Set.of(d, e));

		assertArrayEquals(new long[] { 1, 0, 0 }, Arrays.copyOfRange(subject.getRawIds(), 0, 3));
		assertArrayEquals(new long[] { 3, 0, 0 }, Arrays.copyOfRange(subject.getRawIds(), 12, 15));
	}

	@Test
	void objectContractMet() {
		final var one = new MerkleAccountTokens();
		final var two = new MerkleAccountTokens();
		two.associateAll(Set.of(a, b, c));

		assertNotEquals(null, one);
		assertNotEquals(new Object(), one);
		assertEquals(subject, two);

		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(subject.hashCode(), two.hashCode());
	}

	@Test
	void merkleMethodsWork() {
		assertEquals(MerkleAccountTokens.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleAccountTokens.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
	}

	@Test
	void serializeWorks() throws IOException {
		final var out = mock(SerializableDataOutputStream.class);

		subject.serialize(out);

		verify(out).writeLongArray(initialIds);
	}

	@Test
	void deserializeWorks() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		final var defaultSubject = new MerkleAccountTokens();
		given(in.readLongArray(MerkleAccountTokens.MAX_CONCEIVABLE_TOKEN_ID_PARTS)).willReturn(initialIds);

		defaultSubject.deserialize(in, MerkleAccountTokens.MERKLE_VERSION);

		assertEquals(subject, defaultSubject);
	}

	@Test
	void toStringWorks() {
		assertEquals(
				"MerkleAccountTokens{tokens=[0.0.2, 0.1.2, 1.1.2]}",
				subject.toString());
	}

	@Test
	void copyWorks() {
		final var subjectCopy = subject.copy();

		assertNotSame(subjectCopy, subject);
		assertEquals(subject, subjectCopy);
	}

	@Test
	void updateAssociationsWorks() {
		final var expectedUpdate = "[0.0.1, 0.0.3]";
		final var newIds = new CopyOnWriteIds();
		newIds.addAllIds(Set.of(dId, eId));

		subject.updateAssociationsFrom(newIds);

		assertEquals(expectedUpdate, subject.readableTokenIds());
	}
}
