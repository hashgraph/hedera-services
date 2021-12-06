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
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

@Singleton
public class BackingAccounts implements BackingStore<EntityNum, MerkleAccount> {
	private final Set<EntityNum> extant = new HashSet<>();

	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> delegate;

	@Inject
	public BackingAccounts(Supplier<MerkleMap<EntityNum, MerkleAccount>> delegate) {
		this.delegate = delegate;
	}

	@Override
	public void rebuildFromSources() {
		extant.clear();
		extant.addAll(delegate.get().keySet());
	}

	@Override
	public MerkleAccount getRef(final EntityNum id) {
		return delegate.get().getForModify(id);
	}

	@Override
	public void put(final EntityNum id, final MerkleAccount account) {
		if (!extant.contains(id)) {
			delegate.get().put(id, account);
			extant.add(id);
		}
	}

	@Override
	public boolean contains(final EntityNum id) {
		return extant.contains(id);
	}

	@Override
	public void remove(final EntityNum id) {
		delegate.get().remove(id);
		extant.remove(id);
	}

	@Override
	public Set<EntityNum> idSet() {
		return extant;
	}

	@Override
	public long size() {
		return delegate.get().size();
	}

	@Override
	public MerkleAccount getImmutableRef(EntityNum id) {
		return delegate.get().get(id);
	}
}
