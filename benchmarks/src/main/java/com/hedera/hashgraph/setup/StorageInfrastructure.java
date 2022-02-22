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
import com.swirlds.common.io.SerializableDataOutputStream;
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

	public static StorageInfrastructure from(
			final MerkleMap<EntityNum, MerkleAccount> accounts,
			final VirtualMap<ContractKey, ContractValue> storage
	) {
		final AtomicReference<VirtualMap<ContractKey, ContractValue>> storageRef = new AtomicReference<>(storage);
		final AtomicReference<MerkleMap<EntityNum, MerkleAccount>> accountsRef = new AtomicReference<>(accounts);
		final var backingAccounts = new BackingAccounts(accountsRef::get);
		final var ledger = new TransactionalLedger<>(
				AccountProperty.class, MerkleAccount::new, backingAccounts, new ChangeSummaryManager<>());
		return new StorageInfrastructure(accountsRef, storageRef, ledger);
	}

	public void serializeTo(final String storageLoc) throws IOException {
		final var mMapLoc = InfrastructureManager.mMapIn(storageLoc);
		try (final var mMapOut = new MerkleDataOutputStream(Files.newOutputStream(Paths.get(mMapLoc)))) {
			mMapOut.writeMerkleTree(accounts.get());
		}

		final var vMapMetaLoc = InfrastructureManager.vMapMetaIn(storageLoc);
		try (final var vMapOut = new SerializableDataOutputStream(Files.newOutputStream(Paths.get(vMapMetaLoc)))) {
			storage.get().serializeExternal(vMapOut, new File(storageLoc));
		}
	}
}
