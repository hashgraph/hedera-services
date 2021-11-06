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

import com.swirlds.common.MutabilityException;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.verify;

class MerkleBlobMetaTest {
	private static final String path = "/a/b/c123";

	private MerkleBlobMeta subject;

	@BeforeEach
	private void setup() {
		subject = new MerkleBlobMeta(path);
	}

	@Test
	void merkleMethodsWork() {
		assertEquals(MerkleBlobMeta.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleBlobMeta.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
		assertFalse(subject.isImmutable());
	}

	@Test
	void nullEqualsWorks() {
		final var sameButDifferent = subject;
		assertNotEquals(null, subject);
		assertEquals(subject, sameButDifferent);
	}

	@Test
	void deserializeWorks() throws IOException {
		final var in = mock(SerializableDataInputStream.class);
		final var defaultSubject = new MerkleBlobMeta();
		given(in.readNormalisedString(MerkleBlobMeta.MAX_PATH_LEN)).willReturn(path);

		defaultSubject.deserialize(in, MerkleBlobMeta.MERKLE_VERSION);

		assertEquals(subject, defaultSubject);
	}

	@Test
	void serializeWorks() throws IOException {
		final var out = mock(SerializableDataOutputStream.class);

		subject.serialize(out);

		verify(out).writeNormalisedString(path);
	}

	@Test
	void objectContractMet() {
		final var one = new MerkleBlobMeta();
		final var two = new MerkleBlobMeta(path);
		final var three = new MerkleBlobMeta();

		three.setPath(path);

		assertNotEquals(null, one);
		assertNotEquals(two, one);
		assertEquals(two, three);

		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(two.hashCode(), three.hashCode());
	}

	@Test
	void toStringWorks() {
		assertEquals(
				"MerkleBlobMeta{path=" + path + "}",
				subject.toString());
	}

	@Test
	void copyWorks() {
		final var subjectCopy = subject.copy();

		assertNotSame(subject, subjectCopy);
		assertEquals(subject, subjectCopy);
		assertTrue(subject.isImmutable());
		assertFalse(subjectCopy.isImmutable());
	}

	@Test
	void deleteIsNoop() {
		assertDoesNotThrow(subject::release);
	}

	@Test
	void setPathWorks() {
		final var subjectCopy = subject.copy();
		assertEquals(path, subjectCopy.getPath());

		final var newPath = "/newPath";
		assertThrows(MutabilityException.class, () -> subject.setPath(newPath));

		subjectCopy.setPath(newPath);
		assertEquals(newPath, subjectCopy.getPath());
	}
}
