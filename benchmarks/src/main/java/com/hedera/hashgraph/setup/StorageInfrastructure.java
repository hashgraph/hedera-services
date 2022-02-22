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
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;

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
}
