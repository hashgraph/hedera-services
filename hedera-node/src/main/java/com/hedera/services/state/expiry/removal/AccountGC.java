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
import com.hedera.services.state.expiry.TokenRelsListMutation;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.virtual.UniqueTokenKey;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.MapValueListUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Responsible for "garbage collection" of an expired account whose grace period has ended; such an account
 * may still own fungible token units or NFTs, and we need to either,
 * <ol>
 *     <li>Return these assets to the treasuries of their respective token types; or,</li>
 *     <li>"Burn" them, if they belong to a token type that has been deleted or removed.</li>
 * </ol>
 * Doing a treasury return or burn of fungible units is straightforward. NFTs are a problem, however---we
 * do not know <i>which serial numbers</i> the expired account owned. The current implementation responds
 * by simply "stranding" any such NFTs; that is, leaving them in state with an {@code owner} field still
 * set to the now-missing account.
 *
 * <p>The full implementation, with NFT treasury return, will be done through the tasks listed in issue
 * https://github.com/hashgraph/hedera-services/issues/3174.
 */
@Singleton
public class AccountGC {
    private final AliasManager aliasManager;
    private final SigImpactHistorian sigImpactHistorian;
    private final TreasuryReturnHelper treasuryReturnHelper;
    private final BackingStore<AccountID, MerkleAccount> backingAccounts;
    private final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels;
    private final Supplier<VirtualMap<UniqueTokenKey, UniqueTokenValue>> uniqueTokens;
    private final GlobalDynamicProperties dynamicProperties;

	private RemovalFacilitation removalFacilitation = MapValueListUtils::removeInPlaceFromMapValueList;

    @Inject
    public AccountGC(
            final AliasManager aliasManager,
            final SigImpactHistorian sigImpactHistorian,
            final TreasuryReturnHelper treasuryReturnHelper,
            final BackingStore<AccountID, MerkleAccount> backingAccounts,
            final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels,
            final Supplier<VirtualMap<UniqueTokenKey, UniqueTokenValue>> uniqueTokens,
            final GlobalDynamicProperties dynamicProperties) {
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
			returnNftsToTreasury(
					nftsOwned,
					account.getHeadNftId(),
					account.getHeadNftSerialNum(),
					uniqueTokens.get());

			final var remainingNfts = nftsOwned < dynamicProperties.getMaxReturnedNftsPerTouch() ?
					0 : nftsOwned - dynamicProperties.getMaxReturnedNftsPerTouch();
			account.setNftsOwned(remainingNfts);
			done = remainingNfts == 0;
		}

		if (done) {
			backingAccounts.remove(expiredAccountNum.toGrpcAccountId());
			sigImpactHistorian.markEntityChanged(expiredAccountNum.longValue());
			if (aliasManager.forgetAlias(account.getAlias())) {
				aliasManager.forgetEvmAddress(account.getAlias());
				sigImpactHistorian.markAliasChanged(account.getAlias());
			}
		}

		return new TreasuryReturns(tokenTypes, returnTransfers, done);
	}

    private void returnNftsToTreasury(
            final long nftsOwned,
            final long headNftNum,
            final long headSerialNum,
            final VirtualMap<UniqueTokenKey, UniqueTokenValue> currUniqueTokens) {
        var nftKey = new UniqueTokenKey(headNftNum, headSerialNum);
        var i = Math.min(nftsOwned, dynamicProperties.getMaxReturnedNftsPerTouch());
        while (nftKey != null && i-- > 0) {
            nftKey = treasuryReturnHelper.updateNftReturns(nftKey, currUniqueTokens);
        }
    }

	private void doTreasuryReturnsWith(
			final int expectedRels,
			final EntityNum expiredAccountNum,
			final EntityNumPair firstRelKey,
			final List<EntityId> tokenTypes,
			final List<CurrencyAdjustments> returnTransfers,
			final MerkleMap<EntityNumPair, MerkleTokenRelStatus> curRels
	) {
		final var listRemoval = new TokenRelsListMutation(expiredAccountNum.longValue(), curRels);
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
		EntityNumPair removeNext(EntityNumPair key, EntityNumPair root, TokenRelsListMutation listRemoval);
	}

	@VisibleForTesting
	void setRemovalFacilitation(final RemovalFacilitation removalFacilitation) {
		this.removalFacilitation = removalFacilitation;
	}
}
