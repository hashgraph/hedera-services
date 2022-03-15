package com.hedera.services.state.expiry.renewal;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.context.primitives.StateView.REMOVED_TOKEN;
import static com.hedera.services.context.primitives.StateView.doBoundedIteration;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.DETACHED_ACCOUNT;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.EXPIRED_ACCOUNT_READY_TO_RENEW;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.OTHER;
import static com.hedera.services.utils.EntityNum.fromAccountId;

/**
 * Helper for renewing and removing expired entities. Only crypto accounts are supported in this implementation.
 */
@Singleton
public class RenewalHelper {
	private static final Logger log = LogManager.getLogger(RenewalHelper.class);

	private final TokenStore tokenStore;
	private final SigImpactHistorian sigImpactHistorian;
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels;

	private final BackingStore<AccountID, MerkleAccount> backingAccounts;

	private MerkleAccount lastClassifiedAccount = null;
	private EntityNum lastClassifiedEntityId;

	private AliasManager aliasManager;

	@Inject
	public RenewalHelper(
			final TokenStore tokenStore,
			final SigImpactHistorian sigImpactHistorian,
			final GlobalDynamicProperties dynamicProperties,
			final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels,
			final BackingStore<AccountID, MerkleAccount> backingAccounts,
			final AliasManager aliasManager
	) {
		this.tokens = tokens;
		this.tokenStore = tokenStore;
		this.accounts = accounts;
		this.tokenRels = tokenRels;
		this.dynamicProperties = dynamicProperties;
		this.sigImpactHistorian = sigImpactHistorian;
		this.backingAccounts = backingAccounts;
		this.aliasManager = aliasManager;
	}

	public ExpiredEntityClassification classify(long candidateNum, long now) {
		lastClassifiedEntityId = EntityNum.fromLong(candidateNum);
		var currentAccounts = accounts.get();

		if (!currentAccounts.containsKey(lastClassifiedEntityId)) {
			return OTHER;
		} else {
			lastClassifiedAccount = currentAccounts.get(lastClassifiedEntityId);
			if (lastClassifiedAccount.isSmartContract()) {
				return OTHER;
			}

			final long expiry = lastClassifiedAccount.getExpiry();
			if (expiry > now) {
				return OTHER;
			}

			if (lastClassifiedAccount.getBalance() > 0) {
				return EXPIRED_ACCOUNT_READY_TO_RENEW;
			}
			if (lastClassifiedAccount.isDeleted()) {
				return DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
			}

			final long gracePeriodEnd = expiry + dynamicProperties.autoRenewGracePeriod();
			if (gracePeriodEnd > now) {
				return DETACHED_ACCOUNT;
			}
			final var grpcId = lastClassifiedEntityId.toGrpcAccountId();
			if (tokenStore.isKnownTreasury(grpcId)) {
				return DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN;
			}

			return DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
		}
	}

	Pair<List<EntityId>, List<CurrencyAdjustments>> removeLastClassifiedAccount() {
		assertHasLastClassifiedAccount();
		if (lastClassifiedAccount.getBalance() > 0) {
			throw new IllegalStateException("Cannot remove the last classified account, has non-zero balance!");
		}

		final Pair<List<EntityId>, List<CurrencyAdjustments>> displacements =
				Pair.of(new ArrayList<>(), new ArrayList<>());
		final var curTokens = tokens.get();
		final var curTokenRels = tokenRels.get();
		doBoundedIteration(curTokenRels, curTokens, lastClassifiedAccount, (token, rel) ->
				doReturnToTreasury(rel, token, curTokenRels, displacements));

		// Remove the entry from auto created accounts map if there is an entry in the map
		if (aliasManager.forgetAliasIfPresent(lastClassifiedEntityId, accounts.get())) {
			sigImpactHistorian.markAliasChanged(lastClassifiedAccount.getAlias());
		}

		backingAccounts.remove(lastClassifiedEntityId.toGrpcAccountId());
		sigImpactHistorian.markEntityChanged(lastClassifiedEntityId.longValue());

		log.debug("Removed {}, displacing {}", lastClassifiedEntityId, displacements);

		return displacements;
	}

	void renewLastClassifiedWith(long fee, long renewalPeriod) {
		assertHasLastClassifiedAccount();
		assertLastClassifiedAccountCanAfford(fee);

		final var currentAccounts = accounts.get();

		final var mutableLastClassified = currentAccounts.getForModify(lastClassifiedEntityId);
		final long newExpiry = mutableLastClassified.getExpiry() + renewalPeriod;
		final long newBalance = mutableLastClassified.getBalance() - fee;
		mutableLastClassified.setExpiry(newExpiry);
		mutableLastClassified.setBalanceUnchecked(newBalance);

		final var fundingId = fromAccountId(dynamicProperties.fundingAccount());
		final var mutableFundingAccount = currentAccounts.getForModify(fundingId);
		final long newFundingBalance = mutableFundingAccount.getBalance() + fee;
		mutableFundingAccount.setBalanceUnchecked(newFundingBalance);

		log.debug("Renewed {} at a price of {}tb", lastClassifiedEntityId, fee);
	}

	public MerkleAccount getLastClassifiedAccount() {
		return lastClassifiedAccount;
	}

	private void doReturnToTreasury(
			final MerkleTokenRelStatus expiredRel,
			final MerkleToken token,
			final MerkleMap<EntityNumPair, MerkleTokenRelStatus> curTokenRels,
			final Pair<List<EntityId>, List<CurrencyAdjustments>> displacements
	) {
		final long balance = expiredRel.getBalance();
		final var relKey = expiredRel.getKey();
		curTokenRels.remove(relKey);

		if (token == REMOVED_TOKEN || token.isDeleted() || balance == 0) {
			return;
		}

		final var expiredId = relKey.getHighOrderAsEntityId();
		final var treasuryId = token.treasury();
		final boolean expiredFirst = expiredId.num() < treasuryId.num();
		final var tokenId = relKey.getLowOrderAsEntityId();
		displacements.getLeft().add(tokenId);
		displacements.getRight().add(new CurrencyAdjustments(
				expiredFirst ? new long[] { -balance, +balance } : new long[] { +balance, -balance },
				expiredFirst ? new long[] { expiredId.num(), treasuryId.num() } : new long[] { treasuryId.num(),
						expiredId.num() }
		));

		final var treasuryRel = EntityNumPair.fromLongs(treasuryId.num(), tokenId.num());
		final var mutableTreasuryRelStatus = curTokenRels.getForModify(treasuryRel);
		final long newTreasuryBalance = mutableTreasuryRelStatus.getBalance() + balance;
		mutableTreasuryRelStatus.setBalance(newTreasuryBalance);
	}

	private void assertHasLastClassifiedAccount() {
		if (lastClassifiedAccount == null) {
			throw new IllegalStateException("Cannot remove a last classified account; none is present!");
		}
	}

	private void assertLastClassifiedAccountCanAfford(long fee) {
		if (lastClassifiedAccount.getBalance() < fee) {
			var msg = "Cannot charge " + fee + " to account number " + lastClassifiedEntityId.longValue() + "!";
			throw new IllegalStateException(msg);
		}
	}
}
