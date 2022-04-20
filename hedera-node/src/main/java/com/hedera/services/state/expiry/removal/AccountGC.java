package com.hedera.services.state.expiry.removal;

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

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.backgroundSystemTasks.DissociateNftRemovals;
import com.hedera.services.state.expiry.TokenRelsListRemoval;
import com.hedera.services.state.expiry.UniqueTokensListRemoval;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.MapValueListUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.utils.EntityNumPair.MISSING_NUM_PAIR;
import static com.hedera.services.utils.NftNumPair.MISSING_NFT_NUM_PAIR;

@Singleton
public class AccountGC {
	private final AliasManager aliasManager;
	private final SigImpactHistorian sigImpactHistorian;
	private final TreasuryReturnHelper treasuryReturnHelper;
	private final BackingStore<AccountID, MerkleAccount> backingAccounts;
	private final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels;
	private final Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> uniqueTokens;
	private final GlobalDynamicProperties dynamicProperties;

	private RemovalFacilitation removalFacilitation = MapValueListUtils::removeFromMapValueList;

	@Inject
	public AccountGC(
			final AliasManager aliasManager,
			final SigImpactHistorian sigImpactHistorian,
			final TreasuryReturnHelper treasuryReturnHelper,
			final BackingStore<AccountID, MerkleAccount> backingAccounts,
			final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels,
			final Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> uniqueTokens,
			final GlobalDynamicProperties dynamicProperties
	) {
		this.tokenRels = tokenRels;
		this.aliasManager = aliasManager;
		this.backingAccounts = backingAccounts;
		this.sigImpactHistorian = sigImpactHistorian;
		this.treasuryReturnHelper = treasuryReturnHelper;
		this.uniqueTokens = uniqueTokens;
		this.dynamicProperties = dynamicProperties;
	}

	public TreasuryReturns expireBestEffort(final EntityNum expiredAccountNum, final MerkleAccount account) {
		List<EntityId> tokenTypes = Collections.emptyList();
		List<CurrencyAdjustments> returnTransfers = Collections.emptyList();
		var expectedRels = account.getNumAssociations();
		var done = account.getNftsOwned() == 0;
		if (expectedRels > 0) {
			tokenTypes = new ArrayList<>();
			returnTransfers = new ArrayList<>();
			doTreasuryReturnsWith(
					expectedRels,
					expiredAccountNum,
					account.getLatestAssociation(),
					tokenTypes,
					returnTransfers,
					tokenRels.get());
			// set num rels to 0 as we remove all associations
			account.setNumAssociations(0);
		}

		if (!done) {
			final var nftsOwned = account.getNftsOwned();
			done = returnNfts(
					nftsOwned,
					account.getHeadNftId(),
					account.getHeadNftSerialNum(),
					uniqueTokens.get());
			final var remainingNfts = done ? 0 : nftsOwned - dynamicProperties.getMaxReturnedNftsPerTouch();
			account.setNftsOwned(remainingNfts);
		}

		if (done) {
			backingAccounts.remove(expiredAccountNum.toGrpcAccountId());
			sigImpactHistorian.markEntityChanged(expiredAccountNum.longValue());
			if (aliasManager.forgetAlias(account.getAlias())) {
				sigImpactHistorian.markAliasChanged(account.getAlias());
			}
		}

		return new TreasuryReturns(tokenTypes, returnTransfers, done);
	}

	private boolean returnNfts(
			final long nftsOwned,
			final long headNftNum,
			final long headSerialNum,
			final MerkleMap<EntityNumPair, MerkleUniqueToken> currUniqueTokens
	) {
		final var uniqueTokensRemoval = new UniqueTokensListRemoval(currUniqueTokens);
		var nftKey = EntityNumPair.fromLongs(headNftNum, headSerialNum);
		var i = Math.min(nftsOwned, dynamicProperties.getMaxReturnedNftsPerTouch());
		while (nftKey != null && i-- > 0) {
			nftKey = treasuryReturnHelper.updateNftReturns(nftKey, uniqueTokensRemoval);
		}
		return nftsOwned < dynamicProperties.getMaxReturnedNftsPerTouch();
	}

	public int burnNfts(final DissociateNftRemovals nftRemovalTask, final int maxTouches) {
		final var currUniqueTokens = uniqueTokens.get();
		final var targetTokenNum = nftRemovalTask.getTargetTokenNum();
		final var totalSerialsToRemove = nftRemovalTask.getSerialsCount();
		var nftKey = EntityNumPair.fromLongs(
				nftRemovalTask.getHeadTokenNum(), nftRemovalTask.getHeadSerialNum());
		var touched = 0;
		while (nftKey != MISSING_NUM_PAIR && touched < maxTouches && touched < totalSerialsToRemove) {
			final var nft = currUniqueTokens.get(nftKey);
			final var nextKey = nft.getNext();
			if(nftKey.getHiOrderAsLong() == targetTokenNum) {
				final var prevKey = nft.getPrev();
				if (prevKey != MISSING_NFT_NUM_PAIR) {
					final var prevNft = currUniqueTokens.getForModify(prevKey.asEntityNumPair());
					prevNft.setNext(nextKey);
				}
				if (nextKey != MISSING_NFT_NUM_PAIR) {
					final var nextNft = currUniqueTokens.getForModify(nextKey.asEntityNumPair());
					nextNft.setPrev(prevKey);
				}
				currUniqueTokens.remove(nftKey);
			}
			nftKey = nextKey.asEntityNumPair();
			touched++;
		}
		nftRemovalTask.setSerialsCount(totalSerialsToRemove - touched);
		nftRemovalTask.setHeadSerialNum(nftKey.getHiOrderAsLong());
		nftRemovalTask.setHeadSerialNum(nftKey.getLowOrderAsLong());
		return touched;
	}

	private void doTreasuryReturnsWith(
			final int expectedRels,
			final EntityNum expiredAccountNum,
			final EntityNumPair firstRelKey,
			final List<EntityId> tokenTypes,
			final List<CurrencyAdjustments> returnTransfers,
			final MerkleMap<EntityNumPair, MerkleTokenRelStatus> curRels
	) {
		final var listRemoval = new TokenRelsListRemoval(expiredAccountNum.longValue(), curRels);
		var i = expectedRels;
		var relKey = firstRelKey;
		while (relKey != null && i-- > 0) {
			final var rel = curRels.get(relKey);
			final var tokenNum = relKey.getLowOrderAsNum();
			final var tokenBalance = rel.getBalance();
			if (tokenBalance > 0) {
				treasuryReturnHelper.updateReturns(expiredAccountNum, tokenNum, tokenBalance, returnTransfers);
			}
			// We are always removing the root, hence receiving the new root
			relKey = removalFacilitation.removeNext(relKey, relKey, listRemoval);
			tokenTypes.add(tokenNum.toEntityId());
		}
	}

	@FunctionalInterface
	interface RemovalFacilitation {
		EntityNumPair removeNext(EntityNumPair key, EntityNumPair root, TokenRelsListRemoval tokenRelsListRemoval);
	}

	@VisibleForTesting
	void setRemovalFacilitation(final RemovalFacilitation removalFacilitation) {
		this.removalFacilitation = removalFacilitation;
	}
}
