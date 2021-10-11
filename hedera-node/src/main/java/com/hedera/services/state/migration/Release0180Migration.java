package com.hedera.services.state.migration;

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
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.utils.MiscUtils.forEach;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

public final class Release0180Migration {
	private static final Logger log = LogManager.getLogger(Release0180Migration.class);

	/**
	 * Scans the given {@code accounts} map for ids 0.0.X whose list of associated tokens contains
	 * one or more token ids 0.0.Y such that there is no (0.0.X, 0.0.Y) entry in the given
	 * {@code associations} map.
	 *
	 * After finished scanning, for each such 0.0.X, uses getForModify() to remove the offending
	 * token ides from the account's token list.
	 *
	 * (For safety, also uses the given {@code tokens} map to check existence of all referenced tokens.
	 * Since token expiration is not enabled, this should be superfluous, but can't hurt.)
	 *
	 * @param tokens the map of extant tokens
	 * @param accounts the map of accounts to scan for inconsistent associated token lists
	 * @param associations the map of extant associations
	 */
	public static void cleanInconsistentTokenLists(
			final MerkleMap<EntityNum, MerkleToken> tokens,
			final MerkleMap<EntityNum, MerkleAccount> accounts,
			final MerkleMap<EntityNumPair, MerkleTokenRelStatus> associations
	) {
		final Map<EntityNum, Set<Id>> inconsistentAccountRels = new HashMap<>();

		forEach(accounts, (id, account) -> {
			final var accountTokens = account.tokens();
			if (accountTokens.numAssociations() == 0) {
				return;
			}

			final Set<Id> inconsistentRels = new HashSet<>();
			for (final var tid : accountTokens.asTokenIds()) {
				final var token = tokens.get(EntityNum.fromTokenId(tid));
				if (token == null) {
					log.warn(
							"Found non-existent token {} in associations list for account 0.0.{}",
							readableId(tid),
							id.longValue());
					inconsistentRels.add(Id.fromGrpcToken(tid));
				} else {
					final var tokenRelKey = EntityNumPair.fromLongs(id.longValue(), tid.getTokenNum());
					final var tokenRel = associations.get(tokenRelKey);
					if (tokenRel == null) {
						inconsistentRels.add(Id.fromGrpcToken(tid));
					}
				}
			}
			if (!inconsistentRels.isEmpty()) {
				inconsistentAccountRels.put(id, inconsistentRels);
			}
		});

		/* This ordering isn't required for correctness (getForModify calls are order-insensitive within
		a round), but it will make it slightly easier to review testnet logs during the 0.18.2 upgrade. */
		final var orderedIds = inconsistentAccountRels.keySet()
				.stream()
				.sorted(comparingInt(EntityNum::intValue))
				.collect(toList());

		for (final var id : orderedIds) {
			final var mutableAccount = accounts.getForModify(id);
			final var mutableAccountTokens = mutableAccount.tokens();
			final var inconsistentRels = inconsistentAccountRels.get(id);
			mutableAccountTokens.dissociate(inconsistentRels);
			log.warn("Cleaned inconsistent relationships {} from account 0.0.{}",
					inconsistentRels,
					id.longValue());
		}
	}

	private Release0180Migration() {
		throw new UnsupportedOperationException("Utility class");
	}
}
