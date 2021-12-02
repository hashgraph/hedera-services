package com.hedera.services.state;

import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles a map with all the accounts that are auto-created. The map will be re-built on restart, reconnect.
 * Entries from the map are removed when the entity expires
 */
@Singleton
public class AutoAccountCreationsManager {
	/* Alias Accounts Map that will be rebuilt after restart, reconnect*/
	private Map<ByteString, EntityNum> autoAccountsMap;

	@Inject
	public AutoAccountCreationsManager() {
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
	public void constructAccountAliasRels(MerkleMap<EntityNum, MerkleAccount> accountsMap) {
		for (Map.Entry entry : accountsMap.entrySet()) {
			MerkleAccount value = (MerkleAccount) entry.getValue();
			EntityNum number = (EntityNum) entry.getKey();
			if (!value.state().getAlias().isEmpty()) {
				this.autoAccountsMap.put(value.state().getAlias(), number);
			}
		}
	}


}
