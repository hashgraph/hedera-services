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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.inOrder;

class MerkleBlobMetaTest {
	String path = "/a/b/c123";

	MerkleBlobMeta subject;

	@BeforeEach
	private void setup() {
		subject = new MerkleBlobMeta(path);
	}

	@Test
	public void merkleMethodsWork() {
		// expect;
		assertEquals(MerkleBlobMeta.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleBlobMeta.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var defaultSubject = new MerkleBlobMeta();

		given(in.readNormalisedString(MerkleBlobMeta.MAX_PATH_LEN)).willReturn(path);

		// when:
		defaultSubject.deserialize(in, MerkleBlobMeta.MERKLE_VERSION);

		// then:
		assertEquals(subject, defaultSubject);
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
		inOrder.verify(out).writeNormalisedString(path);
	}

	@Test
	public void legacyProviderWorks() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);

		given(in.readLong()).willReturn(0l).willReturn(1l);
		given(in.readNormalisedString(MerkleBlobMeta.MAX_PATH_LEN)).willReturn(path);

		// when:
		var deSubject = (MerkleBlobMeta)(new MerkleBlobMeta.Provider().deserialize(in));

		// then:
		assertEquals(deSubject, subject);
	}

	@Test
	public void objectContractMet() {
		// given:
		var one = new MerkleBlobMeta();
		var two = new MerkleBlobMeta(path);
		var three = new MerkleBlobMeta();

		// when:
		three.setPath(path);

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
	public void toStringWorks() {
		// expect:
		assertEquals(
				"MerkleBlobMeta{path=" + path + "}",
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
