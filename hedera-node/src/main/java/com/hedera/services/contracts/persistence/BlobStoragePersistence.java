package com.hedera.services.contracts.persistence;

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

import com.hedera.services.contracts.annotations.StorageSource;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.datasource.Source;
import org.ethereum.datasource.StoragePersistence;
import org.ethereum.vm.DataWord;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.function.Supplier;

@Singleton
public class BlobStoragePersistence implements StoragePersistence {
	private static final Logger log = LogManager.getLogger(BlobStoragePersistence.class);

	private final Map<byte[], byte[]> storage;
	private final Supplier<VirtualMap<ContractKey, ContractValue>> contractStorage;

	@Inject
	public BlobStoragePersistence(
			@StorageSource Map<byte[], byte[]> storage,
			Supplier<VirtualMap<ContractKey, ContractValue>> contractStorage
	) {
		this.storage = storage;
		this.contractStorage = contractStorage;
	}

	@Override
	public boolean storageExist(byte[] address) {
		throw new AssertionError("Dead code (to be removed once all ethereumj dependency is gone)");
	}

	@Override
	public void persist(byte[] address, byte[] cache, long ignoredExpiry, long ignoredNow) {
		throw new AssertionError("Dead code (to be removed once all ethereumj dependency is gone)");
	}

	@Override
	public byte[] get(byte[] address) {
		throw new AssertionError("Dead code (to be removed once all ethereumj dependency is gone)");
	}

	@Override
	public Source<DataWord, DataWord> scopedStorageFor(byte[] address) {
		throw new AssertionError("Dead code (to be removed once all ethereumj dependency is gone)");
	}
}
