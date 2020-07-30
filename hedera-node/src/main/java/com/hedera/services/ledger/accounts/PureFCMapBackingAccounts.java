package com.hedera.services.ledger.accounts;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.fcmap.FCMap;

import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;

public class PureFCMapBackingAccounts implements BackingAccounts<AccountID, MerkleAccount>  {
	private final FCMap<MerkleEntityId, MerkleAccount> delegate;

	public PureFCMapBackingAccounts(FCMap<MerkleEntityId, MerkleAccount> delegate) {
		this.delegate = delegate;
	}

	@Override
	public void flushMutableRefs() { }

	@Override
	public MerkleAccount get(AccountID id) {
		return delegate.get(fromAccountId(id));
	}

	@Override
	public void put(AccountID id, MerkleAccount account) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void remove(AccountID id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean contains(AccountID id) {
		return delegate.containsKey(fromAccountId(id));
	}
}
