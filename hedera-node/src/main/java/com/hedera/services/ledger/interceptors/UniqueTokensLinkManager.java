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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.NftNumPair;
import com.swirlds.merkle.map.MerkleMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.function.Supplier;

import static com.hedera.services.utils.NftNumPair.MISSING_NFT_NUM_PAIR;

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

		final var nft = curUniqueTokens.get(nftId);
		final var token = curTokens.get(nftId.getHiOrderAsNum());

		// Update `from` Account
		if (!from.equals(token.treasuryNum())) {
			final var fromAccount = curAccounts.getForModify(from);
			var rootKey = rootKeyOf(fromAccount);

			if (nftId.equals(rootKey)) {
				// make head point to next
				final var nextNftId = nft.getNext();
				if (!nextNftId.equals(MISSING_NFT_NUM_PAIR)) {
					final var nextNft = curUniqueTokens.getForModify(nextNftId.asEntityNumPair());
					nextNft.setPrev(MISSING_NFT_NUM_PAIR);
				}
				fromAccount.setHeadNftId(nextNftId.tokenNum());
				fromAccount.setHeadNftSerialNum(nextNftId.serialNum());
			} else {
				final var nextNftId = nft.getNext();
				final var prevNftId = nft.getPrev();
				final var prevNft = curUniqueTokens.getForModify(prevNftId.asEntityNumPair());
				prevNft.setNext(nextNftId);
				if (!nextNftId.equals(MISSING_NFT_NUM_PAIR)) {
					final var nextNft = curUniqueTokens.getForModify(nextNftId.asEntityNumPair());
					nextNft.setPrev(prevNftId);
				}
			}
		}


		// update `to` Account
		if (to != null && !to.equals(token.treasuryNum())) {
			final var toAccount = curAccounts.getForModify(to);
			final var nftNumPair = nftId.asNftNumPair();
			final var currHeadNftNumTo = toAccount.getHeadNftId();
			final var currHeadNftSerialNumTo = toAccount.getHeadNftSerialNum();
			final var currHeadNftIdTo = NftNumPair.fromLongs(currHeadNftNumTo, currHeadNftSerialNumTo);

			nft.setNext(currHeadNftIdTo);
			if (!currHeadNftIdTo.equals(MISSING_NFT_NUM_PAIR)) {
				final var currHead = curUniqueTokens.getForModify(currHeadNftIdTo.asEntityNumPair());
				currHead.setPrev(nftNumPair);
			}
			toAccount.setHeadNftId(nftNumPair.tokenNum());
			toAccount.setHeadNftSerialNum(nftNumPair.serialNum());
		}
	}

	@Nullable
	private EntityNumPair rootKeyOf(final MerkleAccount account) {
		final var headNum = account.getHeadNftId();
		final var headSerialNum = account.getHeadNftSerialNum();
		return headNum == 0 ? null : EntityNumPair.fromLongs(headNum, headSerialNum);
	}
}
