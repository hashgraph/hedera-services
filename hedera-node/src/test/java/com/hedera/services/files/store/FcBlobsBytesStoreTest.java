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

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.hedera.services.files.store.FcBlobsBytesStore.getType;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

class FcBlobsBytesStoreTest {
	private static final byte[] aData = "BlobA".getBytes();
	private static final byte[] bData = "BlobB".getBytes();
	private static final String pathA = "pathA";
	private static final String pathB = "pathB";
	private static final MerkleBlobMeta aMeta = new MerkleBlobMeta(pathA);
	private static final BlobKey pathAKey = keyed(pathA);
	private static final BlobKey pathBKey = keyed(pathB);

	private MerkleBlob blobA, blobB;
	private Function<byte[], MerkleBlob> blobFactory;
	private MerkleMap<BlobKey, MerkleBlob> pathedBlobs;

	private FcBlobsBytesStore subject;

	@BeforeEach
	private void setup() {
		pathedBlobs = mock(MerkleMap.class);
		blobFactory = mock(Function.class);

		givenMockBlobs();
		given(blobFactory.apply(any()))
				.willReturn(blobA)
				.willReturn(blobB);

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
		given(pathedBlobs.containsKey(pathA)).willReturn(false);

		final var oldBytes = subject.put(pathA, aData);

		verify(pathedBlobs).containsKey(pathA);
		verify(pathedBlobs).put(keyCaptor.capture(), valueCaptor.capture());

		assertEquals(pathA, keyCaptor.getValue());
		assertSame(blobA, valueCaptor.getValue());
		assertNull(oldBytes);
	}

	@Test
	void propagatesNullFromGet() {
		given(pathedBlobs.get(pathA)).willReturn(null);

		assertNull(subject.get(pathA));
	}

	@Test
	void delegatesGet() {
		given(pathedBlobs.get(pathA)).willReturn(blobA);

		assertArrayEquals(aData, subject.get(pathA));
	}

	@Test
	void delegatesContainsKey() {
		given(pathedBlobs.containsKey(pathA)).willReturn(true);

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
	void delegatesEntrySet() {
		final Set<Entry<BlobKey, MerkleBlob>> blobEntries = Set.of(
				new AbstractMap.SimpleEntry<>(pathAKey, blobA),
				new AbstractMap.SimpleEntry<>(pathBKey, blobB));
		given(pathedBlobs.entrySet()).willReturn(blobEntries);

		final var entries = subject.entrySet();

		assertEquals(
				"pathA->BlobA, pathB->BlobB",
				entries
						.stream()
						.sorted(Comparator.comparing(Entry::getKey))
						.map(entry -> String.format("%s->%s", entry.getKey(), new String(entry.getValue())))
						.collect(Collectors.joining(", "))
		);
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

	private MerkleBlobMeta at(final String key) {
		return new MerkleBlobMeta(key);
	}
}
