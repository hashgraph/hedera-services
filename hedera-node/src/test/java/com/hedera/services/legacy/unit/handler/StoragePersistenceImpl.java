package com.hedera.services.legacy.unit.handler;

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

import com.hedera.services.fees.calculation.FeeCalcUtilsTest;
import com.hedera.services.legacy.unit.StorageTestHelper;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.utils.EntityIdUtils;
import com.swirlds.merkle.map.MerkleMap;
import org.ethereum.datasource.StoragePersistence;

public class StoragePersistenceImpl implements StoragePersistence {
	private static String ADDRESS_APENDED_PATH = "/{0}/d{1}";
	private MerkleMap<String, MerkleOptionalBlob> storageMap;

	public StoragePersistenceImpl(final MerkleMap<String, MerkleOptionalBlob> storageMap) {
		this.storageMap = storageMap;
	}

	@Override
	public boolean storageExist(final byte[] key) {
		final var storageWrapper = new StorageTestHelper(storageMap);
		final var filePath = getAddressAppendedPath(key);
		return storageWrapper.fileExists(filePath);

	}

	@Override
	public void persist(final byte[] key, final byte[] storageCache, final long expirationTime, final long currentTime) {
		final var filePath = getAddressAppendedPath(key);
		final var storageWrapper = new StorageTestHelper(storageMap);
		storageWrapper.fileCreate(filePath, storageCache);
	}

	@Override
	public byte[] get(final byte[] key) {
		byte[] serializedCache = null;
		final var storageWrapper = new StorageTestHelper(storageMap);
		if (storageExist(key)) {
			final var filePath = getAddressAppendedPath(key);
			serializedCache = storageWrapper.fileRead(filePath);
		}
		return serializedCache;
	}

	private String getAddressAppendedPath(final byte[] key) {
		final var acctId = EntityIdUtils.accountParsedFromSolidityAddress(key);
		final var path = FeeCalcUtilsTest.buildPath(
				ADDRESS_APENDED_PATH, Long.toString(acctId.getRealmNum()),
				Long.toString(acctId.getAccountNum()));//    /0/d2341/
		return path;
	}
}
