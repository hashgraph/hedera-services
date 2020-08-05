package com.hedera.services.ledger.accounts;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.fcmap.FCMap;

import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;
import static java.util.stream.Collectors.toSet;

public class PureFCMapBackingAccounts implements BackingAccounts<AccountID, MerkleAccount>  {
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> delegate;

	public PureFCMapBackingAccounts(Supplier<FCMap<MerkleEntityId, MerkleAccount>> delegate) {
		this.delegate = delegate;
	}

	@Override
	public void flushMutableRefs() { }

	@Override
	public MerkleAccount getRef(AccountID id) {
		return delegate.get().get(fromAccountId(id));
	}

	@Override
	public MerkleAccount getUnsafeRef(AccountID id) {
		return delegate.get().get(fromAccountId(id));
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
		return delegate.get().containsKey(fromAccountId(id));
	}

	@Override
	public Set<AccountID> idSet() {
		return delegate.get().keySet().stream().map(MerkleEntityId::toAccountId).collect(toSet());
	}
}
