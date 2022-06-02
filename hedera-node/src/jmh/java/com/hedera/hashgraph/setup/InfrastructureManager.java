package com.hedera.hashgraph.setup;

/*-
 * ‌
 * Hedera Services JMH benchmarks
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.state.virtual.VirtualMapFactory.JasperDbBuilderFactory;
import com.hedera.services.utils.EntityNum;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class InfrastructureManager {
	private InfrastructureManager() {
		throw new UnsupportedOperationException();
	}

	private static final String BASE_STORAGE_DIR = "databases";
	private static final String VM_META_FILE_NAME = "smartContractKvStore.meta";
	private static final String MM_FILE_NAME = "accounts.mmap";

	public static StorageInfrastructure newInfrastructureAt(final String storageLoc) {
		final var jdbBuilderFactory = new JasperDbBuilderFactory() {
			@Override
			@SuppressWarnings({"rawtypes", "unchecked"})
			public <K extends VirtualKey<? super K>, V extends VirtualValue> JasperDbBuilder<K, V> newJdbBuilder() {
				return new JasperDbBuilder().storageDir(Paths.get(storageLoc));
			}
		};
		final var vmFactory = new VirtualMapFactory(jdbBuilderFactory);
		final var storage = vmFactory.newVirtualizedIterableStorage();
		final MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
		return StorageInfrastructure.from(accounts, storage);
	}

	public static StorageInfrastructure loadInfrastructureWith(
			final int initNumContracts,
			final int initNumKvPairs
	) throws IOException {
		return StorageInfrastructure.from(storageLocFor(initNumContracts, initNumKvPairs));
	}

	public static boolean hasSavedInfrastructureWith(final int initNumContracts, final int initNumKvPairs) {
		final var f = new File(vmLocFor(initNumContracts, initNumKvPairs));
		return f.exists();
	}

	public static String storageLocFor(final int initNumContracts, final int initNumKvPairs) {
		return BASE_STORAGE_DIR + File.separator
				+ "contracts" + initNumContracts + "_"
				+ "kvPairs" + initNumKvPairs;
	}

	private static String vmLocFor(final int initNumContracts, final int initNumKvPairs) {
		return storageLocFor(initNumContracts, initNumKvPairs) + File.separator + "smartContractKvStore.vmap";
	}

	static String vMapMetaIn(final String loc) {
		return loc + File.separator + VM_META_FILE_NAME;
	}

	static String mMapIn(final String storageDir) {
		return storageDir + File.separator + MM_FILE_NAME;
	}
}
