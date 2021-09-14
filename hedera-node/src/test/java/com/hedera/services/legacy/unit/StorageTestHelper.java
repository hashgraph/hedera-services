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
import com.swirlds.merkle.map.MerkleMap;
import org.apache.commons.lang3.ArrayUtils;

public class StorageTestHelper {
	private MerkleMap<String, MerkleOptionalBlob> storageMap;

	public StorageTestHelper(final MerkleMap<String, MerkleOptionalBlob> storageMap) {
		this.storageMap = storageMap;
	}

	public void fileCreate(final String path, final byte[] content) {
		final var sVal = new MerkleOptionalBlob(content);
		storageMap.put(path, sVal);
	}

	public byte[] fileRead(final String path) {
		MerkleBlobMeta sKey;
		try {
			sKey = validateStorageKey(path);
		} catch (StorageKeyNotFoundException e) {
			return new byte[0];
		}
		return storageMap.get(sKey.getPath()).getData();
	}

	public boolean fileExists(final String path) {
		MerkleBlobMeta sKey;
		try {
			sKey = validateStorageKey(path);
			return storageMap.containsKey(sKey.getPath());
		} catch (StorageKeyNotFoundException ignore) {
		}
		return false;
	}

	public long getSize(final String path) {
		MerkleBlobMeta sKey = new MerkleBlobMeta(path);
		if (storageMap.containsKey(sKey.getPath())) {
			return storageMap.get(sKey.getPath()).getData().length;
		} else {
			return 0l;
		}
	}

	void fileUpdate(final String path, final byte[] content) {
		final var existingContent = fileRead(path);
		final var appendedContent = ArrayUtils.addAll(existingContent, content);
		fileCreate(path, appendedContent);
	}

	private MerkleBlobMeta validateStorageKey(final String path) throws StorageKeyNotFoundException {
		MerkleBlobMeta sKey = new MerkleBlobMeta(path);
		if (!storageMap.containsKey(sKey.getPath())) {
			throw new StorageKeyNotFoundException("Destination file does not exist: '" + path + "'");
		}
		return sKey;
	}

	public void delete(final String path) throws StorageKeyNotFoundException {
		final var sKey = validateStorageKey(path);
		storageMap.remove(sKey.getPath());
	}
}
