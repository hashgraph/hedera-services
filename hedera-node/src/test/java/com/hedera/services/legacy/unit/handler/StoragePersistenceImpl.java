package com.hedera.services.legacy.unit.handler;

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


import com.hedera.services.legacy.handler.FCStorageWrapper;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.legacy.logic.ApplicationConstants;
import com.swirlds.fcmap.FCMap;
import org.ethereum.datasource.StoragePersistence;

public class StoragePersistenceImpl implements StoragePersistence {
	private FCMap<MerkleBlobMeta, MerkleOptionalBlob> storageMap;

	public StoragePersistenceImpl(FCMap<MerkleBlobMeta, MerkleOptionalBlob> storageMap) {
		this.storageMap = storageMap;
	}

	@Override
	public boolean storageExist(byte[] key) {
		FCStorageWrapper storageWrapper = new FCStorageWrapper(
				storageMap);
		String filePath = getAddressAppendedPath(key);
		return storageWrapper.fileExists(filePath);

	}

	@Override
	public void persist(byte[] key, byte[] storageCache, long expirationTime, long currentTime) {
		String filePath = getAddressAppendedPath(key);
		FCStorageWrapper storageWrapper = new FCStorageWrapper(storageMap);
		storageWrapper.fileCreate(filePath, storageCache, currentTime, 0, expirationTime, null);
	}

	@Override
	public byte[] get(byte[] key) {
		byte[] serializedCache = null;
		FCStorageWrapper storageWrapper = new FCStorageWrapper(
				storageMap);
		if (storageExist(key)) {
			String filePath = getAddressAppendedPath(key);
			serializedCache = storageWrapper.fileRead(filePath);
		}
		return serializedCache;
	}

	private String getAddressAppendedPath(byte[] key) {
		AccountID acctId = EntityIdUtils.accountParsedFromSolidityAddress(key);
		String path = ApplicationConstants.buildPath(
				ApplicationConstants.ADDRESS_APENDED_PATH, Long.toString(acctId.getRealmNum()),
				Long.toString(acctId.getAccountNum()));//    /0/d2341/
		return path;
	}
}
