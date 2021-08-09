package com.hedera.services.legacy.unit;

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

import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import static com.hedera.test.utils.TxnUtils.randomUtf8Bytes;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageWrapperTest {
	private FCMap<MerkleBlobMeta, MerkleOptionalBlob> storageMap = new FCMap<>();
	private static final String TEST_CREATE_FILE_PATH = "/0/a1234/";
	private static final String TEST_CREATE_DELETE_FILE_PATH = "/0/a1236/";
	private static final String TEST_CREATE_APPEND_FILE_PATH = "/0/a1237/";
	private static final String TEST_OVERRIDE_FILE_PATH = "/0/a1239/";

	@Test
	void testFileCreateRead() {
		final var fileCreateContent = randomUtf8Bytes(5000);
		final var storageWrapper = new FCStorageWrapper(storageMap);

		storageWrapper.fileCreate(TEST_CREATE_FILE_PATH, fileCreateContent);
		final var fileReadContent = storageWrapper.fileRead(TEST_CREATE_FILE_PATH);

		assertTrue(storageWrapper.fileExists(TEST_CREATE_FILE_PATH));
		assertArrayEquals(fileCreateContent, fileReadContent);
	}

	@Test
	void testFileCreateDelete() throws StorageKeyNotFoundException {
		final var fileCreateContent = randomUtf8Bytes(5000);
		final var storageWrapper = new FCStorageWrapper(storageMap);

		storageWrapper.fileCreate(TEST_CREATE_DELETE_FILE_PATH, fileCreateContent);
		assertTrue(storageWrapper.fileExists(TEST_CREATE_DELETE_FILE_PATH));

		storageWrapper.delete(TEST_CREATE_DELETE_FILE_PATH);
		Assertions.assertFalse(storageWrapper.fileExists(TEST_CREATE_DELETE_FILE_PATH));
	}

	@Test
	void testFileCreateAppend() {
		final var fileCombinedContentExpected = randomUtf8Bytes(6000);
		final var fistChunklength = ThreadLocalRandom.current().nextInt(1000, 5000);
		final var file1Content = Arrays.copyOfRange(fileCombinedContentExpected, 0, fistChunklength);
		final var file2Content = Arrays.copyOfRange(fileCombinedContentExpected, fistChunklength,
				fileCombinedContentExpected.length);
		final var storageWrapper = new FCStorageWrapper(storageMap);

		storageWrapper.fileCreate(TEST_CREATE_APPEND_FILE_PATH, file1Content);
		assertTrue(storageWrapper.fileExists(TEST_CREATE_APPEND_FILE_PATH));

		storageWrapper.fileUpdate(TEST_CREATE_APPEND_FILE_PATH, file2Content);
		final var fileReadContent = storageWrapper.fileRead(TEST_CREATE_APPEND_FILE_PATH);
		assertArrayEquals(fileCombinedContentExpected, fileReadContent);
	}

	@Test
	void testOverrideFile() {
		final var fileCreateContent = randomUtf8Bytes(5000);
		final var storageWrapper = new FCStorageWrapper(storageMap);

		storageWrapper.fileCreate(TEST_OVERRIDE_FILE_PATH, fileCreateContent);
		assertTrue(storageWrapper.fileExists(TEST_OVERRIDE_FILE_PATH));

		final var fileReadContent = storageWrapper.fileRead(TEST_OVERRIDE_FILE_PATH);
		assertArrayEquals(fileCreateContent, fileReadContent);

		final var fileOverriddenContent = randomUtf8Bytes(2000);
		storageWrapper.fileCreate(TEST_OVERRIDE_FILE_PATH, fileOverriddenContent);
		assertTrue(storageWrapper.fileExists(TEST_OVERRIDE_FILE_PATH));

		final var fileReadAfterOverrideContent = storageWrapper.fileRead(TEST_OVERRIDE_FILE_PATH);
		assertArrayEquals(fileOverriddenContent, fileReadAfterOverrideContent);
	}
}
