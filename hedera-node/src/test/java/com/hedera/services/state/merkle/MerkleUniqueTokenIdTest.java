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

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.views.internals.PermHashLong;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.hedera.services.state.merkle.internals.IdentityCodeUtils.MAX_NUM_ALLOWED;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MerkleUniqueTokenIdTest {
	private MerkleUniqueTokenId subject;

	private EntityId tokenId = MISSING_ENTITY_ID;
	private EntityId otherTokenId = MISSING_ENTITY_ID;
	private long serialNumber;
	private long otherSerialNumber;

	@BeforeEach
	void setup() {
		tokenId = new EntityId(0, 0, 3);
		otherTokenId = new EntityId(0, 0, 4);
		serialNumber = 1;
		otherSerialNumber = 2;

		subject = new MerkleUniqueTokenId(tokenId, serialNumber);
	}

	@Test
	void reconstructsExpectedFromIdentityCodeWithSmallNums() {
		// setup:
		long code = (3L << 32) | 4L;
		// and:
		final var expected = new MerkleUniqueTokenId(new EntityId(0, 0, 3), 4);

		// given:
		final var actual = MerkleUniqueTokenId.fromIdentityCode(code);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	void failsFastOnInvalidNums() {
		// setup:
		final var okEntityId = new EntityId(0, 0, 1);
		final var notOkEntityId = new EntityId(0, 0, MAX_NUM_ALLOWED + 1);

		// expect:
		assertThrows(
				IllegalArgumentException.class,
				() -> new MerkleUniqueTokenId(okEntityId, MAX_NUM_ALLOWED + 1));
		assertThrows(
				IllegalArgumentException.class,
				() -> new MerkleUniqueTokenId(notOkEntityId, 1));
	}

	@Test
	void reconstructsExpectedFromIdentityCodeWithLargeNums() {
		// setup:
		long code = (MAX_NUM_ALLOWED << 32) | MAX_NUM_ALLOWED;
		// and:
		final var expected = new MerkleUniqueTokenId(
				new EntityId(0, 0, MAX_NUM_ALLOWED),
				MAX_NUM_ALLOWED);

		// given:
		final var actual = MerkleUniqueTokenId.fromIdentityCode(code);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	void identityCodeWorks() {
		// expect:
		assertEquals(
				(3L << 32) | 1L,
				subject.identityCode());
	}

	@Test
	void equalsContractWorks() {
		// given
		var other = new MerkleUniqueTokenId(otherTokenId, serialNumber);
		var other2 = new MerkleUniqueTokenId(tokenId, otherSerialNumber);
		var identical = new MerkleUniqueTokenId(tokenId, serialNumber);

		// expect
		assertNotEquals(subject, other);
		assertNotEquals(subject, other2);
		assertEquals(subject, identical);
	}

	@Test
	void hashCodeWorks() {
		// given:
		var identical = new MerkleUniqueTokenId(tokenId, serialNumber);
		var other = new MerkleUniqueTokenId(otherTokenId, otherSerialNumber);

		// expect:
		assertNotEquals(subject.hashCode(), other.hashCode());
		assertEquals(subject.hashCode(), identical.hashCode());
	}

	@Test
	void toStringWorks() {
		// setup:
		final var desired = "MerkleUniqueTokenId{tokenId=0.0.3, serialNumber=1}";

		// expect:
		assertEquals(desired, subject.toString());
	}

	@Test
	void copyWorks() {
		// given:
		var copyNftId = subject.copy();
		var other = new Object();
		var dup = subject;

		// expect:
		assertEquals(subject, copyNftId);
		assertEquals(subject, dup);
		assertNotEquals(subject, other);
	}

	@Test
	void serializeWorks() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);

		// when:
		subject.serialize(out);

		// then:
		verify(out).writeLong(subject.identityCode());
	}

	@Test
	void deserializeWorks() throws IOException {
		// setup:
		SerializableDataInputStream in = mock(SerializableDataInputStream.class);

		given(in.readLong()).willReturn(subject.identityCode());

		// and:
		var read = new MerkleUniqueTokenId();

		// when:
		read.deserialize(in, MerkleUniqueTokenId.MERKLE_VERSION);

		// then:
		assertEquals(subject, read);
	}

	@Test
	void merkleMethodsWork() {
		// expect;
		assertEquals(MerkleUniqueTokenId.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleUniqueTokenId.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
	}

	@Test
	void fromNftIdWorks() {
		// given
		var expected = new MerkleUniqueTokenId(
				new EntityId(0, 0, 1),
				1
		);

		// expect:
		assertEquals(expected, PermHashLong.fromNftId(new NftId(0, 0, 1, 1)));
	}
}
