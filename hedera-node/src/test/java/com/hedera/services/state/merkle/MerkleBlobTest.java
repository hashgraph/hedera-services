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

import com.hedera.services.state.merkle.internals.BlobKey;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;

class MerkleBlobTest {
	private final BlobKey key = new BlobKey(BlobKey.BlobType.FILE_DATA, 2);
	private final BlobKey otherKey = new BlobKey(BlobKey.BlobType.FILE_DATA, 1);
	private static final byte[] data = "abcdefghijklmnopqrstuvwxyz".getBytes();
	private static final byte[] newData = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes();

	private MerkleBlob subject;

	@BeforeEach
	void setup() {
		subject = new MerkleBlob(data);
		subject.setKey(key);
	}

	@Test
	void keyedContractMet() {
		// expect:
		assertEquals(key, subject.getKey());
	}

	@Test
	void vadilateDataSetWhenNonEmpty() {
		subject.setData(newData);

		assertEquals(newData, subject.getData());
	}

	@Test
	void vadilateDataSetWhenEmpty() {
		subject = new MerkleBlob();

		subject.setData(newData);
		assertEquals(newData, subject.getData());
	}


	@Test
	void getDataWorksWithStuff() {
		assertArrayEquals(data, subject.getData());
	}

	@Test
	void getDataReturnsNullWithNoStuff() {
		assertArrayEquals(null, new MerkleBlob().getData());
	}

	@Test
	void merkleMethodsWork() {
		assertEquals(MerkleBlob.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleBlob.CLASS_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
	}

	@Test
	void serializeWorks() throws IOException {
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(out);

		subject.serialize(out);

		inOrder.verify(out).writeInt(0);
		inOrder.verify(out).writeLong(2);
		inOrder.verify(out).writeByteArray(data);
	}


	@Test
	void deserializeWorks() throws IOException {
		final var in = mock(SerializableDataInputStream.class);

		final var defaultSubject = new MerkleBlob();

		given(in.readInt()).willReturn(0);
		given(in.readLong()).willReturn(2L);
		given(in.readByteArray(Integer.MAX_VALUE)).willReturn(data);

		defaultSubject.deserialize(in, MerkleBlob.MERKLE_VERSION);

		assertEquals(key, defaultSubject.getKey());
		assertEquals(data, defaultSubject.getData());
	}

	@Test
	void toStringWorks() {
		assertEquals(
				"MerkleBlob{data=[97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, " +
						"114, 115, 116, 117, 118, 119, 120, 121, 122], blobKey=BlobKey[type=FILE_DATA, entityNum=2]}",
				subject.toString());
	}

	@Test
	void objectContractMet() {
		final var one = new MerkleBlob();
		final var two = new MerkleBlob(data);
		final var three = new MerkleBlob(data);
		final var twoRef = two;

		final var equalsForcedCallResult = one.equals(null);
		assertFalse(equalsForcedCallResult);
		assertNotEquals(one, new Object());
		assertNotEquals(two, one);
		assertEquals(two, twoRef);
		assertEquals(two, three);

		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(two.hashCode(), three.hashCode());

		two.setKey(key);
		three.setKey(otherKey);
		assertNotEquals(two, three);
	}

	@Test
	void copyWorks() {
		final var subjectCopy = subject.copy();

		assertNotSame(subject, subjectCopy);
		assertEquals(subject.getData(), subjectCopy.getData());
	}
}
