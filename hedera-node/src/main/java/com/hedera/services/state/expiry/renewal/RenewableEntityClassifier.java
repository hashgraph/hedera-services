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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Supplier;

import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_ACCOUNT;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_CONTRACT;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_CONTRACT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.EXPIRED_ACCOUNT_READY_TO_RENEW;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.EXPIRED_CONTRACT_READY_TO_RENEW;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.OTHER;
import static com.hedera.services.utils.EntityNum.fromAccountId;

/**
 * Helper for renewing and removing expired entities. Only crypto accounts are supported in this implementation.
 */
@Singleton
public class RenewableEntityClassifier {
	private static final Logger log = LogManager.getLogger(RenewableEntityClassifier.class);

	private final TokenStore tokenStore;
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;

	private EntityNum lastClassifiedNum;
	private MerkleAccount lastClassified = null;

	@Inject
	public RenewableEntityClassifier(
			final TokenStore tokenStore,
			final GlobalDynamicProperties dynamicProperties,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts
	) {
		this.accounts = accounts;
		this.tokenStore = tokenStore;
		this.dynamicProperties = dynamicProperties;
	}

	public RenewableEntityType classify(final EntityNum candidateNum, final long now) {
		lastClassifiedNum = candidateNum;
		final var curAccounts = accounts.get();
		if (!curAccounts.containsKey(lastClassifiedNum)) {
			return OTHER;
		} else {
			lastClassified = curAccounts.get(lastClassifiedNum);
			final long expiry = lastClassified.getExpiry();
			if (expiry > now) {
				return OTHER;
			}
			final var isContract = lastClassified.isSmartContract();
			if (lastClassified.getBalance() > 0) {
				return isContract ? EXPIRED_CONTRACT_READY_TO_RENEW : EXPIRED_ACCOUNT_READY_TO_RENEW;
			}
			if (lastClassified.isDeleted()) {
				return isContract ? DETACHED_CONTRACT_GRACE_PERIOD_OVER : DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
			}
			final long gracePeriodEnd = expiry + dynamicProperties.autoRenewGracePeriod();
			if (gracePeriodEnd > now) {
				return isContract ? DETACHED_CONTRACT : DETACHED_ACCOUNT;
			}
			if (lastClassified.isTokenTreasury()) {
				return DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN;
			}
			return isContract ? DETACHED_CONTRACT_GRACE_PERIOD_OVER : DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
		}
	}

	public MerkleAccount getLastClassified() {
		return lastClassified;
	}

	// --- Internal helpers ---
	void renewLastClassifiedWith(long fee, long renewalPeriod) {
		assertHasLastClassifiedAccount();
		assertLastClassifiedAccountCanAfford(fee);

		final var currentAccounts = accounts.get();

		final var mutableLastClassified = currentAccounts.getForModify(lastClassifiedNum);
		final long newExpiry = mutableLastClassified.getExpiry() + renewalPeriod;
		final long newBalance = mutableLastClassified.getBalance() - fee;
		mutableLastClassified.setExpiry(newExpiry);
		mutableLastClassified.setBalanceUnchecked(newBalance);

		final var fundingId = fromAccountId(dynamicProperties.fundingAccount());
		final var mutableFundingAccount = currentAccounts.getForModify(fundingId);
		final long newFundingBalance = mutableFundingAccount.getBalance() + fee;
		mutableFundingAccount.setBalanceUnchecked(newFundingBalance);

		log.debug("Renewed {} at a price of {}tb", lastClassifiedNum, fee);
	}

	private void assertHasLastClassifiedAccount() {
		if (lastClassified == null) {
			throw new IllegalStateException("Cannot remove a last classified account; none is present!");
		}
	}

	private void assertLastClassifiedAccountCanAfford(long fee) {
		if (lastClassified.getBalance() < fee) {
			var msg = "Cannot charge " + fee + " to account number " + lastClassifiedNum.longValue() + "!";
			throw new IllegalStateException(msg);
		}
	}
}
