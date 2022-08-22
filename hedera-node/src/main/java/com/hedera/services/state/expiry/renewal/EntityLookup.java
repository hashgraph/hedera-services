package com.hedera.services.state.expiry.renewal;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import java.util.function.Supplier;

public class EntityLookup {
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	@Inject
	public EntityLookup(final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts){
		this.accounts = accounts;
	}

	public MerkleAccount getAccount(final EntityNum account){
		return accounts.get().get(account);
	}
	public MerkleAccount getMutableAccount(final EntityNum account){
		return accounts.get().getForModify(account);
	}
	public boolean accountsContainsKey(final EntityNum account){
		return accounts.get().containsKey(account);
	}
}
