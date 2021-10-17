package com.hedera.services.files.store;

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

import com.hedera.services.state.merkle.MerkleBlob;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.internals.BlobKey;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.hedera.services.files.store.FcBlobsBytesStore.getEntityNumFromPath;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.CONTRACT_BYTECODE;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.CONTRACT_STORAGE;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.FILE_DATA;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.FILE_METADATA;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.SYSTEM_DELETED_ENTITY_EXPIRY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

class FcBlobsBytesStoreTest {
	private static final byte[] aData = "BlobA".getBytes();
	private static final byte[] bData = "BlobB".getBytes();
	private static final String dataPath = "/0/f112";
	private static final String metadataPath = "/0/k3";
	private static final String bytecodePath = "/0/s4";
	private static final String storagePath = "/0/d123";
	private static final String expiryTimePath = "/0/e5";
	private BlobKey pathAKey;
	private static final MerkleBlobMeta aMeta = new MerkleBlobMeta(dataPath);

	private MerkleBlob blobA;
	private MerkleMap<BlobKey, MerkleBlob> pathedBlobs;

	private FcBlobsBytesStore subject;

	@BeforeEach
	private void setup() {
		pathedBlobs = mock(MerkleMap.class);

		givenMockBlobs();
		subject = new FcBlobsBytesStore(() -> pathedBlobs);

		pathAKey = subject.at(dataPath);
	}

	@Test
	void delegatesClear() {
		subject.clear();

		verify(pathedBlobs).clear();
	}

	@Test
	void delegatesRemoveOfMissing() {
		given(pathedBlobs.remove(aMeta)).willReturn(null);

		assertNull(subject.remove(dataPath));
	}

	@Test
	void delegatesRemoveAndReturnsNull() {
		given(pathedBlobs.remove(aMeta)).willReturn(blobA);

		assertNull(subject.remove(dataPath));
	}

	@Test
	void delegatesPutUsingGetForModifyIfExtantBlob() {
		given(pathedBlobs.containsKey(pathAKey)).willReturn(true);
		given(pathedBlobs.getForModify(pathAKey)).willReturn(blobA);

		final var oldBytes = subject.put(dataPath, aData);

		verify(pathedBlobs).containsKey(pathAKey);
		verify(pathedBlobs).getForModify(pathAKey);
		//verify(blobA).modify(aData);

		assertNull(oldBytes);
	}

	@Test
	void delegatesPutUsingGetAndFactoryIfNewBlob() {
		final var keyCaptor = ArgumentCaptor.forClass(BlobKey.class);
		final var valueCaptor = ArgumentCaptor.forClass(MerkleBlob.class);
		given(pathedBlobs.containsKey(pathAKey)).willReturn(false);

		final var oldBytes = subject.put(dataPath, aData);

		verify(pathedBlobs).containsKey(pathAKey);
		verify(pathedBlobs).put(keyCaptor.capture(), valueCaptor.capture());

		assertEquals(pathAKey, keyCaptor.getValue());
		assertSame(blobA.getData(), valueCaptor.getValue().getData());
		assertNull(oldBytes);
	}

	@Test
	void propagatesNullFromGet() {
		given(pathedBlobs.get(dataPath)).willReturn(null);

		assertNull(subject.get(dataPath));
	}

	@Test
	void delegatesGet() {
		given(pathedBlobs.get(pathAKey)).willReturn(blobA);

		assertArrayEquals(aData, subject.get(dataPath));
	}

	@Test
	void delegatesContainsKey() {
		given(pathedBlobs.containsKey(pathAKey)).willReturn(true);

		assertTrue(subject.containsKey(dataPath));
	}

	@Test
	void delegatesIsEmpty() {
		given(pathedBlobs.isEmpty()).willReturn(true);

		assertTrue(subject.isEmpty());
		verify(pathedBlobs).isEmpty();
	}

	@Test
	void delegatesSize() {
		given(pathedBlobs.size()).willReturn(123);

		assertEquals(123, subject.size());
	}

	@Test
	void entrySetThrows() {
		assertThrows(UnsupportedOperationException.class, () -> subject.entrySet());
	}

	private void givenMockBlobs() {
		blobA = mock(MerkleBlob.class);

		given(blobA.getData()).willReturn(aData);
	}

	@Test
	void putDeletesReplacedValueIfNoCopyIsHeld() {
		final MerkleMap<String, MerkleOptionalBlob> blobs = new MerkleMap<>();
		blobs.put("path", new MerkleOptionalBlob("FIRST".getBytes()));

		final var replaced = blobs.put("path", new MerkleOptionalBlob("SECOND".getBytes()));

		assertTrue(replaced.getDelegate().isReleased());
	}

	@Test
	void putDoesNotDeleteReplacedValueIfCopyIsHeld() {
		final MerkleMap<String, MerkleOptionalBlob> blobs = new MerkleMap<>();
		blobs.put("path", new MerkleOptionalBlob("FIRST".getBytes()));

		final var copy = blobs.copy();
		final var replaced = copy.put("path", new MerkleOptionalBlob("SECOND".getBytes()));

		assertFalse(replaced.getDelegate().isReleased());
	}

	@Test
	void validateBlobTypeBasedOnPath() {
		assertEquals(FILE_DATA, subject.at(dataPath).getType());
		assertEquals(FILE_METADATA, subject.at(metadataPath).getType());
		assertEquals(CONTRACT_BYTECODE, subject.at(bytecodePath).getType());
		assertEquals(SYSTEM_DELETED_ENTITY_EXPIRY, subject.at(expiryTimePath).getType());
		assertEquals(CONTRACT_STORAGE, subject.at(storagePath).getType());
		try {
			subject.at("wrongpath").getType();
		} catch (IllegalArgumentException ex) {
			assertEquals("Invalid legacy code 'n'", ex.getMessage());
		}
	}

	@Test
	void validateBlobKeyBasedOnPath() {
		assertEquals(new BlobKey(FILE_DATA, 112), subject.at(dataPath));
		assertEquals(new BlobKey(FILE_METADATA, 3), subject.at(metadataPath));
		assertEquals(new BlobKey(CONTRACT_STORAGE, 123), subject.at(storagePath));
		assertEquals(new BlobKey(CONTRACT_BYTECODE, 4), subject.at(bytecodePath));
		assertEquals(new BlobKey(SYSTEM_DELETED_ENTITY_EXPIRY, 5), subject.at(expiryTimePath));
	}

	@Test
	void validateEntityNumBasedOnPath() {
		assertEquals(112, getEntityNumFromPath(dataPath));
		assertEquals(3, getEntityNumFromPath(metadataPath));
		assertEquals(4, getEntityNumFromPath(bytecodePath));
		assertEquals(5, getEntityNumFromPath(expiryTimePath));
		assertEquals(123, getEntityNumFromPath(storagePath));
	}
}
