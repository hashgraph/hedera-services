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

import com.swirlds.blob.BinaryObject;
import com.swirlds.blob.BinaryObjectStore;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
class MerkleOptionalBlobTest {
	byte[] stuff = "abcdefghijklmnopqrstuvwxyz".getBytes();
	String readableStuffDelegate = "<me>";
	static final Hash stuffDelegateHash = new Hash(new byte[] {
			(byte)0xf0, (byte)0xe1, (byte)0xd2, (byte)0xc3,
			(byte)0xf4, (byte)0xe5, (byte)0xd6, (byte)0xc7,
			(byte)0xf8, (byte)0xe9, (byte)0xda, (byte)0xcb,
			(byte)0xfc, (byte)0xed, (byte)0xde, (byte)0xcf,
			(byte)0xf0, (byte)0xe1, (byte)0xd2, (byte)0xc3,
			(byte)0xf4, (byte)0xe5, (byte)0xd6, (byte)0xc7,
			(byte)0xf8, (byte)0xe9, (byte)0xda, (byte)0xcb,
			(byte)0xfc, (byte)0xed, (byte)0xde, (byte)0xcf,
			(byte)0xf0, (byte)0xe1, (byte)0xd2, (byte)0xc3,
			(byte)0xf4, (byte)0xe5, (byte)0xd6, (byte)0xc7,
			(byte)0xf8, (byte)0xe9, (byte)0xda, (byte)0xcb,
			(byte)0xfc, (byte)0xed, (byte)0xde, (byte)0xcf,
	});

	BinaryObjectStore blobStore;
	BinaryObject stuffDelegate;
	BinaryObject newDelegate;

	MerkleOptionalBlob subject;

	@BeforeEach
	public void setup() {
		newDelegate = mock(BinaryObject.class);
		stuffDelegate = mock(BinaryObject.class);
		given(stuffDelegate.toString()).willReturn(readableStuffDelegate);
		given(stuffDelegate.getHash()).willReturn(stuffDelegateHash);
		blobStore = mock(BinaryObjectStore.class);
		given(blobStore.put(argThat((byte[] bytes) -> Arrays.equals(bytes, stuff)))).willReturn(stuffDelegate);
		given(blobStore.get(stuffDelegate)).willReturn(stuff);

		MerkleOptionalBlob.blobSupplier = () -> newDelegate;
		MerkleOptionalBlob.blobStoreSupplier = () -> blobStore;

		subject = new MerkleOptionalBlob(stuff);
	}

	@AfterEach
	public void cleanup() {
		MerkleOptionalBlob.blobSupplier = BinaryObject::new;
		MerkleOptionalBlob.blobStoreSupplier = BinaryObjectStore::getInstance;
	}

	@Test
	public void getDataWorksWithStuff() {
		// expect:
		assertArrayEquals(stuff, subject.getData());
	}

	@Test
	public void getDataWorksWithNoStuff() {
		// expect:
		assertArrayEquals(MerkleOptionalBlob.NO_DATA, new MerkleOptionalBlob().getData());
	}

	@Test
	public void emptyHashAsExpected() {
		// given:
		var defaultSubject = new MerkleOptionalBlob();

		// expect;
		assertEquals(MerkleOptionalBlob.MISSING_DELEGATE_HASH, defaultSubject.getHash());
	}

	@Test
	public void stuffHashDelegates() {
		// expect;
		assertEquals(stuffDelegateHash, subject.getHash());
	}

	@Test
	public void merkleMethodsWork() {
		// expect;
		assertEquals(MerkleOptionalBlob.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleOptionalBlob.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
		assertThrows(UnsupportedOperationException.class, () -> subject.setHash(null));
		assertDoesNotThrow(() -> subject.serializeAbbreviated(null));
	}

	@Test
	public void deserializeWorksWithEmpty() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var defaultSubject = new MerkleOptionalBlob();

		given(in.readBoolean()).willReturn(false);

		// when:
		defaultSubject.deserialize(in, MerkleOptionalBlob.MERKLE_VERSION);

		// then:
		verify(newDelegate, never()).deserialize(in, MerkleOptionalBlob.MERKLE_VERSION);
	}

	@Test
	public void serializeWorksWithEmpty() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		// and:
		var defaultSubject = new MerkleOptionalBlob();

		// when:
		defaultSubject.serialize(out);

		// then:
		verify(out).writeBoolean(false);
	}

	@Test
	public void serializeWorksWithDelegate() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		// and:
		InOrder inOrder = inOrder(out, stuffDelegate);

		// when:
		subject.serialize(out);

		// then:
		inOrder.verify(out).writeBoolean(true);
		inOrder.verify(stuffDelegate).serialize(out);
	}

	@Test
	public void deserializeAbbrevWorksWithDelegate() {
		// setup:
		var in = mock(SerializableDataInputStream.class);

		// when:
		subject.deserializeAbbreviated(in, stuffDelegateHash, MerkleOptionalBlob.MERKLE_VERSION);

		// then:
		verify(newDelegate).deserializeAbbreviated(in, stuffDelegateHash, MerkleOptionalBlob.MERKLE_VERSION);
	}

	@Test
	public void deserializeAbbrevWorksWithoutDelegate() {
		// setup:
		var in = mock(SerializableDataInputStream.class);

		// when:
		subject.deserializeAbbreviated(in, MerkleOptionalBlob.MISSING_DELEGATE_HASH, MerkleOptionalBlob.MERKLE_VERSION);

		// then:
		assertEquals(MerkleOptionalBlob.NO_DATA, subject.getData());
		assertEquals(MerkleOptionalBlob.MISSING_DELEGATE, subject.getDelegate());
	}

	@Test
	public void deserializeWorksWithDelegate() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var defaultSubject = new MerkleOptionalBlob();

		given(in.readBoolean()).willReturn(true);

		// when:
		defaultSubject.deserialize(in, MerkleOptionalBlob.MERKLE_VERSION);

		// then:
		verify(newDelegate).deserialize(in, MerkleOptionalBlob.MERKLE_VERSION);
	}

	@Test
	public void toStringWorks() {
		// expect:
		assertEquals(
				"MerkleOptionalBlob{delegate=" + readableStuffDelegate + "}",
				subject.toString());
		assertEquals(
				"MerkleOptionalBlob{delegate=" + null + "}",
				new MerkleOptionalBlob().toString());
	}

	@Test
	public void copyWorks() {
		given(stuffDelegate.copy()).willReturn(stuffDelegate);

		// when:
		var subjectCopy = subject.copy();

		// then:
		assertTrue(subjectCopy != subject);
		assertEquals(subject, subjectCopy);
	}

	@Test
	public void deleteDelegatesIfAppropos() {
		// when:
		subject.delete();

		// then:
		verify(stuffDelegate).delete();
	}

	@Test
	public void doesntDelegateIfMissing() {
		// given:
		subject = new MerkleOptionalBlob();

		// when:
		subject.delete();

		// then:
		verify(stuffDelegate, never()).delete();
	}

	@Test
	public void legacyProviderWorksWithoutDelegate() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		InOrder inOrder = inOrder(newDelegate, in);

		given(in.readLong()).willReturn(0l).willReturn(1l);
		given(in.readBoolean()).willReturn(false);

		// when:
		var something = new MerkleOptionalBlob.Provider().deserialize(in);

		// then:
		inOrder.verify(in, times(2)).readLong();
		inOrder.verify(in).readBoolean();
		assertNotNull(something);
	}

	@Test
	public void legacyProviderWorksWithDelegate() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		InOrder inOrder = inOrder(newDelegate, in);

		given(in.readLong()).willReturn(0l).willReturn(1l);
		given(in.readBoolean()).willReturn(true);

		// when:
		var something = new MerkleOptionalBlob.Provider().deserialize(in);

		// then:
		inOrder.verify(in, times(2)).readLong();
		inOrder.verify(in).readBoolean();
		inOrder.verify(newDelegate).copyFrom(in);
		inOrder.verify(newDelegate).copyFromExtra(in);
		assertNotNull(something);
	}

	@Test
	public void unsupportedOperationsThrow() {
		// given:
		var defaultSubject = new MerkleOptionalBlob();

		// expect:
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.copyTo(null));
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.copyToExtra(null));
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.copyFrom(null));
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.copyFromExtra(null));
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.diffCopyTo(null, null));
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.diffCopyFrom(null, null));
	}

	@Test
	public void objectContractMet() {
		// given:
		var one = new MerkleOptionalBlob();
		var two = new MerkleOptionalBlob(stuff);
		var three = new MerkleOptionalBlob(stuff);
		var four = new Object();

		// then:
		assertNotEquals(null, one);
		assertNotEquals(four, one);
		assertNotEquals(two, one);
		assertEquals(one, one);
		// and:
		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(two.hashCode(), three.hashCode());
	}
}