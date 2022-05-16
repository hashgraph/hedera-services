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
import static com.hedera.services.utils.MapValueListUtils.insertInPlaceAtMapValueListHead;
import static com.hedera.services.utils.MapValueListUtils.linkInPlaceAtMapValueListHead;
import static com.hedera.services.utils.MapValueListUtils.unlinkInPlaceFromMapValueList;

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

	/**
	 * Given the previous owner and new owner of the NFT with some id, updates the link fields of the
	 * {@code accounts} and {@code uniqueTokens} maps.
	 *
	 * <p>If the new owner is null, the call implies a burn.
	 *
	 * <p>If the previous owner is null, the call implies a "non-treasury" mint  via a multi-stage
	 * contract operation. In this case, there is no existing NFT to in the {@code uniqueTokens} map,
	 * and the {@code linksManager} must insert one itself.
	 *
	 * @param from the previous owner of the NFT, if any
	 * @param to the new owner of the NFT, if any
	 * @param nftId the id of the NFT changing owners
	 * @return the newly minted NFT, if one needed to be inserted
	 */
	@Nullable
	public MerkleUniqueToken updateLinks(
			@Nullable final EntityNum from,
			@Nullable final EntityNum to,
			@Nonnull final EntityNumPair nftId
	) {
		final var curAccounts = accounts.get();
		final var curTokens = tokens.get();
		final var curUniqueTokens = uniqueTokens.get();

		final var token = curTokens.get(nftId.getHiOrderAsNum());
		final var listMutation = new UniqueTokensListRemoval(curUniqueTokens);

		MerkleUniqueToken insertedNft = null;
		// Update "from" account
		if (isValidAndNotTreasury(from, token)) {
			final var fromAccount = curAccounts.getForModify(from);
			var rootKey = rootKeyOf(fromAccount);
			// Outside a contract operation, this would be an invariant failure (if a non-treasury account
			// is being wiped, it must certainly own at least the wiped NFT); but a contract can easily transfer
			// can a NFT back to the treasury and burn it in the same transaction
			if (rootKey != null) {
				rootKey = unlinkInPlaceFromMapValueList(nftId, rootKey, listMutation);
			}
			fromAccount.setHeadNftId((rootKey == null) ? 0 : rootKey.getHiOrderAsLong());
			fromAccount.setHeadNftSerialNum((rootKey == null) ? 0 : rootKey.getLowOrderAsLong());
		}

		// Update "to" account
		if (isValidAndNotTreasury(to, token)) {
			final var toAccount = curAccounts.getForModify(to);
			final var nftNumPair = nftId.asNftNumPair();
			var nft = listMutation.getForModify(nftId);
			var rootKey = rootKeyOf(toAccount);
			if (nft != null) {
				linkInPlaceAtMapValueListHead(nftId, nft, rootKey, null, listMutation);
			} else {
				insertedNft = new MerkleUniqueToken();
				insertInPlaceAtMapValueListHead(nftId, insertedNft, rootKey, null, listMutation);
			}
			toAccount.setHeadNftId(nftNumPair.tokenNum());
			toAccount.setHeadNftSerialNum(nftNumPair.serialNum());
		}

		return insertedNft;
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
