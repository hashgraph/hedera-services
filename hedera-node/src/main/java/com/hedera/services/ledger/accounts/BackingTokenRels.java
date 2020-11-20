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

import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.fcmap.FCMap;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.ledger.HederaLedger.ACCOUNT_ID_COMPARATOR;
import static com.hedera.services.ledger.HederaLedger.TOKEN_ID_COMPARATOR;
import static com.hedera.services.state.merkle.MerkleEntityAssociation.fromAccountTokenRel;
import static com.hedera.services.utils.EntityIdUtils.readableId;

/**
 * A store that provides efficient access to the mutable representations
 * of token relationships, indexed by ({@code AccountID}, {@code TokenID})
 * pairs. This class is <b>not</b> thread-safe, and should never be used
 * by any thread other than the {@code handleTransaction} thread.
 *
 * @author Michael Tinker
 */
public class BackingTokenRels implements BackingStore<Map.Entry<AccountID, TokenID>, MerkleTokenRelStatus> {
	public static final Comparator<Map.Entry<AccountID, TokenID>> RELATIONSHIP_COMPARATOR = Comparator
			.<Map.Entry<AccountID, TokenID>, AccountID>comparing(Map.Entry::getKey, ACCOUNT_ID_COMPARATOR)
			.thenComparing(Map.Entry::getValue, TOKEN_ID_COMPARATOR);

	Set<Map.Entry<AccountID, TokenID>> existingRels = new HashSet<>();
	Map<Map.Entry<AccountID, TokenID>, MerkleTokenRelStatus> cache = new HashMap<>();

	private final Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> delegate;

	public BackingTokenRels(Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> delegate) {
		this.delegate = delegate;
		rebuildFromSources();
	}

	@Override
	public void rebuildFromSources() {
		existingRels.clear();
		delegate.get().keySet().stream()
				.map(MerkleEntityAssociation::asAccountTokenRel)
				.forEach(existingRels::add);
	}

	@Override
	public void flushMutableRefs() {
		cache.keySet().stream()
				.sorted(RELATIONSHIP_COMPARATOR)
				.forEach(key ->
						delegate.get().replace(fromAccountTokenRel(key.getKey(), key.getValue()), cache.get(key)));
		cache.clear();
	}

	@Override
	public boolean contains(Map.Entry<AccountID, TokenID> key) {
		return existingRels.contains(key);
	}

	@Override
	public MerkleTokenRelStatus getRef(Map.Entry<AccountID, TokenID> key) {
		return cache.computeIfAbsent(
				key,
				ignore -> delegate.get().getForModify(fromAccountTokenRel(key.getKey(), key.getValue())));
	}

	@Override
	public void put(Map.Entry<AccountID, TokenID> key, MerkleTokenRelStatus status) {
		if (!existingRels.contains(key)) {
			delegate.get().put(fromAccountTokenRel(key), status);
			existingRels.add(key);
		} else if (!cache.containsKey(key) || cache.get(key) != status) {
			throw new IllegalArgumentException(String.format(
					"Existing relationship status '%s' can only be changed using a mutable ref!",
					fromAccountTokenRel(key).toAbbrevString()));
		}
	}

	@Override
	public void remove(Map.Entry<AccountID, TokenID> key) {
		existingRels.remove(key);
		delegate.get().remove(fromAccountTokenRel(key));
	}

	@Override
	public MerkleTokenRelStatus getUnsafeRef(Map.Entry<AccountID, TokenID> id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<Map.Entry<AccountID, TokenID>> idSet() {
		throw new UnsupportedOperationException();
	}

	public static Map.Entry<AccountID, TokenID> asTokenRel(AccountID account, TokenID token) {
		return new AbstractMap.SimpleImmutableEntry<>(account, token);
	}

	public static String readableTokenRel(Map.Entry<AccountID, TokenID> relationship) {
		return String.format("%s <-> %s", readableId(relationship.getKey()), readableId(relationship.getValue()));
	}
}
