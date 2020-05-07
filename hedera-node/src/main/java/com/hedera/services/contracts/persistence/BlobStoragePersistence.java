package com.hedera.services.contracts.persistence;

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

import org.ethereum.datasource.StoragePersistence;

import java.util.Map;

public class BlobStoragePersistence implements StoragePersistence {
	private final Map<byte[], byte[]> storage;

	public BlobStoragePersistence(Map<byte[], byte[]> storage) {
		this.storage = storage;
	}

	@Override
	public boolean storageExist(byte[] address) {
		return storage.containsKey(address);
	}

	@Override
	public void persist(byte[] address, byte[] cache, long ignoredExpiry, long ignoredNow) {
		storage.put(address, cache);
	}

	@Override
	public byte[] get(byte[] address) {
		return storage.get(address);
	}
}
