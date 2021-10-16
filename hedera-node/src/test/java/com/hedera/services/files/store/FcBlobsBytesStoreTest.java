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

import static com.hedera.services.files.store.FcBlobsBytesStore.getType;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.BYTECODE;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.FILE_DATA;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.FILE_METADATA;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.SYSTEM_DELETION_TIME;
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
	private static final byte[] cData = "BlobC".getBytes();
	private static final byte[] dData = "BlobD".getBytes();
	private static final String pathA = "/0/f{2}";
	private static final String pathB = "/0/k{3}";
	private static final String pathC = "/0/s{4}";
	private static final String pathD = "/0/e{5}";
	private static final MerkleBlobMeta aMeta = new MerkleBlobMeta(pathA);
	private static final BlobKey pathAKey = keyed(pathA);
	private static final BlobKey pathBKey = keyed(pathB);
	private static final BlobKey pathCKey = keyed(pathC);
	private static final BlobKey pathDKey = keyed(pathD);

	private MerkleBlob blobA, blobB, blobC, blobD;
	private MerkleMap<BlobKey, MerkleBlob> pathedBlobs;

	private FcBlobsBytesStore subject;

	@BeforeEach
	private void setup() {
		pathedBlobs = mock(MerkleMap.class);

		givenMockBlobs();
		subject = new FcBlobsBytesStore(() -> pathedBlobs);
	}

	@Test
	void delegatesClear() {
		subject.clear();

		verify(pathedBlobs).clear();
	}

	@Test
	void delegatesRemoveOfMissing() {
		given(pathedBlobs.remove(aMeta)).willReturn(null);

		assertNull(subject.remove(pathA));
	}

	@Test
	void delegatesRemoveAndReturnsNull() {
		given(pathedBlobs.remove(aMeta)).willReturn(blobA);

		assertNull(subject.remove(pathA));
	}

	@Test
	void delegatesPutUsingGetForModifyIfExtantBlob() {
		given(pathedBlobs.containsKey(pathAKey)).willReturn(true);
		given(pathedBlobs.getForModify(pathAKey)).willReturn(blobA);

		final var oldBytes = subject.put(pathA, aData);

		verify(pathedBlobs).containsKey(pathAKey);
		verify(pathedBlobs).getForModify(pathAKey);
		//verify(blobA).modify(aData);

		assertNull(oldBytes);
	}

	private static BlobKey keyed(String path) {
		final BlobKey.BlobType type = getType(path.charAt(3));
		final long entityNum = Long.parseLong(String.valueOf(path.charAt(5)));
		return new BlobKey(type, entityNum);
	}

	@Test
	void delegatesPutUsingGetAndFactoryIfNewBlob() {
		final var keyCaptor = ArgumentCaptor.forClass(BlobKey.class);
		final var valueCaptor = ArgumentCaptor.forClass(MerkleBlob.class);
		given(pathedBlobs.containsKey(pathAKey)).willReturn(false);

		final var oldBytes = subject.put(pathA, aData);

		verify(pathedBlobs).containsKey(pathAKey);
		verify(pathedBlobs).put(keyCaptor.capture(), valueCaptor.capture());

		assertEquals(pathAKey, keyCaptor.getValue());
		assertSame(blobA.getData(), valueCaptor.getValue().getData());
		assertNull(oldBytes);
	}

	@Test
	void propagatesNullFromGet() {
		given(pathedBlobs.get(pathA)).willReturn(null);

		assertNull(subject.get(pathA));
	}

	@Test
	void delegatesGet() {
		given(pathedBlobs.get(pathAKey)).willReturn(blobA);

		assertArrayEquals(aData, subject.get(pathA));
	}

	@Test
	void delegatesContainsKey() {
		given(pathedBlobs.containsKey(pathAKey)).willReturn(true);

		assertTrue(subject.containsKey(pathA));
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
		blobB = mock(MerkleBlob.class);

		given(blobA.getData()).willReturn(aData);
		given(blobB.getData()).willReturn(bData);
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
		assertEquals(FILE_DATA, at(pathA).getType());
		assertEquals(FILE_METADATA, at(pathB).getType());
		assertEquals(BYTECODE, at(pathC).getType());
		assertEquals(SYSTEM_DELETION_TIME, at(pathD).getType());
		assertThrows(IllegalArgumentException.class, () -> at("wrongpath").getType());
	}

	private BlobKey at(String key) {
		final BlobKey.BlobType type = getType(key.charAt(3));
		final long entityNum = Long.parseLong(String.valueOf(key.charAt(5)));
		return new BlobKey(type, entityNum);
	}
}
