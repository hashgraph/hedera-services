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
import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.ledger.HederaLedger.ACCOUNT_ID_COMPARATOR;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.DETACHED_ACCOUNT;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.EXPIRED_ACCOUNT_READY_TO_RENEW;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.OTHER;
import static com.hedera.services.state.merkle.MerkleEntityAssociation.fromAccountTokenRel;
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

	/* Only needed for interoperability, will be removed during refactor */
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

		Pair<List<EntityId>, List<CurrencyAdjustments>> displacements = Pair.of(new ArrayList<>(), new ArrayList<>());
		final var lastClassifiedTokens = lastClassifiedAccount.tokens();
		if (lastClassifiedTokens.numAssociations() > 0) {
			final var grpcId = lastClassifiedEntityId.toGrpcAccountId();
			final var currentTokens = tokens.get();
			for (var tId : lastClassifiedTokens.asTokenIds()) {
				doReturnToTreasury(grpcId, tId, displacements, currentTokens);
			}
		}

		/* Remove the entry from auto created accounts map if there is an entry in the map */
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
			AccountID expired,
			TokenID scopedToken,
			Pair<List<EntityId>, List<CurrencyAdjustments>> displacements,
			MerkleMap<EntityNum, MerkleToken> currentTokens
	) {
		final var currentTokenRels = tokenRels.get();
		final var expiredRel = fromAccountTokenRel(expired, scopedToken);
		final var relStatus = currentTokenRels.get(expiredRel);
		final long balance = relStatus.getBalance();

		currentTokenRels.remove(expiredRel);

		final var tKey = EntityNum.fromTokenId(scopedToken);
		if (!currentTokens.containsKey(tKey)) {
			return;
		}

		final var token = currentTokens.get(tKey);
		if (token.isDeleted()) {
			return;
		}

		if (balance == 0L) {
			return;
		}

		final var treasury = token.treasury().toGrpcAccountId();
		final boolean expiredFirst = ACCOUNT_ID_COMPARATOR.compare(expired, treasury) < 0;
		displacements.getLeft().add(EntityId.fromGrpcTokenId(scopedToken));
		final var expiredId = EntityId.fromGrpcAccountId(expired);
		final var treasuryId = EntityId.fromGrpcAccountId(treasury);
		displacements.getRight().add(new CurrencyAdjustments(
				expiredFirst ? new long[] { -balance, +balance } : new long[] { +balance, -balance },
				expiredFirst ? List.of(expiredId, treasuryId) : List.of(treasuryId, expiredId)
		));

		final var treasuryRel = fromAccountTokenRel(treasury, scopedToken);
		final var mutableTreasuryRelStatus = currentTokenRels.getForModify(treasuryRel);
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
