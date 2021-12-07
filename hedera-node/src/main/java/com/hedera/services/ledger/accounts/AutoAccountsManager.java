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

/**
 * Handles a map with all the accounts that are auto-created. The map will be re-built on restart, reconnect.
 * Entries from the map are removed when the entity expires
 */
@Singleton
public class AutoAccountsManager {
	private Map<ByteString, EntityNum> autoAccountsMap;

	@Inject
	public AutoAccountsManager() {
		this.autoAccountsMap = new HashMap<>();
	}

	public Map<ByteString, EntityNum> getAutoAccountsMap() {
		return autoAccountsMap;
	}

	/**
	 * From given MerkleMap of accounts, populate the auto accounts creations map. Iterate through
	 * each account in accountsMap and add an entry to autoAccountsMap if {@code alias} exists on the account.
	 *
	 * @param accountsMap
	 * 		accounts MerkleMap
	 */
	public void rebuildAutoAccountsMap(MerkleMap<? extends EntityNum, ? extends MerkleAccount> accountsMap) {
		for (Map.Entry<? extends EntityNum, ? extends MerkleAccount> entry :
				accountsMap.entrySet()) {
			EntityNum number = entry.getKey();
			MerkleAccount value = entry.getValue();
			if (!value.state().getAlias().isEmpty()) {
				this.autoAccountsMap.put(value.state().getAlias(), number);
			}
		}
	}

	/**
	 * Removes an entry from the autoAccountsMap when an entity is expired and deleted from the ledger.
	 *
	 * @param lastClassifiedEntityId
	 * 		entity id that is expired
	 * @param currentAccounts
	 * 		current accounts map
	 */
	public void remove(final EntityNum lastClassifiedEntityId,
			final MerkleMap<EntityNum, MerkleAccount> currentAccounts) {
		/* get the alias from the account */
		ByteString alias = currentAccounts.get(lastClassifiedEntityId).getAlias();
		remove(alias);
	}

	public void remove(final ByteString alias) {
		if (autoAccountsMap.containsKey(alias)) {
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
	public EntityNum fetchEntityNumFor(ByteString alias) {
		return autoAccountsMap.getOrDefault(alias, MISSING_NUM);
	}

	/* Only for unit tests */
	public void setAutoAccountsMap(Map<ByteString, EntityNum> map) {
		this.autoAccountsMap = map;
	}

	public boolean contains(ByteString alias) {
		return autoAccountsMap.containsKey(alias);
	}
}
