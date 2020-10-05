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

import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HashMapBackingTokenRels implements BackingStore<Map.Entry<AccountID, TokenID>, MerkleTokenRelStatus> {
	private Map<Map.Entry<AccountID, TokenID>, MerkleTokenRelStatus> rels = new HashMap<>();

	@Override
	public void flushMutableRefs() { }

	@Override
	public MerkleTokenRelStatus getRef(Map.Entry<AccountID, TokenID> id) {
		return rels.get(id);
	}

	@Override
	public void put(Map.Entry<AccountID, TokenID> id, MerkleTokenRelStatus rel) {
		rels.put(id, rel);
	}

	@Override
	public boolean contains(Map.Entry<AccountID, TokenID> id) {
		return rels.containsKey(id);
	}

	@Override
	public void remove(Map.Entry<AccountID, TokenID> id) {
		rels.remove(id);
	}

	@Override
	public Set<Map.Entry<AccountID, TokenID>> idSet() {
		return rels.keySet();
	}

	@Override
	public MerkleTokenRelStatus getUnsafeRef(Map.Entry<AccountID, TokenID> id) {
		return rels.get(id);
	}
}
