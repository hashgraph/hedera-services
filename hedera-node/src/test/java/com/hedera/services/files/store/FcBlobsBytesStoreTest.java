package com.hedera.services.files.store;

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

import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
class FcBlobsBytesStoreTest {
	byte[] aData = "BlobA".getBytes(), bData = "BlobB".getBytes();
	MerkleBlobMeta pathA = new MerkleBlobMeta("pathA"), pathB = new MerkleBlobMeta("pathB");

	MerkleOptionalBlob blobA, blobB;
	Function<byte[], MerkleOptionalBlob> blobFactory;
	FCMap<MerkleBlobMeta, MerkleOptionalBlob> pathedBlobs;

	FcBlobsBytesStore subject;

	@BeforeEach
	private void setup() {
		pathedBlobs = mock(FCMap.class);
		blobFactory = mock(Function.class);

		givenMockBlobs();
		given(blobFactory.apply(any()))
				.willReturn(blobA)
				.willReturn(blobB);

		subject = new FcBlobsBytesStore(blobFactory, pathedBlobs);
	}

	@Test
	public void delegatesClear() {
		// when:
		subject.clear();

		// then:
		verify(pathedBlobs).clear();
	}

	@Test
	public void delegatesRemoveOfMissing() {
		given(pathedBlobs.remove(pathA)).willReturn(null);

		// when:
		var prev = subject.remove(pathA.getPath());

		// then:
		assertNull(prev);
	}

	@Test
	public void delegatesRemoveAndReturnsNull() {
		given(pathedBlobs.remove(pathA)).willReturn(blobA);

		// when:
		byte[] prev = subject.remove(pathA.getPath());

		// then:
		assertNull(prev);
	}

	@Test
	public void delegatesPutUsingGetForModifyIfExtantBlob() {
		// setup:
		ArgumentCaptor<MerkleBlobMeta> keyCaptor = ArgumentCaptor.forClass(MerkleBlobMeta.class);
		ArgumentCaptor<MerkleOptionalBlob> valueCaptor = ArgumentCaptor.forClass(MerkleOptionalBlob.class);

		given(pathedBlobs.containsKey(pathA)).willReturn(true);
		given(pathedBlobs.getForModify(pathA)).willReturn(blobA);

		// when:
		var oldBytes = subject.put(pathA.getPath(), blobA.getData());

		// then:
		verify(pathedBlobs).containsKey(pathA);
		verify(pathedBlobs).getForModify(pathA);
		verify(blobA).modify(argThat((byte[] bytes) -> Arrays.equals(bytes, blobA.getData())));
		// and:
		verify(pathedBlobs).put(keyCaptor.capture(), valueCaptor.capture());
		// and:
		assertEquals(pathA, keyCaptor.getValue());
		assertSame(blobA, valueCaptor.getValue());
		assertNull(oldBytes);
	}

	@Test
	public void delegatesPutUsingGetAndFactoryIfNewBlob() {
		// setup:
		ArgumentCaptor<MerkleBlobMeta> keyCaptor = ArgumentCaptor.forClass(MerkleBlobMeta.class);
		ArgumentCaptor<MerkleOptionalBlob> valueCaptor = ArgumentCaptor.forClass(MerkleOptionalBlob.class);

		given(pathedBlobs.containsKey(pathA)).willReturn(false);

		// when:
		var oldBytes = subject.put(pathA.getPath(), blobA.getData());

		// then:
		verify(pathedBlobs).containsKey(pathA);
		verify(pathedBlobs).put(keyCaptor.capture(), valueCaptor.capture());
		// and:
		assertEquals(pathA, keyCaptor.getValue());
		assertSame(blobA, valueCaptor.getValue());
		assertNull(oldBytes);
	}

	@Test
	public void propagatesNullFromGet() {
		given(pathedBlobs.get(argThat(sk -> ((MerkleBlobMeta)sk).getPath().equals(pathA.getPath())))).willReturn(null);

		// when:
		byte[] blob = subject.get(pathA.getPath());

		// then:
		assertNull(blob);
	}

	@Test
	public void delegatesGet() {
		given(pathedBlobs.get(argThat(sk -> ((MerkleBlobMeta)sk).getPath().equals(pathA.getPath())))).willReturn(blobA);

		// when:
		byte[] blob = subject.get(pathA.getPath());

		// then:
		assertEquals(new String(blobA.getData()), new String(blob));
	}

	@Test
	public void delegatesContainsKey() {
		given(pathedBlobs.containsKey(argThat(sk -> ((MerkleBlobMeta)sk).getPath().equals(pathA.getPath()))))
				.willReturn(true);

		// when:
		boolean flag = subject.containsKey(pathA.getPath());

		// then:
		assertTrue(flag);
	}

	@Test
	public void delegatesIsEmpty() {
		given(pathedBlobs.isEmpty()).willReturn(true);

		// when:
		boolean flag = subject.isEmpty();

		// then:
		assertTrue(flag);
		// and:
		verify(pathedBlobs).isEmpty();
	}

	@Test
	public void delegatesSize() {
		given(pathedBlobs.size()).willReturn(123);

		// expect:
		assertEquals(123, subject.size());
	}

	@Test
	public void delegatesEntrySet() {
		// setup:
		Set<Entry<MerkleBlobMeta, MerkleOptionalBlob>> blobEntries = Set.of(
				new AbstractMap.SimpleEntry<>(pathA, blobA),
				new AbstractMap.SimpleEntry<>(pathB, blobB));

		given(pathedBlobs.entrySet()).willReturn(blobEntries);

		// when:
		Set<Entry<String, byte[]>> entries = subject.entrySet();

		// then:
		assertEquals(
				entries
						.stream()
						.sorted(Comparator.comparing(Entry::getKey))
						.map(entry -> String.format("%s->%s", entry.getKey(), new String(entry.getValue())))
						.collect(Collectors.joining(", ")),
				"pathA->BlobA, pathB->BlobB"
		);
	}

	private void givenMockBlobs() {
		blobA = mock(MerkleOptionalBlob.class);
		blobB = mock(MerkleOptionalBlob.class);

		given(blobA.getData()).willReturn(aData);
		given(blobB.getData()).willReturn(bData);
	}

	@Test
	public void putDeletesReplacedValueIfNoCopyIsHeld() {
		// setup:
		FCMap<MerkleBlobMeta, MerkleOptionalBlob> blobs = new FCMap<>(new MerkleBlobMeta.Provider(), new MerkleOptionalBlob.Provider());

		// given:
		blobs.put(at("path"), new MerkleOptionalBlob("FIRST".getBytes()));

		// when:
		var replaced = blobs.put(at("path"), new MerkleOptionalBlob("SECOND".getBytes()));

		// then:
		assertTrue(replaced.getDelegate().isDeleted());
	}

	@Test
	public void putDoesNotDeleteReplacedValueIfCopyIsHeld() {
		// setup:
		FCMap<MerkleBlobMeta, MerkleOptionalBlob> blobs = new FCMap<>(new MerkleBlobMeta.Provider(), new MerkleOptionalBlob.Provider());

		// given:
		blobs.put(at("path"), new MerkleOptionalBlob("FIRST".getBytes()));

		// when:
		var copy = blobs.copy();
		var replaced = blobs.put(at("path"), new MerkleOptionalBlob("SECOND".getBytes()));

		// then:
		assertFalse(replaced.getDelegate().isDeleted());
	}

	private MerkleBlobMeta at(String key) {
		return new MerkleBlobMeta(key);
	}
}
