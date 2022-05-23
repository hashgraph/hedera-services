package com.hedera.services.state.migration;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.store.models.Id.MISSING_ID;
import static com.hedera.services.utils.MiscUtils.forEach;

/**
 * Release 0.25.x migration to initialize the {@code MerkleAccountState#numTreasuryTitles} field with the
 * number of non-deleted tokens for which the account serves as treasury.
 */
public class ReleaseTwentyFiveMigration {
	private static final Logger log = LogManager.getLogger(ReleaseTwentyFiveMigration.class);

	public static void initTreasuryTitleCounts(final ServicesState initializingState) {
		final var tokens = initializingState.tokens();
		final var accounts = initializingState.accounts();
		forEach(tokens, (tokenNum, token) -> {
			if (token.isDeleted()) {
				return;
			}
			final var treasuryNum = token.treasuryNum();
			if (!accounts.containsKey(treasuryNum)) {
				log.warn("Token {} has non-existent treasury {}, skipping",
						tokenNum.toIdString(), treasuryNum.toIdString());
			} else {
				final var mutableAccount = accounts.getForModify(treasuryNum);
				final var curTitles = mutableAccount.getNumTreasuryTitles();
				mutableAccount.setNumTreasuryTitles(curTitles + 1);
			}
		});
	}

	public static void buildAccountTokenAssociationsLinkedList(
			final MerkleMap<EntityNum, MerkleAccount> accounts,
			final MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels
	) {
		for (final var accountId : accounts.keySet()) {
			var merkleAccount = accounts.getForModify(accountId);
			int numAssociations = 0;
			int numPositiveBalances = 0;
			long headTokenNum = MISSING_ID.num();
			MerkleTokenRelStatus prevAssociation = null;
			for (var tokenId : merkleAccount.tokens().asTokenIds()) {
				var newListRootKey = EntityNumPair.fromLongs(accountId.longValue(), tokenId.getTokenNum());
				var association = tokenRels.getForModify(newListRootKey);
				association.setNext(headTokenNum);

				if (prevAssociation != null) {
					prevAssociation.setPrev(tokenId.getTokenNum());
				}

				if (association.getBalance() > 0) {
					numPositiveBalances++;
				}
				numAssociations++;

				prevAssociation = association;
				headTokenNum = tokenId.getTokenNum();
			}
			merkleAccount.setNumAssociations(numAssociations);
			merkleAccount.setNumPositiveBalances(numPositiveBalances);
			merkleAccount.setHeadTokenId(headTokenNum);
			merkleAccount.forgetAssociatedTokens();
		}
	}

	private ReleaseTwentyFiveMigration() {
		throw new UnsupportedOperationException("Utility class");
	}
}
