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
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.expiry.TokenRelsListRemoval;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
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

@Singleton
public class AccountGC {
	private final AliasManager aliasManager;
	private final SigImpactHistorian sigImpactHistorian;
	private final TreasuryReturnHelper treasuryReturnHelper;
	private final BackingStore<AccountID, MerkleAccount> backingAccounts;
	private final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels;

	private RemovalFacilitation removalFacilitation = MapValueListUtils::removeFromMapValueList;

	@Inject
	public AccountGC(
			final AliasManager aliasManager,
			final SigImpactHistorian sigImpactHistorian,
			final TreasuryReturnHelper treasuryReturnHelper,
			final BackingStore<AccountID, MerkleAccount> backingAccounts,
			final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels
	) {
		this.tokenRels = tokenRels;
		this.aliasManager = aliasManager;
		this.backingAccounts = backingAccounts;
		this.sigImpactHistorian = sigImpactHistorian;
		this.treasuryReturnHelper = treasuryReturnHelper;
	}

	public TreasuryReturns expireBestEffort(final EntityNum expiredAccountNum, final MerkleAccount account) {
		List<EntityId> tokenTypes = Collections.emptyList();
		List<CurrencyAdjustments> returnTransfers = Collections.emptyList();
		var expectedRels = account.getNumAssociations();
		if (expectedRels > 0) {
			tokenTypes = new ArrayList<>();
			returnTransfers = new ArrayList<>();
			expireRelsWithTreasuryReturns(
					expectedRels,
					expiredAccountNum,
					account.getLatestAssociation(),
					tokenTypes,
					returnTransfers,
					tokenRels.get());
		}

		backingAccounts.remove(expiredAccountNum.toGrpcAccountId());
		sigImpactHistorian.markEntityChanged(expiredAccountNum.longValue());
		if (aliasManager.forgetAlias(account.getAlias())) {
			sigImpactHistorian.markAliasChanged(account.getAlias());
		}

		return new TreasuryReturns(tokenTypes, returnTransfers, true);
	}

	private void expireRelsWithTreasuryReturns(
			final int maxRels,
			final EntityNum expiredAccountNum,
			final EntityNumPair firstRelKey,
			final List<EntityId> tokenTypes,
			final List<CurrencyAdjustments> returnTransfers,
			final MerkleMap<EntityNumPair, MerkleTokenRelStatus> curRels
	) {
		final var listRemoval = new TokenRelsListRemoval(expiredAccountNum.longValue(), curRels);
		var i = maxRels;
		var relKey = firstRelKey;
		while (relKey != null && i-- > 0) {
			final var rel = curRels.get(relKey);
			final var tokenNum = relKey.getLowOrderAsNum();
			if (rel.getBalance() > 0) {
				treasuryReturnHelper.updateReturns(expiredAccountNum, tokenNum, 0, returnTransfers);
			}
			// We are always removing the root, hence receiving the new root
			relKey = removalFacilitation.removeNext(relKey, relKey, listRemoval);
			tokenTypes.add(tokenNum.toEntityId());
		}
	}

	@FunctionalInterface
	interface RemovalFacilitation {
		EntityNumPair removeNext(EntityNumPair key, EntityNumPair root, TokenRelsListRemoval listRemoval);
	}

	@VisibleForTesting
	void setRemovalFacilitation(final RemovalFacilitation removalFacilitation) {
		this.removalFacilitation = removalFacilitation;
	}
}
