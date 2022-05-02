package com.hedera.services.ledger.interceptors;

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

import com.hedera.services.state.expiry.UniqueTokensListRemoval;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.merkle.map.MerkleMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.function.Supplier;

import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hedera.services.utils.MapValueListUtils.inPlaceInsertAtMapValueListHead;
import static com.hedera.services.utils.MapValueListUtils.unlinkFromMapValueLink;

public class UniqueTokensLinkManager {
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens;
	private final Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> uniqueTokens;

	@Inject
	public UniqueTokensLinkManager(
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens,
			final Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> uniqueTokens
	) {
		this.accounts = accounts;
		this.tokens = tokens;
		this.uniqueTokens = uniqueTokens;
	}

	public void updateLinks(
			@Nonnull final EntityNum from,
			@Nullable final EntityNum to,
			@Nonnull final EntityNumPair nftId
	) {
		final var curAccounts = accounts.get();
		final var curTokens = tokens.get();
		final var curUniqueTokens = uniqueTokens.get();

		final var token = curTokens.get(nftId.getHiOrderAsNum());
		final var listMutation = new UniqueTokensListRemoval(curUniqueTokens);

		// Update `from` Account
		if (isValidAndNotTreasury(from, token)) {
			final var fromAccount = curAccounts.getForModify(from);
			var rootKey = rootKeyOf(fromAccount);

			if (rootKey != null) {
				rootKey = unlinkFromMapValueLink(nftId, rootKey, listMutation);
			}

			fromAccount.setHeadNftId((rootKey == null) ? 0 : rootKey.getHiOrderAsLong());
			fromAccount.setHeadNftSerialNum((rootKey == null) ? 0 : rootKey.getLowOrderAsLong());
		}


		// update `to` Account
		if (isValidAndNotTreasury(to, token)) {
			final var nft = listMutation.getForModify(nftId);
			if (nft != null) {
				final var toAccount = curAccounts.getForModify(to);
				final var nftNumPair = nftId.asNftNumPair();
				final var rootKey = rootKeyOf(toAccount);

				inPlaceInsertAtMapValueListHead(
						nftId, nft, rootKey, null, listMutation, false);
				toAccount.setHeadNftId(nftNumPair.tokenNum());
				toAccount.setHeadNftSerialNum(nftNumPair.serialNum());
			}
		}
	}

	private boolean isValidAndNotTreasury(EntityNum accountNum, MerkleToken token) {
		return accountNum!= null && !accountNum.equals(MISSING_NUM) && !accountNum.equals(token.treasuryNum());
	}

	@Nullable
	private EntityNumPair rootKeyOf(final MerkleAccount account) {
		final var headNum = account.getHeadNftId();
		final var headSerialNum = account.getHeadNftSerialNum();
		return headNum == 0 ? null : EntityNumPair.fromLongs(headNum, headSerialNum);
	}
}
