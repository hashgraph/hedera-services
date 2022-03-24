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

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.backing.BackingAccounts;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.engine.CryptoEngine;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.io.MerkleDataInputStream;
import com.swirlds.common.merkle.io.MerkleDataOutputStream;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

public record StorageInfrastructure(
		AtomicReference<MerkleMap<EntityNum, MerkleAccount>> accounts,
		AtomicReference<VirtualMap<ContractKey, ContractValue>> storage,
		TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger) {

	private static final Cryptography crypto = new CryptoEngine();

	public static StorageInfrastructure from(
			final MerkleMap<EntityNum, MerkleAccount> accounts,
			final VirtualMap<ContractKey, ContractValue> storage
	) {
		final AtomicReference<VirtualMap<ContractKey, ContractValue>> storageRef = new AtomicReference<>(storage);
		final AtomicReference<MerkleMap<EntityNum, MerkleAccount>> accountsRef = new AtomicReference<>(accounts);
		final var backingAccounts = new BackingAccounts(accountsRef::get);
		backingAccounts.rebuildFromSources();
		final var ledger = new TransactionalLedger<>(
				AccountProperty.class, MerkleAccount::new, backingAccounts, new ChangeSummaryManager<>());

		return new StorageInfrastructure(accountsRef, storageRef, ledger);
	}

	public static StorageInfrastructure from(final String storageLoc) throws IOException {
		System.out.print("\n- Found saved storage in " + storageLoc + ", loading...");
		MerkleMap<EntityNum, MerkleAccount> accounts;
		final var mMapLoc = InfrastructureManager.mMapIn(storageLoc);
		try (final var mMapIn = new MerkleDataInputStream(Files.newInputStream(Paths.get(mMapLoc)))) {
			mMapIn.readProtocolVersion();
			accounts = mMapIn.readMerkleTree(Integer.MAX_VALUE);
		}

		VirtualMap<ContractKey, ContractValue> storage = new VirtualMap<>();
		final var vMapMetaLoc = InfrastructureManager.vMapMetaIn(storageLoc);
		final var path = Paths.get(vMapMetaLoc);
		final var storageDir = new File(storageLoc);
		try (final var vMapIn = new MerkleDataInputStream(Files.newInputStream(path), storageDir)) {
			storage.deserializeExternal(vMapIn, storageDir, null, 1);
		}
		System.out.println("done.");
		return from(accounts, storage);
	}

	public void serializeTo(final String storageLoc) throws IOException {
		ensureDir(storageLoc);

		final var mMapLoc = InfrastructureManager.mMapIn(storageLoc);
		try (final var mMapOut = new MerkleDataOutputStream(Files.newOutputStream(Paths.get(mMapLoc)))) {
			mMapOut.writeProtocolVersion();
			mMapOut.writeMerkleTree(accounts.get());
		}

		final var vMapMetaLoc = InfrastructureManager.vMapMetaIn(storageLoc);
		final var curStorage = storage.get();
		final var newStorage = curStorage.copy();
		crypto.digestTreeSync(curStorage);
		try (final var vMapOut = new SerializableDataOutputStream(Files.newOutputStream(Paths.get(vMapMetaLoc)))) {
			curStorage.serializeExternal(vMapOut, new File(storageLoc));
		}
		storage.set(newStorage);
	}

	private static void ensureDir(final String loc) {
		final var f = new File(loc);
		if (!f.exists()) {
			if (!f.mkdirs()) {
				throw new IllegalStateException("Failed to create directory " + f.getAbsolutePath());
			}
		} else if (!f.isDirectory()) {
			throw new IllegalStateException(f.getAbsolutePath() + " is not a directory");
		}
	}
}
