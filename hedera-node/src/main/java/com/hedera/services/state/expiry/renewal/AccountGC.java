package com.hedera.services.state.expiry.renewal;

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

import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Singleton
public class AccountGC {
	private final AliasManager aliasManager;
	private final SigImpactHistorian sigImpactHistorian;
	private final TreasuryReturnHelper treasuryReturnHelper;
	private final BackingStore<AccountID, MerkleAccount> backingAccounts;

	@Inject
	public AccountGC(
			final AliasManager aliasManager,
			final SigImpactHistorian sigImpactHistorian,
			final TreasuryReturnHelper treasuryReturnHelper,
			final BackingStore<AccountID, MerkleAccount> backingAccounts
	) {
		this.aliasManager = aliasManager;
		this.backingAccounts = backingAccounts;
		this.sigImpactHistorian = sigImpactHistorian;
		this.treasuryReturnHelper = treasuryReturnHelper;
	}

	public TreasuryReturns expireBestEffort(final EntityNum num, final MerkleAccount account) {
		List<EntityId> tokenTypes = Collections.emptyList();
		List<CurrencyAdjustments> returnTransfers = Collections.emptyList();

		final var accountTokens = account.tokens();
		if (accountTokens.numAssociations() > 0) {
			tokenTypes = new ArrayList<>();
			returnTransfers = new ArrayList<>();
			final var accountId = num.toGrpcAccountId();
			for (final var tokenId : accountTokens.asTokenIds()) {
				treasuryReturnHelper.updateReturns(accountId, tokenId, tokenTypes, returnTransfers);
			}
		}

		if (aliasManager.forgetAlias(account.getAlias())) {
			sigImpactHistorian.markAliasChanged(account.getAlias());
		}
		backingAccounts.remove(num.toGrpcAccountId());
		sigImpactHistorian.markEntityChanged(num.longValue());

		return new TreasuryReturns(tokenTypes, returnTransfers, true);
	}
}
