package com.hedera.services.ledger.accounts;

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

import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hedera.services.utils.MiscUtils.forEach;

/**
 * Handles a map with all the accounts that are auto-created. The map will be re-built on restart, reconnect.
 * Entries from the map are removed when the entity expires
 */
@Singleton
public class AliasManager {
	private Map<ByteString, EntityNum> autoAccountsMap;

	@Inject
	public AliasManager() {
		this.autoAccountsMap = new HashMap<>();
	}

	public Map<ByteString, EntityNum> getAutoAccountsMap() {
		return autoAccountsMap;
	}

	/**
	 * From given MerkleMap of accounts, populate the auto accounts creations map. Iterate through
	 * each account in accountsMap and add an entry to autoAccountsMap if {@code alias} exists on the account.
	 *
	 * @param accounts the current accounts
	 */
	public void rebuildAliasesMap(MerkleMap<EntityNum, MerkleAccount> accounts) {
		autoAccountsMap.clear();
		forEach(accounts, (k, v) -> {
			if (!v.getAlias().isEmpty()) {
				autoAccountsMap.put(v.getAlias(), k);
			}
		});
	}

	/**
	 * Removes an entry from the autoAccountsMap when an entity is expired and deleted from the ledger.
	 *
	 * @param expiredId
	 * 		entity id that is expired
	 * @param accounts
	 * 		current accounts map
	 */
	public void forgetAliasIfPresent(final EntityNum expiredId, final MerkleMap<EntityNum, MerkleAccount> accounts) {
		final var alias = accounts.get(expiredId).getAlias();
		if (!alias.isEmpty()) {
			autoAccountsMap.remove(alias);
		}
	}

	/**
	 * Returns the entityNum for the given alias
	 *
	 * @param alias
	 * 		alias of the accountId
	 * @return EntityNum mapped to the given alias.
	 */
	public EntityNum lookupIdBy(final ByteString alias) {
		return autoAccountsMap.getOrDefault(alias, MISSING_NUM);
	}

	/* Only for unit tests */
	public void setAutoAccountsMap(final Map<ByteString, EntityNum> map) {
		this.autoAccountsMap = map;
	}

	public boolean contains(ByteString alias) {
		return autoAccountsMap.containsKey(alias);
	}
}
