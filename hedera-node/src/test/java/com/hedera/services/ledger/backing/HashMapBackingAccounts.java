package com.hedera.services.ledger.backing;

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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HashMapBackingAccounts implements BackingStore<EntityNum, MerkleAccount> {
	final EntityNum GENESIS = EntityNum.fromLong(2);
	final long GENESIS_BALANCE = 50_000_000_000L;

	private Map<EntityNum, MerkleAccount> accounts = new HashMap<>();

	{
		MerkleAccount genesisAccount = new MerkleAccount();
		try {
			genesisAccount.setBalance(GENESIS_BALANCE);
		} catch (Exception ignore) {}
		accounts.put(GENESIS, genesisAccount);
	}

	@Override
	public MerkleAccount getRef(EntityNum id) {
		return accounts.get(id);
	}

	@Override
	public void put(EntityNum id, MerkleAccount account) {
		accounts.put(id, account);
	}

	@Override
	public boolean contains(EntityNum id) {
		return accounts.containsKey(id);
	}

	@Override
	public void remove(EntityNum id) {
		accounts.remove(id);
	}

	@Override
	public Set<EntityNum> idSet() {
		return accounts.keySet();
	}

	@Override
	public long size() {
		return accounts.size();
	}

	@Override
	public MerkleAccount getImmutableRef(EntityNum id) {
		return accounts.get(id);
	}
}
