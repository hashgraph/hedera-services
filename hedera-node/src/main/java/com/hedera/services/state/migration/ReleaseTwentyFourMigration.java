package com.hedera.services.state.migration;

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

import com.hedera.services.ServicesState;
import com.hedera.services.state.initialization.BackedSystemAccountsCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Release 0.24.x migration to update network state with accounts 0.0.800 and 0.0.801 to fund staking rewards.
 * These accounts are the only immutable non-contract accounts---that is, their keys are empty key lists. They
 * do not expire for a millennium.
 */
public class ReleaseTwentyFourMigration {
	private static final Logger log = LogManager.getLogger(ReleaseTwentyFourMigration.class);

	public static void ensureStakingFundAccounts(final ServicesState initializingState) {
		final var accounts = initializingState.accounts();
		BackedSystemAccountsCreator.STAKING_FUND_ACCOUNTS.forEach(num -> ensureImmutable(num, accounts));
	}

	private static void ensureImmutable(
			final EntityNum num,
			final MerkleMap<EntityNum, MerkleAccount> accounts
	) {
		if (accounts.containsKey(num)) {
			BackedSystemAccountsCreator.customizeAsStakingFund(accounts.getForModify(num));
			log.info("Customized account 0.0.{} as immutable w/ 1000 year expiry", num.longValue());
		} else {
			final var newAccount = new MerkleAccount();
			BackedSystemAccountsCreator.customizeAsStakingFund(newAccount);
			accounts.put(num, newAccount);
			log.info("Created account 0.0.{} as immutable w/ 1000 year expiry", num.longValue());
		}
	}

	private ReleaseTwentyFourMigration() {
		throw new UnsupportedOperationException("Utility class");
	}
}
