package com.hedera.services.ledger.accounts;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.fcmap.FCMap;

import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;
import static java.util.stream.Collectors.toSet;

public class PureFCMapBackingAccounts implements BackingStore<AccountID, MerkleAccount> {
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
