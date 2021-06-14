package com.hedera.services.state.merkle;

/*
 * Hedera Services Node
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

public class MerkleUniqueTokenIdTest {

	private MerkleUniqueTokenId subject;

	private EntityId tokenId = MISSING_ENTITY_ID;
	private EntityId otherTokenId = MISSING_ENTITY_ID;
	private int serialNumber;
	private int otherSerialNumber;

	@BeforeEach
	public void setup() {
		tokenId = new EntityId(1, 2, 3);
		otherTokenId = new EntityId(1, 2, 4);
		serialNumber = 1;
		otherSerialNumber = 2;

		subject = new MerkleUniqueTokenId(tokenId, serialNumber);
	}

	@AfterEach
	public void cleanup() {
	}

	@Test
	public void equalsContractWorks() {
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
	public void hashCodeWorks() {
		// given:
		var identical = new MerkleUniqueTokenId(tokenId, serialNumber);
		var other = new MerkleUniqueTokenId(otherTokenId, otherSerialNumber);

		// expect:
		assertNotEquals(subject.hashCode(), other.hashCode());
		assertEquals(subject.hashCode(), identical.hashCode());
	}

	@Test
	public void toStringWorks() {
		// given:
		assertEquals("MerkleUniqueTokenId{" +
						"tokenId=" + tokenId + ", " +
						"serialNumber=" + serialNumber + "}",
				subject.toString());
	}

	@Test
	public void copyWorks() {
		// given:
		var copyNftId = subject.copy();
		var other = new Object();

		// expect:
		assertNotSame(copyNftId, subject);
		assertEquals(subject, copyNftId);
		assertEquals(subject, subject);
		assertNotEquals(subject, other);
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
		inOrder.verify(out).writeSerializable(tokenId, true);
		inOrder.verify(out).writeLong(serialNumber);
	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		SerializableDataInputStream in = mock(SerializableDataInputStream.class);

		given(in.readSerializable()).willReturn(tokenId);
		given(in.readInt()).willReturn(serialNumber);

		// and:
		var read = new MerkleUniqueTokenId();

		// when:
		read.deserialize(in, MerkleUniqueTokenId.MERKLE_VERSION);

		// then:
		assertEquals(subject, read);
	}

	@Test
	public void merkleMethodsWork() {
		// expect;
		assertEquals(MerkleUniqueTokenId.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleUniqueTokenId.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
	}
}